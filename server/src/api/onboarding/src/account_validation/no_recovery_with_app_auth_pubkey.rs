use account::service::Service as AccountService;
use async_trait::async_trait;
use recovery::repository::RecoveryRepository;

use super::{error::AccountValidationError, AccountValidationRequest, Rule};
use crate::routes::Config;

pub(crate) struct NoRecoveryWithAppAuthPubkeyRule;

#[async_trait]
impl Rule for NoRecoveryWithAppAuthPubkeyRule {
    async fn validate(
        &self,
        request: &AccountValidationRequest,
        _: &Config,
        _: &AccountService,
        recovery_repository: &RecoveryRepository,
    ) -> Result<(), AccountValidationError> {
        // This check only applies to creating or upgrading to full accounts
        let app_auth_pubkey = match request {
            AccountValidationRequest::CreateFullAccount { auth, .. } => auth.app,
            AccountValidationRequest::UpgradeAccount { auth, .. } => auth.app,
            AccountValidationRequest::CreateSoftwareAccount { auth, .. } => auth.app,
            AccountValidationRequest::CreateFullAccountV2 { auth, .. } => auth.app_pub,
            AccountValidationRequest::UpgradeAccountV2 { auth, .. } => auth.app_pub,
            AccountValidationRequest::CreateLiteAccount { .. } => {
                return Ok(());
            }
        };

        // If the app key is the destination of a pending recovery, error
        if recovery_repository
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
