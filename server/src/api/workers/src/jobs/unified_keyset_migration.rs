use tokio::time::Duration;
use tracing::instrument;

use super::WorkerState;
use crate::error::WorkerError;

#[instrument(skip(_state))]
pub async fn handler(_state: WorkerState, migrate: bool) -> Result<(), WorkerError> {
    loop {
        tokio::time::sleep(Duration::from_secs(1200)).await;
    }
}
