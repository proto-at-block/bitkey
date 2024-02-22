use account::service::Service as AccountService;
use async_trait::async_trait;
use recovery::repository::Repository as RecoveryService;

use crate::routes::Config;

use super::{error::AccountValidationError, AccountValidationRequest, Rule};

pub(crate) struct NoRecoveryWithRecoveryAuthPubkeyRule;

#[async_trait]
impl Rule for NoRecoveryWithRecoveryAuthPubkeyRule {
    async fn validate(
        &self,
        request: &AccountValidationRequest,
        _: &Config,
        _: &AccountService,
        recovery_service: &RecoveryService,
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
            AccountValidationRequest::UpgradeAccount { .. } => {
                return Ok(());
            }
        };

        // If the app key is the destination of a pending recovery, error
        if recovery_service
            .fetch_optional_recovery_by_recovery_auth_pubkey(
                recovery_auth_pubkey,
                recovery::entities::RecoveryStatus::Pending,
            )
            .await?
            .is_some()
        {
            return Err(AccountValidationError::RecoveryAuthPubkeyReuseRecovery)?;
        }

        Ok(())
    }
}
