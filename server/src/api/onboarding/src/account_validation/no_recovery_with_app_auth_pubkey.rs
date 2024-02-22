use account::service::Service as AccountService;
use async_trait::async_trait;
use recovery::repository::Repository as RecoveryService;

use crate::routes::Config;

use super::{error::AccountValidationError, AccountValidationRequest, Rule};

pub(crate) struct NoRecoveryWithAppAuthPubkeyRule;

#[async_trait]
impl Rule for NoRecoveryWithAppAuthPubkeyRule {
    async fn validate(
        &self,
        request: &AccountValidationRequest,
        _: &Config,
        _: &AccountService,
        recovery_service: &RecoveryService,
    ) -> Result<(), AccountValidationError> {
        // This check only applies to creating or upgrading to full accounts
        let app_auth_pubkey = match request {
            AccountValidationRequest::CreateFullAccount { auth, .. } => auth.app,
            AccountValidationRequest::UpgradeAccount { auth, .. } => auth.app,
            AccountValidationRequest::CreateLiteAccount { .. } => {
                return Ok(());
            }
        };

        // If the app key is the destination of a pending recovery, error
        if recovery_service
            .fetch_optional_recovery_by_app_auth_pubkey(
                app_auth_pubkey,
                recovery::entities::RecoveryStatus::Pending,
            )
            .await?
            .is_some()
        {
            return Err(AccountValidationError::AppAuthPubkeyReuseRecovery)?;
        }

        Ok(())
    }
}
