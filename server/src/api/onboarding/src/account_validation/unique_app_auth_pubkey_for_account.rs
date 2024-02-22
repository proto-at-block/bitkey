use account::service::{FetchAccountByAuthKeyInput, Service as AccountService};
use async_trait::async_trait;
use recovery::repository::Repository as RecoveryService;

use crate::routes::Config;

use super::{
    error::AccountValidationError, is_repeat_account_creation, AccountValidationRequest, Rule,
};

pub(crate) struct UniqueAppAuthPubkeyForAccountRule;

#[async_trait]
impl Rule for UniqueAppAuthPubkeyForAccountRule {
    async fn validate(
        &self,
        request: &AccountValidationRequest,
        _: &Config,
        account_service: &AccountService,
        _: &RecoveryService,
    ) -> Result<(), AccountValidationError> {
        // This check only applies to creating or upgrading to full accounts
        let app_auth_pubkey = match request {
            AccountValidationRequest::CreateFullAccount { auth, .. } => auth.app,
            AccountValidationRequest::UpgradeAccount { auth, .. } => auth.app,
            AccountValidationRequest::CreateLiteAccount { .. } => {
                return Ok(());
            }
        };

        // If the app key is in use by an account
        if let Some(existing_account) = account_service
            .fetch_account_by_app_pubkey(FetchAccountByAuthKeyInput {
                pubkey: app_auth_pubkey,
            })
            .await?
        {
            // And the request doesn't pass idempotency checks, error
            if !is_repeat_account_creation(request, &existing_account) {
                return Err(AccountValidationError::AppAuthPubkeyReuseAccount);
            }

            return Err(AccountValidationError::DuplicateAccountForKeys(
                existing_account,
            ));
        }

        Ok(())
    }
}
