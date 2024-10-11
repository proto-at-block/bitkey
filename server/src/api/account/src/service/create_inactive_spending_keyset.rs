use types::account::{entities::FullAccount, identifiers::KeysetId, spending::SpendingKeyset};

use super::{CreateInactiveSpendingKeysetInput, FetchAccountInput, Service};
use crate::error::AccountError;

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
        let inactive_spending_keyset_id = input.spending_keyset_id.clone();

        // Update spending keys
        spending_keysets.insert(inactive_spending_keyset_id.clone(), input.spending.clone());

        let updated_account = FullAccount {
            spending_keysets,
            ..full_account
        }
        .into();

        self.account_repo.persist(&updated_account).await?;
        Ok((input.spending_keyset_id, input.spending))
    }
}
