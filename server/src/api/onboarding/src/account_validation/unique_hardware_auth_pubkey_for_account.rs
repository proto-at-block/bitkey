use account::service::{FetchAccountByAuthKeyInput, Service as AccountService};
use async_trait::async_trait;
use recovery::repository::RecoveryRepository;

use crate::routes::Config;

use super::{
    error::AccountValidationError, is_repeat_account_creation, AccountValidationRequest, Rule,
};

pub(crate) struct UniqueHardwareAuthPubkeyForAccountRule;

#[async_trait]
impl Rule for UniqueHardwareAuthPubkeyForAccountRule {
    async fn validate(
        &self,
        request: &AccountValidationRequest,
        _: &Config,
        account_service: &AccountService,
        _: &RecoveryRepository,
    ) -> Result<(), AccountValidationError> {
        // This check only applies to creating or upgrading to full accounts
        let hw_auth_pubkey = match request {
            AccountValidationRequest::CreateFullAccount { auth, .. } => auth.hardware,
            AccountValidationRequest::UpgradeAccount { auth, .. } => auth.hardware,
            AccountValidationRequest::CreateLiteAccount { .. }
            | AccountValidationRequest::CreateSoftwareAccount { .. } => {
                return Ok(());
            }
        };

        // If the hw key is in use by an account
        if let Some(existing_account) = account_service
            .fetch_account_by_hw_pubkey(FetchAccountByAuthKeyInput {
                pubkey: hw_auth_pubkey,
            })
            .await?
        {
            // And the request doesn't pass idempotency checks, error
            if !is_repeat_account_creation(request, &existing_account) {
                return Err(AccountValidationError::HwAuthPubkeyReuseAccount);
            }

            return Err(AccountValidationError::DuplicateAccountForKeys(
                existing_account,
            ));
        }

        Ok(())
    }
}
