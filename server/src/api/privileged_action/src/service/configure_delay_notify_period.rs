use tracing::instrument;
use types::account::entities::CommonAccountFields;
use types::{
    account::identifiers::AccountId,
    privileged_action::shared::{PrivilegedActionDelayDuration, PrivilegedActionType},
};

use super::{error::ServiceError, Service};

const ONE_DAY_SECS: usize = 24 * 60 * 60;
const ALLOWED_DELAY_PERIOD_DAYS: [u32; 3] = [7, 14, 30];

#[derive(Debug)]
pub struct ConfigureDelayNotifyPeriodInput<'a> {
    pub account_id: &'a AccountId,
    pub delay_period_days: u32,
}

impl Service {
    #[instrument(skip(self))]
    pub async fn configure_delay_notify_period(
        &self,
        input: ConfigureDelayNotifyPeriodInput<'_>,
    ) -> Result<(), ServiceError> {
        if !ALLOWED_DELAY_PERIOD_DAYS.contains(&input.delay_period_days) {
            return Err(ServiceError::InvalidDelayNotifyPeriod(
                input.delay_period_days,
            ));
        }

        let account = &self.account_repository.fetch(input.account_id).await?;
        let delay_secs = input.delay_period_days as usize * ONE_DAY_SECS;

        let common_fields = account.get_common_fields();
        let mut configured_delays = common_fields
            .configured_privileged_action_delay_durations
            .clone();
        configured_delays
            .retain(|d| d.privileged_action_type != PrivilegedActionType::ResetFingerprint);
        configured_delays.push(PrivilegedActionDelayDuration {
            privileged_action_type: PrivilegedActionType::ResetFingerprint,
            delay_duration_secs: delay_secs,
        });

        let updated_common_fields = CommonAccountFields {
            delay_notify_period_secs: Some(delay_secs),
            configured_privileged_action_delay_durations: configured_delays,
            ..common_fields.clone()
        };

        let updated_account = account.update(updated_common_fields)?;
        self.account_repository.persist(&updated_account).await?;

        Ok(())
    }
}
