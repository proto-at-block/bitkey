use account::error::AccountError;
use tracing::instrument;
use types::account::entities::FullAccount;

use types::recovery::inheritance::claim::{
    InheritanceClaim, InheritanceClaimAuthKeys, InheritanceClaimCanceled,
    InheritanceClaimCanceledBy, InheritanceClaimPending,
};

use super::fetch_pending_claims_as_beneficiary;
use super::{error::ServiceError, Service};

pub struct RecreatePendingClaimsForBeneficiaryInput<'a> {
    pub beneficiary: &'a FullAccount,
}

impl Service {
    /// This function recreates all pending inheritance claims for a valid benefactor and beneficiary.
    /// The claim must be in a pending state in order to be recreated. If the claim has already
    /// been canceled and recreated with the new authentication keys, the existing claim will be returned.
    /// There must be a valid recovery relationship between the benefactor and beneficiary.
    ///
    /// This method does not send any notifications upon cancellation or recreation.
    ///
    /// # Arguments
    ///
    /// * `input` - Contains the beneficiary full account
    ///
    /// # Returns
    ///
    /// * The updated inheritance claims for the beneficiary (recreated if auth keys have changed)
    ///     
    #[instrument(skip(self, input))]
    pub async fn recreate_pending_claims_for_beneficiary(
        &self,
        input: RecreatePendingClaimsForBeneficiaryInput<'_>,
    ) -> Result<Vec<InheritanceClaim>, ServiceError> {
        // Get the active auth keys for the beneficiary
        let full_account_auth_keys = input
            .beneficiary
            .active_auth_keys()
            .ok_or(ServiceError::Account(AccountError::InvalidKeysetState))?;
        let auth_keys = InheritanceClaimAuthKeys::FullAccount(full_account_auth_keys.to_owned());

        // Retrieve all pending claims for the beneficiary
        let pending_claims_as_beneficiary =
            fetch_pending_claims_as_beneficiary(self, &input.beneficiary.id).await?;

        let mut updated_claims: Vec<InheritanceClaim> =
            Vec::with_capacity(pending_claims_as_beneficiary.len());
        for claim in pending_claims_as_beneficiary {
            let pending_claim = match &claim {
                InheritanceClaim::Pending(inheritance_claim_pending) => inheritance_claim_pending,
                _ => return Err(ServiceError::InvalidClaimStateForRecreation),
            };

            // If the claim has already been recreated with the new authentication keys, skip it
            if pending_claim.common_fields.auth_keys == auth_keys {
                updated_claims.push(claim);
                continue;
            }

            let canceled_claim = InheritanceClaim::Canceled(InheritanceClaimCanceled {
                common_fields: pending_claim.common_fields.clone(),
                canceled_by: InheritanceClaimCanceledBy::Beneficiary,
            });
            if !matches!(
                self.repository
                    .persist_inheritance_claim(&canceled_claim)
                    .await?,
                InheritanceClaim::Canceled(_)
            ) {
                return Err(ServiceError::InvalidClaimStateForCancellation);
            }

            let recreate_pending_claim =
                InheritanceClaimPending::recreate(pending_claim, auth_keys)?;
            let recreated_claim = self
                .repository
                .persist_inheritance_claim(&InheritanceClaim::Pending(recreate_pending_claim))
                .await?;
            updated_claims.push(recreated_claim);
        }
        Ok(updated_claims)
    }
}
