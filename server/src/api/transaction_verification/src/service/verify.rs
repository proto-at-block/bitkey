use tracing::instrument;

use super::Service;
use crate::error::TransactionVerificationError;
use types::transaction_verification::TransactionVerificationId;
use types::{
    transaction_verification::entities::{
        TransactionVerification, TransactionVerificationDiscriminants,
    },
    transaction_verification::router::TransactionVerificationGrantView,
};

impl Service {
    #[instrument(skip(self))]
    pub async fn verify_with_confirmation_token(
        &self,
        id: &TransactionVerificationId,
        token: &str,
    ) -> Result<TransactionVerification, TransactionVerificationError> {
        let tx_verification = self.repo.fetch(id).await?;

        // Ensure the transaction is in a pending state
        let TransactionVerification::Pending(pending_verification) = tx_verification else {
            return Err(TransactionVerificationError::InvalidVerificationState(
                TransactionVerificationDiscriminants::Pending,
                TransactionVerificationDiscriminants::from(&tx_verification),
            ));
        };

        // Verify that the token matches the confirmation token
        if !pending_verification.is_confirmation_token(token) {
            return Err(TransactionVerificationError::InvalidTokenForCompletion);
        }

        // Try to retrieve the account to get the hardware_auth_pubkey
        let account = self
            .account_service
            .fetch_full_account(account::service::FetchAccountInput {
                account_id: &pending_verification.common_fields.account_id,
            })
            .await?;

        // Use grant service to approve the PSBT
        let hardware_auth_pubkey = account.hardware_auth_pubkey;
        let psbt_str = pending_verification.common_fields.psbt.to_string();

        let grant = self
            .grant_service
            .approve_psbt(&psbt_str, hardware_auth_pubkey)
            .await
            .map_err(TransactionVerificationError::from)?;

        // Create the signed hardware grant view
        let signed_hw_grant = TransactionVerificationGrantView {
            version: grant.version,
            hw_auth_public_key: grant.hw_auth_public_key,
            reverse_hash_chain: grant.reverse_hash_chain,
            commitment: grant.commitment,
            signature: grant.signature,
        };

        // Update the transaction verification status to success with the signed grant
        let updated = pending_verification.mark_as_success(signed_hw_grant);

        // Save the updated transaction verification status
        self.repo.persist(&updated).await?;

        Ok(updated)
    }

    #[instrument(skip(self))]
    pub async fn verify_with_cancellation_token(
        &self,
        id: &TransactionVerificationId,
        token: &str,
    ) -> Result<TransactionVerification, TransactionVerificationError> {
        let tx_verification = self.repo.fetch(id).await?;

        // Ensure the transaction is in a pending state
        let TransactionVerification::Pending(pending_verification) = tx_verification else {
            return Err(TransactionVerificationError::InvalidVerificationState(
                TransactionVerificationDiscriminants::Pending,
                TransactionVerificationDiscriminants::from(&tx_verification),
            ));
        };

        // Verify that the token matches the cancellation token
        if !pending_verification.is_cancellation_token(token) {
            return Err(TransactionVerificationError::InvalidTokenForCompletion);
        }

        // Mark the transaction as failed (canceled)
        let updated = pending_verification.mark_as_failed();

        // Save the updated transaction verification status
        self.repo.persist(&updated).await?;

        Ok(updated)
    }
}
