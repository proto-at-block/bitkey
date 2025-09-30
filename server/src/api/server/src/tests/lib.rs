use std::str::FromStr;
use std::sync::RwLock;

use account::service::tests::{
    create_bdk_wallet, create_descriptor_keys, create_full_account_for_test,
    default_electrum_rpc_uris, generate_test_authkeys, TestAuthenticationKeys,
};
use account::service::{
    ActivateTouchpointForAccountInput, AddPushTouchpointToAccountInput, CreateLiteAccountInput,
    CreateSoftwareAccountInput, FetchAccountInput, FetchOrCreateEmailTouchpointInput,
    FetchOrCreatePhoneTouchpointInput,
};
use authn_authz::routes::NoiseInitiateBundleRequest;

use base64::{engine::general_purpose::STANDARD as b64, Engine as _};
use bdk_utils::bdk::bitcoin::bip32::{
    ChildNumber, DerivationPath, ExtendedPrivKey, ExtendedPubKey,
};
use bdk_utils::bdk::bitcoin::secp256k1::{PublicKey, Secp256k1, SecretKey};
use bdk_utils::bdk::bitcoin::OutPoint;
use bdk_utils::bdk::keys::DescriptorSecretKey;
use bdk_utils::bdk::miniscript::descriptor::{DescriptorXKey, Wildcard};
use bdk_utils::bdk::miniscript::ToPublicKey;
use bdk_utils::bdk::wallet::{get_funded_wallet, AddressIndex, AddressInfo};
use bdk_utils::bdk::{
    bitcoin::psbt::PartiallySignedTransaction, database::AnyDatabase,
    miniscript::DescriptorPublicKey, FeeRate,
};
use bdk_utils::bdk::{SignOptions, Wallet as BdkWallet};
use bdk_utils::get_blockchain;
use external_identifier::ExternalIdentifier;
use http::StatusCode;
use isocountry::CountryCode;
use notification::service::{
    FetchNotificationsPreferencesInput, UpdateNotificationsPreferencesInput,
};
use onboarding::routes::{CreateAccountRequest, CreateKeysetRequest};
use onboarding::routes_v2::CreateAccountRequestV2;
use rand::thread_rng;
use recovery::entities::{
    DelayNotifyRecoveryAction, DelayNotifyRequirements, RecoveryAction, RecoveryDestination,
    RecoveryRequirements, RecoveryStatus, RecoveryType, WalletRecovery,
};
use time::format_description::well_known::Rfc3339;
use time::{Duration, OffsetDateTime};
use types::account::bitcoin::Network;
use types::account::entities::v2::{FullAccountAuthKeysInputV2, SpendingKeysetInputV2};
use types::account::entities::{
    Account, Factor, FullAccount, FullAccountAuthKeysInput, LiteAccount, SoftwareAccount,
    SpendingKeysetInput, Touchpoint, TouchpointPlatform,
};
use types::account::identifiers::{AccountId, AuthKeysId, KeysetId, TouchpointId};
use types::account::keys::{FullAccountAuthKeys, LiteAccountAuthKeys, SoftwareAccountAuthKeys};
use types::account::AccountType;
use types::notification::{NotificationCategory, NotificationChannel};
use types::recovery::social::relationship::{RecoveryRelationship, RecoveryRelationshipId};
use types::time::Clock;
use ulid::Ulid;

use super::requests::axum::TestClient;
use crate::tests::TestContext;
use crate::Services;

pub(crate) fn gen_external_wallet_address() -> AddressInfo {
    let external_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/1/*)").0;
    external_wallet.get_address(AddressIndex::New).unwrap()
}

pub(crate) fn create_keypair() -> (SecretKey, PublicKey) {
    let secp = Secp256k1::new();
    secp.generate_keypair(&mut thread_rng())
}
pub(crate) fn create_pubkey() -> PublicKey {
    let (_, pk) = create_keypair();
    pk
}

pub(crate) fn create_plain_keys() -> (SecretKey, PublicKey) {
    let secp = Secp256k1::new();
    secp.generate_keypair(&mut thread_rng())
}

/// Return randomly generated pubkeys, one for app, one for hw and one for recovery
///
/// Use this when you want to sign auth challenges or do proof-of-possession
pub(crate) fn create_new_authkeys(context: &mut TestContext) -> TestAuthenticationKeys {
    let auth_keys = generate_test_authkeys();
    context.add_authentication_keys(auth_keys.clone());
    auth_keys
}

pub(crate) async fn create_default_account_with_private_wallet(
    context: &mut TestContext,
    client: &TestClient,
    services: &Services,
) -> (FullAccount, BdkWallet<AnyDatabase>) {
    create_default_account_with_predefined_wallet_internal(context, client, services, true, true)
        .await
}

pub(crate) async fn create_default_account_with_predefined_wallet(
    context: &mut TestContext,
    client: &TestClient,
    services: &Services,
) -> (FullAccount, BdkWallet<AnyDatabase>) {
    create_default_account_with_predefined_wallet_internal(context, client, services, true, false)
        .await
}

pub(crate) async fn create_nontest_default_account_with_predefined_wallet(
    context: &mut TestContext,
    client: &TestClient,
    services: &Services,
) -> (FullAccount, BdkWallet<AnyDatabase>) {
    create_default_account_with_predefined_wallet_internal(context, client, services, false, false)
        .await
}

// Creates an account with predefined, "mocked" customer wallets. Note: depending on whether or not
// it is a private keyset, the wallet that gets created will be different. For convenience, we
// provide the wallet descriptors here:
//
// Legacy wallet: wsh(sortedmulti(2,[70cb0ceb/84'/1'/0']tpubDD2SKQzFYz1u2xfFnmhtZKvtbk9HvkhkDhE7CurNXXC7XWA94oFZNJSSnepnfUVxBia4gNLCrehNYpZHeAmakkDs6qPo2BE7qSDA9b7rHXg/0/*,[a601ec2c/84'/1'/0']tpubDC7RbD1aXGn242JYyYjv7shKrQMxyTZ4h1ream2sq9ADwBWoDkhHvnsMS4sRiamESzcx1ZEZwWUf4ayQ1UMGutgZFz88q2FSvubWq6yTFWA/0;1/*,[c345e1e9/84'/1'/0']tpubDDC5YGNGhebUAGw8nKsTCTbfutQwAXNzyATcnCsbhCjfdt2a8cpGbojfgAzPnsdsXxVypwjz2uGUV9dpWh211PeYhuHHumjRs7dgRLKcKk1/0;1/*))
// Private wallet: wsh(sortedmulti(2,[70cb0ceb/84'/1'/0']tpubDD2SKQzFYz1u2xfFnmhtZKvtbk9HvkhkDhE7CurNXXC7XWA94oFZNJSSnepnfUVxBia4gNLCrehNYpZHeAmakkDs6qPo2BE7qSDA9b7rHXg/0;1/*,[a601ec2c/84'/1'/0']tpubDC7RbD1aXGn242JYyYjv7shKrQMxyTZ4h1ream2sq9ADwBWoDkhHvnsMS4sRiamESzcx1ZEZwWUf4ayQ1UMGutgZFz88q2FSvubWq6yTFWA/0;1/*,[dfbc4cf1/84/1/0]tpubDC7dP44ArSSD17nTVFih7g4WFVJtKkmzhH9xaffzV6tAEbVUfxoVWepafD8BPfCeG8vktiZUnpH7rbQqcawrbDoz7CyHRXqzHZB76CAzVek/0;1/*))
//
// Note, the difference between the two is that the private wallet uses unhardened derivation paths,
// whereas the legacy wallet uses hardened derivation paths.
async fn create_default_account_with_predefined_wallet_internal(
    context: &mut TestContext,
    client: &TestClient,
    services: &Services,
    is_test_account: bool,
    is_private_wallet: bool,
) -> (FullAccount, BdkWallet<AnyDatabase>) {
    let network = Network::BitcoinSignet;

    let predefined_keys = predefined_descriptor_public_keys();
    let (app_dprv, app_dpub) = predefined_keys.app_account_descriptor_keypair();
    let hardware_dpub = predefined_keys.hardware_account_dpub;

    let auth = create_auth_keyset_model(context);

    if is_private_wallet {
        let app_xpub = match &app_dpub {
            DescriptorPublicKey::XPub(xpub) => xpub.xkey.public_key,
            _ => panic!("Expected XPub descriptor"),
        };
        let hardware_xpub = match &hardware_dpub {
            DescriptorPublicKey::XPub(xpub) => xpub.xkey.public_key,
            _ => panic!("Expected XPub descriptor"),
        };

        let response = client
            .create_account_v2(
                context,
                &CreateAccountRequestV2 {
                    auth: FullAccountAuthKeysInputV2 {
                        app_pub: auth.app_pubkey,
                        hardware_pub: auth.hardware_pubkey,
                        recovery_pub: auth.recovery_pubkey.unwrap(),
                    },
                    spend: SpendingKeysetInputV2 {
                        network: network.into(),
                        app_pub: app_xpub.to_public_key().inner,
                        hardware_pub: hardware_xpub.to_public_key().inner,
                    },
                    is_test_account,
                },
            )
            .await;

        assert_eq!(response.status_code, StatusCode::OK);

        let response = response.body.unwrap();

        let account = services
            .account_service
            .fetch_full_account(FetchAccountInput {
                account_id: &response.account_id,
            })
            .await
            .unwrap();

        let server_dpub = {
            // The server never has a chain code, so we simulate an app generating one for us
            let server_root_xpub = predefined_server_root_xpub(network, response.server_pub);

            let unhardened_path = DerivationPath::from_str("m/84/1/0").unwrap();
            let derived_xpub = server_root_xpub
                .derive_pub(&Secp256k1::new(), &unhardened_path)
                .expect("derived server xpub");
            let origin = (server_root_xpub.fingerprint(), unhardened_path);
            DescriptorPublicKey::XPub(DescriptorXKey {
                origin: Some(origin),
                xkey: derived_xpub,
                derivation_path: DerivationPath::default(),
                wildcard: Wildcard::Unhardened,
            })
        };

        let wallet = create_bdk_wallet(
            &app_dprv.to_string(),
            &app_dpub.to_string(),
            &hardware_dpub.to_string(),
            &server_dpub.to_string(),
            network.into(),
        );

        let rpc_uris = default_electrum_rpc_uris();
        let blockchain = get_blockchain(network.into(), &rpc_uris).unwrap();
        wallet.sync(&blockchain, Default::default()).unwrap();

        (account, wallet)
    } else {
        let response = client
            .create_account(
                context,
                &CreateAccountRequest::Full {
                    auth: FullAccountAuthKeysInput {
                        app: auth.app_pubkey,
                        hardware: auth.hardware_pubkey,
                        recovery: auth.recovery_pubkey,
                    },
                    spending: SpendingKeysetInput {
                        network: network.into(),
                        app: app_dpub.clone(),
                        hardware: hardware_dpub.clone(),
                    },
                    is_test_account,
                },
            )
            .await;

        assert_eq!(
            response.status_code,
            StatusCode::OK,
            "{}",
            response.body_string
        );

        let response = response.body.unwrap();
        let server_dpub = response
            .keyset
            .expect("Account should have a keyset")
            .spending;

        let account = services
            .account_service
            .fetch_full_account(FetchAccountInput {
                account_id: &response.account_id,
            })
            .await
            .unwrap();

        let wallet = create_bdk_wallet(
            &app_dprv.to_string(),
            &app_dpub.to_string(),
            &hardware_dpub.to_string(),
            &server_dpub.to_string(),
            network.into(),
        );

        let rpc_uris = default_electrum_rpc_uris();
        let blockchain = get_blockchain(network.into(), &rpc_uris).unwrap();
        wallet.sync(&blockchain, Default::default()).unwrap();
        (account, wallet)
    }
}

pub(crate) fn create_auth_keyset_model(context: &mut TestContext) -> FullAccountAuthKeys {
    create_new_authkeys(context).into()
}

pub(crate) fn create_lite_auth_keyset_model(context: &mut TestContext) -> LiteAccountAuthKeys {
    let keys = create_new_authkeys(context);
    LiteAccountAuthKeys::new(keys.recovery.public_key)
}

pub(crate) fn create_software_auth_keyset_model(
    context: &mut TestContext,
) -> SoftwareAccountAuthKeys {
    let keys = create_new_authkeys(context);
    SoftwareAccountAuthKeys::new(keys.app.public_key, keys.recovery.public_key)
}

pub(crate) async fn create_inactive_spending_keyset_for_account(
    context: &TestContext,
    client: &TestClient,
    account_id: &AccountId,
    network: Network,
) -> KeysetId {
    let keys = context
        .get_authentication_keys_for_account_id(account_id)
        .expect("Invalid keys for account");
    let (_, spend_app) = create_descriptor_keys(network);
    let (_, spend_hw) = create_descriptor_keys(network);

    let response = client
        .create_keyset(
            &account_id.to_string(),
            &CreateKeysetRequest {
                spending: SpendingKeysetInput {
                    network: network.into(),
                    app: spend_app,
                    hardware: spend_hw,
                },
            },
            &keys,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let create_keyset_response = response.body.unwrap();
    create_keyset_response.keyset_id
}

pub(crate) async fn create_test_account(
    context: &mut TestContext,
    services: &Services,
    account_type: AccountType,
) -> Account {
    create_account(context, services, account_type, true).await
}

pub(crate) async fn create_account(
    context: &mut TestContext,
    services: &Services,
    account_type: AccountType,
    is_test_account: bool,
) -> Account {
    match account_type {
        AccountType::Full => Account::Full(
            create_full_account(
                context,
                services,
                if is_test_account {
                    Network::BitcoinSignet
                } else {
                    Network::BitcoinMain
                },
                None,
            )
            .await,
        ),
        AccountType::Lite => {
            Account::Lite(create_lite_account(context, services, None, is_test_account).await)
        }
        AccountType::Software => Account::Software(
            create_software_account(context, services, None, is_test_account).await,
        ),
    }
}

pub(crate) async fn create_full_account(
    context: &mut TestContext,
    services: &Services,
    network: Network,
    override_auth_keys: Option<FullAccountAuthKeys>,
) -> FullAccount {
    let auth = if let Some(auth) = override_auth_keys {
        auth
    } else {
        create_auth_keyset_model(context)
    };

    let account = create_full_account_for_test(&services.account_service, network, &auth).await;
    services
        .userpool_service
        .create_or_update_account_users_if_necessary(
            &account.id,
            Some(auth.app_pubkey),
            Some(auth.hardware_pubkey),
            auth.recovery_pubkey,
        )
        .await
        .unwrap();
    context.associate_with_account(
        &account.id,
        account
            .application_auth_pubkey
            .expect("App pubkey not present"),
    );
    account
}

pub(crate) async fn create_lite_account(
    context: &mut TestContext,
    services: &Services,
    override_auth_keys: Option<LiteAccountAuthKeys>,
    is_test_account: bool,
) -> LiteAccount {
    let auth = if let Some(auth) = override_auth_keys {
        auth
    } else {
        create_lite_auth_keyset_model(context)
    };

    let account = services
        .account_service
        .create_lite_account(CreateLiteAccountInput {
            account_id: &AccountId::gen().unwrap(),
            auth_key_id: AuthKeysId::new(Ulid::default()).unwrap(),
            auth: auth.clone(),
            is_test_account,
        })
        .await
        .unwrap();
    services
        .userpool_service
        .create_or_update_account_users_if_necessary(
            &account.id,
            None,
            None,
            Some(auth.recovery_pubkey),
        )
        .await
        .unwrap();
    context.associate_with_account(&account.id, auth.recovery_pubkey);
    account
}

pub(crate) async fn create_software_account(
    context: &mut TestContext,
    services: &Services,
    override_auth_keys: Option<SoftwareAccountAuthKeys>,
    is_test_account: bool,
) -> SoftwareAccount {
    let auth = if let Some(auth) = override_auth_keys {
        auth
    } else {
        create_software_auth_keyset_model(context)
    };

    let account = services
        .account_service
        .create_software_account(CreateSoftwareAccountInput {
            account_id: &AccountId::gen().unwrap(),
            auth_key_id: AuthKeysId::new(Ulid::default()).unwrap(),
            auth: auth.clone(),
            is_test_account,
        })
        .await
        .unwrap();
    services
        .userpool_service
        .create_or_update_account_users_if_necessary(
            &account.id,
            Some(auth.app_pubkey),
            None,
            Some(auth.recovery_pubkey),
        )
        .await
        .unwrap();
    context.associate_with_account(&account.id, auth.app_pubkey);
    context.associate_with_account(&account.id, auth.recovery_pubkey);
    account
}

pub(crate) async fn create_phone_touchpoint(
    services: &Services,
    account_id: &AccountId,
    activate: bool,
) -> TouchpointId {
    let touchpoint = services
        .account_service
        .fetch_or_create_phone_touchpoint(FetchOrCreatePhoneTouchpointInput {
            account_id,
            phone_number: format!("+1{}", if activate { "5555555555" } else { "5555555556" }),
            country_code: CountryCode::as_array().first().unwrap().to_owned(),
        })
        .await
        .unwrap();

    if let Touchpoint::Phone { id, .. } = touchpoint {
        if activate {
            services
                .account_service
                .activate_touchpoint_for_account(ActivateTouchpointForAccountInput {
                    account_id,
                    touchpoint_id: id.clone(),
                    dry_run: false,
                })
                .await
                .unwrap();

            let current_notification_preferences = services
                .notification_service
                .fetch_notifications_preferences(FetchNotificationsPreferencesInput { account_id })
                .await
                .unwrap();
            services
                .notification_service
                .update_notifications_preferences(UpdateNotificationsPreferencesInput {
                    account_id,
                    notifications_preferences: &current_notification_preferences.with_enabled(
                        NotificationCategory::AccountSecurity,
                        NotificationChannel::Sms,
                    ),
                    key_proof: None,
                })
                .await
                .unwrap();
        }

        Some(id)
    } else {
        None
    }
    .unwrap()
}

pub(crate) async fn create_push_touchpoint(
    services: &Services,
    account_id: &AccountId,
) -> Touchpoint {
    let touchpoint = services
        .account_service
        .add_push_touchpoint_for_account(AddPushTouchpointToAccountInput {
            account_id,
            use_local_sns: true,
            platform: TouchpointPlatform::ApnsTeam,
            device_token: "test-device-token".to_owned(),
            access_token: Default::default(),
        })
        .await
        .unwrap();

    let current_notification_preferences = services
        .notification_service
        .fetch_notifications_preferences(FetchNotificationsPreferencesInput { account_id })
        .await
        .unwrap();
    services
        .notification_service
        .update_notifications_preferences(UpdateNotificationsPreferencesInput {
            account_id,
            notifications_preferences: &current_notification_preferences.with_enabled(
                NotificationCategory::AccountSecurity,
                NotificationChannel::Push,
            ),
            key_proof: None,
        })
        .await
        .unwrap();

    touchpoint
}

pub(crate) async fn create_email_touchpoint(
    services: &Services,
    account_id: &AccountId,
    activate: bool,
) -> TouchpointId {
    let touchpoint = services
        .account_service
        .fetch_or_create_email_touchpoint(FetchOrCreateEmailTouchpointInput {
            account_id,
            email_address: "bitcoin@example.com".to_string(),
        })
        .await
        .unwrap();

    if let Touchpoint::Email { id, .. } = touchpoint {
        if activate {
            services
                .account_service
                .activate_touchpoint_for_account(ActivateTouchpointForAccountInput {
                    account_id,
                    touchpoint_id: id.clone(),
                    dry_run: false,
                })
                .await
                .unwrap();

            let current_notification_preferences = services
                .notification_service
                .fetch_notifications_preferences(FetchNotificationsPreferencesInput { account_id })
                .await
                .unwrap();
            services
                .notification_service
                .update_notifications_preferences(UpdateNotificationsPreferencesInput {
                    account_id,
                    notifications_preferences: &current_notification_preferences.with_enabled(
                        NotificationCategory::AccountSecurity,
                        NotificationChannel::Email,
                    ),
                    key_proof: None,
                })
                .await
                .unwrap();
        }

        Some(id)
    } else {
        None
    }
    .unwrap()
}

pub(crate) fn build_transaction_with_amount(
    wallet: &BdkWallet<AnyDatabase>,
    recipient: AddressInfo,
    amt: u64,
    uxtos: &[OutPoint],
) -> PartiallySignedTransaction {
    let mut builder = wallet.build_tx();
    builder
        .add_recipient(recipient.script_pubkey(), amt)
        .fee_rate(FeeRate::from_sat_per_vb(5.0));
    if !uxtos.is_empty() {
        builder.manually_selected_only().add_utxos(uxtos).unwrap();
    }
    let (mut tx, _) = builder.finish().unwrap();
    let _ = wallet.sign(
        &mut tx,
        SignOptions {
            remove_partial_sigs: false,
            ..SignOptions::default()
        },
    );
    tx
}

pub(crate) fn build_sweep_transaction(
    wallet: &BdkWallet<AnyDatabase>,
    recipient: AddressInfo,
) -> PartiallySignedTransaction {
    let mut builder = wallet.build_tx();
    builder
        .drain_wallet()
        .drain_to(recipient.script_pubkey())
        .fee_rate(FeeRate::from_sat_per_vb(5.0));
    let (mut tx, _) = builder.finish().unwrap();
    let _ = wallet.sign(
        &mut tx,
        SignOptions {
            remove_partial_sigs: false,
            ..SignOptions::default()
        },
    );
    tx
}

pub(crate) fn generate_delay_and_notify_recovery(
    account_id: AccountId,
    destination: RecoveryDestination,
    delay_end_time: OffsetDateTime,
    recovery_status: RecoveryStatus,
    lost_factor: Factor,
) -> WalletRecovery {
    let initiation_time = OffsetDateTime::now_utc() - Duration::days(1);
    WalletRecovery {
        account_id,
        created_at: initiation_time,
        recovery_status,
        recovery_type: RecoveryType::DelayAndNotify,
        recovery_type_time: format!(
            "{}:{}",
            RecoveryType::DelayAndNotify,
            initiation_time.format(&Rfc3339).unwrap()
        ),
        requirements: RecoveryRequirements {
            delay_notify_requirements: DelayNotifyRequirements {
                lost_factor,
                delay_end_time,
            }
            .into(),
        },
        recovery_action: RecoveryAction {
            delay_notify_action: DelayNotifyRecoveryAction {
                destination: destination.clone(),
            }
            .into(),
        },
        destination_app_auth_pubkey: Some(destination.app_auth_pubkey),
        destination_hardware_auth_pubkey: Some(destination.hardware_auth_pubkey),
        destination_recovery_auth_pubkey: destination.recovery_auth_pubkey,
        updated_at: initiation_time,
    }
}

pub(crate) async fn update_recovery_relationship_invitation_expiration(
    services: &Services,
    recovery_relationship_id: &RecoveryRelationshipId,
    expires_at: OffsetDateTime,
) {
    let relationship = services
        .recovery_relationship_service
        .repository
        .fetch_recovery_relationship(recovery_relationship_id)
        .await
        .unwrap();

    let mut invitation = match relationship {
        RecoveryRelationship::Invitation(invitation) => invitation,
        _ => panic!("Expected relationship to be an invitation"),
    };

    invitation.expires_at = expires_at;

    services
        .recovery_relationship_service
        .repository
        .persist_recovery_relationship(&RecoveryRelationship::Invitation(invitation))
        .await
        .unwrap();
}

const NOISE_SERVER_PUBKEY: [u8; 65] = [
    0x04, 0x6d, 0x0f, 0x2d, 0x82, 0x02, 0x4c, 0x8a, 0x9d, 0xef, 0xa3, 0x4a, 0xc4, 0xa8, 0x2f, 0x65,
    0x92, 0x47, 0xb3, 0x8e, 0x0f, 0xdf, 0x30, 0x24, 0xd5, 0x79, 0xd9, 0x81, 0xf9, 0xed, 0x7a, 0x86,
    0x61, 0xf8, 0xef, 0xe8, 0xbd, 0x86, 0xdc, 0x1b, 0xa0, 0x5f, 0xc9, 0x86, 0xf1, 0xc9, 0xf1, 0x2e,
    0x45, 0x0e, 0xdc, 0xb1, 0xc3, 0x4d, 0x07, 0x2c, 0x7c, 0xde, 0x13, 0xa8, 0x97, 0x76, 0x70, 0x50,
    0xab,
];

pub async fn setup_noise_secure_channel(
    client: &TestClient,
) -> (crypto::noise::NoiseContext, Vec<u8>) {
    let noise_client_privkey = crypto::noise::PrivateKey::InMemory {
        secret_bytes: crypto::noise::generate_keypair().0,
    };
    let client_noise_context = crypto::noise::NoiseContext::new(
        crypto::noise::NoiseRole::Initiator,
        noise_client_privkey,
        Some(NOISE_SERVER_PUBKEY.to_vec()),
        None,
    )
    .unwrap();
    let client_noise_bundle = client_noise_context.initiate_handshake().unwrap();
    let request = NoiseInitiateBundleRequest {
        bundle: client_noise_bundle,
        server_static_pubkey: b64.encode(NOISE_SERVER_PUBKEY),
    };
    let actual_response = client.initate_noise_secure_channel(&request).await;
    assert_eq!(
        actual_response.status_code,
        StatusCode::OK,
        "{}",
        actual_response.body_string
    );
    let response = actual_response.body.unwrap();
    let server_noise_bundle = response.bundle;
    let noise_session = response.noise_session;
    client_noise_context
        .advance_handshake(server_noise_bundle)
        .unwrap();
    client_noise_context.finalize_handshake().unwrap();

    (client_noise_context, noise_session)
}

pub(crate) struct OffsetClock {
    offset: RwLock<Duration>,
}

impl OffsetClock {
    pub fn new() -> Self {
        Self {
            offset: RwLock::new(Duration::ZERO),
        }
    }

    pub fn add_offset(&self, offset: Duration) {
        *self.offset.write().unwrap() += offset;
    }
}

impl Clock for OffsetClock {
    fn now_utc(&self) -> OffsetDateTime {
        OffsetDateTime::now_utc() + self.offset.read().unwrap().to_owned()
    }
}

pub(crate) struct PredefinedDescriptorKeys {
    pub app_root_xprv: ExtendedPrivKey,
    pub hardware_account_dpub: DescriptorPublicKey,
}

pub(crate) fn predefined_descriptor_public_keys() -> PredefinedDescriptorKeys {
    let app_root_xprv = ExtendedPrivKey::from_str("tprv8ZgxMBicQKsPctZDhFn4GFVRDFR8iErZv4FYC577JFFamP3MkG6sTfX6r2jsU7rPwfWCAh6jfXjcwQhnKLfVFvSXZtQQMHpDJgDkfJvaVo4").unwrap();
    let hardware_dpub = DescriptorPublicKey::from_str("[a601ec2c/84'/1'/0']tpubDC7RbD1aXGn242JYyYjv7shKrQMxyTZ4h1ream2sq9ADwBWoDkhHvnsMS4sRiamESzcx1ZEZwWUf4ayQ1UMGutgZFz88q2FSvubWq6yTFWA/*").unwrap();

    PredefinedDescriptorKeys {
        app_root_xprv,
        hardware_account_dpub: hardware_dpub,
    }
}

impl PredefinedDescriptorKeys {
    pub fn app_account_descriptor_keypair(&self) -> (DescriptorSecretKey, DescriptorPublicKey) {
        let app_account_xprv = self
            .app_root_xprv
            .derive_priv(
                &Secp256k1::new(),
                &DerivationPath::from_str("m/84'/1'/0'").unwrap(),
            )
            .unwrap();
        let app_dprv = DescriptorSecretKey::XPrv(DescriptorXKey {
            origin: Some((
                self.app_root_xprv.fingerprint(&Secp256k1::new()),
                DerivationPath::from_str("m/84'/1'/0'").unwrap(),
            )),
            xkey: app_account_xprv,
            derivation_path: DerivationPath::default(),
            wildcard: Wildcard::Unhardened,
        });

        let app_dpub = app_dprv.to_public(&Secp256k1::new()).unwrap();

        (app_dprv, app_dpub)
    }
}

pub(crate) fn predefined_server_root_xpub(
    network: Network,
    public_key: PublicKey,
) -> ExtendedPubKey {
    let ExtendedPrivKey { chain_code, .. } =
        ExtendedPrivKey::new_master(network.into(), &[0u8; 32]).unwrap();

    ExtendedPubKey {
        network: network.into(),
        depth: 0,
        parent_fingerprint: Default::default(),
        child_number: ChildNumber::from_normal_idx(0).expect("root child number"),
        public_key,
        chain_code,
    }
}
