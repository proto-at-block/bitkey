use http::StatusCode;
use onboarding::routes_v2::CreateAccountRequestV2;
use types::account::{
    bitcoin::Network,
    entities::v2::{FullAccountAuthKeysInputV2, SpendingKeysetInputV2},
};

use crate::tests::{
    gen_services,
    lib::{create_new_authkeys, create_pubkey},
    requests::axum::TestClient,
};

#[tokio::test]
async fn onboarding_test_with_idempotency() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (spending_app_pub, spending_hw_pub) = (create_pubkey(), create_pubkey());
    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
        },
        spend: SpendingKeysetInputV2 {
            network: Network::BitcoinSignet,
            app_pub: spending_app_pub,
            hardware_pub: spending_hw_pub,
        },
        is_test_account: true,
    };
    let actual_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
    let first_create_response = actual_response.body.unwrap();

    assert!(!first_create_response.server_pub_integrity_sig.is_empty());

    let actual_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
    assert_eq!(first_create_response, actual_response.body.unwrap());
}
