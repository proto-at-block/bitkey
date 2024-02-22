use account::service::FetchAccountInput;
use async_trait::async_trait;
use time::Duration;
use types::account::identifiers::AccountId;

use crate::{entities::RecoveryType, error::RecoveryError};

use super::{
    current_account_recovery::CurrentAccountRecoveryState, has_recent_contested_delay_notify,
    pending_recovery::PendingRecoveryState, RecoveryEvent, RecoveryServices, RecoveryStateResponse,
    Transition, TransitionTo, TransitioningRecoveryState, CONTEST_LOOKBACK_DAYS,
};

pub(crate) struct StartRecoveryState {
    pub(crate) account_id: AccountId,
}

#[async_trait]
impl RecoveryStateResponse for StartRecoveryState {
    fn response(self: Box<Self>) -> serde_json::Value {
        serde_json::json!(null)
    }
}

#[async_trait]
impl TransitioningRecoveryState for StartRecoveryState {
    async fn next_transition_or_err(
        self: Box<Self>,
        event: RecoveryEvent,
        services: &RecoveryServices,
    ) -> Result<Transition, RecoveryError> {
        let full_account = services
            .account
            .fetch_full_account(FetchAccountInput {
                account_id: &self.account_id,
            })
            .await?;

        if let RecoveryEvent::CheckAccountRecoveryState = event {
            let recovery_service = services.recovery;
            let recovery = recovery_service
                .fetch_pending(&self.account_id, RecoveryType::DelayAndNotify)
                .await?;

            // Check CONTEST_LOOKBACK_DAYS from the creation of this recovery rather than from today.
            //   This determines if the ongoing recovery was created during a time of contest.
            //   If we only checked CONTEST_LOOKBACK_DAYS from today, it would allow a transition
            //   from ContestedDelayPeriod to UncontestedDelayPeriod in some cases (namely if the N-day
            //   CONTEST_LOOKBACK period terminated within the delay period of this recovery).
            //   EG
            //     day = 0: Recovery created by real user
            //     day = 0: Recovery contested by attacker ("free", no comms verification)
            //     day = 1: Recovery created by real user, with comms verification
            //     day = 31: Recovery contested by attacker, ("free", no comms verification)
            let since = if let Some(recovery) = recovery.as_ref() {
                recovery.created_at - Duration::days(CONTEST_LOOKBACK_DAYS)
            } else {
                services.recovery.cur_time() - Duration::days(CONTEST_LOOKBACK_DAYS)
            };

            let active_contest =
                has_recent_contested_delay_notify(services, &self.account_id, since).await?;

            Ok(Transition::next(
                self,
                CurrentAccountRecoveryState {
                    account: full_account,
                    recovery,
                    active_contest,
                },
            ))
        } else {
            Err(RecoveryError::InvalidTransition)
        }
    }
}

impl TransitionTo<PendingRecoveryState> for StartRecoveryState {}
impl TransitionTo<CurrentAccountRecoveryState> for StartRecoveryState {}
