use std::collections::{HashMap, HashSet};

use crate::tests;
use account::entities::{Network, TouchpointPlatform};
use http::StatusCode;
use notification::clients::iterable::IterableClient;
use notification::routes::{SendTestPushData, SetNotificationsPreferencesRequest};
use notification::service::FetchForAccountInput;
use onboarding::routes::AccountAddDeviceTokenRequest;
use types::account::identifiers::AccountId;
use types::consent::{Consent, NotificationConsentAction};
use types::notification::{NotificationCategory, NotificationChannel, NotificationsPreferences};

use crate::tests::gen_services;
use crate::tests::lib::{create_account, create_default_account_with_predefined_wallet};
use crate::tests::requests::axum::TestClient;

struct SendTestNotificationTestVector {
    override_account_id: Option<AccountId>,
    expected_create_notification: bool,
    expected_status: StatusCode,
}

async fn send_test_notification_test(vector: SendTestNotificationTestVector) {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let (account, _) =
        create_default_account_with_predefined_wallet(&client, &bootstrap.services).await;
    client
        .add_device_token(
            &account.id.to_string(),
            &AccountAddDeviceTokenRequest {
                device_token: "test".to_string(),
                platform: TouchpointPlatform::ApnsTeam,
            },
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
    let bootstrap = gen_services().await;
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
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let IterableClient::Test(store) = &bootstrap.services.iterable_client else {
        panic!("Expected Test IterableClient");
    };

    let account = create_account(&bootstrap.services, Network::BitcoinSignet, None).await;

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

    let set_response = client
        .set_notifications_preferences(
            &account.id.to_string(),
            &SetNotificationsPreferencesRequest {
                account_security: HashSet::default(),
                money_movement: HashSet::from([
                    NotificationChannel::Push,
                    NotificationChannel::Email,
                ]),
                product_marketing: HashSet::from([
                    NotificationChannel::Sms,
                    NotificationChannel::Email,
                ]),
            },
        )
        .await;
    assert_eq!(set_response.status_code, StatusCode::OK);
    assert_eq!(
        set_response.body.unwrap(),
        NotificationsPreferences {
            account_security: HashSet::default(),
            money_movement: HashSet::from([NotificationChannel::Push, NotificationChannel::Email]),
            product_marketing: HashSet::from([
                NotificationChannel::Sms,
                NotificationChannel::Email
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
            NotificationCategory::MoneyMovement,
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

    // Out of band unsubscribe
    store.lock().await.insert(
        account.id.to_string(),
        HashSet::from([NotificationCategory::MoneyMovement]),
    );
    let get_response = client
        .get_notifications_preferences(&account.id.to_string())
        .await;
    assert_eq!(get_response.status_code, StatusCode::OK);
    assert_eq!(
        get_response.body.unwrap(),
        NotificationsPreferences {
            account_security: HashSet::default(),
            money_movement: HashSet::from([NotificationChannel::Push, NotificationChannel::Email]),
            product_marketing: HashSet::from([NotificationChannel::Sms]),
        },
    );
    assert_eq!(
        store
            .lock()
            .await
            .get(&account.id.to_string())
            .cloned()
            .unwrap_or_default(),
        HashSet::from([NotificationCategory::MoneyMovement]),
    );

    let set_response = client
        .set_notifications_preferences(
            &account.id.to_string(),
            &SetNotificationsPreferencesRequest {
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
        )
        .await;
    assert_eq!(set_response.status_code, StatusCode::OK);
    assert_eq!(
        set_response.body.unwrap(),
        NotificationsPreferences {
            account_security: HashSet::default(),
            money_movement: HashSet::from([NotificationChannel::Push, NotificationChannel::Sms]),
            product_marketing: HashSet::from([NotificationChannel::Push, NotificationChannel::Sms]),
        }
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
    let consents = bootstrap
        .services
        .consent_repository
        .fetch_for_account_id(&account.id)
        .await
        .unwrap();
    assert_eq!(consents.iter().filter(|c| matches!(c, Consent::Notification(n) if n.action == NotificationConsentAction::OptIn)).count(), 6);
    assert_eq!(consents.iter().filter(|c| matches!(c, Consent::Notification(n) if n.action == NotificationConsentAction::OptOut)).count(), 2);
}