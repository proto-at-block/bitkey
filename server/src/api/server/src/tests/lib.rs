use std::str::FromStr;
use std::sync::RwLock;

use account::service::tests::{
    create_bdk_wallet, create_descriptor_keys, create_full_account_for_test,
    generate_test_authkeys, TestAuthenticationKeys,
};
use account::service::{
    ActivateTouchpointForAccountInput, AddPushTouchpointToAccountInput, CreateLiteAccountInput,
    CreateSoftwareAccountInput, FetchAccountInput, FetchOrCreateEmailTouchpointInput,
    FetchOrCreatePhoneTouchpointInput,
};

use bdk_utils::bdk::bitcoin::secp256k1::{PublicKey, Secp256k1, SecretKey};
use bdk_utils::bdk::bitcoin::OutPoint;
use bdk_utils::bdk::keys::DescriptorSecretKey;
use bdk_utils::bdk::wallet::{get_funded_wallet, AddressIndex, AddressInfo};
use bdk_utils::bdk::{
    bitcoin::psbt::PartiallySignedTransaction, database::AnyDatabase,
    miniscript::DescriptorPublicKey, FeeRate,
};
use bdk_utils::bdk::{SignOptions, Wallet as BdkWallet};
use external_identifier::ExternalIdentifier;
use http::StatusCode;
use isocountry::CountryCode;
use notification::service::{
    FetchNotificationsPreferencesInput, UpdateNotificationsPreferencesInput,
};
use onboarding::routes::{CreateAccountRequest, CreateKeysetRequest};
use rand::thread_rng;
use recovery::entities::{
    DelayNotifyRecoveryAction, DelayNotifyRequirements, RecoveryAction, RecoveryDestination,
    RecoveryRequirements, RecoveryStatus, RecoveryType, WalletRecovery,
};
use time::format_description::well_known::Rfc3339;
use time::{Duration, OffsetDateTime};
use types::account::bitcoin::Network;
use types::account::entities::{
    Account, Factor, FullAccount, FullAccountAuthKeysPayload, LiteAccount, SoftwareAccount,
    SpendingKeysetRequest, Touchpoint, TouchpointPlatform,
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

const RECEIVE_DERIVATION_PATH: &str = "m/0";
const CHANGE_DERIVATION_PATH: &str = "m/1";

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

pub(crate) async fn create_default_account_with_predefined_wallet(
    context: &mut TestContext,
    client: &TestClient,
    services: &Services,
) -> (FullAccount, BdkWallet<AnyDatabase>) {
    create_default_account_with_predefined_wallet_internal(context, client, services, true).await
}

pub(crate) async fn create_nontest_default_account_with_predefined_wallet(
    context: &mut TestContext,
    client: &TestClient,
    services: &Services,
) -> (FullAccount, BdkWallet<AnyDatabase>) {
    create_default_account_with_predefined_wallet_internal(context, client, services, false).await
}

async fn create_default_account_with_predefined_wallet_internal(
    context: &mut TestContext,
    client: &TestClient,
    services: &Services,
    is_test_account: bool,
) -> (FullAccount, BdkWallet<AnyDatabase>) {
    let network = Network::BitcoinSignet;
    let app_dprv = DescriptorSecretKey::from_str("[7699ff15/84'/1'/0']tprv8iy8auG1S2uBHrd5SWEW3gQELVDMoqur43KKPkbVGDXGTmwLZ4P2Lq7iXA6SzWEh5qB7AG26ENcYqLeDVCgRaJDMXJBdn8A8T5VnZJ6A6C9/*").unwrap();
    let app_dpub = DescriptorPublicKey::from_str("[7699ff15/84'/1'/0']tpubDFfAjKJFaQarBKesL9u6T64LuWjHyB6kdLv6gGdngVKfJGC7BTCcXKjahFxgMfPSgCPyoVFmK4ALuq4L3pk7p2i2FEgBJdGVmbpxty9MQzw/*").unwrap();
    let hardware_dpub = DescriptorPublicKey::from_str("[08254319/84'/1'/0']tpubDEitjEyJG3W5vcinDWPD1sYtY4EmePDrPXCSs15Q7TkR78Fuyi21X1UpEpYXdoWc9sUJbsWpkd77VN8ZiJMHcnHvUhmsRqapsUdU7Mzrncf/*").unwrap();
    let auth = create_auth_keyset_model(context);

    let response = client
        .create_account(
            context,
            &CreateAccountRequest::Full {
                auth: FullAccountAuthKeysPayload {
                    app: auth.app_pubkey,
                    hardware: auth.hardware_pubkey,
                    recovery: auth.recovery_pubkey,
                },
                spending: SpendingKeysetRequest {
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
    (account, wallet)
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
                spending: SpendingKeysetRequest {
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

pub(crate) async fn create_account(
    context: &mut TestContext,
    services: &Services,
    account_type: AccountType,
) -> Account {
    match account_type {
        AccountType::Full => Account::Full(
            create_full_account(context, services, Network::BitcoinSignet, None).await,
        ),
        AccountType::Lite => {
            Account::Lite(create_lite_account(context, services, None, true).await)
        }
        AccountType::Software => {
            Account::Software(create_software_account(context, services, None, true).await)
        }
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
