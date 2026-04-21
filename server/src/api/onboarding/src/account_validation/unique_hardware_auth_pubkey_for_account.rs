use account::service::{FetchAccountInput, Service as AccountService};
use async_trait::async_trait;
use database::ddb::DatabaseError;
use recovery::repository::RecoveryRepository;
use repository::public_key::PublicKeyRepository;

use super::{
    error::AccountValidationError, is_repeat_account_creation, AccountValidationRequest, Rule,
};
use crate::routes::Config;

pub(crate) struct UniqueHardwareAuthPubkeyForAccountRule;

#[async_trait]
impl Rule for UniqueHardwareAuthPubkeyForAccountRule {
    async fn validate(
        &self,
        request: &AccountValidationRequest,
        _: &Config,
        account_service: &AccountService,
        _: &RecoveryRepository,
        public_key_repository: &PublicKeyRepository,
    ) -> Result<(), AccountValidationError> {
        // This check only applies to new full account creation.
        // For upgrades, the account_id is stable across retries, so the
        // conditional put in persist_public_key handles both idempotency
        // (same account_id → succeeds) and cross-account conflicts
        // (different account_id → fails) atomically.
        let hw_auth_pubkey = match request {
            AccountValidationRequest::CreateFullAccount { auth, .. } => auth.hardware,
            AccountValidationRequest::CreateFullAccountV2 { auth, .. } => auth.hardware_pub,
            AccountValidationRequest::UpgradeAccount { .. }
            | AccountValidationRequest::UpgradeAccountV2 { .. }
            | AccountValidationRequest::CreateLiteAccount { .. }
            | AccountValidationRequest::CreateSoftwareAccount { .. } => {
                return Ok(());
            }
        };

        // Check if the hw key was ever previously active on any account
        let Some(existing_record) = public_key_repository
            .fetch_by_public_key(&hw_auth_pubkey.to_string())
            .await?
        else {
            return Ok(());
        };

        // Fetch the owning account to check for idempotent create retry.
        // If the account no longer resolves (e.g. orphaned row from a failed
        // prior create), treat the key as claimed and return a clean error
        // rather than propagating ObjectNotFound.
        let existing_account = match account_service
            .fetch_account(FetchAccountInput {
                account_id: &existing_record.account_id,
            })
            .await
        {
            Ok(account) => account,
            Err(account::error::AccountError::DDBError(DatabaseError::ObjectNotFound(_))) => {
                return Err(AccountValidationError::HwAuthPubkeyReuseAccount);
            }
            Err(e) => return Err(e.into()),
        };

        if is_repeat_account_creation(request, &existing_account) {
            return Err(AccountValidationError::DuplicateAccountForKeys(
                existing_account,
            ));
        }

        Err(AccountValidationError::HwAuthPubkeyReuseAccount)
    }
}
