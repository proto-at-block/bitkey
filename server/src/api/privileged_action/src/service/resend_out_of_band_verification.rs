use serde_json::Value;
use tracing::instrument;
use types::{
    account::identifiers::AccountId,
    privileged_action::{
        repository::{
            AuthorizationStrategyRecord, OutOfBandRecord, PrivilegedActionInstanceRecord,
            RecordStatus, DEFAULT_OOB_MAX_RESENDS,
        },
        shared::PrivilegedActionInstanceId,
    },
};

use super::{error::ServiceError, Service, RESEND_COOLDOWN};

impl Service {
    /// Resend the out-of-band verification email for a pending instance,
    /// reusing the instance's stable `web_auth_token` (so any earlier email
    /// link keeps working). Bounded by a per-instance cooldown
    /// ([`RESEND_COOLDOWN`]) and a total cap ([`DEFAULT_OOB_MAX_RESENDS`]) to
    /// prevent inbox bombing. The 24h `expiry_time` is unchanged — resending
    /// does not extend the session.
    #[instrument(skip(self))]
    pub async fn resend_out_of_band_verification(
        &self,
        account_id: &AccountId,
        instance_id: PrivilegedActionInstanceId,
    ) -> Result<(), ServiceError> {
        let instance_record: PrivilegedActionInstanceRecord<Value> = self
            .privileged_action_repository
            .fetch_by_id(&instance_id)
            .await?;

        if instance_record.account_id != *account_id {
            return Err(ServiceError::RecordAccountIdForbidden);
        }

        // Lazy-expire first so an overdue session is reported as ended rather
        // than resent.
        let instance_record = self
            .expire_pending_out_of_band_if_overdue(instance_record)
            .await?;

        let AuthorizationStrategyRecord::OutOfBand(out_of_band) =
            instance_record.authorization_strategy.clone()
        else {
            return Err(ServiceError::RecordAuthorizationStrategyTypeConflict);
        };

        if out_of_band.status != RecordStatus::Pending {
            return Err(ServiceError::RecordOutofBandStatusConflict);
        }

        let now = self.clock.now_utc();
        if let Some(last_sent_at) = out_of_band.last_sent_at {
            if now < last_sent_at + RESEND_COOLDOWN {
                return Err(ServiceError::OutOfBandResendThrottled);
            }
        }

        if out_of_band.resend_count >= DEFAULT_OOB_MAX_RESENDS {
            return Err(ServiceError::OutOfBandResendLimitExceeded);
        }

        self.send_oob_pending_notification(&instance_record, &out_of_band.web_auth_token)
            .await?;

        let updated_instance = PrivilegedActionInstanceRecord {
            authorization_strategy: AuthorizationStrategyRecord::OutOfBand(OutOfBandRecord {
                resend_count: out_of_band.resend_count.saturating_add(1),
                last_sent_at: Some(now),
                ..out_of_band
            }),
            ..instance_record
        };

        self.privileged_action_repository
            .persist(&updated_instance)
            .await?;

        Ok(())
    }
}
