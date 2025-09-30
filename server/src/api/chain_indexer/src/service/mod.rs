use std::time::Duration;

use crate::repository::ChainIndexerRepository;
use bdk_utils::bdk::bitcoin::Network;
use config::{Config, ConfigError, Environment};
use reqwest::Client;
use reqwest_middleware::{ClientBuilder, ClientWithMiddleware};
use reqwest_retry::{policies::ExponentialBackoff, RetryTransientMiddleware};
use serde::Deserialize;

mod fetch_blockchain_data;
mod update_blockchain_data;

const MEMPOOL_SPACE_SIGNET_URL: &str = "https://bitkey.mempool.space/signet/api";

#[derive(Clone)]
pub struct Service {
    repo: ChainIndexerRepository,
    http_client: ClientWithMiddleware,
    settings: Settings,
}

#[derive(Clone, Deserialize)]
pub struct Settings {
    base_url: String,
    network: Network,
}

impl Settings {
    pub fn new() -> Result<Self, ConfigError> {
        Config::builder()
            .set_default("base_url", MEMPOOL_SPACE_SIGNET_URL)?
            .set_default("network", Network::Signet.to_string())?
            .add_source(Environment::with_prefix("CHAIN_INDEXER"))
            .build()?
            .try_deserialize()
    }
}

impl Service {
    pub fn new(repo: ChainIndexerRepository) -> Self {
        let retry_policy = ExponentialBackoff::builder()
            .retry_bounds(Duration::from_millis(200), Duration::from_secs(8))
            .build_with_max_retries(5);
        let base_client = Client::builder()
            .connect_timeout(Duration::from_secs(2))
            .timeout(Duration::from_secs(8))
            .build()
            .expect("failed to build reqwest client");
        let http_client = ClientBuilder::new(base_client)
            .with(RetryTransientMiddleware::new_with_policy(retry_policy))
            .build();
        let settings = Settings::new().unwrap();

        Self {
            repo,
            http_client,
            settings,
        }
    }

    pub fn set_mock_server(mut self, base_url: String) -> Self {
        self.settings.base_url = base_url;
        self
    }

    pub fn network(&self) -> Network {
        self.settings.network
    }
}
