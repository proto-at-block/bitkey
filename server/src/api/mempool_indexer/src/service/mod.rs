use std::{collections::HashSet, sync::Arc};

use crate::repository::Repository;
use bdk_utils::bdk::bitcoin::{Network, Txid};
use config::{Config, ConfigError, Environment};
use reqwest::Client;
use reqwest_middleware::{ClientBuilder, ClientWithMiddleware};
use reqwest_retry::{policies::ExponentialBackoff, RetryTransientMiddleware};
use serde::Deserialize;
use time::OffsetDateTime;
use tokio::sync::RwLock;

mod get_new_txs;
mod get_stale_txs;

const MEMPOOL_SPACE_SIGNET_URL: &str = "https://bitkey.mempool.space/signet/api";

#[derive(Clone)]
pub struct Service {
    pub repo: Repository,
    http_client: ClientWithMiddleware,
    settings: Settings,
    recorded_txids: Arc<RwLock<HashSet<Txid>>>,
    last_refreshed_recorded_txids: Arc<RwLock<OffsetDateTime>>,
    current_mempool_txids: Arc<RwLock<HashSet<Txid>>>,
    stale_txs_expiring_after: Arc<RwLock<Option<OffsetDateTime>>>,
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
            .add_source(Environment::with_prefix("MEMPOOL_INDEXER"))
            .build()?
            .try_deserialize()
    }
}

impl Service {
    pub fn new(repo: Repository) -> Self {
        let retry_policy = ExponentialBackoff::builder().build_with_max_retries(5);
        let http_client = ClientBuilder::new(Client::new())
            .with(RetryTransientMiddleware::new_with_policy(retry_policy))
            .build();
        let settings = Settings::new().unwrap();

        Self {
            repo,
            http_client,
            settings,
            recorded_txids: Arc::new(RwLock::new(HashSet::new())),
            last_refreshed_recorded_txids: Arc::new(RwLock::new(OffsetDateTime::UNIX_EPOCH)),
            current_mempool_txids: Arc::new(RwLock::new(HashSet::new())),
            stale_txs_expiring_after: Arc::new(RwLock::new(None)),
        }
    }

    pub fn set_mock_server(mut self, base_url: String) -> Self {
        self.settings.base_url = base_url;
        self
    }

    pub fn network(&self) -> Network {
        self.settings.network
    }

    pub async fn get_recorded_tx_ids(&self) -> HashSet<Txid> {
        self.recorded_txids.read().await.clone()
    }

    pub async fn get_fetched_mempool_txids(&self) -> HashSet<Txid> {
        self.current_mempool_txids.read().await.clone()
    }

    pub async fn get_last_refreshed_recorded_txids(&self) -> OffsetDateTime {
        *self.last_refreshed_recorded_txids.read().await
    }

    pub async fn stale_txs_expiring_after(&self) -> Option<OffsetDateTime> {
        *self.stale_txs_expiring_after.read().await
    }
}
