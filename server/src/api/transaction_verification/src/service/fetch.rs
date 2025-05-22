use tracing::instrument;

use types::{
    account::identifiers::AccountId,
    transaction_verification::{entities::TransactionVerification, TransactionVerificationId},
};

use crate::error::TransactionVerificationError;

use super::Service;

impl Service {
    #[instrument(skip(self))]
    pub async fn fetch(
        &self,
        account_id: &AccountId,
        verification_id: &TransactionVerificationId,
    ) -> Result<TransactionVerification, TransactionVerificationError> {
        let tx_verification = self.repo.fetch(verification_id).await?;
        if tx_verification.common_fields().account_id != *account_id {
            return Err(TransactionVerificationError::InvalidVerificationId);
        }
        Ok(tx_verification)
    }
}
