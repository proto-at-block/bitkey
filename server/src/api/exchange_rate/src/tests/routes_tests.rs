use crate::routes::RouteState;
use crate::service::{cache_ttl, ChartCacheKey, Service as ExchangeRateService};
use axum::body::Body;
use axum::http::Request;
use feature_flags::config::Config;
use http_body_util::BodyExt;
use moka::future::{Cache, CacheBuilder};
use serde_json::Value;
use std::collections::HashMap;
use time::OffsetDateTime;
use tower::ServiceExt;
use types::currencies::CurrencyCode;
use types::currencies::CurrencyCode::USD;
use types::exchange_rate::{ExchangeRateChartData, PriceAt};

async fn setup_environment() {
    std::env::set_var("COINGECKO_API_KEY", "fake-api-key");
}

async fn initialize_and_populate_cache(
    days: u16,
    currency: CurrencyCode,
    data: ExchangeRateChartData,
) -> Cache<ChartCacheKey, ExchangeRateChartData> {
    let cache = CacheBuilder::new(1).build();
    cache
        .insert(ChartCacheKey::new(currency, days, 1), data)
        .await;
    cache
}

async fn create_exchange_rate_service_with_caches() -> ExchangeRateService {
    let days = 1;
    let day_cache_data = ExchangeRateChartData {
        from_currency: USD,
        to_currency: USD,
        exchange_rates: vec![PriceAt {
            timestamp: OffsetDateTime::from_unix_timestamp(1721771022).unwrap(),
            price: 1.0,
        }],
    };
    let day_cache = initialize_and_populate_cache(days, USD, day_cache_data).await;
    let mut chart_caches: HashMap<u64, Cache<ChartCacheKey, ExchangeRateChartData>> =
        HashMap::new();
    chart_caches.insert(cache_ttl::FIVE_MINUTES, day_cache);

    ExchangeRateService::new_from_caches(CacheBuilder::new(1).build(), chart_caches)
}

#[tokio::test]
async fn test_get_chart_data() {
    // Arrange
    setup_environment().await;
    let feature_flags_service = Config::new_with_overrides(HashMap::new())
        .to_service()
        .await
        .unwrap();
    // cheating a bit here by short-circuiting via the cache
    // but we just need to know that the route is hooked up properly
    let exchange_rate_service = create_exchange_rate_service_with_caches().await;
    let route_state = RouteState(exchange_rate_service, feature_flags_service);
    let app = route_state.basic_validation_router();
    let body = Body::from(r#"{"currency_code":"USD","days":1,"max_price_points":1}"#);
    let request = Request::builder()
        .uri("/api/exchange-rates/chart")
        .method("POST")
        .header("content-type", "application/json")
        .body(body)
        .unwrap();

    // Act
    let response = app.oneshot(request).await.unwrap();

    // Assert
    assert_eq!(response.status(), 200);
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let body: Value = serde_json::from_slice(&body).unwrap();
    assert_eq!(
        body.to_string(),
        r#"{"exchange_rates":[{"price":1.0,"timestamp":"2024-07-23T21:43:42Z"}],"from_currency":"USD","to_currency":"USD"}"#
    );
}
