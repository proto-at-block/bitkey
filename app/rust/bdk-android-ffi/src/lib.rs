mod blockchain;
mod database;
mod descriptor;
mod keys;
mod psbt;
mod wallet;

use crate::blockchain::{
    Auth, Blockchain, BlockchainConfig, ElectrumConfig, EsploraConfig, RpcConfig, RpcSyncParams,
};
use crate::database::DatabaseConfig;
use crate::descriptor::Descriptor;
use crate::keys::DerivationPath;
use crate::keys::{DescriptorPublicKey, DescriptorSecretKey, Mnemonic};
use crate::psbt::PartiallySignedTransaction;
use crate::wallet::SignOptions;
use crate::wallet::{BumpFeeTxBuilder, TxBuilder, Wallet};
use bdk::bitcoin::address::{NetworkUnchecked, Payload as BdkPayload, WitnessVersion};
use bdk::bitcoin::blockdata::script::ScriptBuf as BdkScriptBuf;
use bdk::bitcoin::blockdata::transaction::TxIn as BdkTxIn;
use bdk::bitcoin::blockdata::transaction::TxOut as BdkTxOut;
use bdk::bitcoin::consensus::encode::serialize;
use bdk::bitcoin::consensus::Decodable;
use bdk::bitcoin::network::constants::Network as BdkNetwork;
use bdk::bitcoin::{
    Address as BdkAddress, OutPoint as BdkOutPoint, Transaction as BdkTransaction, Txid,
};
use bdk::blockchain::Progress as BdkProgress;
use bdk::database::any::SledDbConfiguration;
use bdk::database::any::SqliteDbConfiguration;
use bdk::keys::bip39::WordCount;
use bdk::wallet::AddressIndex as BdkAddressIndex;
use bdk::wallet::AddressInfo as BdkAddressInfo;
use bdk::LocalUtxo as BdkLocalUtxo;
use bdk::TransactionDetails as BdkTransactionDetails;
use bdk::{Balance as BdkBalance, BlockTime, Error as BdkError, FeeRate, KeychainKind};
use std::convert::From;
use std::fmt;
use std::fmt::Debug;
use std::io::Cursor;
use std::str::FromStr;
use std::sync::Arc;

uniffi::include_scaffolding!("bdk");

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Script(pub(crate) BdkScriptBuf);

impl Script {
    pub fn new(raw_output_script: Vec<u8>) -> Self {
        let script: BdkScriptBuf = raw_output_script.into();
        Script(script)
    }

    pub fn to_bytes(&self) -> Vec<u8> {
        self.0.to_bytes()
    }
}

impl From<BdkScriptBuf> for Script {
    fn from(script: BdkScriptBuf) -> Self {
        Script(script)
    }
}

/// A output script and an amount of satoshis.
pub struct ScriptAmount {
    pub script: Arc<Script>,
    pub amount: u64,
}

/// A derived address and the index it was found at.
pub struct AddressInfo {
    /// Child index of this address.
    pub index: u32,
    /// Address.
    pub address: Arc<Address>,
    /// Type of keychain.
    pub keychain: KeychainKind,
}

impl From<BdkAddressInfo> for AddressInfo {
    fn from(address_info: BdkAddressInfo) -> Self {
        AddressInfo {
            index: address_info.index,
            address: Arc::new(Address::from(address_info.address)),
            keychain: address_info.keychain,
        }
    }
}

/// The address index selection strategy to use to derived an address from the wallet's external
/// descriptor.
pub enum AddressIndex {
    /// Return a new address after incrementing the current descriptor index.
    New,
    /// Return the address for the current descriptor index if it has not been used in a received
    /// transaction. Otherwise return a new address as with AddressIndex::New.
    /// Use with caution, if the wallet has not yet detected an address has been used it could
    /// return an already used address. This function is primarily meant for situations where the
    /// caller is untrusted; for example when deriving donation addresses on-demand for a public
    /// web page.
    LastUnused,
    /// Return the address for a specific descriptor index. Does not change the current descriptor
    /// index used by `AddressIndex::New` and `AddressIndex::LastUsed`.
    /// Use with caution, if an index is given that is less than the current descriptor index
    /// then the returned address may have already been used.
    Peek { index: u32 },
    /// Return the address for a specific descriptor index and reset the current descriptor index
    /// used by `AddressIndex::New` and `AddressIndex::LastUsed` to this value.
    /// Use with caution, if an index is given that is less than the current descriptor index
    /// then the returned address and subsequent addresses returned by calls to `AddressIndex::New`
    /// and `AddressIndex::LastUsed` may have already been used. Also if the index is reset to a
    /// value earlier than the [`Blockchain`] stop_gap (default is 20) then a
    /// larger stop_gap should be used to monitor for all possibly used addresses.
    Reset { index: u32 },
}

impl From<AddressIndex> for BdkAddressIndex {
    fn from(address_index: AddressIndex) -> Self {
        match address_index {
            AddressIndex::New => BdkAddressIndex::New,
            AddressIndex::LastUnused => BdkAddressIndex::LastUnused,
            AddressIndex::Peek { index } => BdkAddressIndex::Peek(index),
            AddressIndex::Reset { index } => BdkAddressIndex::Reset(index),
        }
    }
}

/// A wallet transaction
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct TransactionDetails {
    pub transaction: Option<Arc<Transaction>>,
    /// Transaction id.
    pub txid: String,
    /// Received value (sats)
    /// Sum of owned outputs of this transaction.
    pub received: u64,
    /// Sent value (sats)
    /// Sum of owned inputs of this transaction.
    pub sent: u64,
    /// Fee value (sats) if confirmed.
    /// The availability of the fee depends on the backend. It's never None with an Electrum
    /// Server backend, but it could be None with a Bitcoin RPC node without txindex that receive
    /// funds while offline.
    pub fee: Option<u64>,
    /// If the transaction is confirmed, contains height and timestamp of the block containing the
    /// transaction, unconfirmed transaction contains `None`.
    pub confirmation_time: Option<BlockTime>,
}

impl From<BdkTransactionDetails> for TransactionDetails {
    fn from(tx_details: BdkTransactionDetails) -> Self {
        let optional_tx: Option<Arc<Transaction>> =
            tx_details.transaction.map(|tx| Arc::new(tx.into()));

        TransactionDetails {
            transaction: optional_tx,
            fee: tx_details.fee,
            txid: tx_details.txid.to_string(),
            received: tx_details.received,
            sent: tx_details.sent,
            confirmation_time: tx_details.confirmation_time,
        }
    }
}

/// A reference to a transaction output.
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct OutPoint {
    /// The referenced transaction's txid.
    txid: String,
    /// The index of the referenced output in its transaction's vout.
    vout: u32,
}

impl From<&OutPoint> for BdkOutPoint {
    fn from(outpoint: &OutPoint) -> Self {
        BdkOutPoint {
            txid: Txid::from_str(&outpoint.txid).unwrap(),
            vout: outpoint.vout,
        }
    }
}

pub struct Balance {
    // All coinbase outputs not yet matured
    pub immature: u64,
    /// Unconfirmed UTXOs generated by a wallet tx
    pub trusted_pending: u64,
    /// Unconfirmed UTXOs received from an external wallet
    pub untrusted_pending: u64,
    /// Confirmed and immediately spendable balance
    pub confirmed: u64,
    /// Get sum of trusted_pending and confirmed coins
    pub spendable: u64,
    /// Get the whole balance visible to the wallet
    pub total: u64,
}

impl From<BdkBalance> for Balance {
    fn from(bdk_balance: BdkBalance) -> Self {
        Balance {
            immature: bdk_balance.immature,
            trusted_pending: bdk_balance.trusted_pending,
            untrusted_pending: bdk_balance.untrusted_pending,
            confirmed: bdk_balance.confirmed,
            spendable: bdk_balance.get_spendable(),
            total: bdk_balance.get_total(),
        }
    }
}

/// A transaction output, which defines new coins to be created from old ones.
#[derive(Debug, Clone)]
pub struct TxOut {
    /// The value of the output, in satoshis.
    value: u64,
    /// The address of the output.
    script_pubkey: Arc<Script>,
}

impl From<&BdkTxOut> for TxOut {
    fn from(tx_out: &BdkTxOut) -> Self {
        TxOut {
            value: tx_out.value,
            script_pubkey: Arc::new(Script(tx_out.script_pubkey.clone())),
        }
    }
}

pub struct LocalUtxo {
    outpoint: OutPoint,
    txout: TxOut,
    keychain: KeychainKind,
    is_spent: bool,
}

impl From<BdkLocalUtxo> for LocalUtxo {
    fn from(local_utxo: BdkLocalUtxo) -> Self {
        LocalUtxo {
            outpoint: OutPoint {
                txid: local_utxo.outpoint.txid.to_string(),
                vout: local_utxo.outpoint.vout,
            },
            txout: TxOut {
                value: local_utxo.txout.value,
                script_pubkey: Arc::new(Script(local_utxo.txout.script_pubkey)),
            },
            keychain: local_utxo.keychain,
            is_spent: local_utxo.is_spent,
        }
    }
}

/// Trait that logs at level INFO every update received (if any).
pub trait Progress: Send + Sync + 'static {
    /// Send a new progress update. The progress value should be in the range 0.0 - 100.0, and the message value is an
    /// optional text message that can be displayed to the user.
    fn update(&self, progress: f32, message: Option<String>);
}

struct ProgressHolder {
    progress: Box<dyn Progress>,
}

impl BdkProgress for ProgressHolder {
    fn update(&self, progress: f32, message: Option<String>) -> Result<(), BdkError> {
        self.progress.update(progress, message);
        Ok(())
    }
}

impl Debug for ProgressHolder {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("ProgressHolder").finish_non_exhaustive()
    }
}

#[derive(Debug, Clone)]
pub struct TxIn {
    pub previous_output: OutPoint,
    pub script_sig: Arc<Script>,
    pub sequence: u32,
    pub witness: Vec<Vec<u8>>,
}

impl From<&BdkTxIn> for TxIn {
    fn from(tx_in: &BdkTxIn) -> Self {
        TxIn {
            previous_output: OutPoint {
                txid: tx_in.previous_output.txid.to_string(),
                vout: tx_in.previous_output.vout,
            },
            script_sig: Arc::new(Script(tx_in.script_sig.clone())),
            sequence: tx_in.sequence.0,
            witness: tx_in.witness.to_vec(),
        }
    }
}

/// A Bitcoin transaction.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Transaction {
    inner: BdkTransaction,
}

impl Transaction {
    fn new(transaction_bytes: Vec<u8>) -> Result<Self, BdkError> {
        let mut decoder = Cursor::new(transaction_bytes);
        let tx: BdkTransaction = BdkTransaction::consensus_decode(&mut decoder)?;
        Ok(Transaction { inner: tx })
    }

    fn txid(&self) -> String {
        self.inner.txid().to_string()
    }

    fn weight(&self) -> u64 {
        self.inner.weight().to_wu()
    }

    fn size(&self) -> u64 {
        self.inner.size() as u64
    }

    fn vsize(&self) -> u64 {
        self.inner.vsize() as u64
    }

    fn serialize(&self) -> Vec<u8> {
        serialize(&self.inner)
    }

    fn is_coin_base(&self) -> bool {
        self.inner.is_coin_base()
    }

    fn is_explicitly_rbf(&self) -> bool {
        self.inner.is_explicitly_rbf()
    }

    fn is_lock_time_enabled(&self) -> bool {
        self.inner.is_lock_time_enabled()
    }

    fn version(&self) -> i32 {
        self.inner.version
    }

    fn lock_time(&self) -> u32 {
        self.inner.lock_time.to_consensus_u32()
    }

    fn input(&self) -> Vec<TxIn> {
        self.inner.input.iter().map(|x| x.into()).collect()
    }

    fn output(&self) -> Vec<TxOut> {
        self.inner.output.iter().map(|x| x.into()).collect()
    }
}

impl From<BdkTransaction> for Transaction {
    fn from(tx: BdkTransaction) -> Self {
        Transaction { inner: tx }
    }
}

/// A Bitcoin address.
#[derive(Debug, PartialEq, Eq)]
pub struct Address {
    inner: BdkAddress,
}

impl Address {
    pub fn new(address: String, network: Network) -> Result<Self, BdkError> {
        let parsed_address = address
            .parse::<bdk::bitcoin::Address<NetworkUnchecked>>()
            .map_err(|e| BdkError::Generic(e.to_string()))?;

        let network_checked_address = parsed_address
            .require_network(network.into())
            .map_err(|e| BdkError::Generic(e.to_string()))?;

        Ok(Address {
            inner: network_checked_address,
        })
    }

    /// alternative constructor
    fn from_script(script: Arc<Script>, network: Network) -> Result<Self, BdkError> {
        BdkAddress::from_script(&script.0, network.into())
            .map(|a| Address { inner: a })
            .map_err(|e| BdkError::Generic(e.to_string()))
    }

    fn payload(&self) -> Payload {
        match &self.inner.payload.clone() {
            BdkPayload::PubkeyHash(pubkey_hash) => Payload::PubkeyHash {
                pubkey_hash: pubkey_hash.to_string(),
            },
            BdkPayload::ScriptHash(script_hash) => Payload::ScriptHash {
                script_hash: script_hash.to_string(),
            },
            BdkPayload::WitnessProgram(witness_program) => Payload::WitnessProgram {
                version: witness_program.version(),
                program: Vec::from(witness_program.program().as_bytes()),
            },
            _ => panic!("Unsupported address payload type"),
        }
    }

    fn network(&self) -> Network {
        self.inner.network.into()
    }

    fn script_pubkey(&self) -> Arc<Script> {
        Arc::new(Script(self.inner.script_pubkey()))
    }

    fn to_qr_uri(&self) -> String {
        self.inner.to_qr_uri()
    }

    pub fn is_valid_for_network(&self, network: Network) -> bool {
        let address_str = self.inner.to_string();
        if let Ok(unchecked_address) = address_str.parse::<BdkAddress<NetworkUnchecked>>() {
            unchecked_address.is_valid_for_network(network.into())
        } else {
            false
        }
    }

    fn as_string(&self) -> String {
        self.inner.to_string()
    }
}

impl From<BdkAddress> for Address {
    fn from(address: BdkAddress) -> Self {
        Address { inner: address }
    }
}

/// The method used to produce an address.
#[derive(Debug)]
pub enum Payload {
    /// P2PKH address.
    PubkeyHash { pubkey_hash: String },
    /// P2SH address.
    ScriptHash { script_hash: String },
    /// Segwit address.
    WitnessProgram {
        /// The witness program version.
        version: WitnessVersion,
        /// The witness program.
        program: Vec<u8>,
    },
}

#[derive(Clone, Debug)]
enum RbfValue {
    Default,
    Value(u32),
}

/// The result after calling the TxBuilder finish() function. Contains unsigned PSBT and
/// transaction details.
pub struct TxBuilderResult {
    pub(crate) psbt: Arc<PartiallySignedTransaction>,
    pub transaction_details: TransactionDetails,
}

#[derive(PartialEq, Debug)]
pub enum Network {
    Bitcoin,
    Testnet,
    Signet,
    Regtest,
}

impl From<Network> for BdkNetwork {
    fn from(network: Network) -> Self {
        match network {
            Network::Bitcoin => BdkNetwork::Bitcoin,
            Network::Testnet => BdkNetwork::Testnet,
            Network::Signet => BdkNetwork::Signet,
            Network::Regtest => BdkNetwork::Regtest,
        }
    }
}

impl From<BdkNetwork> for Network {
    fn from(network: BdkNetwork) -> Self {
        match network {
            BdkNetwork::Bitcoin => Network::Bitcoin,
            BdkNetwork::Testnet => Network::Testnet,
            BdkNetwork::Signet => Network::Signet,
            BdkNetwork::Regtest => Network::Regtest,
            _ => panic!("Network {} not supported", network),
        }
    }
}

uniffi::deps::static_assertions::assert_impl_all!(Wallet: Sync, Send);

// The goal of these tests to to ensure `bdk-ffi` intermediate code correctly calls `bdk` APIs.
// These tests should not be used to verify `bdk` behavior that is already tested in the `bdk`
// crate.
#[cfg(test)]
mod test {
    use crate::Network::Regtest;
    use crate::{Address, Payload};
    use assert_matches::assert_matches;
    use bdk::bitcoin::address::WitnessVersion;
    use bdk::bitcoin::hashes::hex::FromHex;
    use bdk::bitcoin::Network;

    // Verify that bdk-ffi Transaction can be created from valid bytes and serialized back into the same bytes.
    // #[test]
    // fn test_transaction_serde() {
    //     let test_tx_bytes = Vec::from_hex("020000000001031cfbc8f54fbfa4a33a30068841371f80dbfe166211242213188428f437445c91000000006a47304402206fbcec8d2d2e740d824d3d36cc345b37d9f65d665a99f5bd5c9e8d42270a03a8022013959632492332200c2908459547bf8dbf97c65ab1a28dec377d6f1d41d3d63e012103d7279dfb90ce17fe139ba60a7c41ddf605b25e1c07a4ddcb9dfef4e7d6710f48feffffff476222484f5e35b3f0e43f65fc76e21d8be7818dd6a989c160b1e5039b7835fc00000000171600140914414d3c94af70ac7e25407b0689e0baa10c77feffffffa83d954a62568bbc99cc644c62eb7383d7c2a2563041a0aeb891a6a4055895570000000017160014795d04cc2d4f31480d9a3710993fbd80d04301dffeffffff06fef72f000000000017a91476fd7035cd26f1a32a5ab979e056713aac25796887a5000f00000000001976a914b8332d502a529571c6af4be66399cd33379071c588ac3fda0500000000001976a914fc1d692f8de10ae33295f090bea5fe49527d975c88ac522e1b00000000001976a914808406b54d1044c429ac54c0e189b0d8061667e088ac6eb68501000000001976a914dfab6085f3a8fb3e6710206a5a959313c5618f4d88acbba20000000000001976a914eb3026552d7e3f3073457d0bee5d4757de48160d88ac0002483045022100bee24b63212939d33d513e767bc79300051f7a0d433c3fcf1e0e3bf03b9eb1d70220588dc45a9ce3a939103b4459ce47500b64e23ab118dfc03c9caa7d6bfc32b9c601210354fd80328da0f9ae6eef2b3a81f74f9a6f66761fadf96f1d1d22b1fd6845876402483045022100e29c7e3a5efc10da6269e5fc20b6a1cb8beb92130cc52c67e46ef40aaa5cac5f0220644dd1b049727d991aece98a105563416e10a5ac4221abac7d16931842d5c322012103960b87412d6e169f30e12106bdf70122aabb9eb61f455518322a18b920a4dfa887d30700").unwrap();
    //     let new_tx_from_bytes = Transaction::new(test_tx_bytes.clone()).unwrap();
    // let serialized_tx_to_bytes = new_tx_from_bytes.serialize();
    // assert_eq!(test_tx_bytes, serialized_tx_to_bytes);
    // }

    // Verify that bdk-ffi Address.payload includes expected WitnessProgram variant, version and program bytes.
    #[test]
    fn test_address_witness_program() {
        let address = Address::new(
            "bcrt1qqjn9gky9mkrm3c28e5e87t5akd3twg6xezp0tv".to_string(),
            Network::Regtest.into(),
        )
        .unwrap();
        let payload = address.payload();
        assert_matches!(payload, Payload::WitnessProgram { version, program } => {
            assert_eq!(version, WitnessVersion::V0);
            assert_eq!(program, Vec::from_hex("04a6545885dd87b8e147cd327f2e9db362b72346").unwrap());
        });
        assert_eq!(address.network(), Regtest);
    }
}
