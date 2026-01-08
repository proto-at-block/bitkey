use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use reqwest::Client;
use tracing::instrument;

use crate::destination::Destination;
use crate::errors::AnalyticsError;
use crate::routes::definitions::{Action, ActionServer, Event, EventBundle, ServerEvent};

const AUTH_HEADER: &str = "Authorization";
const AUTH_TYPE: &str = "Basic ";
const BATCH_API_URI: &str = "/v1/batch";
const EVENT_TYPE: &str = "track";

#[derive(Clone, Debug)]
pub struct SegmentTracker {
    pub api_key: String,
    pub api_url: String,
}

impl Destination for SegmentTracker {
    async fn track(&self, events: EventBundle) -> Result<(), AnalyticsError> {
        let segment_track_event_bundle = translate_events(events);
        send_batch(
            segment_track_event_bundle,
            self.api_key.to_owned(),
            self.api_url.to_owned(),
        )
        .await
    }
}

#[derive(Debug, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SegmentTrackEventBundle {
    batch: Vec<SegmentTrackEvent>,
}

#[derive(Debug, serde::Serialize, serde::Deserialize, PartialEq)]
#[serde(untagged)]
enum SegmentProperties {
    Event(Event),
    ServerEvent(ServerEvent),
}

#[derive(Debug, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SegmentTrackEvent {
    user_id: String,
    event: String,
    properties: SegmentProperties,
    timestamp: String,
    #[serde(rename = "type")]
    event_type: String,
}

/*
Translates Event objects to Batch object of Track Segment objects specified in:
https://segment.com/docs/connections/sources/catalog/libraries/server/http-api/#batch
 */
fn translate_events(bundle: EventBundle) -> SegmentTrackEventBundle {
    let segment_track_events: Vec<SegmentTrackEvent> = bundle
        .events
        .into_iter()
        .map(|e| {
            let properties = SegmentProperties::Event(e.clone());
            let action = Action::try_from(e.action).unwrap_or(Action::Unspecified);
            SegmentTrackEvent {
                user_id: e.app_device_id,
                event: Action::as_str_name(&action).to_owned(),
                properties,
                timestamp: e.event_time,
                event_type: String::from(EVENT_TYPE),
            }
        })
        .collect();
    let server_events: Vec<SegmentTrackEvent> = bundle
        .server_events
        .into_iter()
        .map(|e| {
            let properties = SegmentProperties::ServerEvent(e.clone());
            let action = ActionServer::try_from(e.action).unwrap_or(ActionServer::Unspecified);
            SegmentTrackEvent {
                user_id: e.account_id,
                event: ActionServer::as_str_name(&action).to_owned(),
                properties,
                timestamp: e.event_time,
                event_type: String::from(EVENT_TYPE),
            }
        })
        .collect();

    let mut batch = segment_track_events;
    batch.extend(server_events);
    SegmentTrackEventBundle { batch }
}

#[instrument(level = "error")]
async fn send_batch(
    bundle: SegmentTrackEventBundle,
    api_key: String,
    api_url: String,
) -> Result<(), AnalyticsError> {
    let client = Client::new();
    let request_body = serde_json::to_string(&bundle)
        .map_err(|e| AnalyticsError::InvalidEvent(format!("Error deserializing events: {e}")))?;
    let auth_header_value = api_key_to_auth_header(api_key);
    client
        .post(api_url + BATCH_API_URI)
        .header(AUTH_HEADER, auth_header_value)
        .body(request_body)
        .send()
        .await
        .map_err(|e| {
            AnalyticsError::DestinationError(format!("Error sending events to Segment: {e}"))
        })?;
    Ok(())
}

fn api_key_to_auth_header(api_key: String) -> String {
    String::from(AUTH_TYPE) + BASE64.encode(api_key).as_str()
}

#[cfg(test)]
mod local_analytics_segment_tracker_test {

    use crate::{
        destination::segment_tracker::{
            api_key_to_auth_header, translate_events, SegmentProperties,
        },
        routes::definitions::{Action, ActionServer, Event, EventBundle, ServerEvent},
    };

    #[test]
    fn test_translate_event_bundle() {
        let event_bundle = EventBundle {
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
                screen_id: String::from("test-screen-id"),
                fiat_currency_preference: String::from("test-fiat-currency-preference"),
                bitcoin_display_preference: String::from("test-bitcoin-display-preference"),
                counter_id: String::from("counter_id"),
                counter_count: 0,
                fingerprint_scan_stats: None,
            }],
            server_events: vec![],
        };

        let segment_event_bundle = translate_events(event_bundle.clone());

        assert_eq!(segment_event_bundle.batch.len(), event_bundle.events.len());
        assert_eq!(
            segment_event_bundle.batch[0].properties,
            SegmentProperties::Event(event_bundle.events[0].clone())
        );
        assert_eq!(
            segment_event_bundle.batch[0].event,
            Action::as_str_name(&Action::Unspecified)
        );
        assert_eq!(
            segment_event_bundle.batch[0].timestamp,
            event_bundle.events[0].event_time
        );
    }

    #[test]
    fn test_translate_server_event_bundle() {
        let event_bundle = EventBundle {
            events: vec![],
            server_events: vec![ServerEvent {
                event_time: String::from("test-time"),
                action: 0,
                account_id: String::from("test-account-id"),
                inheritance_info: None,
            }],
        };

        let segment_event_bundle = translate_events(event_bundle.clone());

        assert_eq!(
            segment_event_bundle.batch.len(),
            event_bundle.server_events.len()
        );
        assert_eq!(
            segment_event_bundle.batch[0].properties,
            SegmentProperties::ServerEvent(event_bundle.server_events[0].clone())
        );
        assert_eq!(
            segment_event_bundle.batch[0].event,
            ActionServer::as_str_name(&ActionServer::Unspecified)
        );
        assert_eq!(
            segment_event_bundle.batch[0].timestamp,
            event_bundle.server_events[0].event_time
        );
    }

    #[test]
    fn test_api_key_to_auth_header() {
        assert_eq!(
            api_key_to_auth_header(String::from("abc123:")),
            "Basic YWJjMTIzOg=="
        )
    }
}
