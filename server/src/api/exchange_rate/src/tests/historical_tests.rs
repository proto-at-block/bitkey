use crate::historical::HistoricalExchangeRateProvider;
use time::{Duration, OffsetDateTime};
use types::currencies::CurrencyCode::USD;
use types::exchange_rate::coingecko::{
    RateProvider as CoingeckoRateProvider, HISTORICAL_RATE_INTERVAL,
    HISTORICAL_RATE_REQUEST_WINDOW_DURATION,
};
use wiremock::matchers::{method, path, query_param};
use wiremock::{Mock, MockServer, ResponseTemplate};

#[tokio::test]
async fn test_historical_rate_success() {
    let mock_server = MockServer::start().await;
    let coingecko_provider = CoingeckoRateProvider {
        root_url: mock_server.uri(),
        api_key: "abc123".to_string(),
    };
    let response_body = r#"{
            "prices": [
                [
                    1706619663330,
                    43372.69361415296
                ]
            ],
            "market_caps": [
                [
                    1706619663330,
                    850677314393.1049
                ]
            ],
            "total_volumes": [
                [
                    1706619663330,
                    24615439502.524292
                ]
            ]
        }"#;

    let from_epoch = Duration::seconds(1706619600);
    let to_epoch = from_epoch + HISTORICAL_RATE_REQUEST_WINDOW_DURATION;

    Mock::given(method("GET"))
        .and(path("/api/v3/coins/bitcoin/market_chart/range"))
        .and(query_param("vs_currency", "USD"))
        .and(query_param("from", from_epoch.as_seconds_f32().to_string()))
        .and(query_param("to", to_epoch.as_seconds_f32().to_string()))
        .and(query_param("x_cg_pro_api_key", "abc123"))
        .and(query_param(
            "interval",
            HISTORICAL_RATE_INTERVAL.to_string(),
        ))
        .respond_with(ResponseTemplate::new(200).set_body_raw(response_body, "application/json"))
        .mount(&mock_server)
        .await;

    // Act
    let response = coingecko_provider
        .rate(&[USD], OffsetDateTime::UNIX_EPOCH + to_epoch)
        .await;

    // Assert
    match response {
        Ok(rates) => {
            assert_eq!(rates.len(), 1);
            assert_eq!(rates.get("USD").unwrap(), &43372.69361415296);
        }
        Err(_) => panic!("Request failed"),
    }
}
