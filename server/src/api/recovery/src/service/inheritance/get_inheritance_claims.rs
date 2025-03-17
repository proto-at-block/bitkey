use tokio::try_join;
use tracing::instrument;
use types::{
    account::identifiers::AccountId,
    recovery::inheritance::claim::{InheritanceClaim, InheritanceRole},
};

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
        let (claims_as_benefactor, claims_as_beneficiary) = try_join!(
            self.repository.fetch_claims_for_account_id_and_role(
                input.account_id,
                InheritanceRole::Benefactor
            ),
            self.repository.fetch_claims_for_account_id_and_role(
                input.account_id,
                InheritanceRole::Beneficiary
            ),
        )?;
        Ok(GetInheritanceClaimsOutput {
            claims_as_benefactor,
            claims_as_beneficiary,
        })
    }
}
