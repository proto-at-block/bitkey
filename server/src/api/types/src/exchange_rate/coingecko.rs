use crate::currencies::CurrencyCode;
use reqwest::{Client, RequestBuilder};
use serde::de::{SeqAccess, Visitor};
use serde::{de, Deserialize, Deserializer};
use std::ops::Sub;
use std::{env, fmt};
use time::{Duration, OffsetDateTime};

/// Deserializes Coingecko responses from CoingeckoRateProvider.
#[derive(Deserialize)]
pub struct Response {
    pub prices: Vec<PriceAt>,
}

#[derive(Debug)]
pub struct PriceAt {
    pub timestamp: OffsetDateTime,
    pub price: f64,
}

struct PriceAtVisitor;

impl<'de> Visitor<'de> for PriceAtVisitor {
    type Value = PriceAt;

    fn expecting(&self, formatter: &mut fmt::Formatter) -> fmt::Result {
        formatter.write_str("an array of two floats")
    }

    fn visit_seq<A>(self, mut seq: A) -> Result<PriceAt, A::Error>
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

        Ok(PriceAt {
            timestamp: offset_datetime,
            price,
        })
    }
}

impl<'de> Deserialize<'de> for PriceAt {
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
}
