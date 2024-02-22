use crate::historical::HistoricalExchangeRateProvider;
use time::OffsetDateTime;
use types::currencies::CurrencyCode::USD;
use types::exchange_rate::coingecko::RateProvider as CoingeckoRateProvider;
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

    Mock::given(method("GET"))
        .and(path("/api/v3/coins/bitcoin/market_chart/range"))
        .and(query_param("vs_currency", "USD"))
        .and(query_param("from", "1706619600"))
        .and(query_param("to", "1706619900"))
        .and(query_param("x_cg_pro_api_key", "abc123"))
        .respond_with(ResponseTemplate::new(200).set_body_raw(response_body, "application/json"))
        .mount(&mock_server)
        .await;

    // Act
    let response = coingecko_provider
        .rate(
            &vec![USD],
            OffsetDateTime::from_unix_timestamp(1706619900).unwrap(),
        )
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
