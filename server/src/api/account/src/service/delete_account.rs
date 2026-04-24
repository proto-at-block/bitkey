use tracing::{event, Level};
use types::account::entities::Account;

use super::{DeleteAccountInput, Service};
use crate::error::AccountError;

impl Service {
    pub async fn delete_account(&self, input: DeleteAccountInput<'_>) -> Result<(), AccountError> {
        let account = self.account_repo.fetch(input.account_id).await?;

        let Account::Full(full_account) = &account else {
            return Err(AccountError::NotEligibleForDeletion);
        };

        if full_account.common_fields.onboarding_complete {
            return Err(AccountError::NotEligibleForDeletion);
        }

        self.account_repo.delete(&account).await?;

        match self
            .public_key_repo
            .delete_public_key_if_owned_by_account(
                &full_account.hardware_auth_pubkey.to_string(),
                &full_account.id,
            )
            .await
        {
            Ok(true) => {}
            Ok(false) => event!(
                Level::WARN,
                account_id = %full_account.id,
                public_key = %full_account.hardware_auth_pubkey,
                "Deleted account but hardware auth pubkey record ownership changed before cleanup"
            ),
            Err(error) => event!(
                Level::ERROR,
                account_id = %full_account.id,
                public_key = %full_account.hardware_auth_pubkey,
                ?error,
                "Deleted account but failed to clean up hardware auth pubkey record"
            ),
        }

        Ok(())
    }
}
