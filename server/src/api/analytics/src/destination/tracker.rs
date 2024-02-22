use tracing::instrument;

use crate::destination::stdout_tracker::StdoutTracker;
use crate::destination::{AnalyticsDestinationType, Destination};
use crate::errors::AnalyticsError;
use crate::routes::definitions::EventBundle;

use super::segment_tracker::SegmentTracker;

#[derive(Clone, Debug)]
pub struct Tracker {
    destination: AnalyticsDestinationType,
    segment_tracker: Option<SegmentTracker>,
    stdout_tracker: Option<StdoutTracker>,
}

impl Tracker {
    pub fn new(
        destination_type: AnalyticsDestinationType,
        api_key: String,
        api_url: String,
    ) -> Tracker {
        match destination_type {
            AnalyticsDestinationType::Segment => Tracker {
                destination: destination_type,
                segment_tracker: Some(SegmentTracker { api_key, api_url }),
                stdout_tracker: None,
            },
            AnalyticsDestinationType::Stdout => Tracker {
                destination: destination_type,
                segment_tracker: None,
                stdout_tracker: Some(StdoutTracker {}),
            },
        }
    }

    #[instrument(level = "error")]
    pub async fn track(&self, events: EventBundle) -> Result<(), AnalyticsError> {
        match self.destination {
            AnalyticsDestinationType::Segment => {
                let tracker = self.segment_tracker.as_ref().ok_or_else(|| {
                    AnalyticsError::DestinationError(String::from(
                        "Segment tracker is not available",
                    ))
                })?;
                tracker.track(events).await
            }
            AnalyticsDestinationType::Stdout => {
                let tracker = self.stdout_tracker.as_ref().ok_or_else(|| {
                    AnalyticsError::DestinationError(String::from(
                        "Stdout tracker is not available",
                    ))
                })?;
                tracker.track(events).await
            }
        }
    }
}
