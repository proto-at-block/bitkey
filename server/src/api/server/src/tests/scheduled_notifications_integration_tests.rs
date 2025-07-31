use account::service::AddPushTouchpointToAccountInput;
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
    DeliveryStatus, NotificationMessage, NotificationPayload, NotificationPayloadBuilder,
    NotificationPayloadType,
};
use queue::sqs::SqsQueue;
use rstest::rstest;
use time::{format_description::well_known::Rfc3339, Duration, OffsetDateTime};
use types::account::entities::{Factor, TouchpointPlatform};
use types::account::identifiers::AccountId;

use crate::{
    create_bootstrap,
    tests::{
        gen_services,
        lib::create_default_account_with_predefined_wallet,
        lib::{create_phone_touchpoint, create_push_touchpoint},
        requests::axum::TestClient,
        requests::worker::TestWorker,
    },
};

async fn fetch_scheduled_notifications_test(
    entries: Vec<(&'static str, &'static str, &'static str)>,
    account_id: AccountId,
    status: DeliveryStatus,
    cur_time: &'static str,
    expected_num: usize,
) {
    let payload = NotificationPayload {
        recovery_pending_delay_period_payload: Some(RecoveryPendingDelayPeriodPayload {
            initiation_time: OffsetDateTime::now_utc(),
            delay_end_time: OffsetDateTime::now_utc(),
            lost_factor: Factor::Hw,
        }),
        ..Default::default()
    };
    let notifications = entries
        .into_iter()
        .map(|i| {
            let (sharded_execution_date, execution_time, execution_date_time) = i;
            ScheduledNotification {
                account_id: account_id.clone(),
                unique_id: NotificationId::gen_scheduled(),
                sharded_execution_date: sharded_execution_date.to_string(),
                execution_time: execution_time.to_string(),
                execution_date_time: OffsetDateTime::parse(execution_date_time, &Rfc3339).unwrap(),
                payload_type: NotificationPayloadType::RecoveryPendingDelayPeriod,
                payload: payload.clone(),
                delivery_status: status,
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
            account_id: account_id.clone(),
            notifications,
        })
        .await
        .unwrap();

    let cur_time = OffsetDateTime::parse(cur_time, &Rfc3339).unwrap();
    let fetched_notifications = notification_service
        .fetch_scheduled_for_window(FetchScheduledForWindowInput {
            window_end_time: cur_time,
            duration: Duration::minutes(10),
        })
        .await
        .unwrap();
    assert_eq!(fetched_notifications.len(), expected_num);
}

#[rstest]
#[case::single_within_window(
    vec![("2022-12-18:0", "01:00:00:000000", "2022-12-18T01:00:00.000000Z")],
    AccountId::gen().unwrap(),
    DeliveryStatus::New,
    "2022-12-18T01:02:00.000000Z",
    1
)]
#[case::multiple_within_window(
    vec![("2022-11-18:0", "01:00:00:000000", "2022-11-18T01:00:00.000000Z"), ("2022-11-18:0", "01:01:00:000000", "2022-11-18T01:01:00.000000Z")],
    AccountId::gen().unwrap(),
    DeliveryStatus::New,
    "2022-11-18T01:02:00.000000Z",
    2
)]
#[case::multiple_outside_window(
    vec![("2022-10-18:0", "01:00:00:000000", "2022-10-18T01:00:00.000000Z"), ("2022-10-18:0", "01:01:00:000000", "2022-10-18T01:01:00.000000Z")],
    AccountId::gen().unwrap(),
    DeliveryStatus::New,
    "2022-10-18T02:00:00.000000Z",
    0
)]
#[case::multiple_one_within_window(
    vec![("2022-09-18:0", "01:00:00:000000", "2022-09-18T01:00:00.000000Z"), ("2022-09-18:0", "01:06:00:000000", "2022-09-18T01:06:00.000000Z")],
    AccountId::gen().unwrap(),
    DeliveryStatus::New,
    "2022-09-18T01:11:00.000000Z",
    1
)]
#[case::multiple_midnight_window(
    vec![("2022-08-17:0", "23:59:00:000000", "2022-08-17T23:59:00.000000Z"), ("2022-08-18:0", "00:01:00:000000", "2022-08-18T00:01:00.000000Z")],
    AccountId::gen().unwrap(),
    DeliveryStatus::New,
    "2022-08-18T00:02:00.000000Z",
    2
)]
#[case::processing_within_window(
    vec![("2022-07-17:0", "23:59:00:000000", "2022-07-17T23:59:00.000000Z"), ("2022-07-18:0", "00:01:00:000000", "2022-07-18T00:01:00.000000Z")],
    AccountId::gen().unwrap(),
    DeliveryStatus::Enqueued,
    "2022-07-18T00:02:00.000000Z",
    0
)]
#[case::completed_within_window(
    vec![("2022-06-17:0", "23:59:00:000000", "2022-06-17T23:59:00.000000Z"), ("2022-06-18:0", "00:01:00:000000", "2022-06-18T00:01:00.000000Z")],
    AccountId::gen().unwrap(),
    DeliveryStatus::Completed,
    "2022-06-18T00:02:00.000000Z",
    0
)]
#[tokio::test]
async fn test_fetch_scheduled_notifications(
    #[case] entries: Vec<(&'static str, &'static str, &'static str)>,
    #[case] account_id: AccountId,
    #[case] status: DeliveryStatus,
    #[case] cur_time: &'static str,
    #[case] expected_num: usize,
) {
    fetch_scheduled_notifications_test(entries, account_id, status, cur_time, expected_num).await
}

#[tokio::test]
async fn test_scheduled_handler() {
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
    // Create multiple touchpoints
    create_push_touchpoint(&bootstrap.services, &account_id).await;
    create_phone_touchpoint(&bootstrap.services, &account_id, true).await;
    // Create a second push touchpoint
    bootstrap
        .services
        .account_service
        .add_push_touchpoint_for_account(AddPushTouchpointToAccountInput {
            account_id: &account_id,
            use_local_sns: true,
            platform: TouchpointPlatform::ApnsTeam,
            device_token: "test".to_string(),
            access_token: Default::default(),
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
        .fetch_customer_for_account(FetchForAccountInput {
            account_id: account_id.clone(),
        })
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
        let num_customer_messages = messages
            .lock()
            .unwrap()
            .clone()
            .iter()
            .filter(|m| {
                let message =
                    serde_json::from_str::<NotificationMessage>(m.body.as_ref().unwrap()).unwrap();
                message.account_id == account_id
            })
            .count();
        assert_eq!(num_customer_messages, 4);
    } else {
        panic!("Expected sqs queue to be in test mode");
    }
}
