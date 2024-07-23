use crate::{
    entities::{Account, FullAccount, SoftwareAccount, SpendingKeyset},
    error::AccountError,
};
use types::account::identifiers::KeysetId;

use super::{CreateInactiveSpendingKeysetInput, FetchAccountInput, Service};

impl Service {
    pub async fn create_inactive_spending_keyset(
        &self,
        input: CreateInactiveSpendingKeysetInput,
    ) -> Result<(KeysetId, SpendingKeyset), AccountError> {
        let account = self
            .fetch_account(FetchAccountInput {
                account_id: &input.account_id,
            })
            .await?;

        let updated_account: Account = match account {
            Account::Full(full_account) => {
                let mut spending_keysets = full_account.spending_keysets;
                let inactive_spending_keyset_id = input.spending_keyset_id.clone();

                // Update spending keys
                spending_keysets
                    .insert(inactive_spending_keyset_id.clone(), input.spending.clone());

                FullAccount {
                    spending_keysets,
                    ..full_account
                }
                .into()
            }
            Account::Software(software_account) => {
                let mut spending_keysets = software_account.spending_keysets;
                let inactive_spending_keyset_id = input.spending_keyset_id.clone();

                // Update spending keys
                spending_keysets
                    .insert(inactive_spending_keyset_id.clone(), input.spending.clone());

                SoftwareAccount {
                    spending_keysets,
                    ..software_account
                }
                .into()
            }
            _ => {
                return Err(AccountError::InvalidAccountType);
            }
        };

        self.account_repo.persist(&updated_account).await?;
        Ok((input.spending_keyset_id, input.spending))
    }
}
