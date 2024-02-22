use crate::errors::AnalyticsError;
use crate::routes::definitions::EventBundle;

use serde::Deserialize;

pub mod segment_tracker;
pub mod stdout_tracker;
pub mod tracker;

#[derive(Clone, Debug, Deserialize)]
pub enum AnalyticsDestinationType {
    Segment,
    Stdout,
}

#[trait_variant::make(Destination: Send)]
pub trait LocalDestination {
    async fn track(&self, _event: EventBundle) -> Result<(), AnalyticsError>;
}
