use tracing::instrument;
use types::account::entities::{Account, HardwareType};
use types::account::identifiers::AccountId;

use super::Service;
use crate::error::AccountError;

impl Service {
    /// Conditionally flip `FullAccount::hardware_verification_required` from
    /// `false` to `true`, deferring the gate decision to a caller-supplied
    /// predicate. Returns `Ok(true)` when the flag was flipped and persisted,
    /// `Ok(false)` for no-op cases (already enrolled, predicate returned
    /// false, not a `FullAccount`, no derivable hardware type).
    ///
    /// The closure pattern keeps LD-eval logic at the route handler (where
    /// `feature_flags` + `ExperimentationClaims` live) without requiring
    /// `account` to depend on `experimentation` (which would cycle).
    #[instrument(skip(self, should_enroll))]
    pub async fn maybe_mark_hardware_verification_required<F>(
        &self,
        account_id: &AccountId,
        should_enroll: F,
    ) -> Result<bool, AccountError>
    where
        F: FnOnce(HardwareType) -> bool,
    {
        let account = self.account_repo.fetch(account_id).await?;
        let Account::Full(full_account) = account else {
            return Ok(false);
        };
        if full_account.hardware_verification_required {
            return Ok(false);
        }
        let Ok(hardware_type) = full_account.active_hardware_type() else {
            return Ok(false);
        };
        if !should_enroll(hardware_type) {
            return Ok(false);
        }
        let mut updated = full_account;
        updated.hardware_verification_required = true;
        self.account_repo.persist(&Account::Full(updated)).await?;
        Ok(true)
    }
}
