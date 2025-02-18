use tracing::instrument;

use types::account::identifiers::AccountId;
use types::recovery::inheritance::claim::InheritanceClaim;
use types::recovery::social::relationship::RecoveryRelationshipId;

use crate::service::inheritance::get_inheritance_claims::GetInheritanceClaimsInput;

use super::error::ServiceError;
use super::Service;

/// The input for the `has_incompleted_claim` function
///
/// # Fields
///
/// * `actor_account_id` - The account that is checking for an incompleted claim
/// * `recovery_relationship_id` - The recovery relationship that is being checked for an incompleted claim
pub struct HasIncompletedClaimInput<'a> {
    pub actor_account_id: &'a AccountId,
    pub recovery_relationship_id: &'a RecoveryRelationshipId,
}

/// The output for the `has_incompleted_claim` function
///
/// # Fields
///
/// * `as_benefactor` - Whether the account has incomplete claims as a benefactor
/// * `as_beneficiary` - Whether the account has incomplete claims as a beneficiary
pub struct HasIncompletedClaimOutput {
    pub as_benefactor: bool,
    pub as_beneficiary: bool,
}

impl Service {
    #[instrument(skip(self, input))]
    pub async fn has_incompleted_claim(
        &self,
        input: HasIncompletedClaimInput<'_>,
    ) -> Result<HasIncompletedClaimOutput, ServiceError> {
        let claims = self
            .get_inheritance_claims(GetInheritanceClaimsInput {
                account_id: input.actor_account_id,
            })
            .await?;

        Ok(HasIncompletedClaimOutput {
            as_benefactor: has_active_claim_for_relationship(
                &claims.claims_as_benefactor,
                input.recovery_relationship_id,
            ),
            as_beneficiary: has_active_claim_for_relationship(
                &claims.claims_as_beneficiary,
                input.recovery_relationship_id,
            ),
        })
    }
}

fn has_active_claim_for_relationship(
    claims: &[InheritanceClaim],
    relationship_id: &RecoveryRelationshipId,
) -> bool {
    claims.iter().any(|claim| {
        claim.common_fields().recovery_relationship_id == *relationship_id
            && matches!(
                claim,
                InheritanceClaim::Pending { .. } | InheritanceClaim::Locked { .. }
            )
    })
}
