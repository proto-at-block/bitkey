use crate::{entities::Account, error::AccountError};

use super::{DeleteAccountInput, Service};

impl Service {
    pub async fn delete_account(&self, input: DeleteAccountInput<'_>) -> Result<(), AccountError> {
        let account = self.repo.fetch(input.account_id).await?;

        if matches!(account, Account::Full(_) if !account.get_common_fields().onboarding_complete) {
            self.repo.delete(&account).await?;
            return Ok(());
        }

        Err(AccountError::NotEligibleForDeletion)
    }
}
