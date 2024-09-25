use account::entities::Account;
use tokio::join;
use tracing::instrument;
use types::account::identifiers::AccountId;
use types::recovery::inheritance::claim::InheritanceClaim;
use types::recovery::social::relationship::RecoveryRelationship;
use types::recovery::trusted_contacts::TrustedContactRole::Beneficiary;
use types::recovery::{
    inheritance::claim::{InheritanceClaimAuthKeys, InheritanceClaimId},
    social::relationship::RecoveryRelationshipId,
};

use super::{error::ServiceError, Service};
use crate::service::social::relationship::get_recovery_relationships::{
    GetRecoveryRelationshipsInput, GetRecoveryRelationshipsOutput,
};

pub struct CreateInheritanceClaimInput<'a> {
    pub beneficiary_account: &'a Account,
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub auth_keys: InheritanceClaimAuthKeys,
}

impl Service {
    /// This function starts an inheritance claim for a valid benefactor and beneficiary.
    /// There must be no pending claims between the benefactor and beneficiary.
    /// There must also be a valid recovery relationship between the benefactor and beneficiary.
    ///
    /// # Arguments
    ///
    /// * `input` - Contains the beneficiary account, recovery relationship id, and auth keys
    ///
    /// # Returns
    ///
    /// * The newly created inheritance claim
    ///     
    #[instrument(skip(self, input))]
    pub async fn create_claim(
        &self,
        input: CreateInheritanceClaimInput<'_>,
    ) -> Result<InheritanceClaim, ServiceError> {
        // Check to see if the given Recovery Relationships are valid
        let (relationships, all_claims) = fetch_relationships_and_claims(
            self,
            input.beneficiary_account.get_id(),
            &input.recovery_relationship_id,
        )
        .await?;

        // Ensure relationship still exists between benefactor and beneficiary
        let relationship = relationships
            .customers
            .into_iter()
            .find(|r| r.common_fields().id == input.recovery_relationship_id)
            .ok_or(ServiceError::MismatchingRecoveryRelationship)?;

        // Ensure the relationship is endorsed by the customer
        if !matches!(relationship, RecoveryRelationship::Endorsed(_)) {
            return Err(ServiceError::MismatchingRecoveryRelationship);
        }

        // TODO: Ensure that there is a package for this relationship after W-9369

        // Ensure no pending claims for the given relationship
        check_claims_status(&all_claims)?;

        // TODO: Add contestation scenario for Lite Accounts

        let id = InheritanceClaimId::gen()?;
        let claim = self
            .repository
            .persist_inheritance_claim(&InheritanceClaim::new_claim(
                id,
                input.recovery_relationship_id,
                input.auth_keys,
            ))
            .await?;

        Ok(claim)
    }
}

/// This function fetches the recovery relationships and inheritance claims
/// for a given beneficiary account and recovery relationship id
///
/// # Arguments
///
/// * `service` - The inheritance service object
/// * `beneficiary_account_id` - The beneficiary account id
/// * `recovery_relationship_id` - The recovery relationship id
///
async fn fetch_relationships_and_claims(
    service: &Service,
    beneficiary_account_id: &AccountId,
    recovery_relationship_id: &RecoveryRelationshipId,
) -> Result<(GetRecoveryRelationshipsOutput, Vec<InheritanceClaim>), ServiceError> {
    let (relationships_result, all_claims_result) = join!(
        service
            .recovery_relationship_service
            .get_recovery_relationships(GetRecoveryRelationshipsInput {
                account_id: beneficiary_account_id,
                trusted_contact_role: Some(Beneficiary),
            }),
        service
            .repository
            .fetch_claims_for_recovery_relationship_id(recovery_relationship_id)
    );
    Ok((relationships_result?, all_claims_result?))
}

/// This function checks if there are any pending claims for the given relationship
///
/// # Arguments
///
/// * `claims` - A list of inheritance claims
///
/// # Errors
///
/// * If there are any pending claims
///
fn check_claims_status(claims: &[InheritanceClaim]) -> Result<(), ServiceError> {
    if claims
        .iter()
        .any(|claim| matches!(claim, InheritanceClaim::Pending(_)))
    {
        return Err(ServiceError::PendingClaimExists);
    }

    Ok(())
}
