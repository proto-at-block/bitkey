use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use types::account::identifiers::KeysetId;

use super::{
    RecoveryEvent, RecoveryServices, RecoveryStateResponse, Transition, TransitioningRecoveryState,
};
use crate::error::RecoveryError;

#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all = "snake_case")]
pub struct RotatedKeysetResponse {
    pub active_keyset_id: KeysetId,
}

pub(crate) struct RotatedKeysetState {
    pub(crate) active_keyset_id: KeysetId,
}

#[async_trait]
impl RecoveryStateResponse for RotatedKeysetState {
    fn response(self: Box<Self>) -> serde_json::Value {
        serde_json::json!(RotatedKeysetResponse {
            active_keyset_id: self.active_keyset_id
        })
    }
}

#[async_trait]
impl TransitioningRecoveryState for RotatedKeysetState {
    async fn next_transition_or_err(
        self: Box<Self>,
        _event: RecoveryEvent,
        _services: &RecoveryServices,
    ) -> Result<Transition, RecoveryError> {
        Ok(Transition::Complete(Ok(self)))
    }
}
