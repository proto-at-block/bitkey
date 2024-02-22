use metrics::{factory::Histogram, factory::MetricsFactory, Unit};
use once_cell::sync::Lazy;

pub(crate) const FACTORY_NAME: &str = "bdk_utils";
pub(crate) const IS_BDK_UTILS: &str = "is_bdk_utils";

//TODO[W-5630]: Replace with std once stabilized
pub(crate) static FACTORY: Lazy<MetricsFactory> = Lazy::new(|| MetricsFactory::new(FACTORY_NAME));

// Histograms

// Measures the spread of how long it takes to ping the current mainnet Electrum server.
pub static ELECTRUM_MAINNET_PING_RESPONSE_TIME: Lazy<Histogram<u64>> =
    Lazy::new(|| FACTORY.u64_histogram("electrum_mainnet_time_to_ping", Some(Unit::new("ms"))));

// Measures the spread of how long it takes to ping the current signet Electrum server.
pub static ELECTRUM_SIGNET_PING_RESPONSE_TIME: Lazy<Histogram<u64>> =
    Lazy::new(|| FACTORY.u64_histogram("electrum_signet_time_to_ping", Some(Unit::new("ms"))));
