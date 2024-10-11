use super::social::relationship::Service as RecoveryRelationshipService;
use crate::service::inheritance::error::ServiceError;
use crate::service::social::relationship::get_recovery_relationships::{
    GetRecoveryRelationshipsInput, GetRecoveryRelationshipsOutput,
};
use account::service::Service as AccountService;
use feature_flags::service::Service as FeatureFlagsService;
use notification::service::Service as NotificationService;
use repository::recovery::inheritance::InheritanceRepository;
use tokio::join;
use types::account::identifiers::AccountId;
use types::recovery::inheritance::claim::{InheritanceClaim, InheritanceClaimId};
use types::recovery::social::relationship::{RecoveryRelationship, RecoveryRelationshipId};
use types::recovery::trusted_contacts::TrustedContactRole::Beneficiary;

pub mod cancel_inheritance_claim;
pub mod create_inheritance_claim;
pub mod packages;
pub mod update_inheritance_claim_destination;

mod error;
pub mod get_inheritance_claims;
pub mod lock_inheritance_claim;

#[cfg(test)]
mod tests;

#[derive(Clone)]
pub struct Service {
    pub repository: InheritanceRepository,
    pub recovery_relationship_service: RecoveryRelationshipService,
    pub notification_service: NotificationService,
    pub account_service: AccountService,
    pub feature_flags_service: FeatureFlagsService,
}

impl Service {
    #[must_use]
    pub fn new(
        repository: InheritanceRepository,
        recovery_relationship_service: RecoveryRelationshipService,
        notification_service: NotificationService,
        account_service: AccountService,
        feature_flags_service: FeatureFlagsService,
    ) -> Self {
        Self {
            repository,
            recovery_relationship_service,
            notification_service,
            account_service,
            feature_flags_service,
        }
    }
}

/// This function fetches the recovery relationships for a given beneficiary account
/// as well as the claim, if it exists, for a given inheritance claim id
///
/// # Arguments
///
/// * `service` - The inheritance service object
/// * `beneficiary_account_id` - The beneficiary account id
/// * `inheritance_claim_id` - The inheritance claim id
///
async fn fetch_relationships_and_claim(
    service: &Service,
    beneficiary_account_id: &AccountId,
    inheritance_claim_id: &InheritanceClaimId,
) -> Result<(GetRecoveryRelationshipsOutput, InheritanceClaim), ServiceError> {
    let (relationships_result, claim_result) = join!(
        service
            .recovery_relationship_service
            .get_recovery_relationships(GetRecoveryRelationshipsInput {
                account_id: beneficiary_account_id,
                trusted_contact_role_filter: Some(Beneficiary),
            }),
        service
            .repository
            .fetch_inheritance_claim(inheritance_claim_id)
    );
    Ok((relationships_result?, claim_result?))
}

fn filter_endorsed_relationship(
    customers: Vec<RecoveryRelationship>,
    recovery_relationship_id: &RecoveryRelationshipId,
) -> Result<RecoveryRelationship, ServiceError> {
    // Ensure relationship still exists between benefactor and beneficiary
    let relationship = customers
        .into_iter()
        .find(|r| &r.common_fields().id == recovery_relationship_id)
        .ok_or(ServiceError::MismatchingRecoveryRelationship)?;

    // Ensure the relationship is endorsed by the customer
    if !matches!(relationship, RecoveryRelationship::Endorsed(_)) {
        return Err(ServiceError::MismatchingRecoveryRelationship);
    }
    Ok(relationship)
}
