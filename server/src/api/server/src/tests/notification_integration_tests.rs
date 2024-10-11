use std::collections::{HashMap, HashSet};

use http::StatusCode;
use notification::clients::iterable::IterableClient;
use notification::routes::SendTestPushData;
use notification::service::FetchForAccountInput;
use onboarding::routes::{AccountAddDeviceTokenRequest, CompleteOnboardingRequest};
use types::account::bitcoin::Network;
use types::account::entities::TouchpointPlatform;
use types::account::identifiers::AccountId;
use types::consent::{Consent, NotificationConsentAction};
use types::notification::{NotificationCategory, NotificationChannel, NotificationsPreferences};

use crate::tests;
use crate::tests::gen_services;
use crate::tests::lib::{create_default_account_with_predefined_wallet, create_full_account};
use crate::tests::requests::axum::TestClient;

struct SendTestNotificationTestVector {
    override_account_id: Option<AccountId>,
    expected_create_notification: bool,
    expected_status: StatusCode,
}

async fn send_test_notification_test(vector: SendTestNotificationTestVector) {
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

    let request_account_id = vector.override_account_id.unwrap_or(account.id);
    let actual_response = client
        .send_test_push(&request_account_id.to_string(), &SendTestPushData {})
        .await;
    assert_eq!(
        actual_response.status_code, vector.expected_status,
        "{}",
        actual_response.body_string
    );
    if vector.expected_create_notification {
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

tests! {
    runner = send_test_notification_test,
    test_send_test_notification_with_invalid_account_id: SendTestNotificationTestVector {
        override_account_id: AccountId::gen().ok(),
        expected_create_notification: false,
        expected_status: StatusCode::NOT_FOUND,
    },
    test_send_test_notification_with_valid_account_id: SendTestNotificationTestVector {
        override_account_id: None,
        expected_create_notification: true,
        expected_status: StatusCode::OK,
    },
}

struct TwilioStatusCallbackTestVector {
    signature: Option<String>,
    body: HashMap<String, String>,
    expected_status: StatusCode,
}

async fn twilio_status_callback_test(vector: TwilioStatusCallbackTestVector) {
    let (_, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let response = client
        .twilio_status_callback(&vector.body, vector.signature)
        .await;
    assert_eq!(response.status(), vector.expected_status);
}

tests! {
    runner = twilio_status_callback_test,
    test_status_callback_with_no_signature: TwilioStatusCallbackTestVector {
        signature: None,
        body: HashMap::from([("MessageStatus".into(), "sent".into())]),
        expected_status: StatusCode::BAD_REQUEST,
    },
    test_status_callback_with_invalid_signature: TwilioStatusCallbackTestVector {
        signature: Some("INVALID".to_string()),
        body: HashMap::from([("MessageStatus".into(), "sent".into())]),
        expected_status: StatusCode::UNAUTHORIZED,
    },
    test_status_callback_with_invalid_body: TwilioStatusCallbackTestVector {
        signature: Some("VALID".to_string()),
        body: HashMap::from([("Hello".into(), "World".into())]),
        expected_status: StatusCode::BAD_REQUEST,
    },
    test_status_callback: TwilioStatusCallbackTestVector {
        signature: Some("VALID".to_string()),
        body: HashMap::from([("MessageStatus".into(), "sent".into())]),
        expected_status: StatusCode::NO_CONTENT,
    },
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
        Network::BitcoinSignet,
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
