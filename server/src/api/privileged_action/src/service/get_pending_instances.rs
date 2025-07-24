use serde_json::Value;
use tracing::instrument;
use types::{
    account::identifiers::AccountId,
    privileged_action::{
        repository::{PrivilegedActionInstanceRecord, RecordStatus},
        router::AuthorizationStrategyDiscriminants,
        shared::PrivilegedActionType,
    },
};

use super::{error::ServiceError, Service};

#[derive(Debug)]
pub struct GetPendingInstancesInput<'a> {
    pub account_id: &'a AccountId,
    pub authorization_strategy: Option<AuthorizationStrategyDiscriminants>,
    pub privileged_action_type: Option<PrivilegedActionType>,
}

impl Service {
    #[instrument(skip(self))]
    pub async fn get_pending_instances(
        &self,
        input: GetPendingInstancesInput<'_>,
    ) -> Result<Vec<PrivilegedActionInstanceRecord<Value>>, ServiceError> {
        let instances = self
            .privileged_action_repository
            .fetch_for_account_id(
                input.account_id,
                input.authorization_strategy,
                input.privileged_action_type,
                Some(RecordStatus::Pending),
            )
            .await?;

        Ok(instances)
    }
}
