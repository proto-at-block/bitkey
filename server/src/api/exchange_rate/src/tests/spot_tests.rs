use crate::currency_conversion::SpotExchangeRateProvider;
use serde_json::json;
use types::currencies::CurrencyCode::{BTC, EUR};
use types::exchange_rate::cash::CashAppRateProvider;
use wiremock::matchers::{body_json, method, path};
use wiremock::{Mock, MockServer, ResponseTemplate};

#[tokio::test]
async fn test_spot_success() {
    let mock_server = MockServer::start().await;
    let cash_app_rate_provider = CashAppRateProvider {
        root_url: mock_server.uri(),
    };
    let response_body = r#"{
    "exchange_data": {
        "base_currency_code": "EUR",
        "rates": [
            {
                "change_cents": 191105,
                "base_value_cents": 5745976,
                "currency_code": "BTC"
            }
        ]
    }
    }"#;

    Mock::given(method("POST"))
        .and(path(""))
        .and(body_json(json!({ "quote_currency_code": "EUR" })))
        .respond_with(ResponseTemplate::new(200).set_body_raw(response_body, "application/json"))
        .mount(&mock_server)
        .await;

    // Act
    let response = cash_app_rate_provider.rate(&BTC, &EUR).await;
    let in_dollars = 5745976f64 / 100.0;

    // Assert
    match response {
        Ok(rate) => {
            assert_eq!(rate, in_dollars)
        }
        Err(_) => panic!("Request failed"),
    }
}

#[tokio::test]
async fn test_spot_negative_change_cents() {
    let mock_server = MockServer::start().await;
    let cash_app_rate_provider = CashAppRateProvider {
        root_url: mock_server.uri(),
    };
    let response_body = r#"{
    "exchange_data": {
        "base_currency_code": "EUR",
        "rates": [
            {
                "change_cents": -25258,
                "base_value_cents": 5697980,
                "currency_code": "BTC"
            }
        ]
    }
    }"#;

    Mock::given(method("POST"))
        .and(path(""))
        .and(body_json(json!({ "quote_currency_code": "EUR" })))
        .respond_with(ResponseTemplate::new(200).set_body_raw(response_body, "application/json"))
        .mount(&mock_server)
        .await;

    // Act
    let response = cash_app_rate_provider.rate(&BTC, &EUR).await;

    // Assert
    match response {
        Ok(_) => (),
        Err(_) => panic!("Request failed"),
    }
}
