use std::{collections::HashSet, time::Duration};

use async_stream::try_stream;
use bdk_utils::bdk::bitcoin::Txid;
use database::ddb::CHUNK_SIZE_MAX;
use futures::Stream;
use time::OffsetDateTime;
use tracing::{event, Level};

use super::Service;
use crate::{
    entities::{TransactionRecord, TransactionResponse},
    MempoolIndexerError,
};

// Refresh the recorded txids every 6 hours
pub const EXPIRY_UPDATE_WINDOW_MINS: Duration = Duration::from_secs(6 * 60 * 60);

impl Service {
    pub fn get_new_mempool_txs(
        &self,
    ) -> impl Stream<Item = Result<Vec<TransactionRecord>, MempoolIndexerError>> + '_ {
        try_stream! {
            let network = self.settings.network;
            event!(
                Level::INFO,
                "Getting new txs for network {} using base url {}",
                network,
                self.settings.base_url,
            );

            // If this is the first time we're calling this method or every 6 hours, fetch the recorded txids from the database
            let should_refresh = {
                let last_refreshed_read_guard = self.last_refreshed_recorded_txids.read().await;
                let is_outdated = OffsetDateTime::now_utc() - *last_refreshed_read_guard > EXPIRY_UPDATE_WINDOW_MINS;
                let recorded_txids_read_guard = self.recorded_txids.read().await;
                let is_empty = recorded_txids_read_guard.is_empty();
                is_outdated || is_empty
            };
            if should_refresh {
                let mut write_guard_last_refreshed = self.last_refreshed_recorded_txids.write().await;
                *write_guard_last_refreshed = OffsetDateTime::now_utc();
                let new_tx_ids = self.repo.fetch_recorded_transaction_ids(network).await?;
                let mut write_guard = self.recorded_txids.write().await;
                write_guard.clear();
                write_guard.extend(new_tx_ids);
            }

            let num_txids = {
                let txids = self.get_transaction_ids_from_mempool().await?;
                let num_txids = txids.len();
                let mut write_guard = self.current_mempool_txids.write().await;
                write_guard.clear();
                write_guard.extend(txids);
                num_txids
            };

            let unrecorded_tx_ids = {
                let current_mempool_txids = self.current_mempool_txids.read().await;
                let recorded_txids = self.recorded_txids.read().await;
                current_mempool_txids
                    .difference(&recorded_txids)
                    .cloned()
                    .collect::<Vec<Txid>>()
            };
            let num_unrecorded_tx_ids = unrecorded_tx_ids.len();
            event!(
                Level::INFO,
                "Found {num_txids} mempool transactions with {num_unrecorded_tx_ids} not recorded from network {network}"
            );

            // We do these in batches to avoid hitting the DDB write limit
            let chunks = unrecorded_tx_ids
                .chunks(CHUNK_SIZE_MAX)
                .map(|chunk| chunk.to_vec())
                .collect::<Vec<Vec<Txid>>>();

            for unrecorded_tx_ids in chunks {
                let mut tx_records = Vec::new();

                for tx_id in &unrecorded_tx_ids {
                    match self.fetch_transaction_from_mempool(tx_id).await {
                        Ok(transaction_response) => {
                            // If the tx is already confirmed, no use vending a processing tx for it
                            if transaction_response.is_confirmed() {
                                continue;
                            }
                            let record = TransactionRecord::from_mempool_tx(&transaction_response, network);
                            tx_records.push(record);
                        }
                        Err(e) => {
                            event!(
                                Level::ERROR,
                                "Failed to fetch tx from mempool with txid: {} due to error: {e}",
                                tx_id
                            );
                        }
                    };
                }

                if self.repo.persist_batch(&tx_records).await.is_ok() {
                    self.recorded_txids.write().await.extend(tx_records.iter().map(|r| r.txid));
                    yield tx_records;
                } else {
                    event!(
                        Level::ERROR,
                        "Failed to persist tx record for batch with tx ids: {}",
                        tx_records.into_iter().map(|r| r.txid.to_string()).collect::<Vec<String>>().join(", ")
                    );
                }
        }
        }
    }

    async fn fetch_transaction_from_mempool(
        &self,
        tx_id: &Txid,
    ) -> Result<TransactionResponse, MempoolIndexerError> {
        self.http_client
            .get(&format!("{}/tx/{tx_id}", self.settings.base_url))
            .send()
            .await?
            .json()
            .await
            .map_err(MempoolIndexerError::from)
    }

    pub async fn get_transaction_ids_from_mempool(
        &self,
    ) -> Result<HashSet<Txid>, MempoolIndexerError> {
        self.http_client
            .get(&format!("{}/mempool/txids", self.settings.base_url))
            .send()
            .await?
            .json()
            .await
            .map_err(MempoolIndexerError::from)
    }
}
