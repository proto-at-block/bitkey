use std::env;

use axum::body::Bytes;
use axum::extract::State;
use axum::routing::post;
use axum::Router;
use prost::Message;
use serde::Deserialize;
use tracing::{event, Level};

use crate::destination::tracker::Tracker;
use crate::destination::AnalyticsDestinationType;
use crate::errors::{AnalyticsError, ApiError};

use self::definitions::EventBundle;

#[allow(clippy::all)]
pub mod definitions {
    include!("definitions/build.wallet.analytics.v1.rs");
}

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(pub Tracker);

impl From<RouteState> for Router {
    fn from(state: RouteState) -> Self {
        Router::new()
            .route("/api/analytics/events", post(record_event))
            .with_state(state)
    }
}

#[derive(Clone, Deserialize)]
pub struct Config {
    pub analytics_destination: AnalyticsDestinationType,
    pub analytics_api_url: String,
}

impl Config {
    pub fn to_state(self) -> RouteState {
        let api_key = match self.analytics_destination {
            AnalyticsDestinationType::Segment => {
                env::var("SEGMENT_API_KEY").expect("SEGMENT_API_KEY environment variable not set")
            }
            AnalyticsDestinationType::Stdout => String::from(""),
        };

        RouteState(Tracker::new(
            self.analytics_destination,
            api_key,
            self.analytics_api_url,
        ))
    }
}

pub async fn record_event(
    State(tracker): State<Tracker>,
    events_blob: Bytes,
) -> Result<(), ApiError> {
    let events = decode_event_bundle(events_blob)?;
    tracker.track(events).await.map_err(ApiError::from)
}

fn decode_event_bundle(blob: Bytes) -> Result<EventBundle, AnalyticsError> {
    EventBundle::decode(blob.clone()).map_err(|err| {
        event!(
            Level::WARN,
            "Couldn't decode the analytics event payload due to error {}. Full payload: {}",
            err,
            String::from_utf8_lossy(&blob)
        );
        AnalyticsError::InvalidEvent(String::from("Couldn't decode the payload"))
    })
}

#[cfg(test)]
mod local_analytics_test {
    use super::definitions::{Event, EventBundle};
    use prost::Message;

    #[test]
    fn test_encode_decode_event_bundle() {
        // Create event bundle
        let events = vec![Event {
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
            screen_id: String::from("test-screen-id"),
            fiat_currency_preference: String::from("test-fiat-currency-preference"),
            bitcoin_display_preference: String::from("test-bitcoin-display-preference"),
            counter_id: String::from("counter_id"),
            counter_count: 0,
            fingerprint_scan_stats: None,
        }];
        let event_bundle = EventBundle { events };

        // Encode event bundle
        let mut blob = Vec::with_capacity(event_bundle.encoded_len());
        let encoding_result = event_bundle.encode(&mut blob);
        assert!(encoding_result.is_ok());
        let encoded_events: &[u8] = &blob;

        // Decode event bundle
        let decoding_result = EventBundle::decode(encoded_events);
        assert!(decoding_result.is_ok());
        let decoded_events = decoding_result.unwrap().events;

        assert_eq!(decoded_events.len(), 1);
        assert_eq!(decoded_events, event_bundle.events);
    }
}
