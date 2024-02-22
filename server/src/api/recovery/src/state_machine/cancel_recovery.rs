use async_trait::async_trait;
use utoipa::ToSchema;

use super::{
    RecoveryError, RecoveryEvent, RecoveryServices, RecoveryStateResponse, Transition,
    TransitioningRecoveryState,
};

#[derive(ToSchema)]
pub(crate) struct CanceledRecoveryState {}

#[async_trait]
impl RecoveryStateResponse for CanceledRecoveryState {
    fn response(self: Box<Self>) -> serde_json::Value {
        serde_json::json!({})
    }
}

#[async_trait]
impl TransitioningRecoveryState for CanceledRecoveryState {
    async fn next_transition_or_err(
        self: Box<Self>,
        _event: RecoveryEvent,
        _services: &RecoveryServices,
    ) -> Result<Transition, RecoveryError> {
        Ok(Transition::Complete(Ok(self)))
    }
}
