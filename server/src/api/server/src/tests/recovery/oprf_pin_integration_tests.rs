use crate::tests::gen_services;
use crate::tests::lib::{create_full_account, setup_noise_secure_channel};
use crate::tests::requests::axum::TestClient;
use http::StatusCode;
use recovery::routes::delay_notify::EvaluatePinRequest;
use types::account::bitcoin::Network;

#[tokio::test]
async fn test_oprf_evaluate_pin() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let customer_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;

    let (client_noise_context, noise_session) = setup_noise_secure_channel(&client).await;
    let sealed_request = {
        let pin_hash = r#"{"prf_input": "todo"}"#.as_bytes();
        client_noise_context.encrypt_message(pin_hash).unwrap()
    };

    let request = EvaluatePinRequest {
        sealed_request,
        noise_session: noise_session.clone(),
    };
    let actual_response = client
        .evaluate_pin(&customer_account.id.to_string(), &request)
        .await;
    assert_eq!(
        actual_response.status_code,
        StatusCode::OK,
        "{}",
        actual_response.body_string
    )
}
