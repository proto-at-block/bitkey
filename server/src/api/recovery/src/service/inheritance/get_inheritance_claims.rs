use futures::future::try_join_all;
use tracing::instrument;
use types::{
    account::identifiers::AccountId,
    recovery::{inheritance::claim::InheritanceClaim, trusted_contacts::TrustedContactRole},
};

use crate::service::social::relationship::get_recovery_relationships::GetRecoveryRelationshipsInput;

use super::{error::ServiceError, Service};

/// The input for the `get_inheritance_claims` function
///
/// # Fields
///
/// * `account_id` - The account that is trying to retrieve inheritance claims
pub struct GetInheritanceClaimsInput<'a> {
    pub account_id: &'a AccountId,
}

/// The output for the `get_inheritance_claims` function
///
/// # Fields
///
/// * `claims_as_benefactor` - The list of claims for which the account is the benefactor
/// * `claims_as_beneficiary` - The list of claims for which the account is the beneficiary
pub struct GetInheritanceClaimsOutput {
    pub claims_as_benefactor: Vec<InheritanceClaim>,
    pub claims_as_beneficiary: Vec<InheritanceClaim>,
}

impl Service {
    #[instrument(skip(self, input))]
    pub async fn get_inheritance_claims(
        &self,
        input: GetInheritanceClaimsInput<'_>,
    ) -> Result<GetInheritanceClaimsOutput, ServiceError> {
        let relationships = self
            .recovery_relationship_service
            .get_recovery_relationships(GetRecoveryRelationshipsInput {
                account_id: input.account_id,
                trusted_contact_role: Some(TrustedContactRole::Beneficiary),
            })
            .await?;

        // TODO: Investigate if we can/should use ddb batches instead of joining

        let claims_as_beneficiary =
            try_join_all(relationships.customers.iter().map(|benefactor| {
                self.repository
                    .fetch_claims_for_recovery_relationship_id(&benefactor.common_fields().id)
            }))
            .await?
            .into_iter()
            .flatten()
            .collect();

        let claims_as_benefactor = try_join_all(
            relationships
                .endorsed_trusted_contacts
                .iter()
                .map(|beneficiary| {
                    self.repository
                        .fetch_claims_for_recovery_relationship_id(&beneficiary.common_fields().id)
                }),
        )
        .await?
        .into_iter()
        .flatten()
        .collect();

        Ok(GetInheritanceClaimsOutput {
            claims_as_benefactor,
            claims_as_beneficiary,
        })
    }
}
