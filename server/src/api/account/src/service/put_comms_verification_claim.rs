use time::OffsetDateTime;

use crate::{
    entities::{CommsVerificationStatus, FullAccount},
    error::AccountError,
};

use super::{FetchAccountInput, PutCommsVerificationClaimInput, Service};

impl Service {
    pub async fn put_comms_verification_claim(
        &self,
        input: PutCommsVerificationClaimInput,
    ) -> Result<(), AccountError> {
        let full_account = self
            .fetch_full_account(FetchAccountInput {
                account_id: &input.account_id,
            })
            .await?;
        let mut comms_verification_claims = full_account.comms_verification_claims;

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
        let updated_account = FullAccount {
            comms_verification_claims,
            ..full_account
        };
        self.repo.persist(&updated_account.into()).await?;

        Ok(())
    }
}
