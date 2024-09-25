use account::entities::FullAccount;

use http::StatusCode;
use onboarding::routes::RotateSpendingKeysetRequest;

use time::OffsetDateTime;

use crate::tests::gen_services;
use crate::tests::lib::create_inactive_spending_keyset_for_account;
use crate::tests::requests::axum::TestClient;
use crate::Bootstrap;

use super::lib::create_default_account_with_predefined_wallet;
use super::TestContext;

#[tokio::test]
async fn test_get_account_descriptors_single() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let (account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let _keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .expect("Invalid keys for account");

    let response = client
        .get_account_descriptors(&account.id.to_string())
        .await;
    assert_eq!(response.status_code, StatusCode::OK);

    let response_body = response.body.unwrap();
    assert_eq!(response_body.inactive_descriptors.len(), 0)
}

#[tokio::test]
async fn test_get_account_descriptors_after_sweep() {
    let (mut context, bootstrap) = gen_services().await;
    let delay_end_time = bootstrap.services.recovery_service.cur_time();
    let client = TestClient::new(bootstrap.clone().router).await;

    let (account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let _keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .expect("Invalid keys for account");

    rotate_spending_keyset(&context, &account, &bootstrap, &client, delay_end_time).await;

    let response = client
        .get_account_descriptors(&account.id.to_string())
        .await;
    assert_eq!(response.status_code, StatusCode::OK);

    let response_body = response.body.unwrap();
    assert_eq!(response_body.inactive_descriptors.len(), 1)
}

async fn rotate_spending_keyset(
    context: &TestContext,
    account: &FullAccount,
    _bootstrap: &Bootstrap,
    client: &TestClient,
    _delay_end_time: OffsetDateTime,
) {
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .expect("Auth keys not found for account");
    let new_keyset_id = create_inactive_spending_keyset_for_account(
        context,
        client,
        &account.id,
        types::account::bitcoin::Network::BitcoinSignet,
    )
    .await;

    client
        .rotate_to_spending_keyset(
            &account.id.to_string(),
            &new_keyset_id.to_string(),
            &RotateSpendingKeysetRequest {},
            &keys,
        )
        .await;
}
