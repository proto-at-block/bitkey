use serde::{de::DeserializeOwned, Serialize};
use tracing::instrument;
use types::privileged_action::{
    repository::{
        AuthorizationStrategyRecord, OutOfBandRecord, PrivilegedActionInstanceRecord, RecordStatus,
        DEFAULT_OOB_MAX_ATTEMPTS,
    },
    shared::PrivilegedActionInstanceId,
};

use super::{error::ServiceError, Service};

/// Outcome of [`Service::increment_out_of_band_attempts`].
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum IncrementOutOfBandAttemptsResult {
    /// The attempt counter was incremented and the record remains `Pending`.
    Recorded {
        /// Attempts left before the record transitions to `Failed`. Useful
        /// for telling the user "you have N tries remaining."
        remaining_attempts: u32,
    },
    /// The attempt counter reached `max_attempts`; the record has been
    /// transitioned to `Failed` and the OOB flow is terminated.
    AttemptsExhausted,
}

impl Service {
    /// Increments the failed-attempt counter on a pending out-of-band privileged action
    /// instance. If the new attempt count reaches `max_attempts`, the record transitions
    /// to [`RecordStatus::Failed`] and the OOB flow is terminated.
    ///
    /// Callers should invoke this only after their own verification of the user-supplied
    /// confirmation input has failed.
    #[instrument(skip(self))]
    pub async fn increment_out_of_band_attempts<ReqT>(
        &self,
        privileged_action_instance_id: &PrivilegedActionInstanceId,
    ) -> Result<IncrementOutOfBandAttemptsResult, ServiceError>
    where
        ReqT: Serialize + DeserializeOwned + Clone,
    {
        let instance_record: PrivilegedActionInstanceRecord<ReqT> = self
            .privileged_action_repository
            .fetch_by_id(privileged_action_instance_id)
            .await?;

        let AuthorizationStrategyRecord::OutOfBand(out_of_band_record) =
            instance_record.authorization_strategy.clone()
        else {
            return Err(ServiceError::RecordAuthorizationStrategyTypeUnexpected);
        };

        if out_of_band_record.status != RecordStatus::Pending {
            return Err(ServiceError::RecordOutofBandStatusConflict);
        }

        let new_attempts = out_of_band_record.attempts.saturating_add(1);
        let exhausted = new_attempts >= DEFAULT_OOB_MAX_ATTEMPTS;
        let new_status = if exhausted {
            RecordStatus::Failed
        } else {
            RecordStatus::Pending
        };

        let updated_instance = PrivilegedActionInstanceRecord {
            authorization_strategy: AuthorizationStrategyRecord::OutOfBand(OutOfBandRecord {
                status: new_status,
                attempts: new_attempts,
                ..out_of_band_record
            }),
            ..instance_record
        };

        self.privileged_action_repository
            .persist(&updated_instance)
            .await?;

        if exhausted {
            // Same email as the user-canceled path: "your verification
            // session ended" reads identically whether the user gave up
            // or the system did.
            self.send_notification_for_canceled_instance(&updated_instance)
                .await?;
        }

        Ok(if exhausted {
            IncrementOutOfBandAttemptsResult::AttemptsExhausted
        } else {
            IncrementOutOfBandAttemptsResult::Recorded {
                remaining_attempts: DEFAULT_OOB_MAX_ATTEMPTS.saturating_sub(new_attempts),
            }
        })
    }
}
