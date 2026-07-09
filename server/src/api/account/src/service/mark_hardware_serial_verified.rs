use tracing::instrument;
use types::account::entities::Account;
use types::account::identifiers::{AccountId, KeysetId};
use types::account::spending::{AttestedHardwareSerial, SpendingKeyset};

use super::Service;
use crate::error::AccountError;

impl Service {
    /// Promote a keyset's `attested_hardware_serial` from `Pending` to
    /// `Verified` after hardware-serial OOBA. Idempotent on `Verified`.
    /// Errors: [`AccountError::InvalidAccountType`] if not a `FullAccount`,
    /// [`AccountError::HardwareAttestationMissing`] if the keyset has no
    /// attestation to promote.
    #[instrument(skip(self))]
    pub async fn mark_hardware_serial_verified(
        &self,
        account_id: &AccountId,
        keyset_id: &KeysetId,
    ) -> Result<(), AccountError> {
        let account = self.account_repo.fetch(account_id).await?;
        let Account::Full(mut full_account) = account else {
            return Err(AccountError::InvalidAccountType);
        };

        let keyset = full_account
            .spending_keysets
            .get_mut(keyset_id)
            .ok_or(AccountError::HardwareAttestationMissing)?;
        let SpendingKeyset::PrivateMultiSig(private) = keyset else {
            return Err(AccountError::HardwareAttestationMissing);
        };

        match private.attested_hardware_serial.take() {
            Some(state @ AttestedHardwareSerial::Verified(_)) => {
                // Already verified; restore and exit without re-persisting.
                private.attested_hardware_serial = Some(state);
                return Ok(());
            }
            Some(pending @ AttestedHardwareSerial::Pending(_)) => {
                private.attested_hardware_serial = Some(pending.verify());
            }
            None => return Err(AccountError::HardwareAttestationMissing),
        }

        self.account_repo
            .persist(&Account::Full(full_account))
            .await?;
        Ok(())
    }
}
