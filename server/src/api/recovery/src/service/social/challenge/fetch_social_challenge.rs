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
    pub code: &'a str,
}

impl Service {
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

    pub async fn fetch_social_challenge_as_trusted_contact(
        &self,
        input: FetchSocialChallengeAsTrustedContactInput<'_>,
    ) -> Result<SocialChallenge, ServiceError> {
        //TODO(BKR-919): Fix this once we update the social challenge
        let (common_fields, connection_fields) = match self
            .repository
            .fetch_recovery_relationship(input.recovery_relationship_id)
            .await?
        {
            RecoveryRelationship::Invitation(_) => {
                return Err(ServiceError::RecoveryRelationshipStatusMismatch)
            }
            RecoveryRelationship::Unendorsed(r) => (r.common_fields, r.connection_fields),
            RecoveryRelationship::Endorsed(r) => (r.common_fields, r.connection_fields),
        };

        if connection_fields.trusted_contact_account_id != *input.trusted_contact_account_id {
            return Err(ServiceError::AccountNotTrustedContact);
        }

        let id = SocialChallengeId::derive(&common_fields.customer_account_id, input.code);

        let challenge = self.repository.fetch_social_challenge(&id).await?;

        Ok(challenge)
    }
}
