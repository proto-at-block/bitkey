use crate::currencies::CurrencyCode;
use reqwest::{Client, RequestBuilder};
use serde::Deserialize;
use std::collections::HashMap;
use std::env;
use time::OffsetDateTime;

/// Deserializes Coinmarketcap responses from CoinmarketcapRateProvider.
#[derive(Deserialize)]
pub struct Response {
    pub data: ResponseData,
}

#[derive(Deserialize)]
pub struct ResponseData {
    #[serde(rename = "BTC")]
    pub btc: Vec<CryptoQuotes>,
}

#[derive(Deserialize)]
pub struct CryptoQuotes {
    pub quotes: Vec<Quotes>,
}

#[derive(Deserialize)]
pub struct Quotes {
    pub quote: HashMap<String, Value>,
}

#[derive(Deserialize)]
pub struct Value {
    pub price: f64,
}

#[derive(Clone)]
pub struct RateProvider {
    pub root_url: String,
    api_key: String,
}

impl Default for RateProvider {
    fn default() -> Self {
        Self::new()
    }
}

const ROOT_URL: &str = "https://pro-api.coinmarketcap.com/v2/";
const HISTORICAL_RATE_REQUEST_SYMBOL: &str = "BTC";
const HISTORICAL_RATE_REQUEST_COUNT: &str = "1";

impl RateProvider {
    pub fn new() -> Self {
        RateProvider {
            root_url: ROOT_URL.to_string(),
            api_key: env::var("COINMARKETCAP_API_KEY")
                .expect("COINMARKETCAP_API_KEY environment variable not set"),
        }
    }

    pub fn historical_rate_request(
        &self,
        currencies: &[CurrencyCode],
        at_time: &OffsetDateTime,
    ) -> RequestBuilder {
        // Create a copy of currencies vec and convert each value into string
        let currencies_as_strings = currencies
            .iter()
            .map(|c| c.to_string())
            .collect::<Vec<String>>();

        // Join with commas for format API expects
        let currencies_string = currencies_as_strings.join(",");
        Client::new()
            .get(format!(
                "{}{}",
                &self.root_url, "cryptocurrency/quotes/historical"
            ))
            .query(&[
                ("symbol", HISTORICAL_RATE_REQUEST_SYMBOL),
                ("time_start", &at_time.unix_timestamp().to_string()),
                ("count", HISTORICAL_RATE_REQUEST_COUNT),
                ("convert", &currencies_string),
            ])
            .header("X-CMC_PRO_API_KEY", &self.api_key)
    }
}
