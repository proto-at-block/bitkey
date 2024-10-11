use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use types::account::entities::FullAccountAuthKeysPayload;
use utoipa::ToSchema;

use super::{
    RecoveryError, RecoveryEvent, RecoveryServices, RecoveryStateResponse, Transition,
    TransitioningRecoveryState,
};
use crate::{entities::WalletRecovery, state_machine::PendingDelayNotifyRecovery};

pub(crate) struct PendingRecoveryState {
    pub(crate) recovery: WalletRecovery,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct PendingRecoveryResponse {
    pub pending_delay_notify: PendingDelayNotifyRecovery,
}

#[async_trait]
impl RecoveryStateResponse for PendingRecoveryState {
    fn response(self: Box<Self>) -> serde_json::Value {
        let requirements = self
            .recovery
            .requirements
            .delay_notify_requirements
            .unwrap();
        let destination = self
            .recovery
            .recovery_action
            .delay_notify_action
            .unwrap()
            .destination;
        serde_json::json!(PendingRecoveryResponse {
            pending_delay_notify: PendingDelayNotifyRecovery {
                delay_start_time: self.recovery.created_at,
                delay_end_time: requirements.delay_end_time,
                lost_factor: requirements.lost_factor,
                auth_keys: FullAccountAuthKeysPayload {
                    app: destination.app_auth_pubkey,
                    hardware: destination.hardware_auth_pubkey,
                    recovery: destination.recovery_auth_pubkey,
                }
            }
        })
    }
}

#[async_trait]
impl TransitioningRecoveryState for PendingRecoveryState {
    async fn next_transition_or_err(
        self: Box<Self>,
        _event: RecoveryEvent,
        _services: &RecoveryServices,
    ) -> Result<Transition, RecoveryError> {
        Ok(Transition::Complete(Ok(self)))
    }
}
