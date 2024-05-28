use crate::Network;
use instrumentation::metrics::{
    factory::{Histogram, MetricsFactory, ObservableGauge},
    Unit,
};
use once_cell::sync::Lazy;
use serde::Deserialize;

pub(crate) const FACTORY_NAME: &str = "bdk_utils";

pub const ELECTRUM_URI_KEY: &str = "uri";
pub const ELECTRUM_NETWORK_KEY: &str = "network";
pub const ELECTRUM_PROVIDER_KEY: &str = "provider";

#[derive(Clone, Deserialize, Hash, PartialEq, Eq, Debug)]
pub struct MonitoredElectrumNode {
    pub network: Network,
    pub provider: String,
    pub uri: String,
}

//TODO[W-5630]: Replace with std once stabilized
pub static FACTORY: Lazy<MetricsFactory> = Lazy::new(|| MetricsFactory::new(FACTORY_NAME));

// Gauges
pub static ELECTRUM_TIP_HEIGHT: Lazy<ObservableGauge<u64>> =
    Lazy::new(|| FACTORY.u64_observable_gauge("electrum_tip_height", None));

// Histograms

// Measures the spread of how long it takes to ping the current mainnet Electrum server.
pub static ELECTRUM_MAINNET_PING_RESPONSE_TIME: Lazy<Histogram<u64>> =
    Lazy::new(|| FACTORY.u64_histogram("electrum_mainnet_time_to_ping", Some(Unit::new("ms"))));

// Measures the spread of how long it takes to ping the current signet Electrum server.
pub static ELECTRUM_SIGNET_PING_RESPONSE_TIME: Lazy<Histogram<u64>> =
    Lazy::new(|| FACTORY.u64_histogram("electrum_signet_time_to_ping", Some(Unit::new("ms"))));
