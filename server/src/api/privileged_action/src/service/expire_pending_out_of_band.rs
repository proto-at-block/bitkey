//! Lazy expiry for out-of-band privileged actions: `expiry_time` is
//! enforced on the confirm read path rather than by a background job.

use serde::{de::DeserializeOwned, Serialize};
use tracing::instrument;
use types::privileged_action::repository::{
    AuthorizationStrategyRecord, OutOfBandRecord, PrivilegedActionInstanceRecord, RecordStatus,
};

use super::{error::ServiceError, Service};

impl Service {
    /// Transition a Pending OOB record past its `expiry_time` to `Failed`
    /// (with the canceled-email notification). Returns `instance`
    /// unchanged for non-OOB, non-Pending, or not-yet-expired records.
    #[instrument(skip(self, instance))]
    pub async fn expire_pending_out_of_band_if_overdue<T>(
        &self,
        instance: PrivilegedActionInstanceRecord<T>,
    ) -> Result<PrivilegedActionInstanceRecord<T>, ServiceError>
    where
        T: Serialize + DeserializeOwned + Clone,
    {
        let AuthorizationStrategyRecord::OutOfBand(ref oob) = instance.authorization_strategy
        else {
            return Ok(instance);
        };
        if oob.status != RecordStatus::Pending {
            return Ok(instance);
        }
        if self.clock.now_utc() < oob.expiry_time {
            return Ok(instance);
        }
        let updated = PrivilegedActionInstanceRecord {
            authorization_strategy: AuthorizationStrategyRecord::OutOfBand(OutOfBandRecord {
                status: RecordStatus::Failed,
                ..oob.clone()
            }),
            ..instance
        };
        self.privileged_action_repository.persist(&updated).await?;
        self.send_notification_for_canceled_instance(&updated)
            .await?;
        Ok(updated)
    }
}
