use time::OffsetDateTime;

use crate::{
    entities::{CommonAccountFields, CommsVerificationStatus},
    error::AccountError,
};

use super::{FetchAccountInput, PutCommsVerificationClaimInput, Service};

impl Service {
    pub async fn put_comms_verification_claim(
        &self,
        input: PutCommsVerificationClaimInput,
    ) -> Result<(), AccountError> {
        let account = self
            .fetch_account(FetchAccountInput {
                account_id: &input.account_id,
            })
            .await?;

        let common_fields = account.get_common_fields().clone();
        let mut comms_verification_claims = common_fields.comms_verification_claims;

        // Purge claims for the same scope (and expired ones)
        comms_verification_claims.retain(|c| {
            if c.scope == input.claim.scope {
                return false;
            }

            let now = OffsetDateTime::now_utc();
            if let CommsVerificationStatus::Pending { expires_at, .. } = c.status {
                return now < expires_at;
            }

            if let CommsVerificationStatus::Verified { expires_at } = c.status {
                return now < expires_at;
            }

            true
        });

        // Add new claim
        comms_verification_claims.push(input.claim);
        let updated_account = account.update(CommonAccountFields {
            comms_verification_claims,
            ..common_fields
        })?;
        self.account_repo.persist(&updated_account).await?;

        Ok(())
    }
}
