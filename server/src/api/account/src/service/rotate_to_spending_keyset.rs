use types::account::entities::{Account, FullAccount};

use super::{FetchAccountInput, RotateToSpendingKeysetInput, Service};
use crate::error::AccountError;

impl Service {
    pub async fn rotate_to_spending_keyset(
        &self,
        input: RotateToSpendingKeysetInput<'_>,
    ) -> Result<Account, AccountError> {
        let full_account = self
            .fetch_full_account(FetchAccountInput {
                account_id: input.account_id,
            })
            .await?;

        if !full_account.spending_keysets.contains_key(input.keyset_id) {
            return Err(AccountError::InvalidSpendingKeysetIdentifierForRotation);
        }
        let updated_account = FullAccount {
            active_keyset_id: input.keyset_id.to_owned(),
            ..full_account
        }
        .into();

        self.account_repo.persist(&updated_account).await?;
        Ok(updated_account)
    }
}
