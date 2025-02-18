use crate::Network;
use instrumentation::metrics::factory::{Histogram, MetricsFactory, ObservableGauge};
use once_cell::sync::Lazy;
use serde::Deserialize;

pub(crate) const FACTORY_NAME: &str = "bdk_utils";

pub const ELECTRUM_URI_KEY: &str = "uri";
pub const ELECTRUM_NETWORK_KEY: &str = "network";
pub const ELECTRUM_PROVIDER_KEY: &str = "provider";

pub const NETWORK_KEY: &str = "network";

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

pub static BLOCKCHAIN_POLLER_TIP_HEIGHT: Lazy<ObservableGauge<u64>> =
    Lazy::new(|| FACTORY.u64_observable_gauge("blockchain_poller_tip_height", None));
pub static BLOCKCHAIN_POLLER_IN_SYNC: Lazy<ObservableGauge<u64>> =
    Lazy::new(|| FACTORY.u64_observable_gauge("blockchain_poller_in_sync", None));

// Histograms

// Measures the spread of how long it takes to ping the current Electrum servers.
pub static ELECTRUM_PING_RESPONSE_TIME: Lazy<Histogram<u64>> =
    Lazy::new(|| FACTORY.u64_histogram("electrum_time_to_ping", Some("ms")));
