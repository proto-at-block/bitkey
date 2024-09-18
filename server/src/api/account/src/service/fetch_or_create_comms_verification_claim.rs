use crate::{
    entities::{CommonAccountFields, CommsVerificationClaim},
    error::AccountError,
};

use super::{FetchAccountInput, FetchOrCreateCommsVerificationClaimInput, Service};

impl Service {
    pub async fn fetch_or_create_comms_verification_claim(
        &self,
        input: FetchOrCreateCommsVerificationClaimInput<'_>,
    ) -> Result<CommsVerificationClaim, AccountError> {
        let account = self
            .fetch_account(FetchAccountInput {
                account_id: input.account_id,
            })
            .await?;

        let common_fields = account.get_common_fields().clone();

        if let Some(existing_claim) = common_fields
            .comms_verification_claims
            .iter()
            .find(|c| c.scope == input.scope)
        {
            return Ok(existing_claim.to_owned());
        }

        let new_claim = CommsVerificationClaim::new(input.scope, None);

        // Add new claim
        let mut comms_verification_claims = common_fields.comms_verification_claims;
        comms_verification_claims.push(new_claim.to_owned());

        let updated_account = account.update(CommonAccountFields {
            comms_verification_claims,
            ..common_fields
        })?;

        self.account_repo.persist(&updated_account).await?;

        Ok(new_claim)
    }
}
