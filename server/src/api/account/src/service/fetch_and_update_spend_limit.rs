use super::{FetchAccountInput, FetchAndUpdateSpendingLimitInput, Service};
use crate::entities::FullAccount;
use crate::error::AccountError;

impl Service {
    pub async fn fetch_and_update_spend_limit(
        &self,
        input: FetchAndUpdateSpendingLimitInput<'_>,
    ) -> Result<(), AccountError> {
        let full_account = self
            .fetch_full_account(FetchAccountInput {
                account_id: input.account_id,
            })
            .await?;

        let updated_account = FullAccount {
            spending_limit: input.new_spending_limit,
            ..full_account
        }
        .into();

        self.account_repo.persist(&updated_account).await?;
        Ok(())
    }
}
