use crate::bitcoin::{
    Address, Amount, BlockHash, DescriptorId, HashableOutPoint, OutPoint, Script, Transaction,
    TxOut, Txid,
};
use crate::descriptor::Descriptor;
use crate::error::{CreateTxError, RequestBuilderError};

use bdk_wallet::bitcoin::absolute::LockTime as BdkLockTime;
use bdk_wallet::chain::spk_client::SyncItem;
use bdk_wallet::chain::BlockId as BdkBlockId;
use bdk_wallet::chain::Merge;

use bdk_wallet::bitcoin::Transaction as BdkTransaction;
use bdk_wallet::chain::spk_client::FullScanRequest as BdkFullScanRequest;
use bdk_wallet::chain::spk_client::FullScanRequestBuilder as BdkFullScanRequestBuilder;
use bdk_wallet::chain::spk_client::SyncRequest as BdkSyncRequest;
use bdk_wallet::chain::spk_client::SyncRequestBuilder as BdkSyncRequestBuilder;
use bdk_wallet::chain::tx_graph::CanonicalTx as BdkCanonicalTx;
use bdk_wallet::chain::{
    ChainPosition as BdkChainPosition, ConfirmationBlockTime as BdkConfirmationBlockTime,
};
use bdk_wallet::descriptor::policy::{
    Condition as BdkCondition, PkOrF as BdkPkOrF, Policy as BdkPolicy,
    Satisfaction as BdkSatisfaction, SatisfiableItem as BdkSatisfiableItem,
};
#[allow(deprecated)]
use bdk_wallet::signer::{SignOptions as BdkSignOptions, TapLeavesOptions};
use bdk_wallet::AddressInfo as BdkAddressInfo;
use bdk_wallet::Balance as BdkBalance;
use bdk_wallet::LocalOutput as BdkLocalOutput;
use bdk_wallet::Update as BdkUpdate;

use std::collections::{BTreeMap, BTreeSet, HashMap};
use std::convert::TryFrom;
use std::sync::{Arc, Mutex};

use crate::{impl_from_core_type, impl_into_core_type};
use bdk_esplora::esplora_client::api::Tx as BdkTx;
use bdk_esplora::esplora_client::api::TxStatus as BdkTxStatus;

type KeychainKind = bdk_wallet::KeychainKind;

/// Types of keychains.
#[uniffi::remote(Enum)]
pub enum KeychainKind {
    /// External keychain, used for deriving recipient addresses.
    External = 0,
    /// Internal keychain, used for deriving change addresses.
    Internal = 1,
}

type WordCount = bdk_wallet::keys::bip39::WordCount;

#[uniffi::remote(Enum)]
pub enum WordCount {
    Words12,
    Words15,
    Words18,
    Words21,
    Words24,
}

/// Represents the observed position of some chain data.
#[derive(Debug, uniffi::Enum, Clone)]
pub enum ChainPosition {
    /// The chain data is confirmed as it is anchored in the best chain by `A`.
    Confirmed {
        confirmation_block_time: ConfirmationBlockTime,
        /// A child transaction that has been confirmed. Due to incomplete information,
        /// it is only known that this transaction is confirmed at a chain height less than
        /// or equal to this child TXID.
        transitively: Option<Arc<Txid>>,
    },
    /// The transaction was last seen in the mempool at this timestamp.
    Unconfirmed { timestamp: Option<u64> },
}

impl From<BdkChainPosition<BdkConfirmationBlockTime>> for ChainPosition {
    fn from(chain_position: BdkChainPosition<BdkConfirmationBlockTime>) -> Self {
        match chain_position {
            BdkChainPosition::Confirmed {
                anchor,
                transitively,
            } => {
                let block_id = BlockId {
                    height: anchor.block_id.height,
                    hash: Arc::new(BlockHash(anchor.block_id.hash)),
                };
                ChainPosition::Confirmed {
                    confirmation_block_time: ConfirmationBlockTime {
                        block_id,
                        confirmation_time: anchor.confirmation_time,
                    },
                    transitively: transitively.map(|t| Arc::new(Txid(t))),
                }
            }
            BdkChainPosition::Unconfirmed { last_seen, .. } => ChainPosition::Unconfirmed {
                timestamp: last_seen,
            },
        }
    }
}

/// Represents the confirmation block and time of a transaction.
#[derive(Debug, Clone, PartialEq, Eq, std::hash::Hash, uniffi::Record)]
pub struct ConfirmationBlockTime {
    /// The anchor block.
    pub block_id: BlockId,
    /// The confirmation time of the transaction being anchored.
    pub confirmation_time: u64,
}

impl From<BdkConfirmationBlockTime> for ConfirmationBlockTime {
    fn from(value: BdkConfirmationBlockTime) -> Self {
        Self {
            block_id: value.block_id.into(),
            confirmation_time: value.confirmation_time,
        }
    }
}

impl From<ConfirmationBlockTime> for BdkConfirmationBlockTime {
    fn from(value: ConfirmationBlockTime) -> Self {
        Self {
            block_id: value.block_id.into(),
            confirmation_time: value.confirmation_time,
        }
    }
}

/// A reference to a block in the canonical chain.
#[derive(Debug, Clone, PartialEq, Eq, std::hash::Hash, uniffi::Record)]
pub struct BlockId {
    /// The height of the block.
    pub height: u32,
    /// The hash of the block.
    pub hash: Arc<BlockHash>,
}

impl From<BdkBlockId> for BlockId {
    fn from(block_id: BdkBlockId) -> Self {
        BlockId {
            height: block_id.height,
            hash: Arc::new(BlockHash(block_id.hash)),
        }
    }
}

impl From<BlockId> for BdkBlockId {
    fn from(value: BlockId) -> Self {
        Self {
            height: value.height,
            hash: value.hash.0,
        }
    }
}

/// A transaction that is deemed to be part of the canonical history.
#[derive(uniffi::Record)]
pub struct CanonicalTx {
    /// The transaction.
    pub transaction: Arc<Transaction>,
    /// How the transaction is observed in the canonical chain (confirmed or unconfirmed).
    pub chain_position: ChainPosition,
}

impl From<BdkCanonicalTx<'_, Arc<BdkTransaction>, BdkConfirmationBlockTime>> for CanonicalTx {
    fn from(tx: BdkCanonicalTx<'_, Arc<BdkTransaction>, BdkConfirmationBlockTime>) -> Self {
        CanonicalTx {
            transaction: Arc::new(Transaction::from(tx.tx_node.tx.as_ref().clone())),
            chain_position: tx.chain_position.into(),
        }
    }
}

/// A bitcoin script and associated amount.
#[derive(uniffi::Record)]
pub struct ScriptAmount {
    /// The underlying script.
    pub script: Arc<Script>,
    /// The amount owned by the script.
    pub amount: Arc<Amount>,
}

/// A derived address and the index it was found at.
#[derive(uniffi::Record)]
pub struct AddressInfo {
    /// Child index of this address
    pub index: u32,
    /// The address
    pub address: Arc<Address>,
    /// Type of keychain
    pub keychain: KeychainKind,
}

impl From<BdkAddressInfo> for AddressInfo {
    fn from(address_info: BdkAddressInfo) -> Self {
        AddressInfo {
            index: address_info.index,
            address: Arc::new(address_info.address.into()),
            keychain: address_info.keychain,
        }
    }
}

/// Balance, differentiated into various categories.
#[derive(uniffi::Record)]
pub struct Balance {
    /// All coinbase outputs not yet matured
    pub immature: Arc<Amount>,
    /// Unconfirmed UTXOs generated by a wallet tx
    pub trusted_pending: Arc<Amount>,
    /// Unconfirmed UTXOs received from an external wallet
    pub untrusted_pending: Arc<Amount>,
    /// Confirmed and immediately spendable balance
    pub confirmed: Arc<Amount>,
    /// Get sum of trusted_pending and confirmed coins.
    ///
    /// This is the balance you can spend right now that shouldn't get cancelled via another party
    /// double spending it.
    pub trusted_spendable: Arc<Amount>,
    /// Get the whole balance visible to the wallet.
    pub total: Arc<Amount>,
}

impl From<BdkBalance> for Balance {
    fn from(bdk_balance: BdkBalance) -> Self {
        Balance {
            immature: Arc::new(bdk_balance.immature.into()),
            trusted_pending: Arc::new(bdk_balance.trusted_pending.into()),
            untrusted_pending: Arc::new(bdk_balance.untrusted_pending.into()),
            confirmed: Arc::new(bdk_balance.confirmed.into()),
            trusted_spendable: Arc::new(bdk_balance.trusted_spendable().into()),
            total: Arc::new(bdk_balance.total().into()),
        }
    }
}

/// An unspent output owned by a [`Wallet`].
#[derive(uniffi::Record)]
pub struct LocalOutput {
    /// Reference to a transaction output
    pub outpoint: OutPoint,
    /// Transaction output
    pub txout: TxOut,
    /// Type of keychain
    pub keychain: KeychainKind,
    /// Whether this UTXO is spent or not
    pub is_spent: bool,
    /// The derivation index for the script pubkey in the wallet
    pub derivation_index: u32,
    /// The position of the output in the blockchain.
    pub chain_position: ChainPosition,
}

impl From<BdkLocalOutput> for LocalOutput {
    fn from(local_utxo: BdkLocalOutput) -> Self {
        LocalOutput {
            outpoint: OutPoint {
                txid: Arc::new(Txid(local_utxo.outpoint.txid)),
                vout: local_utxo.outpoint.vout,
            },
            txout: TxOut {
                value: Arc::new(Amount(local_utxo.txout.value)),
                script_pubkey: Arc::new(Script(local_utxo.txout.script_pubkey)),
            },
            keychain: local_utxo.keychain,
            is_spent: local_utxo.is_spent,
            derivation_index: local_utxo.derivation_index,
            chain_position: local_utxo.chain_position.into(),
        }
    }
}

// Callback for the FullScanRequest
#[uniffi::export(with_foreign)]
pub trait FullScanScriptInspector: Sync + Send {
    fn inspect(&self, keychain: KeychainKind, index: u32, script: Arc<Script>);
}

// Callback for the SyncRequest
#[uniffi::export(with_foreign)]
pub trait SyncScriptInspector: Sync + Send {
    fn inspect(&self, script: Arc<Script>, total: u64);
}

#[derive(uniffi::Object)]
pub struct FullScanRequestBuilder(
    pub(crate) Mutex<Option<BdkFullScanRequestBuilder<KeychainKind>>>,
);

#[derive(uniffi::Object)]
pub struct SyncRequestBuilder(pub(crate) Mutex<Option<BdkSyncRequestBuilder<(KeychainKind, u32)>>>);

#[derive(uniffi::Object)]
pub struct FullScanRequest(pub(crate) Mutex<Option<BdkFullScanRequest<KeychainKind>>>);

#[derive(uniffi::Object)]
pub struct SyncRequest(pub(crate) Mutex<Option<BdkSyncRequest<(KeychainKind, u32)>>>);

#[uniffi::export]
impl SyncRequestBuilder {
    pub fn inspect_spks(
        &self,
        inspector: Arc<dyn SyncScriptInspector>,
    ) -> Result<Arc<Self>, RequestBuilderError> {
        let guard = self
            .0
            .lock()
            .unwrap()
            .take()
            .ok_or(RequestBuilderError::RequestAlreadyConsumed)?;
        let sync_request_builder = guard.inspect({
            move |script, progress| {
                if let SyncItem::Spk(_, spk) = script {
                    inspector.inspect(Arc::new(Script(spk.to_owned())), progress.total() as u64)
                }
            }
        });
        Ok(Arc::new(SyncRequestBuilder(Mutex::new(Some(
            sync_request_builder,
        )))))
    }

    pub fn build(&self) -> Result<Arc<SyncRequest>, RequestBuilderError> {
        let guard = self
            .0
            .lock()
            .unwrap()
            .take()
            .ok_or(RequestBuilderError::RequestAlreadyConsumed)?;
        Ok(Arc::new(SyncRequest(Mutex::new(Some(guard.build())))))
    }
}

#[uniffi::export]
impl FullScanRequestBuilder {
    pub fn inspect_spks_for_all_keychains(
        &self,
        inspector: Arc<dyn FullScanScriptInspector>,
    ) -> Result<Arc<Self>, RequestBuilderError> {
        let guard = self
            .0
            .lock()
            .unwrap()
            .take()
            .ok_or(RequestBuilderError::RequestAlreadyConsumed)?;
        let full_scan_request_builder = guard.inspect(move |keychain, index, script| {
            inspector.inspect(keychain, index, Arc::new(Script(script.to_owned())))
        });
        Ok(Arc::new(FullScanRequestBuilder(Mutex::new(Some(
            full_scan_request_builder,
        )))))
    }

    pub fn build(&self) -> Result<Arc<FullScanRequest>, RequestBuilderError> {
        let guard = self
            .0
            .lock()
            .unwrap()
            .take()
            .ok_or(RequestBuilderError::RequestAlreadyConsumed)?;
        Ok(Arc::new(FullScanRequest(Mutex::new(Some(guard.build())))))
    }
}

/// An update for a wallet containing chain, descriptor index, and transaction data.
#[derive(uniffi::Object)]
pub struct Update(pub(crate) BdkUpdate);

/// The total value sent and received.
#[derive(uniffi::Record)]
pub struct SentAndReceivedValues {
    /// Amount sent in the transaction.
    pub sent: Arc<Amount>,
    /// The amount received in the transaction, possibly as a change output(s).
    pub received: Arc<Amount>,
}

/// The keychain kind and the index in that keychain.
#[derive(uniffi::Record)]
pub struct KeychainAndIndex {
    /// Type of keychains.
    pub keychain: KeychainKind,
    /// The index in the keychain.
    pub index: u32,
}

/// Descriptor spending policy
#[derive(Debug, PartialEq, Eq, Clone, uniffi::Object)]
pub struct Policy(BdkPolicy);

impl_from_core_type!(BdkPolicy, Policy);
impl_into_core_type!(Policy, BdkPolicy);

#[uniffi::export]
impl Policy {
    pub fn id(&self) -> String {
        self.0.id.clone()
    }

    pub fn as_string(&self) -> String {
        bdk_wallet::serde_json::to_string(&self.0).unwrap()
    }

    pub fn requires_path(&self) -> bool {
        self.0.requires_path()
    }

    pub fn item(&self) -> SatisfiableItem {
        self.0.item.clone().into()
    }

    pub fn satisfaction(&self) -> Satisfaction {
        self.0.satisfaction.clone().into()
    }

    pub fn contribution(&self) -> Satisfaction {
        self.0.contribution.clone().into()
    }
}

#[derive(Debug, Clone, uniffi::Enum)]
pub enum SatisfiableItem {
    EcdsaSignature {
        key: PkOrF,
    },
    SchnorrSignature {
        key: PkOrF,
    },
    Sha256Preimage {
        hash: String,
    },
    Hash256Preimage {
        hash: String,
    },
    Ripemd160Preimage {
        hash: String,
    },
    Hash160Preimage {
        hash: String,
    },
    AbsoluteTimelock {
        value: LockTime,
    },
    RelativeTimelock {
        value: u32,
    },
    Multisig {
        keys: Vec<PkOrF>,
        threshold: u64,
    },
    Thresh {
        items: Vec<Arc<Policy>>,
        threshold: u64,
    },
}

impl From<BdkSatisfiableItem> for SatisfiableItem {
    fn from(value: BdkSatisfiableItem) -> Self {
        match value {
            BdkSatisfiableItem::EcdsaSignature(pk_or_f) => SatisfiableItem::EcdsaSignature {
                key: pk_or_f.into(),
            },
            BdkSatisfiableItem::SchnorrSignature(pk_or_f) => SatisfiableItem::SchnorrSignature {
                key: pk_or_f.into(),
            },
            BdkSatisfiableItem::Sha256Preimage { hash } => SatisfiableItem::Sha256Preimage {
                hash: hash.to_string(),
            },
            BdkSatisfiableItem::Hash256Preimage { hash } => SatisfiableItem::Hash256Preimage {
                hash: hash.to_string(),
            },
            BdkSatisfiableItem::Ripemd160Preimage { hash } => SatisfiableItem::Ripemd160Preimage {
                hash: hash.to_string(),
            },
            BdkSatisfiableItem::Hash160Preimage { hash } => SatisfiableItem::Hash160Preimage {
                hash: hash.to_string(),
            },
            BdkSatisfiableItem::AbsoluteTimelock { value } => SatisfiableItem::AbsoluteTimelock {
                value: value.into(),
            },
            BdkSatisfiableItem::RelativeTimelock { value } => SatisfiableItem::RelativeTimelock {
                value: value.to_consensus_u32(),
            },
            BdkSatisfiableItem::Multisig { keys, threshold } => SatisfiableItem::Multisig {
                keys: keys.iter().map(|e| e.to_owned().into()).collect(),
                threshold: threshold as u64,
            },
            BdkSatisfiableItem::Thresh { items, threshold } => SatisfiableItem::Thresh {
                items: items
                    .iter()
                    .map(|e| Arc::new(e.to_owned().into()))
                    .collect(),
                threshold: threshold as u64,
            },
        }
    }
}

#[derive(Debug, Clone, uniffi::Enum)]
pub enum PkOrF {
    Pubkey { value: String },
    XOnlyPubkey { value: String },
    Fingerprint { value: String },
}

impl From<BdkPkOrF> for PkOrF {
    fn from(value: BdkPkOrF) -> Self {
        match value {
            BdkPkOrF::Pubkey(public_key) => PkOrF::Pubkey {
                value: public_key.to_string(),
            },
            BdkPkOrF::XOnlyPubkey(xonly_public_key) => PkOrF::XOnlyPubkey {
                value: xonly_public_key.to_string(),
            },
            BdkPkOrF::Fingerprint(fingerprint) => PkOrF::Fingerprint {
                value: fingerprint.to_string(),
            },
        }
    }
}

#[derive(Debug, Clone, uniffi::Enum)]
pub enum LockTime {
    Blocks { height: u32 },
    Seconds { consensus_time: u32 },
}

impl From<BdkLockTime> for LockTime {
    fn from(value: BdkLockTime) -> Self {
        match value {
            BdkLockTime::Blocks(height) => LockTime::Blocks {
                height: height.to_consensus_u32(),
            },
            BdkLockTime::Seconds(time) => LockTime::Seconds {
                consensus_time: time.to_consensus_u32(),
            },
        }
    }
}

impl TryFrom<&LockTime> for BdkLockTime {
    type Error = CreateTxError;

    fn try_from(value: &LockTime) -> Result<Self, CreateTxError> {
        match value {
            LockTime::Blocks { height } => BdkLockTime::from_height(*height)
                .map_err(|_| CreateTxError::LockTimeConversionError),
            LockTime::Seconds { consensus_time } => BdkLockTime::from_time(*consensus_time)
                .map_err(|_| CreateTxError::LockTimeConversionError),
        }
    }
}

#[derive(Debug, Clone, uniffi::Enum)]
pub enum Satisfaction {
    Partial {
        n: u64,
        m: u64,
        items: Vec<u64>,
        sorted: Option<bool>,
        conditions: HashMap<u32, Vec<Condition>>,
    },
    PartialComplete {
        n: u64,
        m: u64,
        items: Vec<u64>,
        sorted: Option<bool>,
        conditions: HashMap<Vec<u32>, Vec<Condition>>,
    },
    Complete {
        condition: Condition,
    },
    None {
        msg: String,
    },
}

impl From<BdkSatisfaction> for Satisfaction {
    fn from(value: BdkSatisfaction) -> Self {
        match value {
            BdkSatisfaction::Partial {
                n,
                m,
                items,
                sorted,
                conditions,
            } => Satisfaction::Partial {
                n: n as u64,
                m: m as u64,
                items: items.iter().map(|e| e.to_owned() as u64).collect(),
                sorted,
                conditions: conditions
                    .into_iter()
                    .map(|(index, conditions)| {
                        (
                            index as u32,
                            conditions.into_iter().map(|e| e.into()).collect(),
                        )
                    })
                    .collect(),
            },
            BdkSatisfaction::PartialComplete {
                n,
                m,
                items,
                sorted,
                conditions,
            } => Satisfaction::PartialComplete {
                n: n as u64,
                m: m as u64,
                items: items.iter().map(|e| e.to_owned() as u64).collect(),
                sorted,
                conditions: conditions
                    .into_iter()
                    .map(|(index, conditions)| {
                        (
                            index.iter().map(|e| e.to_owned() as u32).collect(),
                            conditions.into_iter().map(|e| e.into()).collect(),
                        )
                    })
                    .collect(),
            },
            BdkSatisfaction::Complete { condition } => Satisfaction::Complete {
                condition: condition.into(),
            },
            BdkSatisfaction::None => Satisfaction::None {
                msg: "Cannot satisfy or contribute to the policy item".to_string(),
            },
        }
    }
}

/// An extra condition that must be satisfied but that is out of control of the user
#[derive(Debug, Clone, uniffi::Record)]
pub struct Condition {
    /// Optional CheckSequenceVerify condition
    pub csv: Option<u32>,
    /// Optional timelock condition
    pub timelock: Option<LockTime>,
}

impl From<BdkCondition> for Condition {
    fn from(value: BdkCondition) -> Self {
        Condition {
            csv: value.csv.map(|e| e.to_consensus_u32()),
            timelock: value.timelock.map(|e| e.into()),
        }
    }
}

// This is a wrapper type around the bdk type [SignOptions](https://docs.rs/bdk_wallet/1.0.0/bdk_wallet/signer/struct.SignOptions.html)
// because we did not want to expose the complexity behind the `TapLeavesOptions` type. When
// transforming from a SignOption to a BdkSignOptions, we simply use the default values for
// TapLeavesOptions.
/// Options for a software signer.
///
/// Adjust the behavior of our software signers and the way a transaction is finalized.
#[allow(deprecated)]
#[derive(uniffi::Record)]
pub struct SignOptions {
    /// Whether the signer should trust the `witness_utxo`, if the `non_witness_utxo` hasn't been
    /// provided
    ///
    /// Defaults to `false` to mitigate the "SegWit bug" which could trick the wallet into
    /// paying a fee larger than expected.
    ///
    /// Some wallets, especially if relatively old, might not provide the `non_witness_utxo` for
    /// SegWit transactions in the PSBT they generate: in those cases setting this to `true`
    /// should correctly produce a signature, at the expense of an increased trust in the creator
    /// of the PSBT.
    ///
    /// For more details see: <https://blog.trezor.io/details-of-firmware-updates-for-trezor-one-version-1-9-1-and-trezor-model-t-version-2-3-1-1eba8f60f2dd>
    pub trust_witness_utxo: bool,
    /// Whether the wallet should assume a specific height has been reached when trying to finalize
    /// a transaction
    ///
    /// The wallet will only "use" a timelock to satisfy the spending policy of an input if the
    /// timelock height has already been reached. This option allows overriding the "current height" to let the
    /// wallet use timelocks in the future to spend a coin.
    pub assume_height: Option<u32>,
    /// Whether the signer should use the `sighash_type` set in the PSBT when signing, no matter
    /// what its value is
    ///
    /// Defaults to `false` which will only allow signing using `SIGHASH_ALL`.
    pub allow_all_sighashes: bool,
    /// Whether to try finalizing the PSBT after the inputs are signed.
    ///
    /// Defaults to `true` which will try finalizing PSBT after inputs are signed.
    pub try_finalize: bool,
    /// Whether we should try to sign a taproot transaction with the taproot internal key
    /// or not. This option is ignored if we're signing a non-taproot PSBT.
    ///
    /// Defaults to `true`, i.e., we always try to sign with the taproot internal key.
    pub sign_with_tap_internal_key: bool,
    /// Whether we should grind ECDSA signature to ensure signing with low r
    /// or not.
    /// Defaults to `true`, i.e., we always grind ECDSA signature to sign with low r.
    pub allow_grinding: bool,
}

#[allow(deprecated)]
impl From<SignOptions> for BdkSignOptions {
    fn from(options: SignOptions) -> BdkSignOptions {
        BdkSignOptions {
            trust_witness_utxo: options.trust_witness_utxo,
            assume_height: options.assume_height,
            allow_all_sighashes: options.allow_all_sighashes,
            try_finalize: options.try_finalize,
            tap_leaves_options: TapLeavesOptions::default(),
            sign_with_tap_internal_key: options.sign_with_tap_internal_key,
            allow_grinding: options.allow_grinding,
        }
    }
}

/// Transaction confirmation metadata.
#[derive(uniffi::Record, Debug)]
pub struct TxStatus {
    /// Is the transaction in a block.
    pub confirmed: bool,
    /// Height of the block this transaction was included.
    pub block_height: Option<u32>,
    /// Hash of the block.
    pub block_hash: Option<Arc<BlockHash>>,
    /// The time shown in the block, not necessarily the same time as when the block was found.
    pub block_time: Option<u64>,
}

impl From<BdkTxStatus> for TxStatus {
    fn from(status: BdkTxStatus) -> Self {
        TxStatus {
            confirmed: status.confirmed,
            block_height: status.block_height,
            block_hash: status.block_hash.map(|h| Arc::new(BlockHash(h))),
            block_time: status.block_time,
        }
    }
}

/// Bitcoin transaction metadata.
#[derive(Debug, uniffi::Record)]
pub struct Tx {
    /// The transaction identifier.
    pub txid: Arc<Txid>,
    /// The transaction version, of which 0, 1, 2 are standard.
    pub version: i32,
    /// The block height or time restriction on the transaction.
    pub locktime: u32,
    /// The size of the transaction in bytes.
    pub size: u64,
    /// The weight units of this transaction.
    pub weight: u64,
    /// The fee of this transaction in satoshis.
    pub fee: u64,
    /// Confirmation status and data.
    pub status: TxStatus,
}

impl From<BdkTx> for Tx {
    fn from(tx: BdkTx) -> Self {
        Self {
            txid: Arc::new(Txid(tx.txid)),
            version: tx.version,
            locktime: tx.locktime,
            size: tx.size as u64,
            weight: tx.weight,
            fee: tx.fee,
            status: tx.status.into(),
        }
    }
}

/// This type replaces the Rust tuple `(tx, last_seen)` used in the Wallet::apply_unconfirmed_txs` method,
/// where `last_seen` is the timestamp of when the transaction `tx` was last seen in the mempool.
#[derive(uniffi::Record)]
pub struct UnconfirmedTx {
    pub tx: Arc<Transaction>,
    pub last_seen: u64,
}

/// This type replaces the Rust tuple `(txid, evicted_at)` used in the Wallet::apply_evicted_txs` method,
/// where `evicted_at` is the timestamp of when the transaction `txid` was evicted from the mempool.
/// Transactions may be evicted for paying a low fee rate or having invalid scripts.
#[derive(uniffi::Record)]
pub struct EvictedTx {
    pub txid: Arc<Txid>,
    pub evicted_at: u64,
}

/// Mapping of descriptors to their last revealed index.
#[derive(Debug, Clone, uniffi::Record)]
pub struct IndexerChangeSet {
    pub last_revealed: HashMap<Arc<DescriptorId>, u32>,
}

impl From<bdk_wallet::chain::indexer::keychain_txout::ChangeSet> for IndexerChangeSet {
    fn from(mut value: bdk_wallet::chain::indexer::keychain_txout::ChangeSet) -> Self {
        let mut changes = HashMap::new();
        for (id, index) in core::mem::take(&mut value.last_revealed) {
            changes.insert(Arc::new(DescriptorId(id.0)), index);
        }
        Self {
            last_revealed: changes,
        }
    }
}

impl From<IndexerChangeSet> for bdk_wallet::chain::indexer::keychain_txout::ChangeSet {
    fn from(mut value: IndexerChangeSet) -> Self {
        let mut changes = BTreeMap::new();
        for (id, index) in core::mem::take(&mut value.last_revealed) {
            let descriptor_id = bdk_wallet::chain::DescriptorId(id.0);
            changes.insert(descriptor_id, index);
        }
        Self {
            last_revealed: changes,
            spk_cache: Default::default(),
        }
    }
}

impl Default for IndexerChangeSet {
    fn default() -> Self {
        bdk_wallet::chain::indexer::keychain_txout::ChangeSet::default().into()
    }
}

/// The hash added or removed at the given height.
#[derive(Debug, Clone, uniffi::Record)]
pub struct ChainChange {
    /// Effected height
    pub height: u32,
    /// A hash was added or must be removed.
    pub hash: Option<Arc<BlockHash>>,
}

/// Changes to the local chain
#[derive(Debug, Clone, uniffi::Record)]
pub struct LocalChainChangeSet {
    pub changes: Vec<ChainChange>,
}

impl From<bdk_wallet::chain::local_chain::ChangeSet> for LocalChainChangeSet {
    fn from(mut value: bdk_wallet::chain::local_chain::ChangeSet) -> Self {
        let mut changes = Vec::with_capacity(value.blocks.len());
        for (height, hash) in core::mem::take(&mut value.blocks) {
            let hash = hash.map(|h| Arc::new(BlockHash(h)));
            let change = ChainChange { height, hash };
            changes.push(change);
        }
        Self { changes }
    }
}

impl From<LocalChainChangeSet> for bdk_wallet::chain::local_chain::ChangeSet {
    fn from(mut value: LocalChainChangeSet) -> Self {
        let mut changes = BTreeMap::new();
        for change in core::mem::take(&mut value.changes) {
            let height = change.height;
            let hash = change.hash.map(|h| h.0);
            changes.insert(height, hash);
        }
        Self { blocks: changes }
    }
}

impl Default for LocalChainChangeSet {
    fn default() -> Self {
        bdk_wallet::chain::local_chain::ChangeSet::default().into()
    }
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct Anchor {
    pub confirmation_block_time: ConfirmationBlockTime,
    pub txid: Arc<Txid>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct TxGraphChangeSet {
    pub txs: Vec<Arc<Transaction>>,
    pub txouts: HashMap<Arc<HashableOutPoint>, TxOut>,
    pub anchors: Vec<Anchor>,
    pub last_seen: HashMap<Arc<Txid>, u64>,
    pub first_seen: HashMap<Arc<Txid>, u64>,
    pub last_evicted: HashMap<Arc<Txid>, u64>,
}

impl From<bdk_wallet::chain::tx_graph::ChangeSet<BdkConfirmationBlockTime>> for TxGraphChangeSet {
    fn from(mut value: bdk_wallet::chain::tx_graph::ChangeSet<BdkConfirmationBlockTime>) -> Self {
        let btree_txs = core::mem::take(&mut value.txs);
        let txs = btree_txs
            .into_iter()
            .map(|tx| Arc::new(tx.as_ref().into()))
            .collect::<Vec<Arc<Transaction>>>();
        let mut txouts = HashMap::new();
        for (outpoint, txout) in core::mem::take(&mut value.txouts) {
            txouts.insert(Arc::new(HashableOutPoint(outpoint.into())), txout.into());
        }
        let mut anchors = Vec::new();
        for anchor in core::mem::take(&mut value.anchors) {
            let confirmation_block_time = anchor.0.into();
            let txid = Arc::new(Txid(anchor.1));
            let anchor = Anchor {
                confirmation_block_time,
                txid,
            };
            anchors.push(anchor);
        }
        let mut last_seen = HashMap::new();
        for (txid, time) in core::mem::take(&mut value.last_seen) {
            last_seen.insert(Arc::new(Txid(txid)), time);
        }

        let mut first_seen = HashMap::new();
        for (txid, time) in core::mem::take(&mut value.first_seen) {
            first_seen.insert(Arc::new(Txid(txid)), time);
        }

        let mut last_evicted = HashMap::new();
        for (txid, time) in core::mem::take(&mut value.last_evicted) {
            last_evicted.insert(Arc::new(Txid(txid)), time);
        }

        TxGraphChangeSet {
            txs,
            txouts,
            anchors,
            last_seen,
            first_seen,
            last_evicted,
        }
    }
}

impl Default for TxGraphChangeSet {
    fn default() -> Self {
        bdk_wallet::chain::tx_graph::ChangeSet::default().into()
    }
}

impl From<TxGraphChangeSet> for bdk_wallet::chain::tx_graph::ChangeSet<BdkConfirmationBlockTime> {
    fn from(mut value: TxGraphChangeSet) -> Self {
        let mut txs = BTreeSet::new();
        for tx in core::mem::take(&mut value.txs) {
            let tx = Arc::new(tx.as_ref().into());
            txs.insert(tx);
        }
        let mut txouts = BTreeMap::new();
        for txout in core::mem::take(&mut value.txouts) {
            txouts.insert(txout.0.outpoint().into(), txout.1.into());
        }
        let mut anchors = BTreeSet::new();
        for anchor in core::mem::take(&mut value.anchors) {
            let txid = anchor.txid.0;
            anchors.insert((anchor.confirmation_block_time.into(), txid));
        }
        let mut last_seen = BTreeMap::new();
        for (txid, time) in core::mem::take(&mut value.last_seen) {
            last_seen.insert(txid.0, time);
        }

        let mut first_seen = BTreeMap::new();
        for (txid, time) in core::mem::take(&mut value.first_seen) {
            first_seen.insert(txid.0, time);
        }

        let mut last_evicted = BTreeMap::new();
        for (txid, time) in core::mem::take(&mut value.last_evicted) {
            last_evicted.insert(txid.0, time);
        }

        Self {
            txs,
            txouts,
            anchors,
            last_seen,
            first_seen,
            last_evicted,
        }
    }
}

#[derive(Debug, Clone, uniffi::Object)]
pub struct ChangeSet {
    descriptor: Option<Arc<Descriptor>>,
    change_descriptor: Option<Arc<Descriptor>>,
    network: Option<bdk_wallet::bitcoin::Network>,
    local_chain: LocalChainChangeSet,
    tx_graph: TxGraphChangeSet,
    indexer: IndexerChangeSet,
}

#[uniffi::export]
impl ChangeSet {
    /// Create an empty `ChangeSet`.
    #[uniffi::constructor]
    #[allow(clippy::new_without_default)]
    pub fn new() -> Self {
        bdk_wallet::ChangeSet::default().into()
    }

    #[uniffi::constructor]
    pub fn from_aggregate(
        descriptor: Option<Arc<Descriptor>>,
        change_descriptor: Option<Arc<Descriptor>>,
        network: Option<bdk_wallet::bitcoin::Network>,
        local_chain: LocalChainChangeSet,
        tx_graph: TxGraphChangeSet,
        indexer: IndexerChangeSet,
    ) -> Self {
        Self {
            descriptor,
            change_descriptor,
            network,
            local_chain,
            tx_graph,
            indexer,
        }
    }

    #[uniffi::constructor]
    pub fn from_descriptor_and_network(
        descriptor: Option<Arc<Descriptor>>,
        change_descriptor: Option<Arc<Descriptor>>,
        network: Option<bdk_wallet::bitcoin::Network>,
    ) -> Self {
        Self {
            descriptor,
            change_descriptor,
            network,
            local_chain: LocalChainChangeSet::default(),
            tx_graph: TxGraphChangeSet::default(),
            indexer: IndexerChangeSet::default(),
        }
    }

    /// Start a wallet `ChangeSet` from local chain changes.
    #[uniffi::constructor]
    pub fn from_local_chain_changes(local_chain_changes: LocalChainChangeSet) -> Self {
        let local_chain: bdk_wallet::chain::local_chain::ChangeSet = local_chain_changes.into();
        let changeset: bdk_wallet::ChangeSet = local_chain.into();
        changeset.into()
    }

    /// Start a wallet `ChangeSet` from indexer changes.
    #[uniffi::constructor]
    pub fn from_indexer_changeset(indexer_changes: IndexerChangeSet) -> Self {
        let indexer: bdk_wallet::chain::indexer::keychain_txout::ChangeSet = indexer_changes.into();
        let changeset: bdk_wallet::ChangeSet = indexer.into();
        changeset.into()
    }

    /// Start a wallet `ChangeSet` from transaction graph changes.
    #[uniffi::constructor]
    pub fn from_tx_graph_changeset(tx_graph_changeset: TxGraphChangeSet) -> Self {
        let tx_graph: bdk_wallet::chain::tx_graph::ChangeSet<BdkConfirmationBlockTime> =
            tx_graph_changeset.into();
        let changeset: bdk_wallet::ChangeSet = tx_graph.into();
        changeset.into()
    }

    /// Build a `ChangeSet` by merging together two `ChangeSet`.
    #[uniffi::constructor]
    pub fn from_merge(left: Arc<ChangeSet>, right: Arc<ChangeSet>) -> Self {
        let mut left: bdk_wallet::ChangeSet = left.as_ref().clone().into();
        let right: bdk_wallet::ChangeSet = right.as_ref().clone().into();
        left.merge(right);
        left.into()
    }

    /// Get the receiving `Descriptor`.
    pub fn descriptor(&self) -> Option<Arc<Descriptor>> {
        self.descriptor.clone()
    }

    /// Get the change `Descriptor`
    pub fn change_descriptor(&self) -> Option<Arc<Descriptor>> {
        self.change_descriptor.clone()
    }

    /// Get the `Network`
    pub fn network(&self) -> Option<bdk_wallet::bitcoin::Network> {
        self.network
    }

    /// Get the changes to the local chain.
    pub fn localchain_changeset(&self) -> LocalChainChangeSet {
        self.local_chain.clone()
    }

    /// Get the changes to the transaction graph.
    pub fn tx_graph_changeset(&self) -> TxGraphChangeSet {
        self.tx_graph.clone()
    }

    /// Get the changes to the indexer.
    pub fn indexer_changeset(&self) -> IndexerChangeSet {
        self.indexer.clone()
    }
}

impl From<ChangeSet> for bdk_wallet::ChangeSet {
    fn from(value: ChangeSet) -> Self {
        let descriptor = value.descriptor.map(|d| d.extended_descriptor.clone());
        let change_descriptor = value
            .change_descriptor
            .map(|d| d.extended_descriptor.clone());
        let network = value.network;
        let local_chain = value.local_chain.into();
        let tx_graph = value.tx_graph.into();
        let indexer = value.indexer.into();
        Self {
            descriptor,
            change_descriptor,
            network,
            local_chain,
            tx_graph,
            indexer,
        }
    }
}

impl From<bdk_wallet::ChangeSet> for ChangeSet {
    fn from(value: bdk_wallet::ChangeSet) -> Self {
        let descriptor = value.descriptor.map(|d| {
            Arc::new(Descriptor {
                extended_descriptor: d,
                key_map: BTreeMap::new(),
            })
        });
        let change_descriptor = value.change_descriptor.map(|d| {
            Arc::new(Descriptor {
                extended_descriptor: d,
                key_map: BTreeMap::new(),
            })
        });
        let network = value.network;
        let local_chain = value.local_chain.into();
        let tx_graph = value.tx_graph.into();
        let indexer = value.indexer.into();
        Self {
            descriptor,
            change_descriptor,
            network,
            local_chain,
            tx_graph,
            indexer,
        }
    }
}

#[derive(uniffi::Record, Debug, Clone)]
pub struct TxDetails {
    pub txid: Arc<Txid>,
    pub sent: Arc<Amount>,
    pub received: Arc<Amount>,
    pub fee: Option<Arc<Amount>>,
    pub fee_rate: Option<f32>,
    pub balance_delta: i64,
    pub chain_position: ChainPosition,
    pub tx: Arc<Transaction>,
}

impl From<bdk_wallet::TxDetails> for TxDetails {
    fn from(details: bdk_wallet::TxDetails) -> Self {
        TxDetails {
            txid: Arc::new(Txid(details.txid)),
            sent: Arc::new(details.sent.into()),
            received: Arc::new(details.received.into()),
            fee: details.fee.map(|f| Arc::new(f.into())),
            fee_rate: details.fee_rate.map(|fr| fr.to_sat_per_vb_ceil() as f32),
            balance_delta: details.balance_delta.to_sat(),
            chain_position: details.chain_position.into(),
            tx: Arc::new(Transaction::from(details.tx.as_ref().clone())),
        }
    }
}
