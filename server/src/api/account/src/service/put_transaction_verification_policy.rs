use types::account::{entities::Account, identifiers::AccountId};

use super::{FetchAccountInput, Service};
use crate::error::AccountError;
use types::account::entities::TransactionVerificationPolicy;

impl Service {
    pub async fn put_transaction_verification_policy(
        &self,
        account_id: &AccountId,
        policy: TransactionVerificationPolicy,
    ) -> Result<Account, AccountError> {
        let mut full_account = self
            .fetch_full_account(FetchAccountInput { account_id })
            .await?;
        full_account.transaction_verification_policy = Some(policy);
        self.account_repo
            .persist(&full_account.clone().into())
            .await?;
        Ok(Account::Full(full_account))
    }
}
