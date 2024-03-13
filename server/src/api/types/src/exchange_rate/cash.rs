use crate::currencies::CurrencyCode;
use reqwest::{Client, RequestBuilder};
use serde::Deserialize;
use serde_json::json;

#[derive(Deserialize)]
pub struct CashAppRate {
    pub change_cents: i64,
    pub base_value_cents: u64,
    pub currency_code: CurrencyCode,
}

#[derive(Deserialize)]
pub struct CashAppExchangeData {
    pub base_currency_code: CurrencyCode,
    pub rates: Vec<CashAppRate>,
}

#[derive(Deserialize)]
pub struct CashAppQuote {
    pub exchange_data: CashAppExchangeData,
}

#[derive(Clone)]
pub struct CashAppRateProvider {
    pub root_url: String,
}

impl Default for CashAppRateProvider {
    fn default() -> Self {
        Self::new()
    }
}

const ROOT_URL: &str = "https://cash.app/2.0/cash/get-exchange-data";

impl CashAppRateProvider {
    pub fn new() -> Self {
        CashAppRateProvider {
            root_url: ROOT_URL.to_string(),
        }
    }

    pub fn rate_request(&self, currency: &CurrencyCode) -> RequestBuilder {
        Client::new()
            .post(self.root_url.clone())
            .json(&self.json_body(currency))
    }

    fn json_body(&self, currency: &CurrencyCode) -> serde_json::Value {
        json!({ "quote_currency_code": currency.to_string() })
    }
}
