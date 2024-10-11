use http::StatusCode;
use types::currencies::Currency;

use crate::tests::gen_services;
use crate::tests::requests::axum::TestClient;

#[tokio::test]
async fn test_get_currency_definitions() {
    let (_, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let response = client.get_supported_fiat_currencies().await;
    assert_eq!(response.status_code, StatusCode::OK);

    let currency_text_codes = response
        .body
        .unwrap()
        .supported_currencies
        .iter()
        .map(|c| c.currency.text_code.clone())
        .collect::<Vec<String>>();
    Currency::supported_fiat_currencies()
        .iter()
        .for_each(|c| assert!(currency_text_codes.contains(&c.currency.text_code)));
}
