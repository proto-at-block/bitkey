use std::collections::{HashMap, HashSet};

use rand::Rng;
use types::{
    account::identifiers::AccountId,
    recovery::social::{
        challenge::{SocialChallenge, SocialChallengeId, TrustedContactChallengeRequest},
        relationship::{RecoveryRelationship, RecoveryRelationshipId},
    },
};

use crate::service::social::relationship::get_recovery_relationships::GetRecoveryRelationshipsInput;

use super::{error::ServiceError, Service};

const CODE_MAX_VALUE: i64 = 999999;

pub struct CreateSocialChallengeInput<'a> {
    pub customer_account_id: &'a AccountId,
    pub customer_identity_pubkey: &'a str,
    #[deprecated]
    pub customer_ephemeral_pubkey: &'a str,
    pub requests: HashMap<RecoveryRelationshipId, TrustedContactChallengeRequest>,
}

impl Service {
    pub async fn create_social_challenge(
        &self,
        input: CreateSocialChallengeInput<'_>,
    ) -> Result<SocialChallenge, ServiceError> {
        // Check to see if the given Recovery Relationships are valid
        let relationships = self
            .recovery_relationship_service
            .get_recovery_relationships(GetRecoveryRelationshipsInput {
                account_id: &input.customer_account_id,
            })
            .await?;

        //TODO(BKR-919): Update this to only endorsed once we support
        let to_recovery_relationship_ids = |v: Vec<RecoveryRelationship>| {
            v.into_iter()
                .map(|r| r.common_fields().id.clone())
                .collect::<HashSet<_>>()
        };
        let mut eligible_recovery_relationship_ids =
            to_recovery_relationship_ids(relationships.endorsed_trusted_contacts.clone());
        eligible_recovery_relationship_ids.extend(to_recovery_relationship_ids(
            relationships.unendorsed_trusted_contacts,
        ));
        //TODO(BKR-919): Return an error here once the mobile app is passing up the challenge requests
        if !input
            .requests
            .keys()
            .all(|id| eligible_recovery_relationship_ids.contains(id))
        {}

        let code = format!("{:0>6}", rand::thread_rng().gen_range(0..=CODE_MAX_VALUE));
        let id = SocialChallengeId::derive(input.customer_account_id, code.as_str());

        let challenge = self
            .repository
            .persist_social_challenge(&SocialChallenge::new(
                &id,
                input.customer_account_id,
                input.customer_identity_pubkey,
                input.customer_ephemeral_pubkey,
                input.requests,
                &code,
            ))
            .await?;

        Ok(challenge)
    }
}
