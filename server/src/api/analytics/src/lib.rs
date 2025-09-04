use crate::{
    destination::tracker::Tracker,
    routes::definitions::{EventBundle, ServerEvent},
};

pub mod destination;
pub mod errors;
pub mod routes;

pub async fn log_server_event(tracker: Tracker, event: ServerEvent) {
    let event_bundle = EventBundle {
        events: vec![],
        server_events: vec![event],
    };
    let _ = tracker.track(event_bundle).await.inspect_err(|e| {
        tracing::error!("Error tracking server event: {:?}", e);
    });
}
