use types::account::spending::SpendingKeyDefinition;

use crate::{
    entities::{Account, SoftwareAccount},
    error::AccountError,
};

use super::{FetchAccountInput, RotateToSpendingKeyDefinitionInput, Service};

impl Service {
    pub async fn rotate_to_spending_key_definition(
        &self,
        input: RotateToSpendingKeyDefinitionInput<'_>,
    ) -> Result<Account, AccountError> {
        let software_account = self
            .fetch_software_account(FetchAccountInput {
                account_id: input.account_id,
            })
            .await?;

        let key_definition = software_account
            .spending_key_definitions
            .get(input.key_definition_id)
            .ok_or(AccountError::InvalidSpendingKeyDefinitionIdentifierForRotation)?;

        if let SpendingKeyDefinition::DistributedKey(distributed_key) = key_definition {
            if !distributed_key.dkg_complete {
                return Err(AccountError::ConflictingSpendingKeyDefinitionStateForRotation);
            }
        }

        let updated_account = SoftwareAccount {
            active_key_definition_id: Some(input.key_definition_id.clone()),
            ..software_account
        }
        .into();

        self.account_repo.persist(&updated_account).await?;
        Ok(updated_account)
    }
}
