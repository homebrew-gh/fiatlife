//! FiatLife web backend for StartOS.
//!
//! The user's nsec is sealed at rest (`$FL_DATA_DIR/state.json`). After unlock
//! the key lives in memory only. The server connects to the user's Nostr relay
//! (same as the Android app), fetches kind-30078 FiatLife events, and serves
//! the React SPA.

mod blossom;
mod config;
mod crypto;
mod error;
mod fiatlife_tags;
mod nostr_support;
mod outbox;
mod relay_raw;
mod routes;
mod salary_merge;
mod session;
mod state;

use std::net::SocketAddr;

use anyhow::Context;
use axum::Router;
use tokio::net::TcpListener;
use tokio::signal;
use tracing::info;
use tracing_subscriber::EnvFilter;

use crate::config::Config;
use crate::routes::build_router;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_env("FL_LOG").unwrap_or_else(|_| EnvFilter::new("info")),
        )
        .with_target(false)
        .compact()
        .init();

    let cfg = Config::from_env().context("loading config from env")?;
    cfg.ensure_dirs().context("ensuring data dir exists")?;

    let app: Router = build_router(cfg.clone()).await?;

    let addr: SocketAddr = cfg.bind_addr;
    let listener = TcpListener::bind(addr)
        .await
        .with_context(|| format!("binding {addr}"))?;
    info!(
        ?addr,
        data_dir = %cfg.data_dir.display(),
        static_dir = %cfg.static_dir.display(),
        "fiatlife-web listening"
    );

    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await
        .context("axum::serve")?;

    info!("shutdown complete");
    Ok(())
}

async fn shutdown_signal() {
    let ctrl_c = async {
        signal::ctrl_c().await.expect("install SIGINT handler");
    };

    #[cfg(unix)]
    let terminate = async {
        signal::unix::signal(signal::unix::SignalKind::terminate())
            .expect("install SIGTERM handler")
            .recv()
            .await;
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {},
        _ = terminate => {},
    }
}
