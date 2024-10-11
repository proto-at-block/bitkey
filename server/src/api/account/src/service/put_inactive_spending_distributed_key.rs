use types::account::{
    entities::SoftwareAccount,
    identifiers::KeyDefinitionId,
    spending::{SpendingDistributedKey, SpendingKeyDefinition},
};

use super::{FetchAccountInput, PutInactiveSpendingDistributedKeyInput, Service};
use crate::error::AccountError;

impl Service {
    pub async fn put_inactive_spending_distributed_key(
        &self,
        input: PutInactiveSpendingDistributedKeyInput<'_>,
    ) -> Result<(KeyDefinitionId, SpendingDistributedKey), AccountError> {
        let software_account = self
            .fetch_software_account(FetchAccountInput {
                account_id: input.account_id,
            })
            .await?;

        let mut spending_key_definitions = software_account.spending_key_definitions;

        // Update spending keys
        spending_key_definitions.insert(
            input.spending_key_definition_id.clone(),
            SpendingKeyDefinition::DistributedKey(input.spending.clone()),
        );

        let updated_account = SoftwareAccount {
            spending_key_definitions,
            ..software_account
        }
        .into();

        self.account_repo.persist(&updated_account).await?;
        Ok((input.spending_key_definition_id.clone(), input.spending))
    }
}
