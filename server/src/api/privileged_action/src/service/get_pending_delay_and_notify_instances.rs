use serde_json::Value;
use tracing::instrument;
use types::{
    account::identifiers::AccountId,
    privileged_action::repository::{DelayAndNotifyStatus, PrivilegedActionInstanceRecord},
};

use super::{error::ServiceError, Service};

#[derive(Debug)]
pub struct GetPendingDelayAndNotifyInstancesInput<'a> {
    pub account_id: &'a AccountId,
}

impl Service {
    #[instrument(skip(self))]
    pub async fn get_pending_delay_and_notify_instances(
        &self,
        input: GetPendingDelayAndNotifyInstancesInput<'_>,
    ) -> Result<Vec<PrivilegedActionInstanceRecord<Value>>, ServiceError> {
        let instances = self
            .privileged_action_repository
            .fetch_delay_notify_for_account_id(
                input.account_id,
                None,
                Some(DelayAndNotifyStatus::Pending),
            )
            .await?;

        Ok(instances)
    }
}
