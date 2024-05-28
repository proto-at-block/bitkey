use std::collections::HashSet;

use async_stream::try_stream;
use bdk_utils::bdk::bitcoin::Txid;
use database::ddb::CHUNK_SIZE_MAX;
use futures::{stream::FuturesUnordered, FutureExt, Stream, StreamExt};
use tracing::{event, Level};

use super::Service;
use crate::{
    entities::{TransactionRecord, TransactionResponse},
    MempoolIndexerError,
};

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

            // If this is the first time we're calling this method, fetch the recorded txids from the database
            let is_empty = {
                let read_guard = self.recorded_txids.read().await;
                read_guard.is_empty()
            };
            if is_empty {
                let new_tx_ids = self.repo.fetch_recorded_transaction_ids(network).await?;
                let mut write_guard = self.recorded_txids.write().await;
                write_guard.extend(new_tx_ids);
            }

            let current_mempool_tx_ids = self.get_transaction_ids_from_mempool().await?;
            let num_tx_ids = current_mempool_tx_ids.len();
            event!(
                Level::INFO,
                "Retrieved {num_tx_ids} mempool transactions from network {network}"
            );


            let unrecorded_tx_ids = {
                let recorded_txids = self.recorded_txids.read().await;
                current_mempool_tx_ids
                    .difference(&recorded_txids)
                    .cloned()
                    .collect::<Vec<Txid>>()
            };

            // We do these in batches to avoid hitting the DDB write limit
            let chunks = unrecorded_tx_ids
                .chunks(CHUNK_SIZE_MAX)
                .map(|chunk| chunk.to_vec())
                .collect::<Vec<Vec<Txid>>>();

            for unrecorded_tx_ids in chunks {
                let mut tx_records = Vec::new();

                let mut mempool_futures = unrecorded_tx_ids.into_iter().map(|tx_id| {
                    let cloned_tx_id = tx_id;
                    self.fetch_transaction_from_mempool(tx_id).map(move |x| (cloned_tx_id, x))
                }).collect::<FuturesUnordered<_>>();
                while let Some((tx_id, result)) = mempool_futures.next().await {
                    match result {
                        Ok(transaction_response) => {
                            let record = TransactionRecord::from_mempool_tx(&transaction_response, network);
                            tx_records.push(record.clone());
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
                        tx_records.into_iter().map(|r| r.txid.clone().to_string()).collect::<Vec<String>>().join(", ")
                    );
                }
        }
        }
    }

    pub async fn get_recorded_tx_ids(&self) -> HashSet<Txid> {
        self.recorded_txids.read().await.clone()
    }

    async fn fetch_transaction_from_mempool(
        &self,
        tx_id: Txid,
    ) -> Result<TransactionResponse, MempoolIndexerError> {
        self.http_client
            .get(&format!("{}/tx/{tx_id}", self.settings.base_url))
            .send()
            .await?
            .json()
            .await
            .map_err(MempoolIndexerError::from)
    }

    async fn get_transaction_ids_from_mempool(&self) -> Result<HashSet<Txid>, MempoolIndexerError> {
        self.http_client
            .get(&format!("{}/mempool/txids", self.settings.base_url))
            .send()
            .await?
            .json()
            .await
            .map_err(MempoolIndexerError::from)
    }
}
