use std::collections::{BTreeMap, HashSet};
use std::str::FromStr;
use std::{env, fmt};

pub use bdk_electrum as electrum;
pub use bdk_wallet as bdk;
use bdk_wallet::bitcoin::psbt::raw::ProprietaryKey;
use bdk_wallet::chain::keychain_txout::DEFAULT_LOOKAHEAD;

use bdk_wallet::bitcoin::bip32::{ChildNumber, KeySource};
use bdk_wallet::bitcoin::psbt::Psbt;
use bdk_wallet::bitcoin::secp256k1::{PublicKey as SecpPublicKey, Scalar, Secp256k1};

use bdk_bitcoind_rpc::bitcoincore_rpc::{Auth, Client, RpcApi};
use bdk_electrum::electrum_client::Client as ElectrumClient;
use bdk_electrum::electrum_client::Config as ElectrumConfig;
use bdk_electrum::electrum_client::Error as ElectrumClientError;
use bdk_electrum::{electrum_client, BdkElectrumClient};
use bdk_wallet::bitcoin::{Address, Amount, BlockHash, ScriptBuf, TxOut};
use bdk_wallet::descriptor::ExtendedDescriptor;
use bdk_wallet::miniscript::descriptor::DescriptorXKey;
use bdk_wallet::miniscript::{Descriptor, DescriptorPublicKey};
use bdk_wallet::KeychainKind;
use bdk_wallet::{bitcoin::Network, Wallet};
use crypto::chaincode_delegation::common::{PROPRIETARY_KEY_PREFIX, PROPRIETARY_KEY_SUBTYPE};
use feature_flags::flag::{evaluate_flag_value, ContextKey};
use tracing::{event, instrument, Level};
use url::Url;

use error::BdkUtilError;
use errors::ApiError;
use feature_flags::service::Service as FeatureFlagsService;
use flags::{
    FLAG_MAINNET_ELECTRUM_RPC_URI, FLAG_SIGNET_ELECTRUM_RPC_URI, FLAG_TESTNET_ELECTRUM_RPC_URI,
};

pub mod constants;
pub mod error;
pub mod flags;
pub mod metrics;
pub mod serde;
pub mod signature;

pub trait TransactionBroadcasterTrait: Send + Sync {
    fn broadcast(
        &self,
        network: Network,
        transaction: &mut Psbt,
        rpc_uris: &ElectrumRpcUris,
    ) -> Result<(), BdkUtilError>;
}

impl fmt::Debug for dyn TransactionBroadcasterTrait {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Trait object of TransactionBroadcasterTrait")
    }
}

const BAD_TXNS_MISSING_OR_SPENT_MESSAGE: &str = "bad-txns-inputs-missingorspent";
const MIN_RELAY_FEE_NOT_MET_MESSAGE: &str = "min relay fee not met";

pub struct TransactionBroadcaster;

impl TransactionBroadcasterTrait for TransactionBroadcaster {
    #[instrument(skip(self, transaction))]
    fn broadcast(
        &self,
        network: Network,
        transaction: &mut Psbt,
        rpc_uris: &ElectrumRpcUris,
    ) -> Result<(), BdkUtilError> {
        let client = get_bdk_electrum_client(network, rpc_uris)?;
        broadcast_with(transaction, |tx| client.transaction_broadcast(tx))?;

        Ok(())
    }
}

fn broadcast_with(
    transaction: &mut Psbt,
    broadcast: impl FnOnce(
        &bdk_wallet::bitcoin::Transaction,
    ) -> Result<bdk_wallet::bitcoin::Txid, electrum_client::Error>,
) -> Result<(), BdkUtilError> {
    broadcast(&transaction.to_owned().extract_tx()?).map_err(as_bdk_util_err)?;
    Ok(())
}

fn as_bdk_util_err(value: electrum_client::Error) -> BdkUtilError {
    if let ElectrumClientError::Protocol(ref json_value) = value {
        let json_str = json_value.to_string();

        // We broadcast on both App and Server, so we could get this error when the
        // App "wins" the race. Under those circumstances, we just return a 409 and
        // not consider it an error.
        if json_str.contains(BAD_TXNS_MISSING_OR_SPENT_MESSAGE) {
            event!(
                Level::WARN,
                "Failed to broadcast PSBT with message: {}",
                BAD_TXNS_MISSING_OR_SPENT_MESSAGE
            );
            return BdkUtilError::TransactionAlreadyInMempoolError;
        }

        if json_str.contains(MIN_RELAY_FEE_NOT_MET_MESSAGE) {
            event!(
                Level::ERROR,
                "Failed to broadcast PSBT with message: {}",
                MIN_RELAY_FEE_NOT_MET_MESSAGE
            );
            return BdkUtilError::MinRelayFeeNotMetError;
        }
    }

    event!(
        Level::ERROR,
        "Failed to broadcast PSBT: {}",
        value.to_string()
    );

    BdkUtilError::TransactionBroadcastError(value)
}

pub fn get_electrum_client(
    network: Network,
    rpc_uris: &ElectrumRpcUris,
) -> Result<ElectrumClient, BdkUtilError> {
    let electrum_config = get_electrum_server(network, rpc_uris)?;
    let config = ElectrumConfig::builder().timeout(Some(5)).build();
    Ok(ElectrumClient::from_config(
        &electrum_config.to_url(),
        config,
    )?)
}

pub fn get_bdk_electrum_client(
    network: Network,
    rpc_uris: &ElectrumRpcUris,
) -> Result<BdkElectrumClient<ElectrumClient>, BdkUtilError> {
    Ok(BdkElectrumClient::new(get_electrum_client(
        network, rpc_uris,
    )?))
}

#[derive(Clone)]
pub struct ElectrumServerConfig {
    pub scheme: String,
    pub host: String,
    pub port: u16,
}

impl ElectrumServerConfig {
    fn to_url(&self) -> String {
        format!("{}://{}:{}", self.scheme, self.host, self.port)
    }
}

#[derive(Debug, Clone)]
pub struct ElectrumRpcUris {
    pub mainnet: String,
    pub testnet: String,
    pub signet: String,
}

pub fn generate_electrum_rpc_uris(
    service: &FeatureFlagsService,
    context_key: Option<ContextKey>,
) -> ElectrumRpcUris {
    let (mainnet, testnet, signet) = context_key
        .as_ref()
        .map(|context_key| {
            // The following `evaluate_flag_value` calls should not fail, but just to be safe, we fall
            // back to the default flag value.
            macro_rules! evaluate_flag_with_fallback {
                ($flag: expr, $network: expr) => {
                    evaluate_flag_value(service, $flag.key, context_key).unwrap_or_else(|e| {
                        event!(
                            Level::WARN,
                            "Failed to resolve {} Electrum RPC URI using app installation ID: {e}",
                            $network,
                        );
                        $flag.resolver(service).resolve()
                    })
                };
            }
            let mainnet = evaluate_flag_with_fallback!(FLAG_MAINNET_ELECTRUM_RPC_URI, "mainnet");
            let testnet = evaluate_flag_with_fallback!(FLAG_TESTNET_ELECTRUM_RPC_URI, "testnet");
            let signet = evaluate_flag_with_fallback!(FLAG_SIGNET_ELECTRUM_RPC_URI, "signet");
            (mainnet, testnet, signet)
        })
        .unwrap_or_else(|| {
            let mainnet = FLAG_MAINNET_ELECTRUM_RPC_URI.resolver(service).resolve();
            let testnet = FLAG_TESTNET_ELECTRUM_RPC_URI.resolver(service).resolve();
            let signet = FLAG_SIGNET_ELECTRUM_RPC_URI.resolver(service).resolve();
            (mainnet, testnet, signet)
        });

    ElectrumRpcUris {
        mainnet,
        testnet,
        signet,
    }
}

pub fn get_electrum_server(
    network: Network,
    rpc_uris: &ElectrumRpcUris,
) -> Result<ElectrumServerConfig, BdkUtilError> {
    match network {
        Network::Bitcoin => parse_electrum_server(&rpc_uris.mainnet),
        Network::Testnet => parse_electrum_server(&rpc_uris.testnet),
        Network::Signet => parse_electrum_server(&rpc_uris.signet),
        Network::Regtest => {
            let server = env::var("REGTEST_ELECTRUM_SERVER_URI")
                .map_err(|_| BdkUtilError::UnsupportedBitcoinNetwork(network.to_string()))?;
            parse_electrum_server(&server)
        }
        _ => Err(BdkUtilError::UnsupportedBitcoinNetwork(network.to_string())),
    }
}

pub fn parse_electrum_server(server: &str) -> Result<ElectrumServerConfig, BdkUtilError> {
    let url = Url::parse(server).map_err(|_| BdkUtilError::MalformedURI)?;
    let host = url.host().ok_or(BdkUtilError::MalformedURI)?.to_string();
    let port = url.port().ok_or(BdkUtilError::MalformedURI)?;

    Ok(ElectrumServerConfig {
        scheme: url.scheme().to_string(),
        host,
        port,
    })
}

pub const FULL_SCAN_STOP_GAP_AND_BATCH_SIZE: (usize, usize) = (20, 200);

#[instrument(name = "sync_wallet", fields(network), skip(wallet))]
pub fn sync_wallet(wallet: &mut Wallet, rpc_uris: &ElectrumRpcUris) -> Result<(), BdkUtilError> {
    let (stop_gap, batch_size) = FULL_SCAN_STOP_GAP_AND_BATCH_SIZE;

    let network = wallet.network();
    let client = get_bdk_electrum_client(network, rpc_uris)?;
    let request = wallet.start_full_scan();

    let update = client.full_scan(request, stop_gap, batch_size, true)?;

    wallet.apply_update(update)?;
    Ok(())
}

pub fn validate_xpubs(xpubs: &[String]) -> Result<(), ApiError> {
    if xpubs
        .iter()
        .map(|xpub_str| DescriptorPublicKey::from_str(xpub_str))
        .all(|r| r.is_ok())
    {
        Ok(())
    } else {
        Err(ApiError::GenericBadRequest(
            "Invalid input: One or both of the XPubs is invalid".to_string(),
        ))
    }
}

const RECEIVING_PATH: [ChildNumber; 1] = [ChildNumber::Normal { index: 0 }];
const CHANGE_PATH: [ChildNumber; 1] = [ChildNumber::Normal { index: 1 }];

#[derive(Clone, Debug)]
pub struct DescriptorKeyset {
    network: Network,
    app: DescriptorPublicKey,
    hw: DescriptorPublicKey,
    server: DescriptorPublicKey,
}

impl DescriptorKeyset {
    pub fn new(
        network: Network,
        app: DescriptorPublicKey,
        hw: DescriptorPublicKey,
        server: DescriptorPublicKey,
    ) -> Self {
        Self {
            network,
            app,
            hw,
            server,
        }
    }

    pub fn receiving(&self) -> DescriptorKeyset {
        self.derive(&RECEIVING_PATH)
    }
    pub fn change(&self) -> DescriptorKeyset {
        self.derive(&CHANGE_PATH)
    }

    fn derive(&self, path: &[ChildNumber]) -> DescriptorKeyset {
        DescriptorKeyset {
            network: self.network,
            app: extend_descriptor_public_key(&self.app, path),
            hw: extend_descriptor_public_key(&self.hw, path),
            server: extend_descriptor_public_key(&self.server, path),
        }
    }

    pub fn into_multisig_descriptor(self) -> Result<ExtendedDescriptor, BdkUtilError> {
        Descriptor::<DescriptorPublicKey>::new_wsh_sortedmulti(
            2,
            vec![self.app, self.hw, self.server],
        )
        .map_err(BdkUtilError::GenerateDescriptorForDescriptorKeyset)
    }

    pub fn generate_wallet(
        &self,
        sync: bool,
        rpc_uris: &ElectrumRpcUris,
    ) -> Result<Wallet, BdkUtilError> {
        self.generate_wallet_with_lookahead(sync, rpc_uris, None)
    }

    pub fn generate_wallet_with_lookahead(
        &self,
        sync: bool,
        rpc_uris: &ElectrumRpcUris,
        lookahead: Option<u32>,
    ) -> Result<Wallet, BdkUtilError> {
        let lookahead = lookahead.unwrap_or(DEFAULT_LOOKAHEAD);

        let mut wallet = Wallet::create(
            self.receiving().into_multisig_descriptor()?,
            self.change().into_multisig_descriptor()?,
        )
        .network(self.network)
        .lookahead(lookahead)
        .create_wallet_no_persist()?;

        if sync {
            sync_wallet(&mut wallet, rpc_uris)?;
        }

        Ok(wallet)
    }
}

pub const CHECK_SCRIPT_NUM_CACHED_ADDRESSES: u32 = 1000;

pub fn is_addressed_to_wallet(
    synced_wallet: &Wallet,
    script: &ScriptBuf,
) -> Result<bool, BdkUtilError> {
    Ok(script_belongs_to_wallet(synced_wallet, script))
}

const CHECK_PSBT_NUM_CACHED_ADDRESSES: u32 = 100;

pub fn is_psbt_addressed_to_wallet(
    synced_wallet: &Wallet,
    psbt: &Psbt,
) -> Result<bool, BdkUtilError> {
    let cached_script_pubkeys = build_cached_script_pubkeys(synced_wallet);
    Ok(psbt
        .unsigned_tx
        .output
        .iter()
        .all(|tx_out| cached_script_pubkeys.contains(&tx_out.script_pubkey)))
}

fn build_cached_script_pubkeys(synced_wallet: &Wallet) -> HashSet<ScriptBuf> {
    let mut cached_script_pubkeys =
        HashSet::with_capacity((CHECK_SCRIPT_NUM_CACHED_ADDRESSES * 2) as usize);

    for keychain in [KeychainKind::External, KeychainKind::Internal] {
        for index in 0..CHECK_SCRIPT_NUM_CACHED_ADDRESSES {
            cached_script_pubkeys.insert(
                synced_wallet
                    .peek_address(keychain, index)
                    .address
                    .script_pubkey(),
            );
        }
    }

    cached_script_pubkeys
}

fn script_belongs_to_wallet(synced_wallet: &Wallet, script: &ScriptBuf) -> bool {
    for keychain in [KeychainKind::External, KeychainKind::Internal] {
        for index in 0..CHECK_SCRIPT_NUM_CACHED_ADDRESSES {
            if &synced_wallet
                .peek_address(keychain, index)
                .address
                .script_pubkey()
                == script
            {
                return true;
            }
        }
    }

    false
}

pub fn is_psbt_addressed_to_attributable_wallet(
    wallet: &dyn AttributableWallet,
    psbt: &Psbt,
) -> Result<bool, BdkUtilError> {
    let Some(outputs) = psbt.get_all_outputs_as_spk_and_derivation() else {
        return Ok(false);
    };
    if outputs.is_empty() {
        return Ok(false);
    }
    for spk in outputs {
        if !wallet.is_my_psbt_address(&spk)? {
            return Ok(false);
        }
    }
    Ok(true)
}

fn get_unowned_output_iter<'a>(
    wallet: &'a dyn AttributableWallet,
    psbt: &'a Psbt,
) -> impl Iterator<Item = (usize, &'a bdk::bitcoin::TxOut)> + 'a {
    psbt.unsigned_tx
        .output
        .iter()
        .enumerate()
        .filter(|(idx, _output)| {
            // we want to filter OUT addresses that are ours
            // get_output_spk_and_derivation should be none, and then even if its some, is_my_psbt_address should be false
            // so construct the case where it would be our output and negate it.
            !psbt
                .get_output_spk_and_derivation(*idx)
                .is_some_and(|spk| wallet.is_my_psbt_address(&spk).is_ok_and(|x| x))
        })
}

pub fn get_total_outflow_for_psbt(wallet: &dyn AttributableWallet, psbt: &Psbt) -> u64 {
    get_unowned_output_iter(wallet, psbt)
        .map(|(_idx, output)| output.value.to_sat())
        .sum()
}

pub fn get_outflow_addresses_for_psbt(
    wallet: &dyn AttributableWallet,
    psbt: &Psbt,
    network: Network,
) -> Result<Vec<String>, BdkUtilError> {
    let destination_addresses = get_unowned_output_iter(wallet, psbt)
        .map(|(_, output)| {
            Address::from_script(&output.script_pubkey, network)
                .map(|address| address.to_string())
                .map_err(|_| BdkUtilError::InvalidOutputAddressInPsbt)
        })
        .collect::<Result<Vec<String>, BdkUtilError>>()?;
    Ok(destination_addresses)
}

fn extend_descriptor_public_key(
    origin: &DescriptorPublicKey,
    path: &[ChildNumber],
) -> DescriptorPublicKey {
    match origin {
        DescriptorPublicKey::XPub(xpub) => DescriptorPublicKey::XPub(DescriptorXKey {
            derivation_path: xpub.derivation_path.extend(path),
            origin: xpub.origin.clone(),
            ..*xpub
        }),
        // TODO [W-5549] Don't panic when extending invalid DescriptorPublicKey variant
        _ => unreachable!("This branch should never be reached"),
    }
}

pub trait AttributableWallet {
    fn is_addressed_to_self(&self, psbt: &Psbt) -> Result<bool, BdkUtilError>;
    fn all_inputs_are_from_self(&self, psbt: &Psbt) -> Result<bool, BdkUtilError>;
    fn is_my_psbt_address(&self, spk: &SpkWithDerivationPaths) -> Result<bool, BdkUtilError>;
}

impl AttributableWallet for Wallet {
    /// Checks if all outputs in the PSBT are addressed to the wallet
    /// Depends on the derivation path being present in the PSBT
    /// Derivation path is present in the PSBT for outputs that belong to the originating descriptor
    fn is_addressed_to_self(&self, psbt: &Psbt) -> Result<bool, BdkUtilError> {
        if psbt.outputs.is_empty() {
            return Ok(false);
        }
        for output_spk in psbt
            .get_all_outputs_as_spk_and_derivation()
            .ok_or(BdkUtilError::MalformedDerivationPath)?
        {
            if !self.is_my_psbt_address(&output_spk)? {
                return Ok(false);
            }
        }
        Ok(true)
    }

    fn all_inputs_are_from_self(&self, psbt: &Psbt) -> Result<bool, BdkUtilError> {
        for input_spk in psbt
            .get_all_inputs_as_spk_and_derivation()
            .ok_or(BdkUtilError::MissingWitnessUtxo)?
        {
            if !self.is_my_psbt_address(&input_spk)? {
                return Ok(false);
            }
        }
        Ok(true)
    }

    fn is_my_psbt_address(&self, spk: &SpkWithDerivationPaths) -> Result<bool, BdkUtilError> {
        if let Some(first_entry) = spk.derivation_paths.first_key_value() {
            let first_derivation_path = first_entry.1.clone().1;
            let path_components: Vec<ChildNumber> = first_derivation_path.into();
            // all of our derivation paths have two unhardened components at the end ../[change]/index
            let index = match path_components
                .iter()
                .nth_back(0)
                .ok_or(BdkUtilError::MalformedDerivationPath)?
            {
                ChildNumber::Normal { index } => index,
                ChildNumber::Hardened { .. } => return Err(BdkUtilError::MalformedDerivationPath),
            };
            let is_change = match path_components
                .iter()
                .nth_back(1)
                .ok_or(BdkUtilError::MalformedDerivationPath)?
            {
                ChildNumber::Normal { index } => *index == 1,
                ChildNumber::Hardened { .. } => return Err(BdkUtilError::MalformedDerivationPath),
            };

            let keychain = if is_change {
                KeychainKind::Internal
            } else {
                KeychainKind::External
            };
            let derived_address = self.peek_address(keychain, *index);

            Ok(derived_address.address.script_pubkey() == spk.script_pubkey.clone())
        } else {
            Ok(false)
        }
    }
}

/// Helper type that contains a scriptpubkey (address) along with any derivation paths associated with it
/// It is intended to be used when pulling inputs or outputs from a PSBT and checking if they are owned
/// by a particular wallet.
///
/// Additionally, it contains a witness_script and proprietary map. These are used to verify ownership
/// of the output when using Chaincode Delegation.
#[derive(Debug)]
pub struct SpkWithDerivationPaths {
    pub script_pubkey: ScriptBuf,
    pub derivation_paths: BTreeMap<SecpPublicKey, KeySource>,
    pub witness_script: Option<ScriptBuf>,
    pub proprietary: BTreeMap<ProprietaryKey, Vec<u8>>,
}

/// PSBTs contain origin information and scriptpubkeys for inputs and outputs that are owned by our wallet.
/// PsbtWithDerivation is a helper trait that makes it easier to get all that information for checking ownership.
/// Outputs that are not owned by our wallet will be missing derivation paths.
/// Likewise, the scriptpubkey on an input comes from the witness_utxo, which is only populated if the
/// wallet had the UTXO information at construction-time. So all of these methods return Options.
pub trait PsbtWithDerivation {
    fn get_input_spk_and_derivation(&self, idx: usize) -> Option<SpkWithDerivationPaths>;
    fn get_all_inputs_as_spk_and_derivation(&self) -> Option<Vec<SpkWithDerivationPaths>>;
    fn get_output_spk_and_derivation(&self, idx: usize) -> Option<SpkWithDerivationPaths>;
    fn get_all_outputs_as_spk_and_derivation(&self) -> Option<Vec<SpkWithDerivationPaths>>;
}

impl PsbtWithDerivation for Psbt {
    fn get_input_spk_and_derivation(&self, idx: usize) -> Option<SpkWithDerivationPaths> {
        let input = self.inputs.get(idx)?;
        Some(SpkWithDerivationPaths {
            script_pubkey: input.witness_utxo.clone()?.script_pubkey,
            derivation_paths: input.bip32_derivation.clone(),
            witness_script: input.witness_script.clone(),
            proprietary: input.proprietary.clone(),
        })
    }

    fn get_all_inputs_as_spk_and_derivation(&self) -> Option<Vec<SpkWithDerivationPaths>> {
        let mut res = Vec::new();
        for (idx, _) in self.inputs.iter().enumerate() {
            res.push(self.get_input_spk_and_derivation(idx)?);
        }
        Some(res)
    }

    fn get_output_spk_and_derivation(&self, idx: usize) -> Option<SpkWithDerivationPaths> {
        let txout = self.unsigned_tx.output.get(idx)?;
        let output = self.outputs.get(idx)?;
        Some(SpkWithDerivationPaths {
            script_pubkey: txout.script_pubkey.clone(),
            derivation_paths: output.bip32_derivation.clone(),
            witness_script: output.witness_script.clone(),
            proprietary: output.proprietary.clone(),
        })
    }

    fn get_all_outputs_as_spk_and_derivation(&self) -> Option<Vec<SpkWithDerivationPaths>> {
        let mut res = Vec::new();
        for (idx, _) in self.outputs.iter().enumerate() {
            res.push(self.get_output_spk_and_derivation(idx)?);
        }
        Some(res)
    }
}

pub fn treasury_fund_address(address: &Address, amount: Amount) {
    let wallet_name = env::var("BITCOIND_RPC_WALLET_NAME").unwrap_or("testwallet".to_string());
    let rpc_client = generate_rpc_client(Some(wallet_name));

    rpc_client
        .send_to_address(address, amount, None, None, None, None, None, None)
        .expect("Failed to send to address");
}

pub fn generate_block(
    num_blocks: u64,
    address_str: &Address,
) -> Result<Vec<BlockHash>, Box<dyn std::error::Error>> {
    let wallet_name = env::var("BITCOIND_RPC_WALLET_NAME").unwrap_or("testwallet".to_string());
    let rpc_client = generate_rpc_client(Some(wallet_name));

    let block_hashes = rpc_client.generate_to_address(num_blocks, address_str)?;
    Ok(block_hashes)
}

pub fn generate_rpc_client(wallet_name: Option<String>) -> Client {
    let url = if let Some(wallet_name) = wallet_name {
        format!(
            "{}/wallet/{}",
            env::var("REGTEST_BITCOIND_SERVER_URI").unwrap_or("127.0.0.1:18443".to_string()),
            wallet_name
        )
    } else {
        env::var("REGTEST_BITCOIND_SERVER_URI").unwrap_or("127.0.0.1:18443".to_string())
    };
    Client::new(
        &url,
        Auth::UserPass(
            env::var("BITCOIND_RPC_USER").unwrap_or("test".to_string()),
            env::var("BITCOIND_RPC_PASSWORD").unwrap_or("test".to_string()),
        ),
    )
    .expect("Failed to load client")
}

pub struct ChaincodeDelegationPsbt {
    psbt: Psbt,
}

impl ChaincodeDelegationPsbt {
    pub fn new(psbt: &Psbt, participant_public_keys: Vec<SecpPublicKey>) -> anyhow::Result<Self> {
        let proprietary_keys: Vec<ProprietaryKey> = participant_public_keys
            .iter()
            .map(|pk| ProprietaryKey {
                prefix: PROPRIETARY_KEY_PREFIX.to_vec(),
                subtype: PROPRIETARY_KEY_SUBTYPE,
                key: pk.serialize().to_vec(),
            })
            .collect();

        for input in psbt.inputs.iter() {
            for proprietary_key in proprietary_keys.iter() {
                if !input.proprietary.contains_key(proprietary_key) {
                    return Err(anyhow::anyhow!(
                        "Input does not have tweak for participant public key: {proprietary_key:?}"
                    ));
                }
            }
        }

        Ok(Self { psbt: psbt.clone() })
    }
}

pub struct ChaincodeDelegationCollaboratorWallet {
    server_public_key: SecpPublicKey,
    app_public_key: SecpPublicKey,
    hardware_public_key: SecpPublicKey,
}

impl ChaincodeDelegationCollaboratorWallet {
    pub fn new(
        server_public_key: SecpPublicKey,
        app_public_key: SecpPublicKey,
        hardware_public_key: SecpPublicKey,
    ) -> Self {
        Self {
            server_public_key,
            app_public_key,
            hardware_public_key,
        }
    }
}

impl ChaincodeDelegationCollaboratorWallet {
    pub fn chaincode_delegation_psbt(
        &self,
        psbt: &Psbt,
    ) -> anyhow::Result<ChaincodeDelegationPsbt> {
        ChaincodeDelegationPsbt::new(psbt, self.participant_public_keys().to_vec())
    }

    pub fn get_outflow_for_psbt(&self, ccd_psbt: &ChaincodeDelegationPsbt) -> anyhow::Result<u64> {
        let mut outflow = 0u64;

        for output in self.filter_outputs_by_witness_script(&ccd_psbt.psbt, false)? {
            outflow = outflow
                .checked_add(output.value.to_sat())
                .ok_or_else(|| anyhow::anyhow!("Outflow overflow"))?;
        }

        Ok(outflow)
    }

    fn filter_outputs_by_witness_script(
        &self,
        psbt: &Psbt,
        is_change: bool,
    ) -> anyhow::Result<Vec<TxOut>> {
        let mut outputs = vec![];

        for (output_idx, tx_output) in psbt.unsigned_tx.output.iter().enumerate() {
            let is_change_output = if let Some(psbt_output) = psbt.outputs.get(output_idx) {
                let has_witness_script = psbt_output.witness_script.is_some();

                // Without a witness script, we are sure it is not a change output. For it to be a
                // change output, it needs to both have a witness script and match what we expect to see.
                if !has_witness_script {
                    false
                } else {
                    let expected_output_descriptor = self
                        .expected_descriptor_from_proprietary(&psbt_output.proprietary)
                        .ok_or_else(|| {
                            anyhow::anyhow!("Failed to generate expected output descriptor")
                        })?;

                    let expected_witness_script = expected_output_descriptor
                        .script_code()
                        .expect("Failed to generate expected output descriptor script code");

                    expected_output_descriptor.script_pubkey() == tx_output.script_pubkey
                        && &expected_witness_script == psbt_output.witness_script.as_ref().unwrap()
                }
            } else {
                false
            };

            if is_change_output == is_change {
                outputs.push(tx_output.clone());
            }
        }

        Ok(outputs)
    }

    /// Build the tweaked sortedmulti descriptor from proprietary tweak scalars.
    /// Returns None if required proprietary entries are missing / malformed.
    fn expected_descriptor_from_proprietary(
        &self,
        prop: &BTreeMap<ProprietaryKey, Vec<u8>>,
    ) -> Option<Descriptor<SecpPublicKey>> {
        let secp = Secp256k1::new();
        let proprietary_keys = self.generate_proprietary_keys();

        let mut tweaked: Vec<SecpPublicKey> = Vec::with_capacity(proprietary_keys.len());
        for key in &proprietary_keys {
            let tweak_bytes = prop.get(key)?;
            let tweak = Scalar::from_be_bytes(tweak_bytes.as_slice().try_into().ok()?).ok()?;
            let base_pk = SecpPublicKey::from_slice(&key.key).ok()?;
            let tweaked_pk = base_pk.add_exp_tweak(&secp, &tweak).ok()?;
            tweaked.push(tweaked_pk);
        }

        Descriptor::new_wsh_sortedmulti(2, tweaked).ok()
    }

    fn participant_public_keys(&self) -> [SecpPublicKey; 3] {
        [
            self.server_public_key,
            self.app_public_key,
            self.hardware_public_key,
        ]
    }

    fn generate_proprietary_keys(&self) -> [ProprietaryKey; 3] {
        [
            ProprietaryKey {
                prefix: PROPRIETARY_KEY_PREFIX.to_vec(),
                subtype: PROPRIETARY_KEY_SUBTYPE,
                key: self.app_public_key.serialize().to_vec(),
            },
            ProprietaryKey {
                prefix: PROPRIETARY_KEY_PREFIX.to_vec(),
                subtype: PROPRIETARY_KEY_SUBTYPE,
                key: self.server_public_key.serialize().to_vec(),
            },
            ProprietaryKey {
                prefix: PROPRIETARY_KEY_PREFIX.to_vec(),
                subtype: PROPRIETARY_KEY_SUBTYPE,
                key: self.hardware_public_key.serialize().to_vec(),
            },
        ]
    }
}

impl AttributableWallet for ChaincodeDelegationCollaboratorWallet {
    fn is_my_psbt_address(&self, spk: &SpkWithDerivationPaths) -> Result<bool, BdkUtilError> {
        // Chain code delegation requires a witness script and proprietary tweaks to verify ownership.
        let Some(witness_script) = &spk.witness_script else {
            return Ok(false);
        };

        let Some(desc) = self.expected_descriptor_from_proprietary(&spk.proprietary) else {
            return Ok(false);
        };

        // Compare derived scriptPubKey and witness script.
        let expected_ws = match desc.explicit_script() {
            Ok(script) => script,
            Err(_) => return Ok(false),
        };

        let expected_spk = desc.script_pubkey();
        Ok(expected_spk == spk.script_pubkey && &expected_ws == witness_script)
    }

    fn is_addressed_to_self(&self, psbt: &Psbt) -> Result<bool, BdkUtilError> {
        // Parity with Wallet<D> impl: empty outputs => false
        let Some(outputs) = psbt.get_all_outputs_as_spk_and_derivation() else {
            return Ok(false);
        };
        if outputs.is_empty() {
            return Ok(false);
        }
        for spk in outputs {
            if !self.is_my_psbt_address(&spk)? {
                return Ok(false);
            }
        }
        Ok(true)
    }

    fn all_inputs_are_from_self(&self, psbt: &Psbt) -> Result<bool, BdkUtilError> {
        // Parity: error if we can't access witness UTXOs for inputs
        let inputs = psbt
            .get_all_inputs_as_spk_and_derivation()
            .ok_or(BdkUtilError::MissingWitnessUtxo)?;
        for spk in inputs {
            if !self.is_my_psbt_address(&spk)? {
                return Ok(false);
            }
        }
        Ok(true)
    }
}

#[cfg(test)]
pub mod tests {
    use bdk_electrum::electrum_client;
    use bdk_wallet::bitcoin::absolute::LockTime;
    use bdk_wallet::bitcoin::bip32::Xpriv;
    use bdk_wallet::bitcoin::hashes::Hash;
    use bdk_wallet::bitcoin::psbt::Psbt as BitcoinPsbt;
    use bdk_wallet::bitcoin::transaction::Version;
    use bdk_wallet::bitcoin::{Amount, Network, Transaction};
    use bdk_wallet::bitcoin::{BlockHash, OutPoint, TxOut};
    use bdk_wallet::chain::BlockId;
    use bdk_wallet::keys::GeneratableKey;
    use bdk_wallet::template::{Bip84, DescriptorTemplate};
    use bdk_wallet::test_utils::{insert_checkpoint, insert_tx};
    use bdk_wallet::{KeychainKind, TxOrdering, Wallet};
    use rstest::rstest;
    use serde_json::json;

    use super::broadcast_with;
    use crate::error::BdkUtilError;
    use crate::{
        get_electrum_server, get_outflow_addresses_for_psbt, AttributableWallet, ElectrumRpcUris,
        PsbtWithDerivation,
    };

    pub fn get_fake_prefunded_wallet(total_funds: u64) -> Wallet {
        let xprv = Xpriv::generate(()).unwrap();

        // Create descriptors using the Bip84 template
        let external_descriptor = Bip84(xprv.clone(), KeychainKind::External)
            .build(Network::Signet)
            .unwrap();

        let internal_descriptor = Bip84(xprv, KeychainKind::Internal)
            .build(Network::Signet)
            .unwrap();

        let mut wallet = Wallet::create(external_descriptor, internal_descriptor)
            .network(Network::Signet)
            .create_wallet_no_persist()
            .expect("must create wallet");

        // Get an address to fund
        let address = wallet.reveal_next_address(KeychainKind::External).address;

        // Create a fake transaction with the specified amount
        let tx = Transaction {
            version: Version::ONE,
            lock_time: LockTime::ZERO,
            input: vec![],
            output: vec![TxOut {
                value: Amount::from_sat(total_funds),
                script_pubkey: address.script_pubkey(),
            }],
        };

        let txid = tx.compute_txid();

        insert_checkpoint(
            &mut wallet,
            BlockId {
                height: 100,
                hash: BlockHash::all_zeros(),
            },
        );
        insert_tx(&mut wallet, tx);

        wallet.insert_txout(
            OutPoint { txid, vout: 0 },
            TxOut {
                value: Amount::from_sat(total_funds),
                script_pubkey: address.script_pubkey(),
            },
        );

        wallet
    }

    #[test]
    fn test_psbt_input_validation_works() {
        let mut wallet = get_fake_prefunded_wallet(50_000);
        let address = wallet.reveal_next_address(KeychainKind::External).address;

        let mut builder = wallet.build_tx();
        builder.add_recipient(address.script_pubkey(), Amount::from_sat(1000));
        let psbt = builder.finish().unwrap();
        assert_eq!(psbt.inputs.len(), 1); // make sure we have an input
                                          // check the individial input
        assert!(wallet
            .is_my_psbt_address(&psbt.get_input_spk_and_derivation(0).unwrap())
            .unwrap());

        // check the easy way
        assert!(wallet.all_inputs_are_from_self(&psbt).unwrap());
    }

    #[test]
    fn test_psbt_outflow_address_works() {
        let mut wallet = get_fake_prefunded_wallet(50_000);
        let mut recipient = get_fake_prefunded_wallet(50_000);
        let recipient_address = recipient
            .reveal_next_address(KeychainKind::External)
            .address;
        let recipient_script_pubkey = recipient_address.script_pubkey();

        let mut builder = wallet.build_tx();
        builder.add_recipient(recipient_script_pubkey.clone(), Amount::from_sat(1000));
        let psbt = builder.finish().unwrap();
        assert_eq!(psbt.inputs.len(), 1); // make sure we have an input
        assert_eq!(psbt.outputs.len(), 2); // we should have one output to our recipient, one for change

        let outflow_addresses =
            get_outflow_addresses_for_psbt(&wallet, &psbt, Network::Signet).unwrap();
        assert_eq!(outflow_addresses.len(), 1);
        assert_eq!(outflow_addresses[0], recipient_address.to_string());

        let mut builder = wallet.build_tx();
        builder.ordering(TxOrdering::Untouched); // We are going to be looking at specific outputs, so don't reshuffle the ordering
                                                 // coins to self
        let recipient_address_1 = recipient
            .reveal_next_address(KeychainKind::External)
            .address;
        let recipient_script_pubkey_1 = recipient_address_1.script_pubkey();
        let recipient_address_2 = recipient
            .reveal_next_address(KeychainKind::External)
            .address;
        let recipient_script_pubkey_2 = recipient_address_2.script_pubkey();
        builder.add_recipient(recipient_script_pubkey_1.clone(), Amount::from_sat(1000));
        // coins to another wallet
        builder.add_recipient(recipient_script_pubkey_2.clone(), Amount::from_sat(1000));
        let psbt = builder.finish().unwrap();
        assert_eq!(psbt.inputs.len(), 1); // make sure we have an input
        assert_eq!(psbt.outputs.len(), 3); // we should have two outputs for the recipient, one for change

        let outflow_addresses =
            get_outflow_addresses_for_psbt(&wallet, &psbt, Network::Signet).unwrap();
        assert_eq!(outflow_addresses.len(), 2);
        assert_eq!(outflow_addresses[0], recipient_address_1.to_string());
        assert_eq!(outflow_addresses[1], recipient_address_2.to_string());
    }

    #[test]
    fn test_psbt_output_validation_works() {
        let mut wallet = get_fake_prefunded_wallet(50_000);
        let mut recipient = get_fake_prefunded_wallet(50_000);

        // Builder construction creates a mutable borrow, so we need to get the script pubkey before the borrow is dropped
        let wallet_script_pubkey = wallet
            .reveal_next_address(KeychainKind::External)
            .address
            .script_pubkey();
        let recipient_script_pubkey = recipient
            .reveal_next_address(KeychainKind::External)
            .address
            .script_pubkey();

        let mut builder = wallet.build_tx();
        builder.ordering(TxOrdering::Untouched); // We are going to be looking at specific outputs, so don't reshuffle the ordering
                                                 // coins to self
        builder.add_recipient(wallet_script_pubkey, Amount::from_sat(1000));
        // coins to another wallet
        builder.add_recipient(recipient_script_pubkey, Amount::from_sat(1000));
        let psbt = builder.finish().unwrap();
        assert_eq!(psbt.inputs.len(), 1); // make sure we have an input
        assert_eq!(psbt.outputs.len(), 3); // we should have one output for our self-spend, one for the recipient, one for change

        assert!(!wallet.is_addressed_to_self(&psbt).unwrap());

        assert!(wallet
            .is_my_psbt_address(&psbt.get_output_spk_and_derivation(0).unwrap())
            .is_ok_and(|_| true));
        assert!(wallet
            .is_my_psbt_address(&psbt.get_output_spk_and_derivation(1).unwrap())
            .is_ok_and(|result| !result));
    }

    #[test]
    fn test_checking_self_spends_works() {
        let mut wallet = get_fake_prefunded_wallet(50_0000);

        let wallet_script_pubkey = wallet
            .reveal_next_address(KeychainKind::External)
            .address
            .script_pubkey();

        let mut builder = wallet.build_tx();
        // coins to self
        builder.add_recipient(wallet_script_pubkey, Amount::from_sat(1000));
        let psbt = builder.finish().unwrap();
        assert_eq!(psbt.inputs.len(), 1); // make sure we have an input
        assert_eq!(psbt.outputs.len(), 2); // we should have one output for our self-spend, one for change
        assert!(wallet.is_addressed_to_self(&psbt).unwrap());
    }

    #[test]
    fn test_get_electrum_server() {
        let rpc_uris = ElectrumRpcUris {
            mainnet: "ssl://testelectrumserver1.wallet.build:50002".to_string(),
            testnet: "ssl://testelectrumserver2.wallet.build:50002".to_string(),
            signet: "ssl://testelectrumserver3.wallet.build:50002".to_string(),
        };
        assert!(get_electrum_server(Network::Bitcoin, &rpc_uris).is_ok());
        assert!(get_electrum_server(Network::Testnet, &rpc_uris).is_ok());
        assert!(get_electrum_server(Network::Signet, &rpc_uris).is_ok());
        assert!(get_electrum_server(Network::Regtest, &rpc_uris).is_err());
    }

    #[rstest]
    #[case::bad_txns_inputs_missingorspent(json!({"code":-32603,"message":"sendrawtransaction RPC error: sendrawtransaction RPC error: {\"code\":-25,\"message\":\"bad-txns-inputs-missingorspent\"}"}), BdkUtilError::TransactionAlreadyInMempoolError)]
    #[case::min_relay_fee_not_met(json!({"code":-32603,"message":"sendrawtransaction RPC error: {\"code\":-26,\"message\":\"min relay fee not met, 189 < 293\"}"}), BdkUtilError::MinRelayFeeNotMetError)]
    #[test]
    fn test_broadcast_maps_electrum_errors(
        #[case] json: serde_json::Value,
        #[case] expected: BdkUtilError,
    ) {
        let tx = Transaction {
            version: Version::ONE,
            lock_time: LockTime::ZERO,
            input: vec![],
            output: vec![],
        };
        let mut psbt = BitcoinPsbt::from_unsigned_tx(tx).unwrap();
        let actual = broadcast_with(&mut psbt, |_tx| Err(electrum_client::Error::Protocol(json)))
            .unwrap_err();
        assert_eq!(actual.to_string(), expected.to_string());
    }
}
