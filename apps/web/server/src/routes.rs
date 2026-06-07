use std::sync::Arc;

use axum::{
    extract::{Multipart, Path, State},
    http::{header, HeaderValue, StatusCode, Uri},
    response::{IntoResponse, Response},
    routing::{get, post},
    Json, Router,
};
use crate::blossom::{
    download_blob, resolve_blossom_url, upload_blob, BlossomStatusResponse, BlobDescriptor,
};
use axum_extra::extract::{
    cookie::{Cookie, Key, SameSite},
    SignedCookieJar,
};
use serde::{Deserialize, Serialize};
use tokio::sync::Mutex;
use tower_http::services::ServeDir;
use tower_http::set_header::SetResponseHeaderLayer;
use tower_http::trace::TraceLayer;

use crate::config::{
    detected_relay_url, normalize_relay_url, relay_prefill_url, suggested_relay_url, Config,
    DETECTED_RELAY_LABEL,
};
use crate::crypto::{open as crypto_open, seal};
use crate::error::{AppError, AppResult};
use crate::fiatlife_tags::is_fiatlife_d_tag;
use crate::nostr_support::{
    build_addressable_deletion_event, build_app_data_event, build_cypherlog_subscription_event,
    decrypt_from_self, encrypt_to_self, fetch_cypherlog_subscription_records, parse_nsec,
    CypherLogSubscriptionRecord, KIND_APP_DATA, KIND_CYPHERLOG_SUBSCRIPTION,
};
use crate::outbox::{Outbox, OutboxStatus};
use crate::session::{SessionStore, SESSION_COOKIE};
use crate::state::{dedupe_relay_urls, PersistentState, SealedRecord};

#[derive(Clone)]
pub struct AppState {
    pub cfg: Config,
    pub sessions: SessionStore,
    pub persistent: Arc<Mutex<PersistentState>>,
    pub cookie_key: Key,
    pub outbox: Outbox,
}

pub async fn build_router(cfg: Config) -> anyhow::Result<Router> {
    let persistent = PersistentState::load(&cfg.state_path())?;
    let cookie_key = Key::from(&cfg.cookie_signing_key);
    let sessions = SessionStore::new(cfg.session_idle);

    let state = AppState {
        cfg: cfg.clone(),
        sessions,
        persistent: Arc::new(Mutex::new(persistent)),
        cookie_key,
        outbox: Outbox::new(),
    };

    let assets_service = ServeDir::new(cfg.static_dir.join("assets"));

    let api = Router::new()
        .route("/health", get(health))
        .route("/auth/status", get(auth_status))
        .route("/auth/setup", post(auth_setup))
        .route("/auth/unlock", post(auth_unlock))
        .route("/auth/lock", post(auth_lock))
        .route("/auth/wipe", post(auth_wipe))
        .route("/settings/relay", get(get_relay).put(put_relay))
        .route("/crypto/nip44/encrypt-self", post(nip44_encrypt_self))
        .route("/crypto/nip44/decrypt-self", post(nip44_decrypt_self))
        .route("/nostr/app-data", get(list_app_data).post(publish_app_data))
        .route("/nostr/connection", get(relay_connection))
        .route(
            "/nostr/cypherlog/subscriptions",
            get(list_cypherlog_subscriptions),
        )
        .route(
            "/nostr/cypherlog/subscription",
            post(publish_cypherlog_subscription),
        )
        .route("/nostr/deletion", post(publish_nostr_deletion))
        .route("/nostr/outbox", get(outbox_status))
        .route("/nostr/outbox/retry", post(outbox_retry))
        .route("/blossom/status", get(blossom_status))
        .route("/blossom/upload", post(blossom_upload))
        .route("/blossom/:sha256", get(blossom_download_handler));

    let app = Router::new()
        .nest("/api", api)
        .nest_service("/assets", assets_service)
        .fallback(spa_fallback)
        .with_state(state)
        .layer(SetResponseHeaderLayer::if_not_present(
            header::CACHE_CONTROL,
            HeaderValue::from_static("no-store"),
        ))
        .layer(SetResponseHeaderLayer::if_not_present(
            header::X_CONTENT_TYPE_OPTIONS,
            HeaderValue::from_static("nosniff"),
        ))
        .layer(SetResponseHeaderLayer::if_not_present(
            header::REFERRER_POLICY,
            HeaderValue::from_static("no-referrer"),
        ))
        .layer(TraceLayer::new_for_http());

    Ok(app)
}

#[derive(Serialize)]
struct Health {
    ok: bool,
}

async fn health() -> Json<Health> {
    Json(Health { ok: true })
}

#[derive(Serialize)]
pub struct AuthStatus {
    has_state: bool,
    unlocked: bool,
    npub: Option<String>,
    relay_url: Option<String>,
    relay_urls: Vec<String>,
    detected_relay_url: Option<String>,
    detected_relay_label: Option<String>,
    suggested_relay_url: Option<String>,
    relay_prefill_url: Option<String>,
}

fn auth_status_from(p: &PersistentState, unlocked: bool) -> AuthStatus {
    let detected = detected_relay_url();
    let suggested = suggested_relay_url();
    let prefill = relay_prefill_url();
    AuthStatus {
        has_state: p.sealed.is_some(),
        unlocked,
        npub: p.npub.clone(),
        relay_url: p.primary_relay_url().map(str::to_string),
        relay_urls: p.relay_urls().to_vec(),
        detected_relay_label: detected
            .as_ref()
            .or(suggested.as_ref())
            .map(|_| DETECTED_RELAY_LABEL.to_string()),
        detected_relay_url: detected,
        suggested_relay_url: suggested,
        relay_prefill_url: prefill,
    }
}

async fn auth_status(State(s): State<AppState>, jar: SignedCookieJar) -> Json<AuthStatus> {
    let p = s.persistent.lock().await;
    let unlocked = match jar.get(SESSION_COOKIE) {
        Some(c) => s.sessions.touch_unlocked(c.value()).await,
        None => false,
    };
    Json(auth_status_from(&p, unlocked))
}

#[derive(Deserialize)]
pub struct SetupBody {
    nsec: String,
    passphrase: String,
    #[serde(default)]
    relay_url: String,
}

async fn auth_setup(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<SetupBody>,
) -> AppResult<(SignedCookieJar, Json<AuthStatus>)> {
    let (_, identity) = parse_nsec(&body.nsec).map_err(|e| AppError::BadRequest(e.to_string()))?;
    if body.passphrase.len() < 8 {
        return Err(AppError::BadRequest(
            "passphrase must be at least 8 characters".into(),
        ));
    }
    let relay_url = resolve_setup_relay_url(&body.relay_url)?;

    let mut p = s.persistent.lock().await;
    if p.sealed.is_some() {
        return Err(AppError::Conflict(
            "state already initialized; unlock or log out and remove the key from Settings".into(),
        ));
    }

    let blob = seal(&body.passphrase, body.nsec.as_bytes()).map_err(AppError::Internal)?;
    p.sealed = Some(SealedRecord::from_blob(&blob));
    p.set_relay_urls(vec![relay_url]);
    p.npub = Some(identity.npub.clone());
    p.save(&s.cfg.state_path()).map_err(AppError::Internal)?;

    let secret = zeroize::Zeroizing::new(body.nsec.clone().into_bytes());
    let sid = s.sessions.open(secret, identity).await;
    let jar = jar.add(session_cookie(sid, s.cfg.cookie_secure));

    Ok((jar, Json(auth_status_from(&p, true))))
}

#[derive(Deserialize)]
pub struct UnlockBody {
    passphrase: String,
}

async fn auth_unlock(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<UnlockBody>,
) -> AppResult<(SignedCookieJar, Json<AuthStatus>)> {
    let p = s.persistent.lock().await;
    let sealed = p
        .sealed
        .as_ref()
        .ok_or_else(|| AppError::BadRequest("no state to unlock; run setup first".into()))?;
    let blob = sealed.to_blob().map_err(AppError::Internal)?;
    drop(p);

    let plaintext = crypto_open(&body.passphrase, &blob).map_err(|_| AppError::Unauthorized)?;
    let nsec = std::str::from_utf8(plaintext.as_slice())
        .map_err(|e| AppError::Internal(anyhow::anyhow!("stored nsec is not utf-8: {e}")))?;
    let (_, identity) = parse_nsec(nsec).map_err(|e| AppError::Internal(anyhow::anyhow!(e)))?;
    let sid = s.sessions.open(plaintext, identity).await;
    let jar = jar.add(session_cookie(sid, s.cfg.cookie_secure));

    let p = s.persistent.lock().await;
    Ok((jar, Json(auth_status_from(&p, true))))
}

#[derive(Serialize)]
struct OkBody {
    ok: bool,
}

async fn auth_lock(
    State(s): State<AppState>,
    jar: SignedCookieJar,
) -> (SignedCookieJar, Json<OkBody>) {
    if let Some(c) = jar.get(SESSION_COOKIE) {
        s.sessions.close(c.value()).await;
    }
    let jar = jar.remove(Cookie::from(SESSION_COOKIE));
    (jar, Json(OkBody { ok: true }))
}

#[derive(Deserialize)]
struct WipeBody {
    passphrase: String,
    confirmation: String,
}

async fn auth_wipe(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<WipeBody>,
) -> AppResult<(SignedCookieJar, Json<OkBody>)> {
    if body.confirmation.trim() != "DELETE" {
        return Err(AppError::BadRequest(
            "type DELETE in the confirmation field to remove the key".into(),
        ));
    }

    let mut p = s.persistent.lock().await;
    if let Some(sealed) = p.sealed.as_ref() {
        let blob = sealed.to_blob().map_err(AppError::Internal)?;
        crypto_open(&body.passphrase, &blob).map_err(|_| AppError::Unauthorized)?;
    }

    *p = PersistentState::default();
    p.save(&s.cfg.state_path()).map_err(AppError::Internal)?;
    drop(p);

    if let Some(c) = jar.get(SESSION_COOKIE) {
        s.sessions.close(c.value()).await;
    }
    s.sessions.close_all().await;
    let jar = jar.remove(Cookie::from(SESSION_COOKIE));
    Ok((jar, Json(OkBody { ok: true })))
}

#[derive(Serialize, Deserialize)]
pub struct RelayBody {
    #[serde(default)]
    relay_url: String,
    #[serde(default)]
    relay_urls: Vec<String>,
}

async fn get_relay(State(s): State<AppState>, jar: SignedCookieJar) -> AppResult<Json<RelayBody>> {
    require_unlocked(&s, &jar).await?;
    let p = s.persistent.lock().await;
    Ok(Json(RelayBody {
        relay_url: p.primary_relay_url().unwrap_or("").to_string(),
        relay_urls: p.relay_urls().to_vec(),
    }))
}

async fn put_relay(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<RelayBody>,
) -> AppResult<Json<RelayBody>> {
    require_unlocked(&s, &jar).await?;
    let urls = normalize_relay_urls_from_body(&body)?;
    let mut p = s.persistent.lock().await;
    p.set_relay_urls(urls);
    p.save(&s.cfg.state_path()).map_err(AppError::Internal)?;
    Ok(Json(RelayBody {
        relay_url: p.primary_relay_url().unwrap_or("").to_string(),
        relay_urls: p.relay_urls().to_vec(),
    }))
}

#[derive(Deserialize)]
struct Nip44EncryptBody {
    plaintext: String,
}

#[derive(Serialize)]
struct Nip44EncryptResponse {
    ciphertext: String,
}

async fn nip44_encrypt_self(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<Nip44EncryptBody>,
) -> AppResult<Json<Nip44EncryptResponse>> {
    let (keys, _) = require_keys(&s, &jar).await?;
    let ciphertext = encrypt_to_self(&keys, &body.plaintext).map_err(AppError::Internal)?;
    Ok(Json(Nip44EncryptResponse { ciphertext }))
}

#[derive(Deserialize)]
struct Nip44DecryptBody {
    ciphertext: String,
}

#[derive(Serialize)]
struct Nip44DecryptResponse {
    plaintext: String,
}

async fn nip44_decrypt_self(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<Nip44DecryptBody>,
) -> AppResult<Json<Nip44DecryptResponse>> {
    let (keys, _) = require_keys(&s, &jar).await?;
    let plaintext = decrypt_from_self(&keys, &body.ciphertext)
        .map_err(|e| AppError::BadRequest(e.to_string()))?;
    Ok(Json(Nip44DecryptResponse { plaintext }))
}

#[derive(Serialize)]
struct RelayConnectionResponse {
    connected: bool,
    message: Option<String>,
}

async fn relay_connection(
    State(s): State<AppState>,
    jar: SignedCookieJar,
) -> AppResult<Json<RelayConnectionResponse>> {
    let (keys, _) = require_keys(&s, &jar).await?;
    let relay_urls = configured_relay_urls(&s).await?;
    let cfg = s.cfg.clone();
    match crate::nostr_support::fetch_app_data_events_from_relays(
        &keys,
        &relay_urls,
        |url| cfg.relay_connect_options(url),
    )
    .await
    {
        Ok(_) => Ok(Json(RelayConnectionResponse {
            connected: true,
            message: None,
        })),
        Err(err) => Ok(Json(RelayConnectionResponse {
            connected: false,
            message: Some(err.to_string()),
        })),
    }
}

async fn list_app_data(
    State(s): State<AppState>,
    jar: SignedCookieJar,
) -> AppResult<Json<Vec<crate::nostr_support::AppDataRecord>>> {
    let (keys, _) = require_keys(&s, &jar).await?;
    let relay_urls = configured_relay_urls(&s).await?;
    tracing::info!(
        relay_count = relay_urls.len(),
        primary = relay_urls.first().map(String::as_str),
        "fetching FiatLife app-data from relays"
    );
    let cfg = s.cfg.clone();
    let records = crate::nostr_support::fetch_decrypted_app_data(&keys, &relay_urls, |url| {
        cfg.relay_connect_options(url)
    })
    .await
    .map_err(|e| AppError::BadRequest(format!("relay fetch failed: {e}")))?;

    let records: Vec<_> = records
        .into_iter()
        .filter(|r| r.d_tag.as_deref().map(is_fiatlife_d_tag).unwrap_or(false))
        .collect();

    tracing::info!(
        count = records.len(),
        decrypted = records.iter().filter(|r| r.plaintext.is_some()).count(),
        "FiatLife app-data fetch complete"
    );
    Ok(Json(records))
}

#[derive(Deserialize)]
struct PublishAppDataBody {
    d_tag: String,
    plaintext: String,
}

#[derive(Serialize)]
struct PublishAppDataResponse {
    event_id: String,
}

#[derive(Deserialize)]
struct PublishCypherLogSubscriptionBody {
    tags: Vec<Vec<String>>,
}

async fn list_cypherlog_subscriptions(
    State(s): State<AppState>,
    jar: SignedCookieJar,
) -> AppResult<Json<Vec<CypherLogSubscriptionRecord>>> {
    let (keys, _) = require_keys(&s, &jar).await?;
    let relay_urls = configured_relay_urls(&s).await?;
    tracing::info!(
        relay_count = relay_urls.len(),
        primary = relay_urls.first().map(String::as_str),
        "fetching CypherLog kind-37004 subscriptions from relays"
    );
    let cfg = s.cfg.clone();
    let records =
        fetch_cypherlog_subscription_records(&keys, &relay_urls, |url| {
            cfg.relay_connect_options(url)
        })
        .await
        .map_err(|e| AppError::BadRequest(format!("CypherLog fetch failed: {e}")))?;
    tracing::info!(count = records.len(), "CypherLog 37004 fetch complete");
    Ok(Json(records))
}

async fn publish_cypherlog_subscription(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<PublishCypherLogSubscriptionBody>,
) -> AppResult<Json<PublishAppDataResponse>> {
    let d_tag = body
        .tags
        .iter()
        .find(|t| t.first().map(|k| k == "d").unwrap_or(false))
        .and_then(|t| t.get(1))
        .map(|s| s.trim())
        .filter(|s| !s.is_empty())
        .ok_or_else(|| AppError::BadRequest("tags must include a d tag".into()))?;
    if d_tag.starts_with("subscription:") {
        return Err(AppError::BadRequest(
            "CypherLog kind-37004 d tag must be a UUID, not subscription: prefix".into(),
        ));
    }
    let (keys, _) = require_keys(&s, &jar).await?;
    let relay_urls = configured_relay_urls(&s).await?;
    let event = build_cypherlog_subscription_event(&keys, &body.tags)
        .map_err(|e| AppError::BadRequest(format!("sign failed: {e}")))?;
    let event_id = event.id.to_string();
    s.outbox
        .enqueue(s.cfg.clone(), keys, event, relay_urls, "subscription".into())
        .await;
    Ok(Json(PublishAppDataResponse { event_id }))
}

#[derive(Deserialize)]
struct PublishNostrDeletionBody {
    kind: u16,
    d_tag: String,
}

async fn publish_nostr_deletion(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<PublishNostrDeletionBody>,
) -> AppResult<Json<PublishAppDataResponse>> {
    if body.d_tag.trim().is_empty() {
        return Err(AppError::BadRequest("d_tag cannot be empty".into()));
    }
    if body.kind != KIND_APP_DATA && body.kind != KIND_CYPHERLOG_SUBSCRIPTION {
        return Err(AppError::BadRequest(format!(
            "unsupported deletion kind {}; expected {KIND_APP_DATA} or {KIND_CYPHERLOG_SUBSCRIPTION}",
            body.kind
        )));
    }
    let (keys, _) = require_keys(&s, &jar).await?;
    let relay_urls = configured_relay_urls(&s).await?;
    let event = build_addressable_deletion_event(&keys, body.kind, &body.d_tag)
        .map_err(|e| AppError::BadRequest(format!("sign deletion failed: {e}")))?;
    let event_id = event.id.to_string();
    s.outbox
        .enqueue(s.cfg.clone(), keys, event, relay_urls, "deletion".into())
        .await;
    Ok(Json(PublishAppDataResponse { event_id }))
}

async fn publish_app_data(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Json(body): Json<PublishAppDataBody>,
) -> AppResult<Json<PublishAppDataResponse>> {
    if !is_fiatlife_d_tag(&body.d_tag) {
        return Err(AppError::BadRequest(
            "d_tag must be a FiatLife namespace tag".into(),
        ));
    }
    let (keys, _) = require_keys(&s, &jar).await?;
    let relay_urls = configured_relay_urls(&s).await?;
    let event = build_app_data_event(&keys, &body.d_tag, &body.plaintext)
        .map_err(|e| AppError::BadRequest(format!("sign failed: {e}")))?;
    let event_id = event.id.to_string();
    s.outbox
        .enqueue(s.cfg.clone(), keys, event, relay_urls, body.d_tag.clone())
        .await;
    Ok(Json(PublishAppDataResponse { event_id }))
}

#[derive(Serialize)]
struct OutboxStatusResponse {
    pending: usize,
    failed: usize,
    failed_items: Vec<OutboxItemView>,
}

#[derive(Serialize)]
struct OutboxItemView {
    id: u64,
    label: String,
    error: String,
}

impl From<OutboxStatus> for OutboxStatusResponse {
    fn from(s: OutboxStatus) -> Self {
        OutboxStatusResponse {
            pending: s.pending,
            failed: s.failed,
            failed_items: s
                .failed_items
                .into_iter()
                .map(|f| OutboxItemView {
                    id: f.id,
                    label: f.label,
                    error: f.error,
                })
                .collect(),
        }
    }
}

async fn outbox_status(State(s): State<AppState>) -> Json<OutboxStatusResponse> {
    Json(s.outbox.status().await.into())
}

async fn outbox_retry(State(s): State<AppState>) -> Json<OutboxStatusResponse> {
    s.outbox.retry_failed().await;
    Json(s.outbox.status().await.into())
}

async fn blossom_status(
    State(s): State<AppState>,
    jar: SignedCookieJar,
) -> AppResult<Json<BlossomStatusResponse>> {
    let (keys, _) = require_keys(&s, &jar).await?;
    let relay_urls = configured_relay_urls(&s).await?;
    let cfg = s.cfg.clone();
    let url = resolve_blossom_url(&keys, &relay_urls, |url| cfg.relay_connect_options(url)).await?;
    Ok(Json(BlossomStatusResponse {
        configured: url.is_some(),
        url,
    }))
}

async fn blossom_upload(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    mut multipart: Multipart,
) -> AppResult<Json<BlobDescriptor>> {
    let (keys, _) = require_keys(&s, &jar).await?;
    let relay_urls = configured_relay_urls(&s).await?;
    let cfg = s.cfg.clone();
    let blossom_url = resolve_blossom_url(&keys, &relay_urls, |url| cfg.relay_connect_options(url))
        .await?
        .ok_or_else(|| {
            AppError::BadRequest(
                "Blossom server URL is not configured in app settings".into(),
            )
        })?;

    let mut file_bytes: Option<Vec<u8>> = None;
    let mut content_type = "application/octet-stream".to_string();
    let mut filename = "upload".to_string();

    while let Some(field) = multipart
        .next_field()
        .await
        .map_err(|e| AppError::BadRequest(format!("multipart: {e}")))?
    {
        if field.name() != Some("file") {
            continue;
        }
        if let Some(name) = field.file_name() {
            filename = name.to_string();
        }
        if let Some(ct) = field.content_type() {
            content_type = ct.to_string();
        }
        file_bytes = Some(
            field
                .bytes()
                .await
                .map_err(|e| AppError::BadRequest(format!("read file: {e}")))?
                .to_vec(),
        );
        break;
    }

    let data = file_bytes.ok_or_else(|| AppError::BadRequest("file field required".into()))?;
    let descriptor = upload_blob(&blossom_url, &keys, data, &content_type, &filename).await?;
    Ok(Json(descriptor))
}

async fn blossom_download_handler(
    State(s): State<AppState>,
    jar: SignedCookieJar,
    Path(sha256): Path<String>,
) -> AppResult<Response> {
    let (keys, _) = require_keys(&s, &jar).await?;
    let relay_urls = configured_relay_urls(&s).await?;
    let cfg = s.cfg.clone();
    let blossom_url = resolve_blossom_url(&keys, &relay_urls, |url| cfg.relay_connect_options(url))
        .await?
        .ok_or_else(|| {
            AppError::BadRequest(
                "Blossom server URL is not configured in app settings".into(),
            )
        })?;
    download_blob(&blossom_url, &keys, &sha256).await
}

async fn require_unlocked(s: &AppState, jar: &SignedCookieJar) -> AppResult<()> {
    let sid = jar.get(SESSION_COOKIE).ok_or(AppError::Unauthorized)?;
    if !s.sessions.touch_unlocked(sid.value()).await {
        return Err(AppError::Unauthorized);
    }
    Ok(())
}

async fn require_keys(
    s: &AppState,
    jar: &SignedCookieJar,
) -> AppResult<(nostr::Keys, crate::nostr_support::KeyIdentity)> {
    let sid = jar.get(SESSION_COOKIE).ok_or(AppError::Unauthorized)?;
    s.sessions
        .keys_for(sid.value())
        .await
        .map_err(AppError::Internal)?
        .ok_or(AppError::Unauthorized)
}

async fn configured_relay_urls(s: &AppState) -> AppResult<Vec<String>> {
    let p = s.persistent.lock().await;
    let urls = stored_or_detected_relay_urls(p.relay_urls());
    if urls.is_empty() {
        return Err(AppError::BadRequest("relay url is not configured".into()));
    }
    Ok(urls)
}

fn stored_or_detected_relay_urls(stored: &[String]) -> Vec<String> {
    let urls: Vec<String> = stored
        .iter()
        .filter(|url| is_allowed_relay_url(url))
        .cloned()
        .collect();
    if !urls.is_empty() {
        return urls;
    }
    detected_relay_url().into_iter().collect()
}

fn resolve_setup_relay_url(user_url: &str) -> AppResult<String> {
    let trimmed = user_url.trim();
    if !trimmed.is_empty() {
        if !is_allowed_relay_url(trimmed) {
            return Err(AppError::BadRequest(relay_url_policy_message()));
        }
        return Ok(normalize_relay_url(trimmed));
    }
    detected_relay_url()
        .ok_or_else(|| AppError::BadRequest(relay_url_policy_message()))
}

fn normalize_relay_urls_from_body(body: &RelayBody) -> AppResult<Vec<String>> {
    let mut urls = if !body.relay_urls.is_empty() {
        body.relay_urls.clone()
    } else if !body.relay_url.trim().is_empty() {
        vec![body.relay_url.clone()]
    } else {
        Vec::new()
    };
    urls = dedupe_relay_urls(urls);
    if urls.is_empty() {
        if let Some(detected) = detected_relay_url() {
            return Ok(vec![detected]);
        }
        return Err(AppError::BadRequest("at least one relay url is required".into()));
    }
    for url in &urls {
        if !is_allowed_relay_url(url) {
            return Err(AppError::BadRequest(relay_url_policy_message()));
        }
    }
    Ok(urls.into_iter().map(|u| normalize_relay_url(&u)).collect())
}

fn is_allowed_relay_url(url: &str) -> bool {
    if url.starts_with("wss://") {
        return true;
    }
    let Some(rest) = url.strip_prefix("ws://") else {
        return false;
    };
    let host_port_path = rest.split('/').next().unwrap_or_default();
    let host_with_port = host_port_path
        .rsplit_once('@')
        .map(|(_, host)| host)
        .unwrap_or(host_port_path);
    let host = if let Some(rest) = host_with_port.strip_prefix('[') {
        rest.split(']').next().unwrap_or_default()
    } else {
        host_with_port.split(':').next().unwrap_or_default()
    };
    if host.ends_with(".startos") {
        return true;
    }
    matches!(host, "127.0.0.1" | "localhost" | "[::1]" | "::1")
}

fn relay_url_policy_message() -> String {
    "relay url must be wss://, ws://<package>.startos (StartOS internal relay), or ws://127.0.0.1 / ws://localhost for a local encrypted tunnel".into()
}

fn session_cookie(sid: String, secure: bool) -> Cookie<'static> {
    let mut c = Cookie::new(SESSION_COOKIE, sid);
    c.set_http_only(true);
    c.set_same_site(SameSite::Strict);
    c.set_path("/");
    c.set_secure(secure);
    c
}

impl axum::extract::FromRef<AppState> for Key {
    fn from_ref(state: &AppState) -> Self {
        state.cookie_key.clone()
    }
}

async fn spa_fallback(State(s): State<AppState>, uri: Uri) -> Response {
    if uri.path().starts_with("/api/") {
        return (
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({"error":"not found"})),
        )
            .into_response();
    }
    let index = s.cfg.static_dir.join("index.html");
    match tokio::fs::read(&index).await {
        Ok(bytes) => ([(header::CONTENT_TYPE, "text/html; charset=utf-8")], bytes).into_response(),
        Err(_) => (
            StatusCode::NOT_FOUND,
            "frontend assets missing; build apps/web/web/ and set FL_STATIC_DIR",
        )
            .into_response(),
    }
}
