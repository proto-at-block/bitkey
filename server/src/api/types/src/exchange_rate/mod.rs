use std::time::{Duration, Instant};

use crate::currencies::CurrencyCode;
use coingecko::RateProvider as CoingeckoRateProvider;
use local_rate_provider::LocalRateProvider;
use serde::{Deserialize, Serialize};
use time::serde::rfc3339;
use time::OffsetDateTime;
use utoipa::ToSchema;

pub mod bitstamp;
pub mod cash;
pub mod coingecko;
pub mod coinmarketcap;
pub mod local_rate_provider;

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, ToSchema)]
pub struct ExchangeRate {
    /// Base currency of an exchange rate.
    pub from_currency: CurrencyCode,
    /// Target currency of an exchange rate.
    pub to_currency: CurrencyCode,
    /// The timestamp of the exchange rate quote in UTC.
    #[serde(with = "rfc3339")]
    pub time_retrieved: OffsetDateTime,
    /// The exchange rate quote, represented in the smallest units of the target currency.
    pub rate: f64,
}

/// Time window for determining latest exchange rate.
#[derive(Clone, Debug)]
pub struct TimeWindow {
    mark: Instant,
    duration: Duration,
}

impl TimeWindow {
    pub fn new(mark: Instant, duration: Duration) -> Self {
        Self { mark, duration }
    }

    pub fn within(&self, time: Instant) -> bool {
        time.duration_since(self.mark) < self.duration
    }
}

#[derive(Debug, Serialize, PartialEq, Clone)]
pub struct PriceAt {
    #[serde(with = "rfc3339")]
    pub timestamp: OffsetDateTime,
    pub price: f64,
}

#[derive(Serialize, Debug, ToSchema, Clone, PartialEq)]
pub struct ExchangeRateChartData {
    pub from_currency: CurrencyCode,
    pub to_currency: CurrencyCode,
    pub exchange_rates: Vec<PriceAt>,
}

pub enum RateProvider {
    Local(LocalRateProvider),
    Coingecko(CoingeckoRateProvider),
}

#[cfg(test)]
mod tests {
    use crate::exchange_rate::TimeWindow;
    use std::time::{Duration, Instant};

    #[test]
    fn test_within() {
        let mark = Instant::now();
        let window = TimeWindow {
            mark,
            duration: Duration::from_secs(5 * 60),
        };

        assert!(window.within(Instant::now()));
        let future = mark + Duration::from_secs(5 * 60 + 1); // 5 minutes + 1 second
        assert!(!window.within(future));
    }
}
