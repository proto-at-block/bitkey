use account::entities::Account;
use tokio::join;
use tracing::instrument;
use types::account::identifiers::AccountId;
use types::recovery::inheritance::claim::{
    InheritanceClaim, InheritanceClaimCanceled, InheritanceClaimCanceledBy, InheritanceClaimId,
};
use types::recovery::social::relationship::RecoveryRelationship;
use types::recovery::trusted_contacts::TrustedContactRole::Beneficiary;

use super::{error::ServiceError, Service};
use crate::service::social::relationship::get_recovery_relationships::{
    GetRecoveryRelationshipsInput, GetRecoveryRelationshipsOutput,
};

pub struct CancelInheritanceClaimInput<'a> {
    pub account: &'a Account,
    pub inheritance_claim_id: InheritanceClaimId,
}

impl Service {
    /// This function cancels an inheritance claim for a valid benefactor and beneficiary.
    /// The claim must be in a pending state in order for it to be canceled and there must
    /// be a valid recovery relationship between the benefactor and beneficiary.
    ///
    /// If the claim has already been canceled, the existing canceled claim will be returned.
    ///
    /// # Arguments
    ///
    /// * `input` - Contains the beneficiary or benefactor account and inheritance claim id to be cancelled
    ///
    /// # Returns
    ///
    /// * The canceled inheritance claim
    ///     
    #[instrument(skip(self, input))]
    pub async fn cancel_claim(
        &self,
        input: CancelInheritanceClaimInput<'_>,
    ) -> Result<(InheritanceClaimCanceledBy, InheritanceClaim), ServiceError> {
        // Check to see if the given Recovery Relationships are valid
        let (relationships, claim) = fetch_relationships_and_claim(
            self,
            input.account.get_id(),
            &input.inheritance_claim_id,
        )
        .await?;

        if !matches!(claim, InheritanceClaim::Pending(_))
            && !matches!(claim, InheritanceClaim::Canceled(_))
        {
            return Err(ServiceError::InvalidClaimStateForCancelation);
        }

        // Determine the role of the account in the inheritance claim
        let recovery_relationship_id = claim.common_fields().recovery_relationship_id.to_owned();
        let canceled_by = if relationships
            .endorsed_trusted_contacts
            .iter()
            .any(|r| r.common_fields().id == recovery_relationship_id)
        {
            InheritanceClaimCanceledBy::Benefactor
        } else if relationships.customers.iter().any(|r| {
            r.common_fields().id == recovery_relationship_id
                && matches!(r, RecoveryRelationship::Endorsed(_))
        }) {
            InheritanceClaimCanceledBy::Beneficiary
        } else {
            return Err(ServiceError::MismatchingRecoveryRelationship);
        };

        let pending_claim = match claim {
            InheritanceClaim::Pending(pending_claim) => pending_claim,
            InheritanceClaim::Canceled(_) => return Ok((canceled_by, claim)), // If the recovery relationship is valid, return the existing canceled claim
            _ => return Err(ServiceError::InvalidClaimStateForCancelation), // This should not happen due to the previous check
        };

        // TODO[W-9712]: Add contestation scenario for Lite Accounts

        let updated_claim = InheritanceClaim::Canceled(InheritanceClaimCanceled {
            common_fields: pending_claim.common_fields.clone(),
            canceled_by: canceled_by.to_owned(),
        });
        let claim = self
            .repository
            .persist_inheritance_claim(&updated_claim)
            .await?;

        Ok((canceled_by, claim))
    }
}

/// This function fetches the recovery relationships and inheritance claim
/// for a given beneficiary account and inheritance claim id
///
/// # Arguments
///
/// * `service` - The inheritance service object
/// * `account_id` - The account id
/// * `inheritance_claim_id` - The identifier for the inheritance claim
///
async fn fetch_relationships_and_claim(
    service: &Service,
    account_id: &AccountId,
    inheritance_claim_id: &InheritanceClaimId,
) -> Result<(GetRecoveryRelationshipsOutput, InheritanceClaim), ServiceError> {
    let (relationships_result, claim_result) = join!(
        service
            .recovery_relationship_service
            .get_recovery_relationships(GetRecoveryRelationshipsInput {
                account_id,
                trusted_contact_role: Some(Beneficiary),
            }),
        service
            .repository
            .fetch_inheritance_claim(inheritance_claim_id)
    );
    Ok((relationships_result?, claim_result?))
}
