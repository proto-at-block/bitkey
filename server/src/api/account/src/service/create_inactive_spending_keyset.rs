use crate::{
    entities::{FullAccount, SpendingKeyset},
    error::AccountError,
};
use types::account::identifiers::KeysetId;

use super::{CreateInactiveSpendingKeysetInput, FetchAccountInput, Service};

impl Service {
    pub async fn create_inactive_spending_keyset(
        &self,
        input: CreateInactiveSpendingKeysetInput,
    ) -> Result<(KeysetId, SpendingKeyset), AccountError> {
        let full_account = self
            .fetch_full_account(FetchAccountInput {
                account_id: &input.account_id,
            })
            .await?;

        let mut spending_keysets = full_account.spending_keysets;
        let inactive_spending_keyset_id = input.spending_keyset_id;

        // Update spending keys
        spending_keysets.insert(inactive_spending_keyset_id.clone(), input.spending.clone());

        let updated_account = FullAccount {
            spending_keysets,
            ..full_account
        }
        .into();

        self.repo.persist(&updated_account).await?;
        Ok((inactive_spending_keyset_id, input.spending))
    }
}
