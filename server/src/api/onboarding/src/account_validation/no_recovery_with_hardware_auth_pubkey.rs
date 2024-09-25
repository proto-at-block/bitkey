use account::service::Service as AccountService;
use async_trait::async_trait;
use recovery::repository::RecoveryRepository;

use crate::routes::Config;

use super::{error::AccountValidationError, AccountValidationRequest, Rule};

pub(crate) struct NoRecoveryWithHardwareAuthPubkeyRule;

#[async_trait]
impl Rule for NoRecoveryWithHardwareAuthPubkeyRule {
    async fn validate(
        &self,
        request: &AccountValidationRequest,
        _: &Config,
        _: &AccountService,
        recovery_repository: &RecoveryRepository,
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

        // If the hardware key is the destination of a pending recovery, error
        if recovery_repository
            .fetch_optional_recovery_by_hardware_auth_pubkey(
                hw_auth_pubkey,
                recovery::entities::RecoveryStatus::Pending,
            )
            .await?
            .is_some()
        {
            return Err(AccountValidationError::HwAuthPubkeyReuseRecovery)?;
        }
        Ok(())
    }
}
