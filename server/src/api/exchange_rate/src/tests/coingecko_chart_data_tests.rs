use crate::chart::ExchangeRateChartProvider;
use types::currencies::CurrencyCode::USD;
use types::exchange_rate::coingecko::RateProvider as CoingeckoRateProvider;
use wiremock::matchers::{method, path, query_param};
use wiremock::{Mock, MockServer, ResponseTemplate};

#[tokio::test]
async fn test_chart_data_success_with_limited_price_points() {
    // arrange
    let mock_server = MockServer::start().await;
    let coingecko_provider = CoingeckoRateProvider {
        root_url: mock_server.uri(),
        api_key: "abc123".to_string(),
    };
    let response_body = r#"{
        "prices": [
            [1706619600000, 43300.0],
            [1706619660000, 43350.0],
            [1706619720000, 43400.0],
            [1706619780000, 43450.0],
            [1706619840000, 43500.0],
            [1706619900000, 43550.0],
            [1706619960000, 43600.0]
        ]
    }"#
    .to_string();
    Mock::given(method("GET"))
        .and(path("/api/v3/coins/bitcoin/market_chart"))
        .and(query_param("vs_currency", "USD"))
        .and(query_param("days", "1"))
        .and(query_param("x_cg_pro_api_key", "abc123"))
        .respond_with(ResponseTemplate::new(200).set_body_raw(response_body, "application/json"))
        .mount(&mock_server)
        .await;

    // act
    let max_price_points = 5;
    let response = coingecko_provider
        .chart_data(&USD, 1, max_price_points)
        .await
        .unwrap();

    // assert
    assert_eq!(response.len(), max_price_points);
    assert_eq!(response[0].price, 43300.0);
    assert_eq!(response[1].price, 43400.0);
    assert_eq!(response[2].price, 43500.0);
    assert_eq!(response[3].price, 43600.0);
}

#[tokio::test]
async fn test_chart_data_success_with_more_price_points_requested_than_available() {
    // arrange
    let mock_server = MockServer::start().await;
    let coingecko_provider = CoingeckoRateProvider {
        root_url: mock_server.uri(),
        api_key: "abc123".to_string(),
    };
    let response_body = r#"{
        "prices": [
            [1706619600000, 43300.0],
            [1706619660000, 43350.0],
            [1706619720000, 43400.0]
        ]
    }"#
    .to_string();
    Mock::given(method("GET"))
        .and(path("/api/v3/coins/bitcoin/market_chart"))
        .and(query_param("vs_currency", "USD"))
        .and(query_param("days", "1"))
        .and(query_param("x_cg_pro_api_key", "abc123"))
        .respond_with(ResponseTemplate::new(200).set_body_raw(response_body, "application/json"))
        .mount(&mock_server)
        .await;

    // act
    let number_of_price_points = 5; // Requesting more points than available
    let response = coingecko_provider
        .chart_data(&USD, 1, number_of_price_points)
        .await
        .unwrap();

    // assert
    assert!(response.len() <= number_of_price_points);
}

#[tokio::test]
async fn test_chart_data_rate_limit() {
    // arrange
    let mock_server = MockServer::start().await;
    let coingecko_provider = CoingeckoRateProvider {
        root_url: mock_server.uri(),
        api_key: "abc123".to_string(),
    };
    Mock::given(method("GET"))
        .and(path("/api/v3/coins/bitcoin/market_chart"))
        .and(query_param("vs_currency", "USD"))
        .and(query_param("days", "1"))
        .and(query_param("x_cg_pro_api_key", "abc123"))
        .respond_with(ResponseTemplate::new(429))
        .mount(&mock_server)
        .await;

    // act
    let response = coingecko_provider.chart_data(&USD, 1, 1).await;

    // assert
    assert!(response.is_err());
    assert_eq!(
        response.unwrap_err().to_string(),
        "Could not retrieve rates from Coingecko due to rate limits"
    );
}
