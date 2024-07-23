use crate::{
    entities::{Account, FullAccount, SoftwareAccount},
    error::AccountError,
};

use super::{FetchAccountInput, RotateToSpendingKeysetInput, Service};

impl Service {
    pub async fn rotate_to_spending_keyset(
        &self,
        input: RotateToSpendingKeysetInput<'_>,
    ) -> Result<Account, AccountError> {
        let account = self
            .fetch_account(FetchAccountInput {
                account_id: input.account_id,
            })
            .await?;

        let updated_account: Account = match account {
            Account::Full(full_account) => {
                if !full_account.spending_keysets.contains_key(input.keyset_id) {
                    return Err(AccountError::InvalidSpendingKeysetIdentifierForRotation);
                }
                FullAccount {
                    active_keyset_id: input.keyset_id.to_owned(),
                    ..full_account
                }
                .into()
            }
            Account::Software(software_account) => {
                if !software_account
                    .spending_keysets
                    .contains_key(input.keyset_id)
                {
                    return Err(AccountError::InvalidSpendingKeysetIdentifierForRotation);
                }
                SoftwareAccount {
                    active_keyset_id: Some(input.keyset_id.to_owned()),
                    ..software_account
                }
                .into()
            }
            _ => {
                return Err(AccountError::InvalidAccountType);
            }
        };

        self.account_repo.persist(&updated_account).await?;
        Ok(updated_account)
    }
}
