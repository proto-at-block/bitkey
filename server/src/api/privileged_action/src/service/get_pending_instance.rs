use database::ddb::{DatabaseError, DatabaseObject};
use serde_json::Value;
use tracing::instrument;
use types::{
    account::identifiers::AccountId,
    privileged_action::{
        repository::{AuthorizationStrategyRecord, PrivilegedActionInstanceRecord, RecordStatus},
        shared::PrivilegedActionInstanceId,
    },
};

use super::{error::ServiceError, Service};

#[derive(Debug)]
pub struct GetPendingInstanceInput<'a> {
    pub account_id: &'a AccountId,
    pub privileged_action_id: &'a PrivilegedActionInstanceId,
}

impl Service {
    #[instrument(skip(self))]
    pub async fn get_pending_instance(
        &self,
        input: GetPendingInstanceInput<'_>,
    ) -> Result<PrivilegedActionInstanceRecord<Value>, ServiceError> {
        let instance = self
            .privileged_action_repository
            .fetch_by_id(input.privileged_action_id)
            .await?;

        if instance.account_id != *input.account_id {
            return Err(ServiceError::RecordAccountIdForbidden);
        }

        let is_pending = match &instance.authorization_strategy {
            AuthorizationStrategyRecord::DelayAndNotify(record) => {
                record.status == RecordStatus::Pending
            }
            AuthorizationStrategyRecord::OutOfBand(record) => {
                record.status == RecordStatus::Pending
            }
            AuthorizationStrategyRecord::HardwareProofOfPossession => {
                // HardwareProofOfPossession records are always considered completed, never pending
                false
            }
        };

        if !is_pending {
            return Err(DatabaseError::ObjectNotFound(DatabaseObject::PrivilegedAction).into());
        }

        Ok(instance)
    }
}
