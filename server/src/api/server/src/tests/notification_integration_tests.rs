use std::collections::{HashMap, HashSet};

use bdk_utils::bdk::bitcoin::{
    address::NetworkUnchecked,
    secp256k1::{rand::rngs::StdRng, rand::SeedableRng, Secp256k1},
    Address, PublicKey,
};
use http::StatusCode;
use notification::address_repo::AddressAndKeysetId;
use notification::clients::iterable::IterableClient;
use notification::routes::{SendTestPushData, SetNotificationsTriggersRequest};
use notification::service::FetchForAccountInput;
use notification::NotificationPayloadType;
use onboarding::routes::{AccountAddDeviceTokenRequest, CompleteOnboardingRequest};
use types::account::bitcoin::Network;
use types::account::entities::TouchpointPlatform;
use types::account::identifiers::AccountId;
use types::consent::{Consent, NotificationConsentAction};
use types::notification::{
    NotificationCategory, NotificationChannel, NotificationsPreferences, NotificationsTriggerType,
};

use crate::tests::gen_services;
use crate::tests::lib::{create_default_account_with_predefined_wallet, create_full_account};
use crate::tests::requests::axum::TestClient;
use rstest::rstest;

#[rstest]
#[case::invalid_account_id(
    AccountId::gen().ok(),
    false,
    StatusCode::NOT_FOUND
)]
#[case::valid_account_id(None, true, StatusCode::OK)]
#[tokio::test]
async fn test_send_test_notification(
    #[case] override_account_id: Option<AccountId>,
    #[case] expected_create_notification: bool,
    #[case] expected_status: StatusCode,
) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let (account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();

    client
        .add_device_token(
            &account.id.to_string(),
            &AccountAddDeviceTokenRequest {
                device_token: "test".to_string(),
                platform: TouchpointPlatform::ApnsTeam,
            },
        )
        .await;
    client
        .set_notifications_preferences(
            &account.id.to_string(),
            &NotificationsPreferences {
                account_security: HashSet::from([NotificationChannel::Push]),
                money_movement: HashSet::default(),
                product_marketing: HashSet::default(),
            },
            false,
            false,
            &keys,
        )
        .await;

    let request_account_id = override_account_id.unwrap_or(account.id);
    let actual_response = client
        .send_test_push(&request_account_id.to_string(), &SendTestPushData {})
        .await;
    assert_eq!(
        actual_response.status_code, expected_status,
        "{}",
        actual_response.body_string
    );
    if expected_create_notification {
        let notifications = bootstrap
            .services
            .notification_service
            .fetch_customer_for_account(FetchForAccountInput {
                account_id: request_account_id,
            })
            .await
            .expect("Retrieve all notifications for user wallet");
        assert_eq!(notifications.len(), 1);
    }
}

#[rstest]
#[case::no_signature(
    None,
    HashMap::from([("MessageStatus".into(), "sent".into())]),
    StatusCode::BAD_REQUEST
)]
#[case::invalid_signature(
    Some("INVALID".to_string()),
    HashMap::from([("MessageStatus".into(), "sent".into())]),
    StatusCode::UNAUTHORIZED
)]
#[case::invalid_body(
    Some("VALID".to_string()),
    HashMap::from([("Hello".into(), "World".into())]),
    StatusCode::BAD_REQUEST
)]
#[case::valid(
    Some("VALID".to_string()),
    HashMap::from([("MessageStatus".into(), "sent".into())]),
    StatusCode::NO_CONTENT
)]
#[tokio::test]
async fn test_twilio_status_callback(
    #[case] signature: Option<String>,
    #[case] body: HashMap<String, String>,
    #[case] expected_status: StatusCode,
) {
    let (_, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let response = client.twilio_status_callback(&body, signature).await;
    assert_eq!(response.status(), expected_status);
}

#[tokio::test]
async fn test_notifications_preferences() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let IterableClient::Test(store) = &bootstrap.services.iterable_client else {
        panic!("Expected Test IterableClient");
    };

    let account = create_full_account(
        &mut context,
        &bootstrap.services,
        types::account::bitcoin::Network::BitcoinSignet,
        None,
    )
    .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();

    let get_response = client
        .get_notifications_preferences(&account.id.to_string())
        .await;
    assert_eq!(get_response.status_code, StatusCode::OK);
    assert_eq!(
        get_response.body.unwrap(),
        NotificationsPreferences::default()
    );
    assert_eq!(
        store
            .lock()
            .await
            .get(&account.id.to_string())
            .cloned()
            .unwrap_or_default(),
        HashSet::default(),
    );

    // Initial subscribe
    let set_response = client
        .set_notifications_preferences(
            &account.id.to_string(),
            &NotificationsPreferences {
                account_security: HashSet::from([
                    NotificationChannel::Push,
                    NotificationChannel::Email,
                ]),
                money_movement: HashSet::default(),
                product_marketing: HashSet::from([
                    NotificationChannel::Sms,
                    NotificationChannel::Email,
                ]),
            },
            false,
            false,
            &keys,
        )
        .await;
    assert_eq!(set_response.status_code, StatusCode::OK);
    assert_eq!(
        set_response.body.unwrap(),
        NotificationsPreferences {
            account_security: HashSet::from([
                NotificationChannel::Push,
                NotificationChannel::Email,
            ]),
            money_movement: HashSet::default(),
            product_marketing: HashSet::from([
                NotificationChannel::Sms,
                NotificationChannel::Email,
            ]),
        }
    );
    assert_eq!(
        store
            .lock()
            .await
            .get(&account.id.to_string())
            .cloned()
            .unwrap_or_default(),
        HashSet::from([
            NotificationCategory::AccountSecurity,
            NotificationCategory::ProductMarketing
        ]),
    );
    let consents = bootstrap
        .services
        .consent_repository
        .fetch_for_account_id(&account.id)
        .await
        .unwrap();
    assert_eq!(consents.iter().filter(|c| matches!(c, Consent::Notification(n) if n.action == NotificationConsentAction::OptIn)).count(), 4);
    assert_eq!(consents.iter().filter(|c| matches!(c, Consent::Notification(n) if n.action == NotificationConsentAction::OptOut)).count(), 0);

    // Complete onboarding
    let complete_response = client
        .complete_onboarding(&account.id.to_string(), &CompleteOnboardingRequest {})
        .await;
    assert_eq!(complete_response.status_code, StatusCode::OK);

    let set_response = client
        .set_notifications_preferences(
            &account.id.to_string(),
            &NotificationsPreferences {
                account_security: HashSet::from([
                    NotificationChannel::Push,
                    NotificationChannel::Email,
                ]),
                money_movement: HashSet::from([
                    NotificationChannel::Push,
                    NotificationChannel::Sms,
                ]),
                product_marketing: HashSet::default(),
            },
            false,
            false,
            &keys,
        )
        .await;
    assert_eq!(set_response.status_code, StatusCode::OK);
    assert_eq!(
        set_response.body.unwrap(),
        NotificationsPreferences {
            account_security: HashSet::from([
                NotificationChannel::Push,
                NotificationChannel::Email
            ]),
            money_movement: HashSet::from([NotificationChannel::Push, NotificationChannel::Sms]),
            product_marketing: HashSet::default(),
        }
    );
    assert_eq!(
        store
            .lock()
            .await
            .get(&account.id.to_string())
            .cloned()
            .unwrap_or_default(),
        HashSet::from([NotificationCategory::AccountSecurity,]),
    );

    let consents = bootstrap
        .services
        .consent_repository
        .fetch_for_account_id(&account.id)
        .await
        .unwrap();
    assert_eq!(consents.iter().filter(|c| matches!(c, Consent::Notification(n) if n.action == NotificationConsentAction::OptIn)).count(), 6);
    assert_eq!(consents.iter().filter(|c| matches!(c, Consent::Notification(n) if n.action == NotificationConsentAction::OptOut)).count(), 2);

    // Unsubscribe from account security without signatures
    let set_response = client
        .set_notifications_preferences(
            &account.id.to_string(),
            &NotificationsPreferences {
                account_security: HashSet::default(),
                money_movement: HashSet::from([
                    NotificationChannel::Push,
                    NotificationChannel::Sms,
                ]),
                product_marketing: HashSet::from([
                    NotificationChannel::Push,
                    NotificationChannel::Sms,
                ]),
            },
            false,
            false,
            &keys,
        )
        .await;
    assert_eq!(set_response.status_code, StatusCode::FORBIDDEN);
}

#[tokio::test]
async fn test_notifications_triggers() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let account = create_full_account(
        &mut context,
        &bootstrap.services,
        types::account::bitcoin::Network::BitcoinSignet,
        None,
    )
    .await;

    assert_eq!(
        client
            .set_notifications_triggers(
                &account.id.to_string(),
                &SetNotificationsTriggersRequest {
                    notifications_triggers: vec![NotificationsTriggerType::SecurityHubWalletAtRisk],
                }
            )
            .await
            .status_code,
        StatusCode::OK
    );

    let triggers = bootstrap
        .services
        .account_repository
        .fetch(&account.id)
        .await
        .unwrap()
        .get_common_fields()
        .notifications_triggers
        .clone();

    assert_eq!(triggers.len(), 1);
    let (created_at, updated_at) = (triggers[0].created_at, triggers[0].updated_at);

    let mut scheduled_notifications = bootstrap
        .services
        .notification_service
        .fetch_scheduled_for_account(FetchForAccountInput {
            account_id: account.id.clone(),
        })
        .await
        .unwrap();
    scheduled_notifications.sort_by_key(|n| n.execution_date_time);
    let scheduled_notifications_types = scheduled_notifications
        .iter()
        .map(|n| n.payload_type)
        .collect::<Vec<NotificationPayloadType>>();

    assert_eq!(
        scheduled_notifications_types,
        vec![
            NotificationPayloadType::SecurityHub,
            NotificationPayloadType::SecurityHub
        ],
    );

    // Re-apply the same trigger
    assert_eq!(
        client
            .set_notifications_triggers(
                &account.id.to_string(),
                &SetNotificationsTriggersRequest {
                    notifications_triggers: vec![NotificationsTriggerType::SecurityHubWalletAtRisk],
                }
            )
            .await
            .status_code,
        StatusCode::OK
    );

    let mut scheduled_notifications = bootstrap
        .services
        .notification_service
        .fetch_scheduled_for_account(FetchForAccountInput {
            account_id: account.id.clone(),
        })
        .await
        .unwrap();
    scheduled_notifications.sort_by_key(|n| n.execution_date_time);
    let scheduled_notifications_types = scheduled_notifications
        .iter()
        .map(|n| n.payload_type)
        .collect::<Vec<NotificationPayloadType>>();

    // Should not create a new notification schedule
    assert_eq!(
        scheduled_notifications_types,
        vec![
            NotificationPayloadType::SecurityHub,
            NotificationPayloadType::SecurityHub
        ],
    );

    let triggers = bootstrap
        .services
        .account_repository
        .fetch(&account.id)
        .await
        .unwrap()
        .get_common_fields()
        .notifications_triggers
        .clone();

    assert_eq!(triggers.len(), 1);

    // However, the existing trigger should be updated
    assert_eq!(triggers[0].created_at, created_at);
    assert_ne!(triggers[0].updated_at, updated_at);

    // Un-apply the trigger
    assert_eq!(
        client
            .set_notifications_triggers(
                &account.id.to_string(),
                &SetNotificationsTriggersRequest {
                    notifications_triggers: vec![],
                }
            )
            .await
            .status_code,
        StatusCode::OK
    );

    let triggers = bootstrap
        .services
        .account_repository
        .fetch(&account.id)
        .await
        .unwrap()
        .get_common_fields()
        .notifications_triggers
        .clone();

    assert_eq!(triggers.len(), 0);

    // Re-apply the trigger
    assert_eq!(
        client
            .set_notifications_triggers(
                &account.id.to_string(),
                &SetNotificationsTriggersRequest {
                    notifications_triggers: vec![NotificationsTriggerType::SecurityHubWalletAtRisk],
                }
            )
            .await
            .status_code,
        StatusCode::OK
    );

    let mut scheduled_notifications = bootstrap
        .services
        .notification_service
        .fetch_scheduled_for_account(FetchForAccountInput {
            account_id: account.id.clone(),
        })
        .await
        .unwrap();
    scheduled_notifications.sort_by_key(|n| n.execution_date_time);
    let scheduled_notifications_types = scheduled_notifications
        .iter()
        .map(|n| n.payload_type)
        .collect::<Vec<NotificationPayloadType>>();

    // Should create a new notification schedule (the old one will not validate)
    assert_eq!(
        scheduled_notifications_types,
        vec![
            NotificationPayloadType::SecurityHub,
            NotificationPayloadType::SecurityHub,
            NotificationPayloadType::SecurityHub,
            NotificationPayloadType::SecurityHub
        ],
    );

    let triggers = bootstrap
        .services
        .account_repository
        .fetch(&account.id)
        .await
        .unwrap()
        .get_common_fields()
        .notifications_triggers
        .clone();

    assert_eq!(triggers.len(), 1);

    // There should be a new trigger
    assert_ne!(triggers[0].created_at, created_at);
    assert_ne!(triggers[0].updated_at, updated_at);
}

#[tokio::test]
async fn test_address_cleanup_when_money_movement_disabled() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    // 1. Create account with predefined wallet
    let (account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;

    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();

    // 2. Enable money movement notifications
    let preferences_with_money_movement = NotificationsPreferences {
        account_security: HashSet::new(),
        money_movement: HashSet::from([NotificationChannel::Push, NotificationChannel::Email]),
        product_marketing: HashSet::new(),
    };

    let set_response = client
        .set_notifications_preferences(
            &account.id.to_string(),
            &preferences_with_money_movement,
            true,
            true,
            &keys,
        )
        .await;
    assert_eq!(set_response.status_code, StatusCode::OK);

    // 3. Generate unique watch addresses for this test
    let secp = Secp256k1::new();
    let mut rng = StdRng::seed_from_u64(42);

    let pubkey_1 = PublicKey::new(secp.generate_keypair(&mut rng).1);
    let pubkey_2 = PublicKey::new(secp.generate_keypair(&mut rng).1);

    let addr_string_1 = Address::p2wpkh(&pubkey_1, Network::BitcoinSignet.into())
        .unwrap()
        .to_string();
    let unchecked_address_1: Address<NetworkUnchecked> = addr_string_1.parse().unwrap();
    let address_1 = AddressAndKeysetId::new(
        unchecked_address_1,
        types::account::identifiers::KeysetId::gen().unwrap(),
    );

    let addr_string_2 = Address::p2wpkh(&pubkey_2, Network::BitcoinSignet.into())
        .unwrap()
        .to_string();
    let unchecked_address_2: Address<NetworkUnchecked> = addr_string_2.parse().unwrap();
    let address_2 = AddressAndKeysetId::new(
        unchecked_address_2,
        types::account::identifiers::KeysetId::gen().unwrap(),
    );

    // 4. Register addresses (now that money movement notifications are enabled)
    let register_response = client
        .register_watch_address(
            &account.id,
            &vec![address_1.clone(), address_2.clone()].into(),
        )
        .await;
    assert_eq!(register_response.status_code, StatusCode::OK);

    // 5. Verify addresses exist in database
    let address_repo = &bootstrap.services.address_service;
    let results_before = address_repo
        .get(&[address_1.address.clone(), address_2.address.clone()])
        .await
        .expect("Failed to query addresses");
    assert_eq!(
        results_before.len(),
        2,
        "Both addresses should exist after registration with money movement notifications enabled"
    );

    // 6. Disable money movement notifications to trigger cleanup
    let preferences_no_money_movement = NotificationsPreferences {
        account_security: HashSet::from([NotificationChannel::Push, NotificationChannel::Email]),
        money_movement: HashSet::new(),
        product_marketing: HashSet::from([NotificationChannel::Sms]),
    };

    let cleanup_response = client
        .set_notifications_preferences(
            &account.id.to_string(),
            &preferences_no_money_movement,
            true,
            true,
            &keys,
        )
        .await;
    assert_eq!(cleanup_response.status_code, StatusCode::OK);

    // 7. Verify addresses are automatically deleted
    let results_after = address_repo
        .get(&[address_1.address.clone(), address_2.address.clone()])
        .await
        .expect("Failed to query addresses after cleanup");
    assert!(
        results_after.is_empty(),
        "Addresses should be automatically deleted when money movement notifications are disabled"
    );
}
