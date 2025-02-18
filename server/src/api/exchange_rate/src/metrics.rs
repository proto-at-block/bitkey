use instrumentation::metrics::factory::{Counter, Histogram, MetricsFactory, ObservableGauge};
use once_cell::sync::Lazy;

pub(crate) const FACTORY_NAME: &str = "exchange_rate";

//TODO[W-5630]: Replace with std once stabilized
static FACTORY: Lazy<MetricsFactory> = Lazy::new(|| MetricsFactory::new(FACTORY_NAME));

// Counters

// Counts the number of requests to get an exchange rate.
pub(crate) static GET_EXCHANGE_RATE: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("get_exchange_rate", None));
pub(crate) static GET_EXCHANGE_RATE_CACHE_HITS: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("get_exchange_rate.hits", None));

// Gauges

// We measure the USD price of bitcoin.
pub(crate) static BTC_USD_PRICE: Lazy<ObservableGauge<f64>> =
    Lazy::new(|| FACTORY.f64_observable_gauge("btcusd_price", None));

// Histograms

// Measures the spread of how long it takes to respond to an uncached response.
pub(crate) static UNCACHED_RESPONSE_TIME: Lazy<Histogram<u64>> =
    Lazy::new(|| FACTORY.u64_histogram("uncached_response_time", Some("ms")));

// Measures the spread of how long it takes to respond to a cached response.
pub(crate) static CACHED_RESPONSE_TIME: Lazy<Histogram<u64>> =
    Lazy::new(|| FACTORY.u64_histogram("cached_response_time", Some("ms")));
