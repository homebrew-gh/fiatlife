//! BTC/USD spot price for net-worth display. Tries mempool.space first, then
//! Coinbase. Requests carry no user data — only the fact that a price was asked for.

use std::time::Duration;

use reqwest::Client;
use serde::{Deserialize, Serialize};

const REQUEST_TIMEOUT: Duration = Duration::from_secs(15);
const MEMPOOL_URL: &str = "https://mempool.space/api/v1/prices";
const COINBASE_URL: &str = "https://api.coinbase.com/v2/prices/BTC-USD/spot";

#[derive(Debug, Clone, Serialize)]
pub struct BtcPrice {
    pub usd: f64,
    pub fetched_at_ms: i64,
    pub source: &'static str,
}

#[derive(Deserialize)]
struct MempoolPrices {
    #[serde(rename = "USD")]
    usd: f64,
}

#[derive(Deserialize)]
struct CoinbaseSpot {
    data: CoinbaseAmount,
}

#[derive(Deserialize)]
struct CoinbaseAmount {
    amount: String,
}

fn valid(usd: f64) -> Option<f64> {
    (usd.is_finite() && usd > 0.0).then_some(usd)
}

async fn from_mempool(client: &Client) -> anyhow::Result<f64> {
    let prices: MempoolPrices = client
        .get(MEMPOOL_URL)
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;
    valid(prices.usd).ok_or_else(|| anyhow::anyhow!("mempool.space returned no USD price"))
}

async fn from_coinbase(client: &Client) -> anyhow::Result<f64> {
    let spot: CoinbaseSpot = client
        .get(COINBASE_URL)
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;
    spot.data
        .amount
        .trim()
        .parse::<f64>()
        .ok()
        .and_then(valid)
        .ok_or_else(|| anyhow::anyhow!("Coinbase returned no USD price"))
}

pub async fn fetch_btc_price(now_ms: i64) -> anyhow::Result<BtcPrice> {
    let client = Client::builder()
        .https_only(true)
        .timeout(REQUEST_TIMEOUT)
        .build()?;
    match from_mempool(&client).await {
        Ok(usd) => Ok(BtcPrice {
            usd,
            fetched_at_ms: now_ms,
            source: "mempool.space",
        }),
        Err(mempool_err) => {
            tracing::warn!("mempool.space price fetch failed: {mempool_err}");
            let usd = from_coinbase(&client).await.map_err(|coinbase_err| {
                anyhow::anyhow!(
                    "BTC price unavailable (mempool.space: {mempool_err}; Coinbase: {coinbase_err})"
                )
            })?;
            Ok(BtcPrice {
                usd,
                fetched_at_ms: now_ms,
                source: "Coinbase",
            })
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_price_payloads() {
        let mempool: MempoolPrices =
            serde_json::from_str(r#"{"time":1791152407,"USD":86602,"EUR":76918}"#).unwrap();
        assert_eq!(valid(mempool.usd), Some(86602.0));
        let coinbase: CoinbaseSpot = serde_json::from_str(
            r#"{"data":{"amount":"86493.515","base":"BTC","currency":"USD"}}"#,
        )
        .unwrap();
        assert_eq!(coinbase.data.amount.parse::<f64>().ok().and_then(valid), Some(86493.515));
        assert_eq!(valid(0.0), None);
    }
}
