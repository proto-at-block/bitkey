use tracing::instrument;

use super::Service;
use crate::error::TransactionVerificationError;

use types::{
    account::identifiers::AccountId,
    transaction_verification::{
        entities::{TransactionVerification, TransactionVerificationDiscriminants},
        TransactionVerificationId,
    },
};

impl Service {
    #[instrument(skip(self))]
    pub async fn cancel(
        &self,
        account_id: &AccountId,
        id: &TransactionVerificationId,
    ) -> Result<TransactionVerification, TransactionVerificationError> {
        let tx_verification = self.repo.fetch(id).await?;
        if tx_verification.common_fields().account_id != *account_id {
            return Err(TransactionVerificationError::InvalidVerificationId);
        }

        let pending_verification = match tx_verification {
            TransactionVerification::Pending(pending_verification) => pending_verification,
            TransactionVerification::Success(_) => {
                return Err(TransactionVerificationError::InvalidVerificationState(
                    TransactionVerificationDiscriminants::Pending,
                    TransactionVerificationDiscriminants::from(&tx_verification),
                ));
            }
            TransactionVerification::Expired(_) | TransactionVerification::Failed(_) => {
                return Ok(tx_verification);
            }
        };

        // Update the transaction verification status to failed
        let updated = pending_verification.mark_as_failed();

        // Save the updated transaction verification status
        self.repo.persist(&updated).await?;

        Ok(updated)
    }
}
