use account::{
    entities::{Factor, TouchpointPlatform},
    service::AddPushTouchpointToAccountInput,
};
use notification::{
    entities::{NotificationTouchpoint, ScheduledNotification},
    identifiers::NotificationId,
    payloads::{
        recovery_pending_delay_period::RecoveryPendingDelayPeriodPayload,
        test_notification::TestNotificationPayload,
    },
    schedule::ScheduleNotificationType,
    service::{
        FetchForAccountInput, FetchScheduledForWindowInput, PersistScheduledNotificationsInput,
        ScheduleNotificationsInput,
    },
    DeliveryStatus, NotificationPayload, NotificationPayloadBuilder, NotificationPayloadType,
};
use queue::sqs::SqsQueue;
use time::{format_description::well_known::Rfc3339, Duration, OffsetDateTime};
use types::account::identifiers::AccountId;

use crate::{
    create_bootstrap, tests,
    tests::{
        gen_services, lib::create_default_account_with_predefined_wallet,
        requests::axum::TestClient,
    },
    tests::{
        lib::{create_phone_touchpoint, create_push_touchpoint},
        requests::worker::TestWorker,
    },
};

#[derive(Debug)]
pub struct FetchScheduledNotificationsTestVector {
    pub entries: Vec<(&'static str, &'static str, &'static str)>,
    pub account_id: AccountId,
    pub status: DeliveryStatus,
    pub cur_time: &'static str,
    pub expected_num: usize,
}

async fn fetch_scheduled_notifications_test(input: FetchScheduledNotificationsTestVector) {
    let payload = NotificationPayload {
        recovery_pending_delay_period_payload: Some(RecoveryPendingDelayPeriodPayload {
            initiation_time: OffsetDateTime::now_utc(),
            delay_end_time: OffsetDateTime::now_utc(),
            lost_factor: Factor::Hw,
        }),
        ..Default::default()
    };
    let notifications = input
        .entries
        .into_iter()
        .map(|i| {
            let (sharded_execution_date, execution_time, execution_date_time) = i;
            ScheduledNotification {
                account_id: input.account_id.clone(),
                unique_id: NotificationId::gen_scheduled(),
                sharded_execution_date: sharded_execution_date.to_string(),
                execution_time: execution_time.to_string(),
                execution_date_time: OffsetDateTime::parse(execution_date_time, &Rfc3339).unwrap(),
                payload_type: NotificationPayloadType::RecoveryPendingDelayPeriod,
                payload: payload.clone(),
                delivery_status: input.status,
                created_at: OffsetDateTime::now_utc(),
                updated_at: OffsetDateTime::now_utc(),
                schedule: None,
            }
        })
        .collect::<Vec<ScheduledNotification>>();

    let bootstrap = create_bootstrap(Some("test")).await.unwrap();
    let notification_service = bootstrap.services.notification_service;

    notification_service
        .persist_scheduled_notifications(PersistScheduledNotificationsInput {
            account_id: input.account_id.clone(),
            notifications,
        })
        .await
        .unwrap();

    let cur_time = OffsetDateTime::parse(input.cur_time, &Rfc3339).unwrap();
    let fetched_notifications = notification_service
        .fetch_scheduled_for_window(FetchScheduledForWindowInput {
            window_end_time: cur_time,
            duration: Duration::minutes(10),
        })
        .await
        .unwrap();
    assert_eq!(fetched_notifications.len(), input.expected_num);
}

tests! {
    runner = fetch_scheduled_notifications_test,
    test_single_notification_within_window: FetchScheduledNotificationsTestVector {
        entries: vec![("2022-12-18:0", "01:00:00:000000", "2022-12-18T01:00:00.000000Z")],
        account_id: AccountId::gen().unwrap(),
        status: DeliveryStatus::New,
        cur_time: "2022-12-18T01:02:00.000000Z",
        expected_num: 1,
    },
    test_multiple_notification_within_window: FetchScheduledNotificationsTestVector {
        entries: vec![("2022-12-18:0", "01:00:00:000000", "2022-12-18T01:00:00.000000Z"), ("2022-12-18:0", "01:01:00:000000", "2022-12-18T01:01:00.000000Z")],
        account_id: AccountId::gen().unwrap(),
        status: DeliveryStatus::New,
        cur_time: "2022-12-18T01:02:00.000000Z",
        expected_num: 2,
    },
    test_multiple_notification_outside_window: FetchScheduledNotificationsTestVector {
        entries: vec![("2022-12-18:0", "01:00:00:000000", "2022-12-18T01:00:00.000000Z"), ("2022-12-18:0", "01:01:00:000000", "2022-12-18T01:01:00.000000Z")],
        account_id: AccountId::gen().unwrap(),
        status: DeliveryStatus::New,
        cur_time: "2022-12-18T02:00:00.000000Z",
        expected_num: 0,
    },
    test_multiple_notification_one_within_window: FetchScheduledNotificationsTestVector {
        entries: vec![("2022-12-18:0", "01:00:00:000000", "2022-12-18T01:00:00.000000Z"), ("2022-12-18:0", "01:06:00:000000", "2022-12-18T01:06:00.000000Z")],
        account_id: AccountId::gen().unwrap(),
        status: DeliveryStatus::New,
        cur_time: "2022-12-18T01:11:00.000000Z",
        expected_num: 1,
    },
    test_multiple_notification_midnight_window: FetchScheduledNotificationsTestVector {
        entries: vec![("2022-12-17:0", "23:59:00:000000", "2022-12-17T23:59:00.000000Z"), ("2022-12-18:0", "00:01:00:000000", "2022-12-18T00:01:00.000000Z")],
        account_id: AccountId::gen().unwrap(),
        status: DeliveryStatus::New,
        cur_time: "2022-12-18T00:02:00.000000Z",
        expected_num: 2,
    },
    test_processing_notifications_within_window: FetchScheduledNotificationsTestVector {
        entries: vec![("2022-12-17:0", "23:59:00:000000", "2022-12-17T23:59:00.000000Z"), ("2022-12-18:0", "00:01:00:000000", "2022-12-18T00:01:00.000000Z")],
        account_id: AccountId::gen().unwrap(),
        status: DeliveryStatus::Enqueued,
        cur_time: "2022-12-18T00:02:00.000000Z",
        expected_num: 0,
    },
    test_completed_notifications_within_window: FetchScheduledNotificationsTestVector {
        entries: vec![("2022-12-17:0", "23:59:00:000000", "2022-12-17T23:59:00.000000Z"), ("2022-12-18:0", "00:01:00:000000", "2022-12-18T00:01:00.000000Z")],
        account_id: AccountId::gen().unwrap(),
        status: DeliveryStatus::Completed,
        cur_time: "2022-12-18T00:02:00.000000Z",
        expected_num: 0,
    },
}

#[tokio::test]
async fn test_scheduled_handler() {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router.clone()).await;
    let state = workers::jobs::WorkerState {
        config: http_server::config::extract(None).unwrap(),
        notification_service: bootstrap.services.notification_service.clone(),
        account_service: bootstrap.services.account_service.clone(),
        recovery_service: bootstrap.services.recovery_service.clone(),
        chain_indexer_service: bootstrap.services.chain_indexer_service.clone(),
        address_repo: bootstrap.services.address_repo.clone(),
        sqs: bootstrap.services.sqs.clone(),
        feature_flags_service: bootstrap.services.feature_flags_service.clone(),
    };
    let worker = TestWorker::new(state.clone()).await;

    let (account, _) =
        create_default_account_with_predefined_wallet(&client, &bootstrap.services).await;
    let account_id = account.id;
    // Create multiple touchpoints
    create_push_touchpoint(&bootstrap.services, &account_id).await;
    create_phone_touchpoint(&bootstrap.services, &account_id, true).await;
    // Create a second push touchpoint
    bootstrap
        .services
        .account_service
        .add_push_touchpoint_for_account(AddPushTouchpointToAccountInput {
            account_id: account_id.clone(),
            use_local_sns: true,
            platform: TouchpointPlatform::ApnsTeam,
            device_token: "test".to_string(),
        })
        .await
        .unwrap();

    let payload = NotificationPayloadBuilder::default()
        .test_notification_payload(Some(TestNotificationPayload::default()))
        .build()
        .unwrap();
    let _ = bootstrap
        .services
        .notification_service
        .schedule_notifications(ScheduleNotificationsInput {
            account_id: account_id.clone(),
            notification_type: ScheduleNotificationType::TestPushNotification,
            payload,
        })
        .await;

    let notifications = bootstrap
        .services
        .notification_service
        .fetch_scheduled_for_account(FetchForAccountInput {
            account_id: account_id.clone(),
        })
        .await
        .unwrap();

    // The first one gets sent immediately (1x2 touchpoints = 2 customer/queued) and rescheduled (1 scheduled in NEW status)
    assert_eq!(notifications.len(), 1);
    assert!(notifications
        .into_iter()
        .all(|n| n.delivery_status == DeliveryStatus::New));

    worker.scheduled_notification().await;

    let scheduled_notifications = bootstrap
        .services
        .notification_service
        .fetch_scheduled_for_account(FetchForAccountInput {
            account_id: account_id.clone(),
        })
        .await
        .unwrap();

    // Gets sent (2x2 touchpoints = 4 customer/queued, 1 scheduled in COMPLETED status) and rescheduled (1 scheduled in NEW status)
    assert_eq!(scheduled_notifications.len(), 2);
    assert_eq!(
        scheduled_notifications
            .iter()
            .filter(|n| n.delivery_status == DeliveryStatus::New)
            .count(),
        1
    );
    assert_eq!(
        scheduled_notifications
            .iter()
            .filter(|n| n.delivery_status == DeliveryStatus::Completed)
            .count(),
        1
    );

    let customer_notifications = bootstrap
        .services
        .notification_service
        .fetch_customer_for_account(FetchForAccountInput { account_id })
        .await
        .unwrap();
    assert_eq!(customer_notifications.len(), 4);
    assert!(customer_notifications
        .iter()
        .all(|n| n.delivery_status == DeliveryStatus::Enqueued));
    assert!(customer_notifications
        .into_iter()
        .all(|n| matches!(n.touchpoint, NotificationTouchpoint::Push { .. })));

    if let SqsQueue::Test(messages) = bootstrap.services.sqs {
        assert_eq!(messages.lock().unwrap().len(), 4);
    } else {
        panic!("Expected sqs queue to be in test mode");
    }
}
