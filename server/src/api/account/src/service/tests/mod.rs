use crate::service::{CreateAccountAndKeysetsInput, Service};
use bdk_utils::bdk::bitcoin::bip32::{
    DerivationPath, ExtendedPrivKey, ExtendedPubKey, Fingerprint,
};
use bdk_utils::bdk::bitcoin::key::Secp256k1;
use bdk_utils::bdk::bitcoin::secp256k1::rand::{thread_rng, Rng};
use bdk_utils::bdk::bitcoin::secp256k1::{PublicKey, SecretKey};
use bdk_utils::bdk::database::{AnyDatabase, MemoryDatabase};
use bdk_utils::bdk::descriptor::DescriptorPublicKey;
use bdk_utils::bdk::keys::{DescriptorSecretKey, KeyMap};
use bdk_utils::bdk::miniscript::descriptor::{DescriptorXKey, Wildcard};
use bdk_utils::bdk::{bitcoin, SyncOptions, Wallet};
use bdk_utils::flags::{
    DEFAULT_MAINNET_ELECTRUM_RPC_URI, DEFAULT_SIGNET_ELECTRUM_RPC_URI,
    DEFAULT_TESTNET_ELECTRUM_RPC_URI,
};
use bdk_utils::{get_blockchain, DescriptorKeyset, ElectrumRpcUris};
use database::ddb;
use database::ddb::Repository;
use external_identifier::ExternalIdentifier;
use http_server::config;
use repository::account::AccountRepository;
use repository::consent::ConsentRepository;
use std::str::FromStr;
use types::account::bitcoin::Network;
use types::account::entities::{FullAccount, Keyset};
use types::account::identifiers::{AccountId, AuthKeysId, KeysetId};
use types::account::keys::FullAccountAuthKeys;
use types::account::spending::SpendingKeyset;
use ulid::Ulid;
use userpool::userpool::UserPoolService;

const DEFAULT_DERIVATION_PATH_STR: &str = "m/84'/1'/0'";
const RECEIVE_DERIVATION_PATH: &str = "m/0";
const CHANGE_DERIVATION_PATH: &str = "m/1";

#[derive(Debug, Clone)]
pub struct TestKeypair {
    pub public_key: PublicKey,
    pub secret_key: SecretKey,
}

#[derive(Debug, Clone)]
pub struct TestAuthenticationKeys {
    pub app: TestKeypair,
    pub hw: TestKeypair,
    pub recovery: TestKeypair,
}

impl From<TestAuthenticationKeys> for FullAccountAuthKeys {
    fn from(keys: TestAuthenticationKeys) -> Self {
        FullAccountAuthKeys {
            app_pubkey: keys.app.public_key,
            hardware_pubkey: keys.hw.public_key,
            recovery_pubkey: Some(keys.recovery.public_key),
        }
    }
}

pub fn default_electrum_rpc_uris() -> ElectrumRpcUris {
    ElectrumRpcUris {
        mainnet: DEFAULT_MAINNET_ELECTRUM_RPC_URI.to_owned(),
        testnet: DEFAULT_TESTNET_ELECTRUM_RPC_URI.to_owned(),
        signet: DEFAULT_SIGNET_ELECTRUM_RPC_URI.to_owned(),
    }
}

pub fn generate_test_authkeys() -> TestAuthenticationKeys {
    let secp = Secp256k1::new();
    let app_sk = SecretKey::new(&mut thread_rng());
    let app_pk = app_sk.public_key(&secp);

    let hw_sk = SecretKey::new(&mut thread_rng());
    let hw_pk = hw_sk.public_key(&secp);

    let recovery_sk = SecretKey::new(&mut thread_rng());
    let recovery_pk = recovery_sk.public_key(&secp);

    TestAuthenticationKeys {
        app: TestKeypair {
            public_key: app_pk,
            secret_key: app_sk,
        },
        hw: TestKeypair {
            public_key: hw_pk,
            secret_key: hw_sk,
        },
        recovery: TestKeypair {
            public_key: recovery_pk,
            secret_key: recovery_sk,
        },
    }
}

pub async fn construct_test_account_service() -> Service {
    let profile = Some("test");
    let ddb_config = config::extract::<ddb::Config>(profile).expect("extract ddb config");
    let ddb_connection = ddb_config.to_connection().await;

    let account_repository = AccountRepository::new(ddb_connection.clone());
    let consent_repository = ConsentRepository::new(ddb_connection.clone());

    let cognito_config =
        config::extract::<userpool::userpool::Config>(profile).expect("extract cognito config");
    let cognito_connection = cognito_config.to_connection().await;
    let userpool_service = UserPoolService::new(cognito_connection);

    Service::new(
        account_repository.clone(),
        consent_repository.clone(),
        userpool_service.clone(),
    )
}

pub fn create_descriptor_keys(network: Network) -> (DescriptorSecretKey, DescriptorPublicKey) {
    let derivation_path = DerivationPath::from_str(DEFAULT_DERIVATION_PATH_STR).unwrap();
    let (parent_fingerprint, xprv, xpub) = create_keys(network);
    let xpubkey = DescriptorXKey {
        origin: Some((parent_fingerprint, derivation_path.clone())),
        xkey: xpub,
        derivation_path: DerivationPath::master(),
        wildcard: Wildcard::Unhardened,
    };
    let xprvkey = DescriptorXKey {
        origin: Some((parent_fingerprint, derivation_path)),
        xkey: xprv,
        derivation_path: DerivationPath::master(),
        wildcard: Wildcard::Unhardened,
    };
    (
        DescriptorSecretKey::XPrv(xprvkey),
        DescriptorPublicKey::XPub(xpubkey),
    )
}

pub fn create_spend_keyset(network: Network) -> (SpendingKeyset, Wallet<AnyDatabase>) {
    let (app_xprv, app_xpub) = create_descriptor_keys(network);
    let (_, hardware_xpub) = create_descriptor_keys(network);
    let (_, server_xpub) = create_descriptor_keys(network);
    let keyset = SpendingKeyset::new(
        network.to_owned(),
        app_xpub.clone(),
        hardware_xpub.clone(),
        server_xpub.clone(),
    );
    let wallet = create_bdk_wallet(
        &app_xprv.to_string(),
        &app_xpub.to_string(),
        &hardware_xpub.to_string(),
        &server_xpub.to_string(),
        network.into(),
    );
    (keyset, wallet)
}

pub fn create_bdk_wallet(
    app_xprv: &str,
    app_xpub: &str,
    hw_xpub: &str,
    server_xpub: &str,
    network: bitcoin::Network,
) -> Wallet<AnyDatabase> {
    let app = DescriptorPublicKey::from_str(app_xpub).unwrap();
    let hw = DescriptorPublicKey::from_str(hw_xpub).unwrap();
    let server = DescriptorPublicKey::from_str(server_xpub).unwrap();

    let keyset = DescriptorKeyset::new(network, app, hw, server);
    let receive_keymap = gen_keymap_with_derivation_path(
        &DescriptorSecretKey::from_str(app_xprv).unwrap(),
        &DescriptorPublicKey::from_str(app_xpub).unwrap(),
        DerivationPath::from_str(RECEIVE_DERIVATION_PATH).unwrap(),
    );
    let change_keymap = gen_keymap_with_derivation_path(
        &DescriptorSecretKey::from_str(app_xprv).unwrap(),
        &DescriptorPublicKey::from_str(app_xpub).unwrap(),
        DerivationPath::from_str(CHANGE_DERIVATION_PATH).unwrap(),
    );
    let receive_descriptor = keyset.receiving().into_multisig_descriptor().unwrap();
    let change_descriptor = keyset.change().into_multisig_descriptor().unwrap();
    let wallet = Wallet::new(
        (receive_descriptor, receive_keymap),
        Some((change_descriptor, change_keymap)),
        network,
        AnyDatabase::Memory(MemoryDatabase::new()),
    )
    .unwrap();
    let rpc_uris = default_electrum_rpc_uris();
    let blockchain = get_blockchain(network, &rpc_uris).unwrap();
    wallet.sync(&blockchain, SyncOptions::default()).unwrap();
    wallet
}

pub async fn create_full_account_for_test(
    account_service: &Service,
    network: Network,
    auth: &FullAccountAuthKeys,
) -> FullAccount {
    let (spend, _) = create_spend_keyset(network);
    account_service
        .create_account_and_keysets(CreateAccountAndKeysetsInput {
            account_id: AccountId::gen().unwrap(),
            network: spend.network,
            keyset_id: KeysetId::new(Ulid::default()).unwrap(),
            auth_key_id: AuthKeysId::new(Ulid::default()).unwrap(),
            keyset: Keyset {
                auth: FullAccountAuthKeys {
                    app_pubkey: auth.app_pubkey,
                    hardware_pubkey: auth.hardware_pubkey,
                    recovery_pubkey: auth.recovery_pubkey,
                },
                spending: SpendingKeyset {
                    network,
                    app_dpub: spend.app_dpub,
                    hardware_dpub: spend.hardware_dpub,
                    server_dpub: spend.server_dpub,
                },
            },
            is_test_account: network != Network::BitcoinMain,
        })
        .await
        .expect("full account should be created")
}

fn gen_keymap_with_derivation_path(
    xprv: &DescriptorSecretKey,
    xpub: &DescriptorPublicKey,
    derivation_path: DerivationPath,
) -> KeyMap {
    let derived_xprv = match xprv {
        DescriptorSecretKey::XPrv(xkey) => DescriptorSecretKey::XPrv(DescriptorXKey {
            derivation_path: derivation_path.clone(),
            origin: xkey.origin.clone(),
            ..*xkey
        }),
        _ => panic!("Invalid Secret Key"),
    };
    let derived_xpub = match xpub {
        DescriptorPublicKey::XPub(xkey) => DescriptorPublicKey::XPub(DescriptorXKey {
            derivation_path,
            origin: xkey.origin.clone(),
            ..*xkey
        }),
        _ => panic!("Invalid Public Key"),
    };
    KeyMap::from([(derived_xpub, derived_xprv)])
}

fn create_keys(network: Network) -> (Fingerprint, ExtendedPrivKey, ExtendedPubKey) {
    let secp = Secp256k1::new();
    let mut rng = thread_rng();
    let seed: [u8; 32] = rng.gen();
    let derivation_path = DerivationPath::from_str(DEFAULT_DERIVATION_PATH_STR).unwrap();
    let sk = ExtendedPrivKey::new_master(network.to_owned().into(), &seed).unwrap();
    let derived_sk = sk.derive_priv(&secp, &derivation_path).unwrap();
    (
        sk.fingerprint(&secp),
        derived_sk,
        ExtendedPubKey::from_priv(&secp, &derived_sk),
    )
}
