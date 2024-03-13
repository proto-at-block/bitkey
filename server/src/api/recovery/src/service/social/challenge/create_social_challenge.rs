use std::collections::{HashMap, HashSet};

use account::{
    entities::FullAccount, service::FetchAndUpdateSpendingLimitInput, spend_limit::SpendingLimit,
};

use tracing::instrument;
use types::recovery::social::{
    challenge::{SocialChallenge, SocialChallengeId, TrustedContactChallengeRequest},
    relationship::RecoveryRelationshipId,
};

use crate::service::social::relationship::get_recovery_relationships::GetRecoveryRelationshipsInput;

use super::{error::ServiceError, Service};

pub struct CreateSocialChallengeInput<'a> {
    pub customer_account: &'a FullAccount,
    pub requests: HashMap<RecoveryRelationshipId, TrustedContactChallengeRequest>,
}

impl Service {
    #[instrument(skip(self, input))]
    pub async fn create_social_challenge(
        &self,
        input: CreateSocialChallengeInput<'_>,
    ) -> Result<SocialChallenge, ServiceError> {
        // Check to see if the given Recovery Relationships are valid
        let relationships = self
            .recovery_relationship_service
            .get_recovery_relationships(GetRecoveryRelationshipsInput {
                account_id: &input.customer_account.id,
            })
            .await?;

        let eligible_recovery_relationship_ids = relationships
            .endorsed_trusted_contacts
            .into_iter()
            .map(|r| r.common_fields().id.clone())
            .collect::<HashSet<_>>();
        if !input
            .requests
            .keys()
            .all(|id| eligible_recovery_relationship_ids.contains(id))
        {
            return Err(ServiceError::MismatchingRecoveryRelationships);
        }

        self.account_service
            .fetch_and_update_spend_limit(FetchAndUpdateSpendingLimitInput {
                account_id: &input.customer_account.id,
                new_spending_limit: input.customer_account.spending_limit.as_ref().map_or_else(
                    || None,
                    |old_limit| {
                        Some(SpendingLimit {
                            active: false,
                            ..old_limit.clone()
                        })
                    },
                ),
            })
            .await?;

        let counter = u32::try_from(
            self.repository
                .count_social_challenges_for_customer(&input.customer_account.id)
                .await?,
        )?;
        let id = SocialChallengeId::derive(&input.customer_account.id, counter);

        let challenge = self
            .repository
            .persist_social_challenge(&SocialChallenge::new(
                &id,
                &input.customer_account.id,
                input.requests,
                counter,
            ))
            .await?;

        Ok(challenge)
    }
}
