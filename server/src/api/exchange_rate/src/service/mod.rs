use crate::currency_conversion::SpotExchangeRateProvider;
use crate::error::ExchangeRateError::CacheRead;
use crate::error::{ExchangeRateError, ProviderResponseError};
use futures::future::join_all;
use moka::future::{Cache, CacheBuilder};
use std::collections::HashMap;
use std::hash::Hash;
use std::time::Duration;
use time::OffsetDateTime;
use tracing::instrument;
use tracing::log::error;

use crate::chart::ExchangeRateChartProvider;
use crate::historical::HistoricalExchangeRateProvider;
use crate::metrics as exchange_rate_metrics;
use types::currencies::CurrencyCode::{BTC, USD};
use types::currencies::{Currency, CurrencyCode};
use types::exchange_rate::{ExchangeRate, ExchangeRateChartData};

/// A service for fetching the latest exchange rates.
#[derive(Clone)]
pub struct Service {
    /// A cache of exchange rates, keyed by their ISO-4217 currency code.
    spot_rate_cache: Cache<u16, ExchangeRate>,
    /// A map of rate chart data caches, keyed by the ttl.
    charts_caches: HashMap<u64, Cache<ChartCacheKey, ExchangeRateChartData>>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct ChartCacheKey {
    currency: u16, // ISO-4217 numeric currency code
    days: u16,
    max_price_points: usize,
}

impl ChartCacheKey {
    pub fn new(currency_code: CurrencyCode, days: u16, max_price_points: usize) -> Self {
        Self {
            currency: currency_code as u16,
            days,
            max_price_points,
        }
    }
}

pub mod cache_ttl {
    pub const MINUTE: u64 = 60;
    pub const FIVE_MINUTES: u64 = 5 * MINUTE;
    pub const ONE_HOUR: u64 = 60 * MINUTE;
    pub const ONE_DAY: u64 = 24 * ONE_HOUR;
}

const SPOT_RATE_CACHE_TTL: u64 = cache_ttl::FIVE_MINUTES;
const CHART_CACHE_TTLS: [u64; 3] = [
    cache_ttl::FIVE_MINUTES,
    cache_ttl::ONE_HOUR,
    cache_ttl::ONE_DAY,
];

impl Default for Service {
    fn default() -> Self {
        Self::new()
    }
}

impl Service {
    /// Creates a new service object with empty caches.
    pub fn new() -> Self {
        let rate_cache = Self::build_cache(SPOT_RATE_CACHE_TTL);

        let chart_caches: HashMap<u64, Cache<ChartCacheKey, ExchangeRateChartData>> =
            CHART_CACHE_TTLS
                .iter()
                .map(|&ttl| (ttl, Self::build_cache(ttl)))
                .collect();

        Self::new_from_caches(rate_cache, chart_caches)
    }

    pub fn new_from_caches(
        rate_cache: Cache<u16, ExchangeRate>,
        chart_caches: HashMap<u64, Cache<ChartCacheKey, ExchangeRateChartData>>,
    ) -> Self {
        Self {
            spot_rate_cache: rate_cache,
            charts_caches: chart_caches,
        }
    }

    fn build_cache<K, V>(ttl: u64) -> Cache<K, V>
    where
        K: Hash + Eq + Clone + Send + Sync + 'static,
        V: Clone + Send + Sync + 'static,
    {
        // We set the max capacity to 1000 since the current max ISO-4217 currency codes is 1000.
        let duration = Duration::from_secs(ttl);
        CacheBuilder::new(1000).time_to_live(duration).build()
    }

    /// Returns the latest exchange rate for a given currency.
    ///
    /// If what we have in-memory was less than [`TIME_WINDOW_DURATION`] ago, we return that.
    /// Otherwise, we fetch the latest rates from the provider and return those.
    #[instrument(err, skip(self, rate_provider))]
    pub async fn get_latest_rate<T>(
        &self,
        rate_provider: T,
        currency_code: CurrencyCode,
    ) -> Result<ExchangeRate, ExchangeRateError>
    where
        T: SpotExchangeRateProvider + 'static,
    {
        let rate_retrieval_start_time = OffsetDateTime::now_utc();

        let new_rate = self
            .spot_rate_cache
            .entry(currency_code as u16)
            .or_try_insert_with(async {
                Self::fetch_latest_rate(&rate_provider, OffsetDateTime::now_utc(), currency_code)
                    .await
            })
            .await
            .map_err(|_| CacheRead)?;

        // Log the response time for cache misses and hits.
        let rate_retrieval_duration =
            (OffsetDateTime::now_utc() - rate_retrieval_start_time).whole_milliseconds() as u64;
        if new_rate.is_fresh() {
            exchange_rate_metrics::UNCACHED_RESPONSE_TIME.record(rate_retrieval_duration, &[])
        } else {
            exchange_rate_metrics::CACHED_RESPONSE_TIME.record(rate_retrieval_duration, &[]);
            exchange_rate_metrics::GET_EXCHANGE_RATE_CACHE_HITS.add(1, &[]);
        }
        exchange_rate_metrics::GET_EXCHANGE_RATE.add(1, &[]);

        Ok(new_rate.into_value())
    }

    /// Returns the latest exchange rates for all supported fiat currencies.
    ///
    /// If what we have in-memory was less than [`TIME_WINDOW_DURATION`] ago, we return that.
    /// Otherwise, we fetch the latest rates from the provider and return those.
    #[instrument(err, skip(self, rate_provider))]
    pub async fn get_latest_rates<T>(
        &self,
        rate_provider: T,
    ) -> Result<Vec<ExchangeRate>, ExchangeRateError>
    where
        T: SpotExchangeRateProvider + 'static + Clone,
    {
        // If one of the requests fails, we log and return `None` for that currency.
        let request_futures = Currency::supported_currency_codes()
            .into_iter()
            .map(|currency| {
                let self_copy = self.clone();
                let rate_provider_copy = rate_provider.clone();
                let currency_copy = currency;
                async move {
                    match self_copy
                        .get_latest_rate(rate_provider_copy, currency_copy)
                        .await
                    {
                        Ok(currency_rate) => Some(currency_rate),
                        Err(error) => {
                            error!(
                                "Failed to get latest rate for currency {}: {:?}",
                                currency, error
                            );
                            None
                        }
                    }
                }
            })
            .collect::<Vec<_>>();

        // We filter out the `None` values and collect the results.
        let results = join_all(request_futures)
            .await
            .into_iter()
            .flatten()
            .collect();

        Ok(results)
    }

    // Performs the network call to fetch the latest rate from the provider.
    #[instrument(err, skip(rate_provider))]
    async fn fetch_latest_rate<T>(
        rate_provider: &T,
        time_retrieved: OffsetDateTime,
        currency_code: CurrencyCode,
    ) -> Result<ExchangeRate, ExchangeRateError>
    where
        T: SpotExchangeRateProvider,
    {
        rate_provider
            .rate(&BTC, &currency_code)
            .await
            .map(|rate| Self::measure_rate(rate, &currency_code))
            .map(|rate| ExchangeRate {
                from_currency: BTC,
                to_currency: currency_code,
                time_retrieved,
                rate,
            })
    }

    fn measure_rate(rate: f64, currency: &CurrencyCode) -> f64 {
        // Currently, we only measure rates in USD for monitoring for dramatic price swings in
        // DataDog.
        if *currency == USD {
            exchange_rate_metrics::BTC_USD_PRICE.observe(rate, &[]);
        }
        rate
    }

    /// Returns the historical exchange rates for all given timestamps,
    pub async fn get_historical_rates<T>(
        &self,
        rate_provider: T,
        currency: CurrencyCode,
        timestamps: Vec<OffsetDateTime>,
    ) -> Result<Vec<ExchangeRate>, ExchangeRateError>
    where
        T: HistoricalExchangeRateProvider + 'static + Clone,
    {
        // If one of the requests fails, we log and return `None` for that currency.
        let request_futures = timestamps
            .into_iter()
            .map(|timestamp| {
                let self_copy = self.clone();
                let rate_provider_copy = rate_provider.clone();
                let currency_copy = currency;
                async move {
                    match self_copy
                        .fetch_historical_rate(rate_provider_copy, currency_copy, timestamp)
                        .await
                    {
                        Ok(currency_rate) => Some(currency_rate),
                        Err(error) => {
                            error!(
                                "Failed to get historical rate for currency {}: {:?}",
                                currency_copy, error
                            );
                            None
                        }
                    }
                }
            })
            .collect::<Vec<_>>();

        // We filter out the `None` values and collect the results.
        let results = join_all(request_futures)
            .await
            .into_iter()
            .flatten()
            .collect();

        Ok(results)
    }

    pub async fn fetch_chart_data(
        &self,
        rate_provider: impl ExchangeRateChartProvider,
        currency: CurrencyCode,
        days: u16,
        max_price_points: usize,
    ) -> Result<ExchangeRateChartData, ExchangeRateError> {
        self.get_chart_cache(days)?
            .entry(ChartCacheKey::new(currency, days, max_price_points))
            .or_try_insert_with(async {
                rate_provider
                    .chart_data(&currency, days, max_price_points)
                    .await
                    .map(|data| ExchangeRateChartData {
                        from_currency: BTC,
                        to_currency: currency,
                        exchange_rates: data,
                    })
            })
            .await
            .map_err(|_| CacheRead)
            .map(|data| data.into_value())
    }

    fn get_chart_cache(
        &self,
        days: u16,
    ) -> Result<&Cache<ChartCacheKey, ExchangeRateChartData>, ExchangeRateError> {
        let ttl = match days {
            0..=1 => cache_ttl::FIVE_MINUTES, // Day chart -> ttl of 5 minutes
            2..=90 => cache_ttl::ONE_HOUR,    // 2 - 90 days (Week or Month chart) -> ttl of 1 hour
            _ => cache_ttl::ONE_DAY, // above 90 days (Year or All time chart) -> ttl of 1 day
        };
        self.charts_caches.get(&ttl).ok_or(CacheRead)
    }

    // Performs the network call to fetch the historical rate from the provider.
    async fn fetch_historical_rate<T>(
        &self,
        rate_provider: T,
        currency: CurrencyCode,
        time: OffsetDateTime,
    ) -> Result<ExchangeRate, ExchangeRateError>
    where
        T: HistoricalExchangeRateProvider,
    {
        let rate = rate_provider
            .rate(&vec![currency], time)
            .await?
            .remove(&currency.to_string())
            .ok_or_else(|| ProviderResponseError::MissingData(T::rate_provider_type()))?;

        Ok(ExchangeRate {
            from_currency: BTC,
            to_currency: currency,
            time_retrieved: time,
            rate,
        })
    }
}

#[cfg(test)]
mod tests {

    use crate::service::Service;
    use std::sync::Arc;
    use tokio::sync::Mutex;
    use types::currencies::Currency;
    use types::currencies::CurrencyCode::USD;
    use types::exchange_rate::local_rate_provider::LocalRateProvider;

    #[tokio::test]
    async fn test_get_latest_rate_success() {
        let exchange_rate_service = Arc::new(Service::new());

        let exchange_rate = exchange_rate_service
            .clone()
            .get_latest_rate(LocalRateProvider::new_with_rate(Some(1.0), None), USD)
            .await;

        assert!(exchange_rate.is_ok());
        assert_eq!(exchange_rate.unwrap().rate, 1.0);
    }

    #[tokio::test]
    async fn test_get_latest_rate_error() {
        let exchange_rate_service = Service::new();

        let exchange_rate = exchange_rate_service
            .get_latest_rate(LocalRateProvider::new_with_rate(None, None), USD)
            .await;

        assert!(exchange_rate.is_err());
    }

    // Test rate is not fetched again within the time window if it has already been fetched.
    #[tokio::test]
    async fn test_get_latest_rate_caching() {
        let exchange_rate_service = Arc::new(Service::new());
        let mock_provider = LocalRateProvider::new_with_rate(Some(1.0), None);

        // Call get_latest_rate twice
        let exchange_rate_1 = exchange_rate_service
            .clone()
            .get_latest_rate(mock_provider.clone(), USD)
            .await;
        let exchange_rate_2 = exchange_rate_service
            .clone()
            .get_latest_rate(mock_provider.clone(), USD)
            .await;

        // Assert that rates were fetched once but returned twice due to caching
        assert_eq!(mock_provider.get_network_call_count().await, 1);

        // Assert that rates are the same as first call.
        match (&exchange_rate_1, &exchange_rate_2) {
            (Ok(rate_1), Ok(rate_2)) => {
                assert_eq!(rate_1.rate, 1.0);
                assert_eq!(rate_2.rate, 1.0);
            }
            _ => panic!("Request failed"),
        }
    }

    // Check concurrent calls to `get_latest_rate` handle `rate_updated` mechanism correctly
    // and doesn't result in unnecessary rate provider calls, or race conditions.
    #[tokio::test]
    async fn test_get_latest_rate_concurrency() {
        // Share reference across providers so they can be moved.
        let shared_network_call_count: Arc<Mutex<u32>> = Arc::new(Mutex::new(0));
        let mock_provider_1 = LocalRateProvider::new_with_count(
            Some(1.0),
            None,
            Arc::clone(&shared_network_call_count),
        );
        // We deliberately have the second mock provider return a different rate. It should never
        // be called and influence the second exchange rate call, handle2.
        let mock_provider_2 = LocalRateProvider::new_with_count(
            Some(2.0),
            None,
            Arc::clone(&shared_network_call_count),
        );

        // We wrap in Arc so that we always access the same instance of Service, and clone in
        // advance to accomodate the moves below.
        let exchange_rate_service_1 = Arc::new(Service::new());
        let exchange_rate_service_2 = exchange_rate_service_1.clone();

        // Spawn multiple tasks to call get_latest_rate simultaneously
        let handle1 = tokio::spawn(async move {
            exchange_rate_service_1
                .clone()
                .get_latest_rate(mock_provider_1, USD)
                .await
        });
        let handle2 = tokio::spawn(async move {
            exchange_rate_service_2
                .clone()
                .get_latest_rate(mock_provider_2, USD)
                .await
        });

        // Wait for both to complete and assert that the rates are only updated once
        // despite the concurrent attempts.
        let (rate_1, rate_2) = tokio::join!(handle1, handle2);

        // Verify the `request` method was called only N number of times, where N is the number
        // of currencies we support.
        assert_eq!(*shared_network_call_count.lock().await, 1);
        // Assert both handles return rates without errors, and no unnecessary updates occurred.
        let unwrapped_exchange_rate_1 = rate_1.unwrap();
        let unwrapped_exchange_rate_2 = rate_2.unwrap();

        match (&unwrapped_exchange_rate_1, &unwrapped_exchange_rate_2) {
            (Ok(rate_1), Ok(rate_2)) => {
                assert_eq!(rate_1.rate, 1.0);
                assert_eq!(rate_2.rate, 1.0);
            }
            _ => panic!("Request failed"),
        }
    }

    // Test rates is not fetched again within the time window if it has already been fetched.
    #[tokio::test]
    async fn test_get_latest_rates_caching() {
        let exchange_rate_service = Arc::new(Service::new());
        let mock_provider = LocalRateProvider::new_with_rate(Some(1.0), None);

        // Call get_latest_rates twice
        let exchange_rates_1 = exchange_rate_service
            .clone()
            .get_latest_rates(mock_provider.clone())
            .await;
        let exchange_rates_2 = exchange_rate_service
            .clone()
            .get_latest_rates(mock_provider.clone())
            .await;

        // Assert that we only made N network calls, where N is the number of currencies we support.
        assert_eq!(
            mock_provider.get_network_call_count().await,
            Currency::supported_fiat_currencies().len() as u32,
        );

        // Assert that rates are the same as first call.
        assert_eq!(exchange_rates_1.unwrap(), exchange_rates_2.unwrap());
    }

    // Test individual rate fetch is not fetched again within the time window if a prior fetch
    // for all rates has already been made.
    #[tokio::test]
    async fn test_get_latest_rates_and_rate_caching() {
        let exchange_rate_service = Arc::new(Service::new());
        let mock_provider = LocalRateProvider::new_with_rate(Some(1.0), None);

        let test_fiat_currency = USD;

        // Call get_latest_rates
        let exchange_rates_1 = exchange_rate_service
            .clone()
            .get_latest_rates(mock_provider.clone())
            .await;
        let fiat_rate = exchange_rates_1
            .unwrap()
            .into_iter()
            .find(|r| r.to_currency == test_fiat_currency)
            .unwrap_or_else(|| {
                panic!(
                    "Failed to get the fiat rate for currency: {:?}",
                    test_fiat_currency
                );
            });

        // Call get_latest_rate for the same currency
        let fiat_rate_2 = exchange_rate_service
            .clone()
            .get_latest_rate(mock_provider.clone(), test_fiat_currency)
            .await
            .unwrap();

        // Assert that we only made N network calls, where N is the number of currencies we support.
        assert_eq!(
            mock_provider.get_network_call_count().await,
            Currency::supported_fiat_currencies().len() as u32,
        );

        // Assert that fiat rate is the same
        assert_eq!(fiat_rate.rate, fiat_rate_2.rate);
    }

    #[tokio::test]
    async fn test_fetch_chart_data_success() {
        // arrange
        let exchange_rate_service = Service::new();
        let mock_provider = LocalRateProvider::new();

        // act
        let chart_data = exchange_rate_service
            .fetch_chart_data(mock_provider.clone(), USD, 1, 1)
            .await;

        // assert
        assert!(chart_data.is_ok());
        assert_eq!(chart_data.unwrap().exchange_rates.len(), 1);
    }

    #[tokio::test]
    async fn test_fetch_chart_data_cache() {
        // arrange
        let exchange_rate_service = Arc::new(Service::new());
        let mock_provider = LocalRateProvider::new();
        let chart_data_1 = exchange_rate_service
            .clone()
            .fetch_chart_data(mock_provider.clone(), USD, 1, 1)
            .await;

        // act
        let chart_data_2 = exchange_rate_service
            .clone()
            .fetch_chart_data(mock_provider.clone(), USD, 1, 1)
            .await;

        // assert
        assert_eq!(mock_provider.get_network_call_count().await, 1);
        assert_eq!(chart_data_1.unwrap(), chart_data_2.unwrap());
    }
}
