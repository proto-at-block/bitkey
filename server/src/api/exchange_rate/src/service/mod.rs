use crate::currency_conversion::SpotExchangeRateProvider;
use crate::error::ExchangeRateError::CacheRead;
use crate::error::{ExchangeRateError, ProviderResponseError};
use futures::future::join_all;
use moka::future::{Cache, CacheBuilder};
use time::OffsetDateTime;
use tracing::instrument;
use tracing::log::error;

use crate::historical::HistoricalExchangeRateProvider;
use types::currencies::CurrencyCode::{BTC, USD};
use types::currencies::{Currency, CurrencyCode};
use types::exchange_rate::ExchangeRate;

use crate::metrics as exchange_rate_metrics;

/// A service for fetching the latest exchange rates.
#[derive(Clone)]
pub struct Service {
    /// A cache of exchange rates, keyed by their ISO-4217 currency code.
    cache: Cache<u16, ExchangeRate>,
}

pub const TIME_WINDOW_DURATION: core::time::Duration = core::time::Duration::from_secs(5 * 60);

impl Default for Service {
    fn default() -> Self {
        Self::new()
    }
}

impl Service {
    /// Creates a new service object with an empty set of exchange rates.
    ///
    /// The initializer chooses a default time window of between [`TIME_WINDOW_DURATION`] ago and
    /// now, so the next call of get_latest_rates will always trigger a refresh.
    pub fn new() -> Self {
        // We set the max capacity to 1000 since the current max ISO-4217 currency codes is 1000.
        let cache = CacheBuilder::new(1000)
            .time_to_live(TIME_WINDOW_DURATION)
            .build();

        Self { cache }
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
            .cache
            .entry(currency_code.clone() as u16)
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
                let currency_copy = currency.clone();
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
                let currency_copy = currency.clone();
                async move {
                    match self_copy
                        .fetch_historical_rate(rate_provider_copy, currency_copy.clone(), timestamp)
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
            .rate(&vec![currency.clone()], time)
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
            .get_latest_rate(LocalRateProvider::new_with_rate(Some(1.0)), USD)
            .await;

        assert!(exchange_rate.is_ok());
        assert_eq!(exchange_rate.unwrap().rate, 1.0);
    }

    #[tokio::test]
    async fn test_get_latest_rate_error() {
        let exchange_rate_service = Service::new();

        let exchange_rate = exchange_rate_service
            .get_latest_rate(LocalRateProvider::new_with_rate(None), USD)
            .await;

        assert!(exchange_rate.is_err());
    }

    // Test rate is not fetched again within the time window if it has already been fetched.
    #[tokio::test]
    async fn test_get_latest_rate_caching() {
        let exchange_rate_service = Arc::new(Service::new());
        let mock_provider = LocalRateProvider::new_with_rate(Some(1.0));

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
        let mock_provider_1 =
            LocalRateProvider::new_with_count(Some(1.0), Arc::clone(&shared_network_call_count));
        // We deliberately have the second mock provider return a different rate. It should never
        // be called and influence the second exchange rate call, handle2.
        let mock_provider_2 =
            LocalRateProvider::new_with_count(Some(2.0), Arc::clone(&shared_network_call_count));

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
        let mock_provider = LocalRateProvider::new_with_rate(Some(1.0));

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
        let mock_provider = LocalRateProvider::new_with_rate(Some(1.0));

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
}
