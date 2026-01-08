use crate::bitcoin::{BlockHash, Header, Transaction, Txid};
use crate::error::ElectrumError;
use crate::types::Update;
use crate::types::{FullScanRequest, SyncRequest};

use bdk_electrum::electrum_client::HeaderNotification as BdkHeaderNotification;
use bdk_electrum::electrum_client::ServerFeaturesRes as BdkServerFeaturesRes;
use bdk_electrum::BdkElectrumClient as BdkBdkElectrumClient;
use bdk_wallet::bitcoin::Transaction as BdkTransaction;
use bdk_wallet::chain::spk_client::FullScanRequest as BdkFullScanRequest;
use bdk_wallet::chain::spk_client::FullScanResponse as BdkFullScanResponse;
use bdk_wallet::chain::spk_client::SyncRequest as BdkSyncRequest;
use bdk_wallet::chain::spk_client::SyncResponse as BdkSyncResponse;
use bdk_wallet::KeychainKind;
use bdk_wallet::Update as BdkUpdate;

use bdk_electrum::electrum_client::ElectrumApi;
use bdk_wallet::bitcoin::hex::{Case, DisplayHex};
use std::collections::BTreeMap;
use std::convert::TryFrom;
use std::sync::Arc;

/// Wrapper around an electrum_client::ElectrumApi which includes an internal in-memory transaction
/// cache to avoid re-fetching already downloaded transactions.
#[derive(uniffi::Object)]
pub struct ElectrumClient(BdkBdkElectrumClient<bdk_electrum::electrum_client::Client>);

#[uniffi::export]
impl ElectrumClient {
    /// Creates a new bdk client from a electrum_client::ElectrumApi
    /// Optional: Set the proxy of the builder
    #[uniffi::constructor(default(socks5 = None))]
    pub fn new(url: String, socks5: Option<String>) -> Result<Self, ElectrumError> {
        let mut config = bdk_electrum::electrum_client::ConfigBuilder::new();
        if let Some(socks5) = socks5 {
            config = config.socks5(Some(bdk_electrum::electrum_client::Socks5Config::new(
                socks5.as_str(),
            )));
        }
        let inner_client =
            bdk_electrum::electrum_client::Client::from_config(url.as_str(), config.build())?;
        let client = BdkBdkElectrumClient::new(inner_client);
        Ok(Self(client))
    }

    /// Full scan the keychain scripts specified with the blockchain (via an Electrum client) and
    /// returns updates for bdk_chain data structures.
    ///
    /// - `request`: struct with data required to perform a spk-based blockchain client
    ///   full scan, see `FullScanRequest`.
    /// - `stop_gap`: the full scan for each keychain stops after a gap of script pubkeys with no
    ///   associated transactions.
    /// - `batch_size`: specifies the max number of script pubkeys to request for in a single batch
    ///   request.
    /// - `fetch_prev_txouts`: specifies whether we want previous `TxOuts` for fee calculation. Note
    ///   that this requires additional calls to the Electrum server, but is necessary for
    ///   calculating the fee on a transaction if your wallet does not own the inputs. Methods like
    ///   `Wallet.calculate_fee` and `Wallet.calculate_fee_rate` will return a
    ///   `CalculateFeeError::MissingTxOut` error if those TxOuts are not present in the transaction
    ///   graph.
    pub fn full_scan(
        &self,
        request: Arc<FullScanRequest>,
        stop_gap: u64,
        batch_size: u64,
        fetch_prev_txouts: bool,
    ) -> Result<Arc<Update>, ElectrumError> {
        // using option and take is not ideal but the only way to take full ownership of the request
        let request: BdkFullScanRequest<KeychainKind> = request
            .0
            .lock()
            .unwrap()
            .take()
            .ok_or(ElectrumError::RequestAlreadyConsumed)?;

        let full_scan_result: BdkFullScanResponse<KeychainKind> = self.0.full_scan(
            request,
            stop_gap as usize,
            batch_size as usize,
            fetch_prev_txouts,
        )?;

        let update = BdkUpdate {
            last_active_indices: full_scan_result.last_active_indices,
            tx_update: full_scan_result.tx_update,
            chain: full_scan_result.chain_update,
        };

        Ok(Arc::new(Update(update)))
    }

    /// Sync a set of scripts with the blockchain (via an Electrum client) for the data specified and returns updates for bdk_chain data structures.
    ///
    /// - `request`: struct with data required to perform a spk-based blockchain client
    ///   sync, see `SyncRequest`.
    /// - `batch_size`: specifies the max number of script pubkeys to request for in a single batch
    ///   request.
    /// - `fetch_prev_txouts`: specifies whether we want previous `TxOuts` for fee calculation. Note
    ///   that this requires additional calls to the Electrum server, but is necessary for
    ///   calculating the fee on a transaction if your wallet does not own the inputs. Methods like
    ///   `Wallet.calculate_fee` and `Wallet.calculate_fee_rate` will return a
    ///   `CalculateFeeError::MissingTxOut` error if those TxOuts are not present in the transaction
    ///   graph.
    ///
    /// If the scripts to sync are unknown, such as when restoring or importing a keychain that may
    /// include scripts that have been used, use full_scan with the keychain.
    pub fn sync(
        &self,
        request: Arc<SyncRequest>,
        batch_size: u64,
        fetch_prev_txouts: bool,
    ) -> Result<Arc<Update>, ElectrumError> {
        // using option and take is not ideal but the only way to take full ownership of the request
        let request: BdkSyncRequest<(KeychainKind, u32)> = request
            .0
            .lock()
            .unwrap()
            .take()
            .ok_or(ElectrumError::RequestAlreadyConsumed)?;

        let sync_result: BdkSyncResponse =
            self.0
                .sync(request, batch_size as usize, fetch_prev_txouts)?;

        let update = BdkUpdate {
            last_active_indices: BTreeMap::default(),
            tx_update: sync_result.tx_update,
            chain: sync_result.chain_update,
        };

        Ok(Arc::new(Update(update)))
    }

    /// Broadcasts a transaction to the network.
    pub fn transaction_broadcast(&self, tx: &Transaction) -> Result<Arc<Txid>, ElectrumError> {
        let bdk_transaction: BdkTransaction = tx.into();
        self.0
            .transaction_broadcast(&bdk_transaction)
            .map_err(ElectrumError::from)
            .map(|txid| Arc::new(Txid(txid)))
    }

    /// Returns the capabilities of the server.
    pub fn server_features(&self) -> Result<ServerFeaturesRes, ElectrumError> {
        let res = self
            .0
            .inner
            .server_features()
            .map_err(ElectrumError::from)?;

        ServerFeaturesRes::try_from(res)
    }

    /// Estimates the fee required in bitcoin per kilobyte to confirm a transaction in `number` blocks.
    pub fn estimate_fee(&self, number: u64) -> Result<f64, ElectrumError> {
        self.0
            .inner
            .estimate_fee(number as usize)
            .map_err(ElectrumError::from)
    }

    /// Subscribes to notifications for new block headers, by sending a blockchain.headers.subscribe call.
    pub fn block_headers_subscribe(&self) -> Result<HeaderNotification, ElectrumError> {
        self.0
            .inner
            .block_headers_subscribe()
            .map_err(ElectrumError::from)
            .map(HeaderNotification::from)
    }

    /// Pings the server.
    pub fn ping(&self) -> Result<(), ElectrumError> {
        self.0.inner.ping().map_err(ElectrumError::from)
    }
}

/// Response to an ElectrumClient.server_features request.
#[derive(uniffi::Record)]
pub struct ServerFeaturesRes {
    /// Server version reported.
    pub server_version: String,
    /// Hash of the genesis block.
    pub genesis_hash: Arc<BlockHash>,
    /// Minimum supported version of the protocol.
    pub protocol_min: String,
    /// Maximum supported version of the protocol.
    pub protocol_max: String,
    /// Hash function used to create the `ScriptHash`.
    pub hash_function: Option<String>,
    /// Pruned height of the server.
    pub pruning: Option<i64>,
}

impl TryFrom<BdkServerFeaturesRes> for ServerFeaturesRes {
    type Error = ElectrumError;

    fn try_from(value: BdkServerFeaturesRes) -> Result<ServerFeaturesRes, ElectrumError> {
        let hash_str = value.genesis_hash.to_hex_string(Case::Lower);
        let blockhash = hash_str
            .parse::<bdk_wallet::bitcoin::BlockHash>()
            .map_err(|err| ElectrumError::InvalidResponse {
                error_message: format!(
                    "invalid genesis hash returned by server: {hash_str} ({err})"
                ),
            })?;

        Ok(ServerFeaturesRes {
            server_version: value.server_version,
            genesis_hash: Arc::new(BlockHash(blockhash)),
            protocol_min: value.protocol_min,
            protocol_max: value.protocol_max,
            hash_function: value.hash_function,
            pruning: value.pruning,
        })
    }
}

/// Notification of a new block header.
#[derive(uniffi::Record)]
pub struct HeaderNotification {
    /// New block height.
    pub height: u64,
    /// Newly added header.
    pub header: Header,
}

impl From<BdkHeaderNotification> for HeaderNotification {
    fn from(value: BdkHeaderNotification) -> HeaderNotification {
        HeaderNotification {
            height: value.height as u64,
            header: value.header.into(),
        }
    }
}
