use std::sync::Arc;
use tokio::sync::Mutex;

pub const LOCAL_ONE_BTC_IN_FIAT: f64 = 22678.0; // bitstamp BTCUSD ask as of 2023-01-20 01:00 UTC

#[derive(Clone)]
pub struct LocalRateProvider {
    pub rate_to_return: Option<f64>,
    // We need to have clones share the same counter, and interior mutability to be able to
    // increment this
    network_call_count: Arc<Mutex<u32>>,
}
pub struct LocalRateType {
    pub rate: f64,
}

impl Default for LocalRateProvider {
    fn default() -> Self {
        Self::new()
    }
}

impl LocalRateProvider {
    pub fn new() -> Self {
        Self {
            rate_to_return: Some(LOCAL_ONE_BTC_IN_FIAT),
            network_call_count: Arc::new(Mutex::new(0)),
        }
    }

    pub fn new_with_rate(rate_to_return: Option<f64>) -> Self {
        Self {
            rate_to_return,
            network_call_count: Arc::new(Mutex::new(0)),
        }
    }

    pub fn new_with_count(
        rate_to_return: Option<f64>,
        network_call_count: Arc<Mutex<u32>>,
    ) -> Self {
        Self {
            rate_to_return,
            network_call_count,
        }
    }

    pub async fn increment_network_call_count(&self) {
        let mut guard = self.network_call_count.lock().await;
        *guard += 1;
    }

    pub async fn get_network_call_count(&self) -> u32 {
        *self.network_call_count.lock().await
    }
}
