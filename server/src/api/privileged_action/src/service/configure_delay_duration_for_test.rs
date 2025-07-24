use serde_json::Value;
use time::Duration;
use tracing::instrument;
use types::account::identifiers::AccountId;
use types::privileged_action::repository::{
    AuthorizationStrategyRecord, DelayAndNotifyRecord, PrivilegedActionInstanceRecord, RecordStatus,
};
use types::privileged_action::shared::PrivilegedActionInstanceId;

use super::{error::ServiceError, Service};

#[derive(Debug)]
pub struct ConfigureDelayDurationForTestInput<'a> {
    pub account_id: &'a AccountId,
    pub privilege_action_id: &'a PrivilegedActionInstanceId,
    pub delay_duration: i64,
}

impl Service {
    #[instrument(skip(self))]
    pub async fn configure_delay_duration_for_test(
        &self,
        input: ConfigureDelayDurationForTestInput<'_>,
    ) -> Result<(), ServiceError> {
        let account = &self.account_repository.fetch(input.account_id).await?;
        let is_test_account = account.get_common_fields().properties.is_test_account;
        if !is_test_account {
            return Err(ServiceError::CannotUpdateDelayForNonTestAccount);
        }

        let instance: PrivilegedActionInstanceRecord<Value> = self
            .privileged_action_repository
            .fetch_by_id(input.privilege_action_id)
            .await?;
        let AuthorizationStrategyRecord::DelayAndNotify(delay_and_notify_record) =
            instance.authorization_strategy
        else {
            return Err(ServiceError::RecordAuthorizationStrategyTypeUnexpected);
        };

        if delay_and_notify_record.status != RecordStatus::Pending {
            return Err(ServiceError::RecordDelayAndNotifyStatusConflict);
        }

        let delay_end_time = instance.created_at + Duration::seconds(input.delay_duration);
        let updated_record = DelayAndNotifyRecord {
            delay_end_time,
            ..delay_and_notify_record
        };
        let updated_instance = PrivilegedActionInstanceRecord {
            authorization_strategy: AuthorizationStrategyRecord::DelayAndNotify(updated_record),
            ..instance
        };
        self.privileged_action_repository
            .persist(&updated_instance)
            .await?;

        return Ok(());
    }
}
