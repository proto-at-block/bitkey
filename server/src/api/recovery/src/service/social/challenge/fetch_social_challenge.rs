use tracing::instrument;
use types::{
    account::identifiers::AccountId,
    recovery::social::{
        challenge::{SocialChallenge, SocialChallengeId},
        relationship::{RecoveryRelationship, RecoveryRelationshipId},
    },
};

use super::{error::ServiceError, Service};

pub struct FetchSocialChallengeAsCustomerInput<'a> {
    pub customer_account_id: &'a AccountId,
    pub social_challenge_id: &'a SocialChallengeId,
}

pub struct FetchSocialChallengeAsTrustedContactInput<'a> {
    pub trusted_contact_account_id: &'a AccountId,
    pub recovery_relationship_id: &'a RecoveryRelationshipId,
    pub counter: u32,
}

pub struct CountSocialChallengesInput<'a> {
    pub customer_account_id: &'a AccountId,
}

impl Service {
    #[instrument(skip(self, input))]
    pub async fn fetch_social_challenge_as_customer(
        &self,
        input: FetchSocialChallengeAsCustomerInput<'_>,
    ) -> Result<SocialChallenge, ServiceError> {
        let challenge = self
            .repository
            .fetch_social_challenge(input.social_challenge_id)
            .await?;

        if challenge.customer_account_id != *input.customer_account_id {
            return Err(ServiceError::AccountNotCustomer);
        }

        Ok(challenge)
    }

    #[instrument(skip(self, input))]
    pub async fn fetch_social_challenge_as_trusted_contact(
        &self,
        input: FetchSocialChallengeAsTrustedContactInput<'_>,
    ) -> Result<SocialChallenge, ServiceError> {
        // Only fetch the Social Challenge if the Recovery Relationship is endorsed
        let (common_fields, connection_fields) = match self
            .repository
            .fetch_recovery_relationship(input.recovery_relationship_id)
            .await?
        {
            RecoveryRelationship::Invitation(_) | RecoveryRelationship::Unendorsed(_) => {
                return Err(ServiceError::RecoveryRelationshipStatusMismatch)
            }
            RecoveryRelationship::Endorsed(r) => (r.common_fields, r.connection_fields),
        };

        if connection_fields.trusted_contact_account_id != *input.trusted_contact_account_id {
            return Err(ServiceError::AccountNotTrustedContact);
        }

        let id = SocialChallengeId::derive(&common_fields.customer_account_id, input.counter);

        let challenge = self.repository.fetch_social_challenge(&id).await?;

        Ok(challenge)
    }

    #[instrument(skip(self, input))]
    pub async fn count_social_challenges(
        &self,
        input: CountSocialChallengesInput<'_>,
    ) -> Result<usize, ServiceError> {
        Ok(self
            .repository
            .count_social_challenges_for_customer(input.customer_account_id)
            .await?)
    }
}
