use account::service::{FetchAccountByAuthKeyInput, Service as AccountService};
use async_trait::async_trait;
use recovery::repository::Repository as RecoveryService;

use crate::routes::Config;

use super::{
    error::AccountValidationError, is_repeat_account_creation, AccountValidationRequest, Rule,
};

pub(crate) struct UniqueRecoveryAuthPubkeyForAccountRule;

#[async_trait]
impl Rule for UniqueRecoveryAuthPubkeyForAccountRule {
    async fn validate(
        &self,
        request: &AccountValidationRequest,
        _: &Config,
        account_service: &AccountService,
        _: &RecoveryService,
    ) -> Result<(), AccountValidationError> {
        // This check only applies to creating full or lite accounts
        let recovery_auth_pubkey = match request {
            AccountValidationRequest::CreateFullAccount { auth, .. } => {
                if let Some(recovery_auth_pubkey) = auth.recovery {
                    recovery_auth_pubkey
                } else {
                    return Ok(());
                }
            }
            AccountValidationRequest::CreateLiteAccount { auth, .. } => auth.recovery,
            AccountValidationRequest::CreateSoftwareAccount { auth, .. } => auth.recovery,
            AccountValidationRequest::UpgradeAccount { .. } => {
                return Ok(());
            }
        };

        // If the recovery key is in use by an account
        if let Some(existing_account) = account_service
            .fetch_account_by_recovery_pubkey(FetchAccountByAuthKeyInput {
                pubkey: recovery_auth_pubkey,
            })
            .await?
        {
            // And the request doesn't pass idempotency checks, error
            if !is_repeat_account_creation(request, &existing_account) {
                return Err(AccountValidationError::RecoveryAuthPubkeyReuseAccount);
            }

            return Err(AccountValidationError::DuplicateAccountForKeys(
                existing_account,
            ));
        }

        Ok(())
    }
}
