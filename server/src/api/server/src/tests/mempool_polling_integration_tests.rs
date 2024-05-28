use crate::tests::{
    gen_services,
    lib::{create_account, create_default_account_with_predefined_wallet},
    requests::{axum::TestClient, worker::TestWorker},
};
use account::entities::FullAccount;
use account::{entities::TouchpointPlatform, service::AddPushTouchpointToAccountInput};
use bdk_utils::bdk::bitcoin::Txid;
use httpmock::prelude::*;
use mempool_indexer::service::Service as MempoolIndexerService;
use notification::address_repo::AddressAndKeysetId;
use notification::service::FetchForAccountInput;
use notification::service::Service as NotificationService;
use notification::NotificationPayloadType;
use serde_json::json;
use std::{
    collections::{HashMap, HashSet},
    str::FromStr,
};
use types::{
    account::identifiers::{AccountId, KeysetId},
    notification::{NotificationChannel, NotificationsPreferences},
};

struct MempoolPollingMockData<'a> {
    mock_server: &'a MockServer,
    worker: &'a TestWorker,
    current_mempool_txids: Vec<&'a str>,
    all_txids: Vec<&'a str>,
    expected_tx_hits: Vec<&'a str>,
}

async fn run_and_test_mempool_polling(data: MempoolPollingMockData<'_>) {
    let mut mempool_tx_ids_mock = data.mock_server.mock(|when, then| {
        when.method(GET).path("/mempool/txids");
        then.status(200)
            .header("content-type", "application/json")
            .json_body(json!(data.current_mempool_txids));
    });

    let mut tx_mocks = HashMap::new();
    for tx_id in data.all_txids {
        let tx_mock = data.mock_server.mock(|when, then| {
            when.method(GET).path(format!("/tx/{}", tx_id));
            then.status(200)
                .header("content-type", "document")
                .body_from_file(format!("src/tests/raw_txs/{}.json", tx_id));
        });
        tx_mocks.insert(tx_id, tx_mock);
    }

    data.worker.mempool_polling().await;

    // Assert hits on the endpoints
    mempool_tx_ids_mock.assert_hits(1);
    mempool_tx_ids_mock.delete();

    tx_mocks.into_iter().for_each(|(tx_id, mut tx_mock)| {
        tx_mock.assert_hits(if data.expected_tx_hits.contains(&tx_id) {
            1
        } else {
            0
        });
        tx_mock.delete()
    });
}

async fn assert_indexer_recorded_ids_includes(
    indexer_service: &MempoolIndexerService,
    expected_txids: Vec<&str>,
) {
    let expected_hashset = expected_txids
        .iter()
        .map(|txid| Txid::from_str(txid).unwrap())
        .collect::<HashSet<Txid>>();
    assert!(
        expected_hashset
            .difference(&indexer_service.get_recorded_tx_ids().await)
            .count()
            == 0
    );
}

#[tokio::test]
async fn test_init_mempool_received_pending_payment() {
    let (mock_server, account, worker, notification_service, indexer_service) =
        setup_full_accounts_and_server(vec![
            "tb1qtssct2napwu7n8djqkhpzserhdjhfqujv2jv8fp4wz6dzwzrhghshckgeu",
            "tb1qke2zj0a25exlkvrdzku2und773uzzu4lak6enlmgtp2xw8umvhqqrz5pct",
        ])
        .await;

    // Mempool only has one transaction addressed to customer
    run_and_test_mempool_polling(MempoolPollingMockData {
        mock_server: &mock_server,
        worker: &worker,
        current_mempool_txids: vec![
            "5b0b1233d4e6aa997b6e0bee6c05dca90bc465ae7903dc60ae821eff6fc94636",
        ],
        all_txids: vec![
            "812bbb253849545891adb727b2016ff1acf077210a21be3ce01a86dd88ac2305",
            "17ce57b054f39f0700ba918201857de5e1669791c6e2bdaca4d1362954909f36",
            "384572d9caa3488cb2aad1b10ffe1aec995f50ee9898e50cec61e78076b44414",
            "5b0b1233d4e6aa997b6e0bee6c05dca90bc465ae7903dc60ae821eff6fc94636",
            "fa60c61a1492a74e2d83a6121e0c17396ec89ff2f286ce55787024c93bf45452",
        ],
        expected_tx_hits: vec!["5b0b1233d4e6aa997b6e0bee6c05dca90bc465ae7903dc60ae821eff6fc94636"],
    })
    .await;
    // The account should have received 1 pending payment notification
    test_queue_message(&notification_service, &account.id, 1).await;
    assert_indexer_recorded_ids_includes(
        &indexer_service,
        vec!["5b0b1233d4e6aa997b6e0bee6c05dca90bc465ae7903dc60ae821eff6fc94636"],
    )
    .await;

    // Mempool only has one transaction not addressed to our customers
    run_and_test_mempool_polling(MempoolPollingMockData {
        mock_server: &mock_server,
        worker: &worker,
        current_mempool_txids: vec![
            "17ce57b054f39f0700ba918201857de5e1669791c6e2bdaca4d1362954909f36",
        ],
        all_txids: vec![
            "812bbb253849545891adb727b2016ff1acf077210a21be3ce01a86dd88ac2305",
            "17ce57b054f39f0700ba918201857de5e1669791c6e2bdaca4d1362954909f36",
            "384572d9caa3488cb2aad1b10ffe1aec995f50ee9898e50cec61e78076b44414",
            "5b0b1233d4e6aa997b6e0bee6c05dca90bc465ae7903dc60ae821eff6fc94636",
            "fa60c61a1492a74e2d83a6121e0c17396ec89ff2f286ce55787024c93bf45452",
        ],
        expected_tx_hits: vec!["17ce57b054f39f0700ba918201857de5e1669791c6e2bdaca4d1362954909f36"],
    })
    .await;
    // The account should have the same number of pending payment notifications
    test_queue_message(&notification_service, &account.id, 1).await;
    assert_indexer_recorded_ids_includes(
        &indexer_service,
        vec![
            "5b0b1233d4e6aa997b6e0bee6c05dca90bc465ae7903dc60ae821eff6fc94636",
            "17ce57b054f39f0700ba918201857de5e1669791c6e2bdaca4d1362954909f36",
        ],
    )
    .await;

    // Mempool only has one transaction not addressed to our customers
    run_and_test_mempool_polling(MempoolPollingMockData {
        mock_server: &mock_server,
        worker: &worker,
        current_mempool_txids: vec![
            "fa60c61a1492a74e2d83a6121e0c17396ec89ff2f286ce55787024c93bf45452",
        ],
        all_txids: vec![
            "812bbb253849545891adb727b2016ff1acf077210a21be3ce01a86dd88ac2305",
            "17ce57b054f39f0700ba918201857de5e1669791c6e2bdaca4d1362954909f36",
            "384572d9caa3488cb2aad1b10ffe1aec995f50ee9898e50cec61e78076b44414",
            "5b0b1233d4e6aa997b6e0bee6c05dca90bc465ae7903dc60ae821eff6fc94636",
            "fa60c61a1492a74e2d83a6121e0c17396ec89ff2f286ce55787024c93bf45452",
        ],
        expected_tx_hits: vec!["fa60c61a1492a74e2d83a6121e0c17396ec89ff2f286ce55787024c93bf45452"],
    })
    .await;
    // The account should no longer receive payment notifications
    test_queue_message(&notification_service, &account.id, 2).await;
    assert_indexer_recorded_ids_includes(
        &indexer_service,
        vec![
            "5b0b1233d4e6aa997b6e0bee6c05dca90bc465ae7903dc60ae821eff6fc94636",
            "17ce57b054f39f0700ba918201857de5e1669791c6e2bdaca4d1362954909f36",
            "fa60c61a1492a74e2d83a6121e0c17396ec89ff2f286ce55787024c93bf45452",
        ],
    )
    .await;
}

#[tokio::test]
async fn test_duplicates_in_mempool() {
    let (mock_server, account, worker, notification_service, indexer_service) =
        setup_full_accounts_and_server(vec![
            "tb1qkrw4dts90c7udh843r4qupq6helf8g7362dxtlkmv0lcv7ajr58qtqpefp",
        ])
        .await;

    run_and_test_mempool_polling(MempoolPollingMockData {
        mock_server: &mock_server,
        worker: &worker,
        current_mempool_txids: vec![
            "f355c0614e422796bb0cdd5e905c1f09316fd694d886263f5a0c23118ea17ea2",
        ],
        all_txids: vec!["f355c0614e422796bb0cdd5e905c1f09316fd694d886263f5a0c23118ea17ea2"],
        expected_tx_hits: vec!["f355c0614e422796bb0cdd5e905c1f09316fd694d886263f5a0c23118ea17ea2"],
    })
    .await;
    // The account should have received 1 pending payment notification
    test_queue_message(&notification_service, &account.id, 1).await;
    assert_indexer_recorded_ids_includes(
        &indexer_service,
        vec!["f355c0614e422796bb0cdd5e905c1f09316fd694d886263f5a0c23118ea17ea2"],
    )
    .await;

    run_and_test_mempool_polling(MempoolPollingMockData {
        mock_server: &mock_server,
        worker: &worker,
        current_mempool_txids: vec![
            "f355c0614e422796bb0cdd5e905c1f09316fd694d886263f5a0c23118ea17ea2",
        ],
        all_txids: vec!["f355c0614e422796bb0cdd5e905c1f09316fd694d886263f5a0c23118ea17ea2"],
        expected_tx_hits: vec![],
    })
    .await;
    // The account should not receive new pending payment notifications
    test_queue_message(&notification_service, &account.id, 1).await;
    assert_indexer_recorded_ids_includes(
        &indexer_service,
        vec!["f355c0614e422796bb0cdd5e905c1f09316fd694d886263f5a0c23118ea17ea2"],
    )
    .await;
}

#[tokio::test]
async fn test_tx_not_available_with_txid_in_mempool() {
    let (mock_server, account, worker, notification_service, indexer_service) =
        setup_full_accounts_and_server(vec![
            "tb1qtp00ma7wrq46ceeuwcapjayyq9s5ahnpw7qpvl25yp644lzd0fsq3aphsj",
        ])
        .await;

    // The transaction is in the mempool but not available to fetch
    run_and_test_mempool_polling(MempoolPollingMockData {
        mock_server: &mock_server,
        worker: &worker,
        current_mempool_txids: vec![
            "b294b9d5e156282e373e51358a6f61b3c839be73fefb8bc91bd561c2f5f142a0",
        ],
        all_txids: vec![],
        expected_tx_hits: vec![],
    })
    .await;
    // The account should not receive any pending payment notifications
    test_queue_message(&notification_service, &account.id, 0).await;

    // Now the transaction information is available to fetch and the customer should get 1 notification
    run_and_test_mempool_polling(MempoolPollingMockData {
        mock_server: &mock_server,
        worker: &worker,
        current_mempool_txids: vec![
            "b294b9d5e156282e373e51358a6f61b3c839be73fefb8bc91bd561c2f5f142a0",
        ],
        all_txids: vec!["b294b9d5e156282e373e51358a6f61b3c839be73fefb8bc91bd561c2f5f142a0"],
        expected_tx_hits: vec!["b294b9d5e156282e373e51358a6f61b3c839be73fefb8bc91bd561c2f5f142a0"],
    })
    .await;
    test_queue_message(&notification_service, &account.id, 1).await;
    assert_indexer_recorded_ids_includes(
        &indexer_service,
        vec!["b294b9d5e156282e373e51358a6f61b3c839be73fefb8bc91bd561c2f5f142a0"],
    )
    .await;
}

async fn setup_full_accounts_and_server(
    addresses: Vec<&str>,
) -> (
    MockServer,
    FullAccount,
    TestWorker,
    NotificationService,
    MempoolIndexerService,
) {
    let mock_server = MockServer::start();
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router.clone()).await;
    let (account_with_payment, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let account_with_payment_keys = context
        .get_authentication_keys_for_account_id(&account_with_payment.id)
        .expect("Keys for account with payment not found");
    let account_without_payment = create_account(
        &mut context,
        &bootstrap.services,
        account::entities::Network::BitcoinSignet,
        None,
    )
    .await;
    let account_without_payment_keys = context
        .get_authentication_keys_for_account_id(&account_without_payment.id)
        .expect("Keys for account without payment not found");
    let state = workers::jobs::WorkerState {
        config: http_server::config::extract(None).unwrap(),
        notification_service: bootstrap.services.notification_service.clone(),
        account_service: bootstrap.services.account_service.clone(),
        recovery_service: bootstrap.services.recovery_service.clone(),
        address_repo: bootstrap.services.address_repo.clone(),
        chain_indexer_service: bootstrap.services.chain_indexer_service.clone(),
        mempool_indexer_service: bootstrap
            .services
            .mempool_indexer_service
            .clone()
            .set_mock_server(mock_server.base_url()),
        sqs: bootstrap.services.sqs.clone(),
        feature_flags_service: bootstrap.services.feature_flags_service.clone(),
    };
    state
        .account_service
        .add_push_touchpoint_for_account(AddPushTouchpointToAccountInput {
            account_id: account_with_payment.id.clone(),
            use_local_sns: true,
            platform: TouchpointPlatform::ApnsTeam,
            device_token: "test".to_string(),
            access_token: Default::default(),
        })
        .await
        .unwrap();

    client
        .set_notifications_preferences(
            &account_with_payment.id.to_string(),
            &NotificationsPreferences {
                account_security: HashSet::default(),
                money_movement: HashSet::from([NotificationChannel::Push]),
                product_marketing: HashSet::new(),
            },
            false,
            false,
            &account_with_payment_keys,
        )
        .await;
    state
        .account_service
        .add_push_touchpoint_for_account(AddPushTouchpointToAccountInput {
            account_id: account_without_payment.id.clone(),
            use_local_sns: true,
            platform: TouchpointPlatform::ApnsTeam,
            device_token: "test".to_string(),
            access_token: Default::default(),
        })
        .await
        .unwrap();
    client
        .set_notifications_preferences(
            &account_without_payment.id.to_string(),
            &NotificationsPreferences {
                account_security: HashSet::default(),
                money_movement: HashSet::from([NotificationChannel::Push]),
                product_marketing: HashSet::new(),
            },
            false,
            false,
            &account_without_payment_keys,
        )
        .await;

    // Add destination address from mempool to watchlist table.
    // Note, these addresses aren't actually derivable from the account's bdk wallet.
    let fake_registrations = addresses
        .into_iter()
        .map(|address| AddressAndKeysetId::new(address.parse().unwrap(), KeysetId::gen().unwrap()))
        .collect::<Vec<AddressAndKeysetId>>();

    bootstrap
        .services
        .address_repo
        .clone()
        .insert(&fake_registrations, &account_with_payment.id)
        .await
        .unwrap();

    let worker = TestWorker::new(state).await;

    assert!(bootstrap
        .services
        .mempool_indexer_service
        .get_recorded_tx_ids()
        .await
        .is_empty());
    (
        mock_server,
        account_with_payment,
        worker,
        bootstrap.services.notification_service,
        bootstrap.services.mempool_indexer_service,
    )
}

async fn test_queue_message(
    notification_service: &NotificationService,
    account_id: &AccountId,
    aggregate_message_count: usize,
) {
    let scheduled_notifications = notification_service
        .fetch_scheduled_for_account(FetchForAccountInput {
            account_id: account_id.clone(),
        })
        .await
        .unwrap();
    assert_eq!(
        scheduled_notifications.len(),
        0,
        "{:?}",
        scheduled_notifications
    );

    let notifications = notification_service
        .fetch_customer_for_account(FetchForAccountInput {
            account_id: account_id.clone(),
        })
        .await
        .unwrap();

    assert_eq!(
        notifications.len(),
        aggregate_message_count,
        "{:?}",
        notifications
    );
    assert!(
        notifications
            .iter()
            .all(|n| n.payload_type == NotificationPayloadType::PendingPaymentNotification),
        "{:?}",
        notifications
    );
    assert!(
        notifications.iter().all(|n| n.account_id == *account_id),
        "{:?}",
        notifications
    );
}
