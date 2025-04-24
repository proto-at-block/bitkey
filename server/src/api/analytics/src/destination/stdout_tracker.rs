use serde_json;

use crate::destination::Destination;
use crate::errors::AnalyticsError;
use crate::routes::definitions::EventBundle;

#[derive(Clone, Debug)]
pub struct StdoutTracker {}

impl Destination for StdoutTracker {
    #[allow(clippy::print_stdout)]
    async fn track(&self, events: EventBundle) -> Result<(), AnalyticsError> {
        for event in events.events {
            let str_event = serde_json::to_string(&event).map_err(|_| {
                AnalyticsError::InvalidEvent(String::from(
                    "Stdout track failed to unpack the event",
                ))
            })?;
            println!("{str_event}");
        }
        Ok(())
    }
}
