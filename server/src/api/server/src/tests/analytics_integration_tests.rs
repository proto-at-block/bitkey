use analytics::routes::definitions::{Event, EventBundle};
use http::StatusCode;
use prost::Message;

use crate::tests::gen_services;

use super::requests::axum::TestClient;

#[tokio::test]
async fn event_tracking_request_succeeds_with_valid_request_empty_events() {
    let (_, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let events = EventBundle { events: Vec::new() };
    let mut request = Vec::new();
    request.reserve(events.encoded_len());
    let encode_result = events.encode(&mut request);
    assert!(encode_result.is_ok());

    let result = client.track_analytics_event(request).await;

    assert_eq!(result.status_code, StatusCode::OK,);
}

#[tokio::test]
async fn event_tracking_request_succeeds_with_valid_request_non_empty_events() {
    let (_, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let events = EventBundle {
        events: vec![Event {
            event_time: String::from("test-time"),
            action: 0,
            account_id: String::from("test-account-id"),
            app_device_id: String::from("test-app-device-id"),
            app_installation_id: String::from("test-app-installation-id"),
            keyset_id: String::from("test-keyset-id"),
            session_id: String::from("test-session-id"),
            country: String::from("test-country"),
            locale_currency: String::from("test-locale-currency"),
            platform_info: None,
            hw_info: None,
            screen_id: String::from("screen_id"),
            fiat_currency_preference: String::from("test-fiat-currency-preference"),
            bitcoin_display_preference: String::from("test-bitcoin-display-preference"),
            counter_id: String::from("counter_id"),
            counter_count: 0,
        }],
    };

    let mut request = Vec::new();
    request.reserve(events.encoded_len());
    let encode_result = events.encode(&mut request);
    assert!(encode_result.is_ok());

    let result = client.track_analytics_event(request).await;

    assert_eq!(result.status_code, StatusCode::OK,);
}
