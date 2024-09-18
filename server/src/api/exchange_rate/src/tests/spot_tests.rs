use crate::currency_conversion::SpotExchangeRateProvider;
use serde_json::json;
use types::currencies::CurrencyCode::{BTC, EUR};
use types::exchange_rate::cash::CashAppRateProvider;
use types::exchange_rate::coingecko::RateProvider as CoingeckoRateProvider;
use wiremock::matchers::{body_json, method, path, query_param};
use wiremock::{Mock, MockServer, ResponseTemplate};

#[tokio::test]
async fn test_cash_spot_success() {
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
async fn test_cash_spot_negative_change_cents() {
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

#[tokio::test]
async fn test_coingecko_spot_success() {
    let mock_server = MockServer::start().await;
    let coingecko_app_rate_provider = CoingeckoRateProvider {
        root_url: mock_server.uri(),
        api_key: "fake".to_string(),
    };
    let response_body = r#"{
        "bitcoin": {
            "eur": 67187.33,
            "eur_market_cap": 1317802988326.2493,
            "eur_24h_vol": 31260929299.52484,
            "eur_24h_change": 3.637278946773539,
            "last_updated_at": 1711356300
        }
    }"#;

    Mock::given(method("GET"))
        .and(path("/api/v3/simple/price"))
        .and(query_param("ids", "bitcoin"))
        .and(query_param("vs_currencies", "eur"))
        .and(query_param("x_cg_pro_api_key", "fake"))
        .respond_with(ResponseTemplate::new(200).set_body_raw(response_body, "application/json"))
        .mount(&mock_server)
        .await;

    // Act
    let response = coingecko_app_rate_provider.rate(&BTC, &EUR).await;
    let in_dollars = 6718733f64 / 100.0;

    // Assert
    match response {
        Ok(rate) => {
            assert_eq!(rate, in_dollars)
        }
        Err(_) => panic!("Request failed"),
    }
}
