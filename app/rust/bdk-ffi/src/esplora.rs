use crate::bitcoin::BlockHash;
use crate::bitcoin::Transaction;
use crate::bitcoin::Txid;
use crate::error::EsploraError;
use crate::types::Tx;
use crate::types::TxStatus;
use crate::types::Update;
use crate::types::{FullScanRequest, SyncRequest};

use bdk_esplora::esplora_client::{BlockingClient, Builder};
use bdk_esplora::EsploraExt;
use bdk_wallet::bitcoin::Transaction as BdkTransaction;
use bdk_wallet::chain::spk_client::FullScanRequest as BdkFullScanRequest;
use bdk_wallet::chain::spk_client::FullScanResponse as BdkFullScanResponse;
use bdk_wallet::chain::spk_client::SyncRequest as BdkSyncRequest;
use bdk_wallet::chain::spk_client::SyncResponse as BdkSyncResponse;
use bdk_wallet::KeychainKind;
use bdk_wallet::Update as BdkUpdate;

use std::collections::{BTreeMap, HashMap};
use std::sync::Arc;

/// Wrapper around an esplora_client::BlockingClient which includes an internal in-memory transaction
/// cache to avoid re-fetching already downloaded transactions.
#[derive(uniffi::Object)]
pub struct EsploraClient(BlockingClient);

#[uniffi::export]
impl EsploraClient {
    /// Creates a new bdk client from an esplora_client::BlockingClient.
    /// Optional: Set the proxy of the builder.
    #[uniffi::constructor(default(proxy = None))]
    pub fn new(url: String, proxy: Option<String>) -> Self {
        let mut builder = Builder::new(url.as_str());
        if let Some(proxy) = proxy {
            builder = builder.proxy(proxy.as_str());
        }
        Self(builder.build_blocking())
    }

    /// Scan keychain scripts for transactions against Esplora, returning an update that can be
    /// applied to the receiving structures.
    ///
    /// `request` provides the data required to perform a script-pubkey-based full scan
    /// (see [`FullScanRequest`]). The full scan for each keychain (`K`) stops after a gap of
    /// `stop_gap` script pubkeys with no associated transactions. `parallel_requests` specifies
    /// the maximum number of HTTP requests to make in parallel.
    pub fn full_scan(
        &self,
        request: Arc<FullScanRequest>,
        stop_gap: u64,
        parallel_requests: u64,
    ) -> Result<Arc<Update>, EsploraError> {
        // using option and take is not ideal but the only way to take full ownership of the request
        let request: BdkFullScanRequest<KeychainKind> = request
            .0
            .lock()
            .unwrap()
            .take()
            .ok_or(EsploraError::RequestAlreadyConsumed)?;

        let result: BdkFullScanResponse<KeychainKind> =
            std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                self.0
                    .full_scan(request, stop_gap as usize, parallel_requests as usize)
            }))
            .map_err(|payload| {
                let error_message = payload
                    .downcast_ref::<String>()
                    .map(String::as_str)
                    .or_else(|| payload.downcast_ref::<&str>().copied())
                    .unwrap_or("panic in esplora client")
                    .to_string();

                EsploraError::Parsing { error_message }
            })??;

        let update = BdkUpdate {
            last_active_indices: result.last_active_indices,
            tx_update: result.tx_update,
            chain: result.chain_update,
        };

        Ok(Arc::new(Update(update)))
    }

    /// Sync a set of scripts, txids, and/or outpoints against Esplora.
    ///
    /// `request` provides the data required to perform a script-pubkey-based sync (see
    /// [`SyncRequest`]). `parallel_requests` specifies the maximum number of HTTP requests to make
    /// in parallel.
    pub fn sync(
        &self,
        request: Arc<SyncRequest>,
        parallel_requests: u64,
    ) -> Result<Arc<Update>, EsploraError> {
        // using option and take is not ideal but the only way to take full ownership of the request
        let request: BdkSyncRequest<(KeychainKind, u32)> = request
            .0
            .lock()
            .unwrap()
            .take()
            .ok_or(EsploraError::RequestAlreadyConsumed)?;

        let result: BdkSyncResponse =
            std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                self.0.sync(request, parallel_requests as usize)
            }))
            .map_err(|payload| {
                let error_message = payload
                    .downcast_ref::<String>()
                    .map(String::as_str)
                    .or_else(|| payload.downcast_ref::<&str>().copied())
                    .unwrap_or("panic in esplora client")
                    .to_string();

                EsploraError::Parsing { error_message }
            })??;

        let update = BdkUpdate {
            last_active_indices: BTreeMap::default(),
            tx_update: result.tx_update,
            chain: result.chain_update,
        };

        Ok(Arc::new(Update(update)))
    }

    /// Broadcast a [`Transaction`] to Esplora.
    pub fn broadcast(&self, transaction: &Transaction) -> Result<(), EsploraError> {
        let bdk_transaction: BdkTransaction = transaction.into();
        self.0
            .broadcast(&bdk_transaction)
            .map_err(EsploraError::from)
    }

    /// Get a [`Transaction`] option given its [`Txid`].
    pub fn get_tx(&self, txid: Arc<Txid>) -> Result<Option<Arc<Transaction>>, EsploraError> {
        let tx_opt = self.0.get_tx(&txid.0)?;
        Ok(tx_opt.map(|inner| Arc::new(Transaction::from(inner))))
    }

    /// Get the height of the current blockchain tip.
    pub fn get_height(&self) -> Result<u32, EsploraError> {
        self.0.get_height().map_err(EsploraError::from)
    }

    /// Get a map where the key is the confirmation target (in number of
    /// blocks) and the value is the estimated feerate (in sat/vB).
    pub fn get_fee_estimates(&self) -> Result<HashMap<u16, f64>, EsploraError> {
        self.0.get_fee_estimates().map_err(EsploraError::from)
    }

    /// Get the [`BlockHash`] of a specific block height.
    pub fn get_block_hash(&self, block_height: u32) -> Result<Arc<BlockHash>, EsploraError> {
        self.0
            .get_block_hash(block_height)
            .map(|hash| Arc::new(BlockHash(hash)))
            .map_err(EsploraError::from)
    }

    /// Get the status of a [`Transaction`] given its [`Txid`].
    pub fn get_tx_status(&self, txid: Arc<Txid>) -> Result<TxStatus, EsploraError> {
        self.0
            .get_tx_status(&txid.0)
            .map(TxStatus::from)
            .map_err(EsploraError::from)
    }

    /// Get transaction info given its [`Txid`].
    pub fn get_tx_info(&self, txid: Arc<Txid>) -> Result<Option<Tx>, EsploraError> {
        self.0
            .get_tx_info(&txid.0)
            .map(|tx| tx.map(Tx::from))
            .map_err(EsploraError::from)
    }
}
