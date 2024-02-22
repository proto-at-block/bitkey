use crate::currencies::CurrencyCode;
use reqwest::{Client, RequestBuilder};
use serde::Deserialize;

/// Deserializes Bitstamp responses from BitstampRateProvider.
#[derive(Deserialize)]
pub struct BitstampRate {
    pub ask: String,
}

#[derive(Clone)]
pub struct BitstampRateProvider {
    pub root_url: String,
}

impl Default for BitstampRateProvider {
    fn default() -> Self {
        Self::new()
    }
}

const ROOT_URL: &str = "https://www.bitstamp.net/api/v2/ticker";

impl BitstampRateProvider {
    pub fn new() -> Self {
        BitstampRateProvider {
            root_url: ROOT_URL.to_string(),
        }
    }

    pub fn rate_request(&self, currency: &CurrencyCode) -> RequestBuilder {
        Client::new().get(format!(
            "{}/btc{}",
            self.root_url,
            currency.to_string().to_lowercase()
        ))
    }
}
