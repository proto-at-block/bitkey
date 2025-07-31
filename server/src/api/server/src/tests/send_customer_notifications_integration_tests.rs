use std::{
    collections::{HashMap, HashSet},
    str::FromStr,
};

use aws_sdk_sqs::types::Message;
use notification::{
    entities::{CustomerNotification, NotificationTouchpoint},
    identifiers::NotificationId,
    payloads::{
        comms_verification::{CommsVerificationPayload, TemplateType},
        recovery_pending_delay_period::RecoveryPendingDelayPeriodPayload,
    },
    service::{
        FetchForAccountInput, FetchForCompositeKeyInput, PersistCustomerNotificationsInput,
        SendNotificationInput, UpdateDeliveryStatusInput,
    },
    DeliveryStatus, NotificationMessage, NotificationPayloadBuilder, NotificationPayloadType,
};
use rstest::rstest;
use time::OffsetDateTime;
use types::account::entities::{Factor, TouchpointPlatform};
use types::{account::identifiers::TouchpointId, notification::NotificationChannel};

use crate::tests::{
    gen_services,
    lib::{
        create_default_account_with_predefined_wallet, create_email_touchpoint,
        create_phone_touchpoint, create_push_touchpoint,
    },
    requests::axum::TestClient,
    requests::worker::TestWorker,
};

#[rstest]
#[case::only_push(vec![NotificationChannel::Push])]
#[case::only_email(vec![NotificationChannel::Email])]
#[case::only_sms(vec![NotificationChannel::Sms])]
#[case::push_and_email(vec![NotificationChannel::Push, NotificationChannel::Email])]
#[tokio::test]
async fn test_send_customer_notifications(#[case] entries: Vec<NotificationChannel>) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let state = workers::jobs::WorkerState {
        config: http_server::config::extract(None).unwrap(),
        notification_service: bootstrap.services.notification_service.clone(),
        account_service: bootstrap.services.account_service.clone(),
        recovery_service: bootstrap.services.recovery_service.clone(),
        chain_indexer_service: bootstrap.services.chain_indexer_service.clone(),
        mempool_indexer_service: bootstrap.services.mempool_indexer_service.clone(),
        address_service: bootstrap.services.address_service.clone(),
        sqs: bootstrap.services.sqs.clone(),
        feature_flags_service: bootstrap.services.feature_flags_service.clone(),
        privileged_action_repository: bootstrap.services.privileged_action_repository.clone(),
        inheritance_repository: bootstrap.services.inheritance_repository.clone(),
        social_recovery_repository: bootstrap.services.social_recovery_repository.clone(),
        account_repository: bootstrap.services.account_repository.clone(),
    };
    let worker = TestWorker::new(state.clone()).await;

    let (account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let account_id = account.id;

    let push_touchpoint = &create_push_touchpoint(&bootstrap.services, &account_id)
        .await
        .into();
    let phone_touchpoint_id = create_phone_touchpoint(&bootstrap.services, &account_id, true).await;
    let phone_touchpoint = &NotificationTouchpoint::Phone {
        touchpoint_id: phone_touchpoint_id,
    };
    let email_touchpoint_id = create_email_touchpoint(&bootstrap.services, &account_id, true).await;
    let email_touchpoint = &NotificationTouchpoint::Email {
        touchpoint_id: email_touchpoint_id,
    };

    let payload = RecoveryPendingDelayPeriodPayload {
        initiation_time: OffsetDateTime::now_utc(),
        delay_end_time: OffsetDateTime::now_utc(),
        lost_factor: Factor::Hw,
    };

    let notifications = entries
        .into_iter()
        .map(|channel| {
            let touchpoint = match channel {
                NotificationChannel::Email => email_touchpoint,
                NotificationChannel::Sms => phone_touchpoint,
                NotificationChannel::Push => push_touchpoint,
            };
            CustomerNotification {
                account_id: account_id.clone(),
                unique_id: NotificationId::gen_customer(),
                touchpoint: touchpoint.to_owned(),
                payload_type: NotificationPayloadType::RecoveryPendingDelayPeriod,
                delivery_status: DeliveryStatus::Enqueued,
                created_at: OffsetDateTime::now_utc(),
                updated_at: OffsetDateTime::now_utc(),
            }
        })
        .collect::<Vec<CustomerNotification>>();

    let notification_service = bootstrap.services.notification_service;
    notification_service
        .persist_customer_notifications(PersistCustomerNotificationsInput {
            account_id: account_id.clone(),
            notifications: notifications.clone(),
        })
        .await
        .unwrap();

    let new_messages = notifications
        .clone()
        .into_iter()
        .map(|m| {
            let sns_message =
                TryInto::<NotificationMessage>::try_into((m.composite_key(), payload.clone()))
                    .unwrap();
            Message::builder()
                .body(serde_json::to_string(&sns_message).unwrap())
                .build()
        })
        .collect::<Vec<Message>>();

    bootstrap
        .services
        .sqs
        .update_test_messages(new_messages)
        .await
        .unwrap();
    worker
        .customer_notification(NotificationChannel::Push)
        .await;

    let fetched_notifications = notification_service
        .fetch_customer_for_account(FetchForAccountInput { account_id })
        .await
        .unwrap();
    assert_eq!(fetched_notifications.len(), notifications.len());
    assert!(fetched_notifications
        .into_iter()
        .all(|n| n.delivery_status == DeliveryStatus::Completed));
}

#[tokio::test]
async fn customer_notification_lifecycle_test() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let account_id = account.id;

    let entries: Vec<(&'static str, NotificationTouchpoint)> = vec![
        (
            "customer-01H46635S0PXRAP97RNVS6SRQC",
            NotificationTouchpoint::Push {
                platform: TouchpointPlatform::ApnsTeam,
                device_token: "test".to_string(),
            },
        ),
        (
            "customer-01H4661RMJDM407YDJ63BHAJ2R",
            NotificationTouchpoint::Email {
                touchpoint_id: TouchpointId::gen().unwrap(),
            },
        ),
        (
            "customer-01H4663WJ99EZEDKNW21JYR56Y",
            NotificationTouchpoint::Phone {
                touchpoint_id: TouchpointId::gen().unwrap(),
            },
        ),
    ];

    let notifications = entries
        .into_iter()
        .map(|(uid, touchpoint)| {
            let unique_id =
                NotificationId::from_str(uid).expect("Successfully parse NotificationId");
            let now = OffsetDateTime::now_utc();
            (
                unique_id,
                CustomerNotification {
                    account_id: account_id.clone(),
                    unique_id,
                    touchpoint,
                    payload_type: NotificationPayloadType::TestPushNotification,
                    delivery_status: DeliveryStatus::Enqueued,
                    created_at: now,
                    updated_at: now,
                },
            )
        })
        .collect::<HashMap<NotificationId, CustomerNotification>>();

    bootstrap
        .services
        .notification_service
        .persist_customer_notifications(PersistCustomerNotificationsInput {
            account_id: account_id.clone(),
            notifications: notifications.values().cloned().collect(),
        })
        .await
        .expect("Successfully persist customer notifications");

    let customer_notifications = bootstrap
        .services
        .notification_service
        .fetch_customer_for_account(FetchForAccountInput { account_id })
        .await
        .expect("Successfully fetched customer notifications");

    for n in customer_notifications.clone() {
        let expected = notifications
            .get(&n.unique_id)
            .expect("Notification for UniqueId exists in Hashmap")
            .to_owned();
        assert_eq!(n.account_id, expected.account_id);
        assert_eq!(n.touchpoint, expected.touchpoint);
    }

    let pending_notification = bootstrap
        .services
        .notification_service
        .fetch_pending(FetchForCompositeKeyInput {
            composite_key: customer_notifications.first().unwrap().composite_key(),
        })
        .await
        .expect("Fetch pending notification");
    assert!(pending_notification.is_some());

    bootstrap
        .services
        .notification_service
        .update_delivery_status(UpdateDeliveryStatusInput {
            composite_key: customer_notifications.first().unwrap().composite_key(),
            status: DeliveryStatus::Completed,
        })
        .await
        .expect("Update delivery status to Completed");

    let nonspending_notification = bootstrap
        .services
        .notification_service
        .fetch_pending(FetchForCompositeKeyInput {
            composite_key: customer_notifications.first().unwrap().composite_key(),
        })
        .await
        .expect("Successfully fetch pending notification for composite key");
    assert!(nonspending_notification.is_none());
}

#[tokio::test]
async fn service_send_notifications_test() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let account_id = account.id;

    let active_phone_touchpoint_id =
        create_phone_touchpoint(&bootstrap.services, &account_id, true).await;
    let inactive_phone_touchpoint_id =
        create_phone_touchpoint(&bootstrap.services, &account_id, false).await;
    create_email_touchpoint(&bootstrap.services, &account_id, true).await;

    let payload = &NotificationPayloadBuilder::default()
        .comms_verification_payload(Some(CommsVerificationPayload {
            account_id: account_id.clone(),
            code: "123456".to_owned(),
            template_type: TemplateType::Onboarding,
        }))
        .build()
        .unwrap();

    bootstrap
        .services
        .notification_service
        .send_notification(SendNotificationInput {
            account_id: &account_id,
            payload_type: NotificationPayloadType::CommsVerification,
            payload,
            only_touchpoints: None,
        })
        .await
        .unwrap();

    // Should have sent to 2 active touchpoints
    let enqueued_messages = bootstrap.services.sqs.fetch_messages("").await.unwrap();
    assert_eq!(enqueued_messages.len(), 2);
    bootstrap
        .services
        .sqs
        .update_test_messages(vec![])
        .await
        .unwrap();

    bootstrap
        .services
        .notification_service
        .send_notification(SendNotificationInput {
            account_id: &account_id,
            payload_type: NotificationPayloadType::CommsVerification,
            payload,
            only_touchpoints: Some(HashSet::from([NotificationTouchpoint::Phone {
                touchpoint_id: active_phone_touchpoint_id.clone(),
            }])),
        })
        .await
        .unwrap();

    // Should have sent to 1 active touchpoints
    let enqueued_messages = bootstrap.services.sqs.fetch_messages("").await.unwrap();
    assert_eq!(enqueued_messages.len(), 1);
    bootstrap
        .services
        .sqs
        .update_test_messages(vec![])
        .await
        .unwrap();

    bootstrap
        .services
        .notification_service
        .send_notification(SendNotificationInput {
            account_id: &account_id,
            payload_type: NotificationPayloadType::CommsVerification,
            payload,
            only_touchpoints: Some(HashSet::from([NotificationTouchpoint::Phone {
                touchpoint_id: inactive_phone_touchpoint_id.clone(),
            }])),
        })
        .await
        .unwrap();

    // Should have sent to 1 inactive touchpoints
    let enqueued_messages = bootstrap.services.sqs.fetch_messages("").await.unwrap();
    assert_eq!(enqueued_messages.len(), 1);
    bootstrap
        .services
        .sqs
        .update_test_messages(vec![])
        .await
        .unwrap();

    bootstrap
        .services
        .notification_service
        .send_notification(SendNotificationInput {
            account_id: &account_id,
            payload_type: NotificationPayloadType::CommsVerification,
            payload,
            only_touchpoints: Some(HashSet::from([
                NotificationTouchpoint::Phone {
                    touchpoint_id: active_phone_touchpoint_id,
                },
                NotificationTouchpoint::Phone {
                    touchpoint_id: inactive_phone_touchpoint_id,
                },
            ])),
        })
        .await
        .unwrap();

    // Should have sent to 1 active & 1 inactive touchpoints
    let enqueued_messages = bootstrap.services.sqs.fetch_messages("").await.unwrap();
    assert_eq!(enqueued_messages.len(), 2);
    bootstrap
        .services
        .sqs
        .update_test_messages(vec![])
        .await
        .unwrap();
}
