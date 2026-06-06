//! Direct WebSocket relay I/O for self-hosted relays with private TLS (e.g. Start9 LAN certs).
//!
//! Used when [`RelayConnectOptions::insecure_tls`] is set for `wss://` URLs. The nostr-sdk
//! stack trusts Mozilla webpki roots only; Start9 serves a private LAN CA that fails with
//! `UnknownIssuer` unless the CA is installed system-wide.

use std::time::Duration;

use anyhow::{anyhow, Context};
use futures_util::{SinkExt, StreamExt};
use nostr::event::Tag;
use nostr::{Event, EventBuilder, Filter, JsonUtil, Keys, Kind, Url};
use tokio::net::TcpStream;
use tokio::time::{timeout, Instant};
use tokio_tungstenite::tungstenite::client::IntoClientRequest;
use tokio_tungstenite::tungstenite::Message;
use tokio_tungstenite::{connect_async, connect_async_tls_with_config, Connector, MaybeTlsStream, WebSocketStream};

const KIND_AUTH: u16 = 22242;
const CONNECT_TIMEOUT: Duration = Duration::from_secs(30);
const MESSAGE_TIMEOUT: Duration = Duration::from_secs(20);
const AUTH_WAIT_FALLBACK: Duration = Duration::from_secs(7);
const FETCH_HARD_TIMEOUT: Duration = Duration::from_secs(25);
const READ_SLICE: Duration = Duration::from_secs(5);

#[derive(Debug, Clone, Copy, Default)]
pub struct RelayConnectOptions {
    /// Accept self-signed / private LAN TLS for `wss://` (Start9, etc.).
    pub insecure_tls: bool,
}

type WsStream = WebSocketStream<MaybeTlsStream<TcpStream>>;

pub async fn send_event(
    keys: &Keys,
    relay_url: &str,
    event: &Event,
    opts: RelayConnectOptions,
) -> anyhow::Result<()> {
    if !opts.insecure_tls || !relay_url.starts_with("wss://") {
        return Err(anyhow!(
            "raw send requires wss:// with insecure relay TLS enabled"
        ));
    }
    let mut ws = connect(relay_url, opts).await?;
    let event_json = event.as_json();
    let event_id = event.id.to_string();
    publish_on_open_socket(&mut ws, keys, relay_url, &event_json, &event_id).await?;
    let _ = ws.close(None).await;
    Ok(())
}

pub async fn fetch_events(
    keys: &Keys,
    relay_url: &str,
    filter: Filter,
    opts: RelayConnectOptions,
) -> anyhow::Result<Vec<Event>> {
    if !opts.insecure_tls || !relay_url.starts_with("wss://") {
        return Err(anyhow!(
            "raw fetch requires wss:// with insecure relay TLS enabled"
        ));
    }
    let mut ws = connect(relay_url, opts).await?;
    tokio::time::sleep(Duration::from_millis(300)).await;

    let filter_json = serde_json::to_string(&filter).context("serialize filter")?;
    let sub_id = format!("fiatlife-raw-{}", Instant::now().elapsed().as_millis());
    let req = format!(r#"["REQ","{sub_id}",{filter_json}]"#);
    let mut events = Vec::new();
    let mut eose = false;

    let auth_deadline = Instant::now() + AUTH_WAIT_FALLBACK;
    while Instant::now() < auth_deadline {
        let remaining = auth_deadline.saturating_duration_since(Instant::now());
        let slice = remaining.min(READ_SLICE);
        let Some(msg) = read_ws_message(&mut ws, slice).await? else {
            break;
        };
        if handle_inbound_for_auth(&mut ws, keys, relay_url, &msg).await? {
            continue;
        }
        let _ = msg;
    }

    ws.send(Message::Text(req.into()))
        .await
        .context("send REQ")?;

    let deadline = Instant::now() + FETCH_HARD_TIMEOUT;
    while Instant::now() < deadline && !eose {
        let remaining = deadline.saturating_duration_since(Instant::now());
        let slice = remaining.min(READ_SLICE);
        let Some(msg) = read_ws_message(&mut ws, slice).await? else {
            break;
        };
        let Message::Text(text) = msg else {
            continue;
        };
        let arr: serde_json::Value = serde_json::from_str(&text).context("parse relay message")?;
        let Some(kind) = arr.get(0).and_then(|v| v.as_str()) else {
            continue;
        };
        match kind {
            "AUTH" => {
                handle_inbound_for_auth(&mut ws, keys, relay_url, &Message::Text(text.clone()))
                    .await?;
            }
            "EVENT" if arr.get(1).and_then(|v| v.as_str()) == Some(sub_id.as_str()) => {
                if let Some(ev_val) = arr.get(2) {
                    let ev: Event = Event::from_json(ev_val.to_string())
                        .map_err(|e| anyhow!("bad EVENT json: {e}"))?;
                    events.push(ev);
                }
            }
            "EOSE" if arr.get(1).and_then(|v| v.as_str()) == Some(sub_id.as_str()) => {
                eose = true;
            }
            "CLOSED" if arr.get(1).and_then(|v| v.as_str()) == Some(sub_id.as_str()) => {
                let reason = arr
                    .get(2)
                    .and_then(|v| v.as_str())
                    .unwrap_or("subscription closed");
                if events.is_empty() {
                    return Err(anyhow!("relay closed subscription: {reason}"));
                }
                break;
            }
            _ => {}
        }
    }

    let _ = ws.close(None).await;
    Ok(events)
}

async fn read_ws_message(ws: &mut WsStream, wait: Duration) -> anyhow::Result<Option<Message>> {
    if wait.is_zero() {
        return Ok(None);
    }
    match timeout(wait, ws.next()).await {
        Ok(Some(Ok(msg))) => Ok(Some(msg)),
        Ok(Some(Err(e))) => Err(anyhow!("websocket: {e}")),
        Ok(None) => Err(anyhow!("relay closed connection")),
        Err(_) => Ok(None),
    }
}

async fn handle_inbound_for_auth(
    ws: &mut WsStream,
    keys: &Keys,
    relay_url: &str,
    msg: &Message,
) -> anyhow::Result<bool> {
    let Message::Text(text) = msg else {
        return Ok(false);
    };
    let arr: serde_json::Value = serde_json::from_str(text).context("parse AUTH message")?;
    if arr.get(0).and_then(|v| v.as_str()) != Some("AUTH") {
        return Ok(false);
    }
    let challenge = arr
        .get(1)
        .and_then(|v| v.as_str())
        .ok_or_else(|| anyhow!("AUTH missing challenge"))?;
    let auth_json = build_auth_event_json(keys, relay_url, challenge)?;
    ws.send(Message::Text(format!(r#"["AUTH",{auth_json}]"#).into()))
        .await
        .context("send AUTH")?;
    Ok(true)
}

async fn connect(relay_url: &str, opts: RelayConnectOptions) -> anyhow::Result<WsStream> {
    let url = Url::parse(relay_url).context("parse relay url")?;
    let request = url
        .as_str()
        .into_client_request()
        .context("build websocket request")?;

    let connect_fut = async {
        if relay_url.starts_with("wss://") {
            if opts.insecure_tls {
                let tls = native_tls::TlsConnector::builder()
                    .danger_accept_invalid_certs(true)
                    .build()
                    .context("build tls connector")?;
                let connector = Connector::NativeTls(tls);
                connect_async_tls_with_config(request, None, false, Some(connector))
                    .await
                    .map_err(|e| anyhow!("connect: {e}"))
            } else {
                connect_async_tls_with_config(request, None, false, None)
                    .await
                    .map_err(|e| anyhow!("connect: {e}"))
            }
        } else {
            connect_async(request)
                .await
                .map_err(|e| anyhow!("connect: {e}"))
        }
    };

    let (ws, _) = timeout(CONNECT_TIMEOUT, connect_fut)
        .await
        .map_err(|_| anyhow!("connect timed out after {CONNECT_TIMEOUT:?}"))??;
    Ok(ws)
}

async fn publish_on_open_socket(
    ws: &mut WsStream,
    keys: &Keys,
    relay_url: &str,
    event_json: &str,
    event_id: &str,
) -> anyhow::Result<()> {
    let mut sent = false;
    let deadline = Instant::now() + MESSAGE_TIMEOUT;
    while Instant::now() < deadline {
        let remaining = deadline.saturating_duration_since(Instant::now());
        if !sent {
            let msg = timeout(std::cmp::min(remaining, Duration::from_secs(3)), ws.next()).await;
            match msg {
                Ok(Some(Ok(Message::Text(text)))) => {
                    if let Ok(arr) = serde_json::from_str::<serde_json::Value>(&text) {
                        if arr.get(0).and_then(|v| v.as_str()) == Some("AUTH") {
                            let challenge = arr
                                .get(1)
                                .and_then(|v| v.as_str())
                                .ok_or_else(|| anyhow!("AUTH missing challenge"))?;
                            let auth_json = build_auth_event_json(keys, relay_url, challenge)?;
                            ws.send(Message::Text(format!(r#"["AUTH",{auth_json}]"#).into()))
                                .await
                                .context("send AUTH")?;
                        }
                    }
                }
                Ok(Some(Ok(_))) | Ok(None) | Err(_) | Ok(Some(Err(_))) => {}
            }
            ws.send(Message::Text(format!(r#"["EVENT",{event_json}]"#).into()))
                .await
                .context("send EVENT")?;
            sent = true;
            continue;
        }
        let msg = timeout(remaining, ws.next())
            .await
            .map_err(|_| anyhow!("publish timeout waiting for OK"))?
            .ok_or_else(|| anyhow!("relay closed before OK"))?
            .map_err(|e| anyhow!("websocket: {e}"))?;
        let Message::Text(text) = msg else {
            continue;
        };
        let arr: serde_json::Value = serde_json::from_str(&text).context("parse OK message")?;
        if arr.get(0).and_then(|v| v.as_str()) != Some("OK") {
            continue;
        }
        if arr.get(1).and_then(|v| v.as_str()) != Some(event_id) {
            continue;
        }
        let accepted = arr.get(2).and_then(|v| v.as_bool()).unwrap_or(false);
        if accepted {
            return Ok(());
        }
        let reason = arr
            .get(3)
            .and_then(|v| v.as_str())
            .unwrap_or("relay rejected event");
        return Err(anyhow!("relay rejected event: {reason}"));
    }
    Err(anyhow!("publish timeout"))
}

fn build_auth_event_json(keys: &Keys, relay_url: &str, challenge: &str) -> anyhow::Result<String> {
    let auth = EventBuilder::new(Kind::Custom(KIND_AUTH), "")
        .tag(Tag::parse(["relay", relay_url]).map_err(|e| anyhow!("{e}"))?)
        .tag(Tag::parse(["challenge", challenge]).map_err(|e| anyhow!("{e}"))?)
        .sign_with_keys(keys)
        .map_err(|e| anyhow!("sign auth: {e}"))?;
    Ok(auth.as_json())
}

#[cfg(test)]
mod tests {
    use super::*;
    use nostr::FromBech32;

    #[tokio::test]
    #[ignore = "requires local Start9 relay"]
    async fn start9_relay_returns_web_npub_app_data() {
        let relay = std::env::var("FL_TEST_RELAY_URL")
            .unwrap_or_else(|_| "wss://embassy-fasting-gangs.local:64644".into());
        let npub = std::env::var("FL_TEST_NPUB").unwrap_or_else(|_| {
            "npub155dvu679jn7jnf3klzudgm5zy2thm2vjtd08309a4r2xtj43744sz98rxq".into()
        });
        let author = nostr::PublicKey::from_bech32(&npub).expect("npub");
        let keys = Keys::generate();
        let filter = Filter::new()
            .author(author)
            .kind(Kind::Custom(30078))
            .limit(50);
        let events = fetch_events(
            &keys,
            &relay,
            filter,
            RelayConnectOptions {
                insecure_tls: true,
            },
        )
        .await
        .expect("relay fetch");
        eprintln!("found {} kind-30078 event(s) for {npub}", events.len());
        for event in &events {
            eprintln!("  d={:?}", event.tags.identifier());
        }
    }
}
