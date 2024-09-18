use crate::currencies::CurrencyCode;
use crate::exchange_rate::PriceAt;
use reqwest::{Client, RequestBuilder};
use serde::de::{SeqAccess, Visitor};
use serde::{de, Deserialize, Deserializer};
use std::collections::HashMap;
use std::ops::Sub;
use std::{env, fmt};

use time::{Duration, OffsetDateTime};

/// Deserializes Coingecko responses from CoingeckoRateProvider.
#[derive(Deserialize)]
pub struct Response {
    pub prices: Vec<CoingeckoPriceAt>,
}

#[derive(Debug)]
pub struct CoingeckoPriceAt {
    pub timestamp: OffsetDateTime,
    pub price: f64,
}

impl From<CoingeckoPriceAt> for PriceAt {
    fn from(price_at: CoingeckoPriceAt) -> Self {
        PriceAt {
            timestamp: price_at.timestamp,
            price: price_at.price,
        }
    }
}

#[derive(Deserialize, Debug)]
pub struct CoingeckoQuote {
    pub bitcoin: HashMap<String, f64>,
}

pub struct CoingeckoCurrencyQuote {
    pub quote: f64,
}

struct PriceAtVisitor;

impl<'de> Visitor<'de> for PriceAtVisitor {
    type Value = CoingeckoPriceAt;

    fn expecting(&self, formatter: &mut fmt::Formatter) -> fmt::Result {
        formatter.write_str("an array of two floats")
    }

    fn visit_seq<A>(self, mut seq: A) -> Result<CoingeckoPriceAt, A::Error>
    where
        A: SeqAccess<'de>,
    {
        // First item in this vector is the timestamp in milliseconds, second item is the price.
        let ts_in_ms: i64 = seq
            .next_element()?
            .ok_or_else(|| de::Error::invalid_length(0, &self))?;
        let seconds = ts_in_ms / 1000;
        let nanos = (ts_in_ms % 1000) * 1_000_000;

        let offset_datetime = OffsetDateTime::from_unix_timestamp(seconds)
            .map_err(serde::de::Error::custom)?
            + Duration::nanoseconds(nanos);

        let price: f64 = seq
            .next_element()?
            .ok_or_else(|| de::Error::invalid_length(1, &self))?;

        Ok(CoingeckoPriceAt {
            timestamp: offset_datetime,
            price,
        })
    }
}

impl<'de> Deserialize<'de> for CoingeckoPriceAt {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        deserializer.deserialize_seq(PriceAtVisitor)
    }
}

#[derive(Clone)]
pub struct RateProvider {
    pub root_url: String,
    pub api_key: String,
}

impl Default for RateProvider {
    fn default() -> Self {
        Self::new()
    }
}

const ROOT_URL: &str = "https://pro-api.coingecko.com";
const HISTORICAL_RATE_REQUEST_COIN: &str = "bitcoin";
const HISTORICAL_RATE_REQUEST_GRANULARITY: Duration = Duration::minutes(5);

impl RateProvider {
    pub fn new() -> Self {
        RateProvider {
            root_url: ROOT_URL.to_string(),
            api_key: env::var("COINGECKO_API_KEY")
                .expect("COINGECKO_API_KEY environment variable not set"),
        }
    }

    pub fn historical_rate_request(
        &self,
        currency: &CurrencyCode,
        at_time: &OffsetDateTime,
    ) -> RequestBuilder {
        let path = format!(
            "/api/v3/coins/{}/market_chart/range",
            HISTORICAL_RATE_REQUEST_COIN
        );
        let from_timestamp = at_time
            .sub(HISTORICAL_RATE_REQUEST_GRANULARITY)
            .unix_timestamp();
        let to_timestamp = at_time.unix_timestamp();

        Client::new()
            .get(format!("{}{}", &self.root_url, path))
            .query(&[
                ("vs_currency", currency.to_string()),
                ("from", from_timestamp.to_string()),
                ("to", to_timestamp.to_string()),
                ("x_cg_pro_api_key", self.api_key.clone()),
            ])
    }

    pub fn rate_chart_request(&self, currency: &CurrencyCode, days: u16) -> RequestBuilder {
        let path = format!(
            "/api/v3/coins/{}/market_chart",
            HISTORICAL_RATE_REQUEST_COIN
        );
        Client::new()
            .get(format!("{}{}", &self.root_url, path))
            .query(&[
                ("vs_currency", currency.to_string()),
                ("days", days.to_string()),
                ("x_cg_pro_api_key", self.api_key.clone()),
            ])
    }

    pub fn rate_request(&self, currency: &CurrencyCode) -> RequestBuilder {
        let path = "/api/v3/simple/price";

        Client::new()
            .get(format!("{}{}", &self.root_url, path))
            .query(&[
                ("ids", HISTORICAL_RATE_REQUEST_COIN.to_string()),
                ("vs_currencies", currency.to_string().to_lowercase()),
                ("x_cg_pro_api_key", self.api_key.clone()),
            ])
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_rate_chart_request_url_construction() {
        // arrange
        let provider = RateProvider {
            root_url: "https://coingecko.example.com".to_string(),
            api_key: "abc123".to_string(),
        };
        let currency = CurrencyCode::USD;
        let days = 30u16;

        // act
        let request = provider
            .rate_chart_request(&currency, days)
            .build()
            .unwrap();

        // assert
        let expected_url = format!(
            "{}/api/v3/coins/{}/market_chart?vs_currency={}&days={}&x_cg_pro_api_key={}",
            provider.root_url, HISTORICAL_RATE_REQUEST_COIN, "USD", days, provider.api_key
        );
        assert_eq!(request.url().as_str(), expected_url);
    }

    #[test]
    fn test_rate_chart_request_query_parameters() {
        // arrange
        let provider = RateProvider {
            root_url: "https://coingecko.example.com".to_string(),
            api_key: "abc123".to_string(),
        };
        let currency = CurrencyCode::EUR;
        let days = 7u16;

        // act
        let request = provider
            .rate_chart_request(&currency, days)
            .build()
            .unwrap();
        let query: Vec<(String, String)> = request.url().query_pairs().into_owned().collect();

        // assert
        assert!(query.contains(&("vs_currency".to_string(), "EUR".to_string())));
        assert!(query.contains(&("days".to_string(), days.to_string())));
        assert!(query.contains(&("x_cg_pro_api_key".to_string(), provider.api_key.clone())));
    }

    #[test]
    fn test_spot_request_query_parameters() {
        // arrange
        let provider = RateProvider {
            root_url: "https://coingecko.example.com".to_string(),
            api_key: "abc123".to_string(),
        };
        let currency = CurrencyCode::EUR;

        // act
        let request = provider.rate_request(&currency).build().unwrap();
        let query: Vec<(String, String)> = request.url().query_pairs().into_owned().collect();

        // assert
        assert!(query.contains(&("ids".to_string(), "bitcoin".to_string())));
        assert!(query.contains(&("vs_currencies".to_string(), "eur".to_string())));
        assert!(query.contains(&("x_cg_pro_api_key".to_string(), provider.api_key.clone())));
    }
}
