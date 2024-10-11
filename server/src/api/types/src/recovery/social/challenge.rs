use crate::account::identifiers::AccountId;
use base32::Alphabet;
use external_identifier::ExternalIdentifier;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    collections::HashMap,
    fmt::{self, Display, Formatter},
    str::FromStr,
};
use time::{serde::rfc3339, OffsetDateTime};
use urn::Urn;
use utoipa::ToSchema;

use super::relationship::RecoveryRelationshipId;

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq, Hash)]
pub struct SocialChallengeId(urn::Urn);

impl FromStr for SocialChallengeId {
    type Err = urn::Error;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(Urn::from_str(s)?.into())
    }
}

impl From<urn::Urn> for SocialChallengeId {
    fn from(urn: urn::Urn) -> Self {
        Self(urn)
    }
}

impl ExternalIdentifier<String> for SocialChallengeId {
    fn namespace() -> &'static str {
        "social-challenge"
    }
}

impl SocialChallengeId {
    // Derive a SocialChallengeId based on a customer's account ID and a challenge counter
    // Encode this the same way Ulids are encoded (Crockford Base32) to be consistent with other IDs
    pub fn derive(customer_account_id: &AccountId, counter: u32) -> Self {
        let mut hasher = Sha256::new();
        hasher.update(customer_account_id.to_string().as_bytes());
        hasher.update(counter.to_string().as_bytes());
        let hash = hasher.finalize();
        let encoded = base32::encode(Alphabet::Crockford, &hash[0..16]);
        Self::new(encoded).unwrap()
    }
}

impl Display for SocialChallengeId {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct SocialChallengeResponse {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub trusted_contact_recovery_pake_pubkey: String,
    pub recovery_pake_confirmation: String,
    pub resealed_dek: String,
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct TrustedContactChallengeRequest {
    pub protected_customer_recovery_pake_pubkey: String,
    pub sealed_dek: String,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct SocialChallenge {
    #[serde(rename = "partition_key")]
    pub id: SocialChallengeId,
    pub customer_account_id: AccountId,
    #[serde(default)]
    pub trusted_contact_challenge_requests:
        HashMap<RecoveryRelationshipId, TrustedContactChallengeRequest>,
    pub counter: u32,
    pub responses: Vec<SocialChallengeResponse>,
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
}

impl SocialChallenge {
    #[must_use]
    pub fn new(
        id: &SocialChallengeId,
        customer_account_id: &AccountId,
        trusted_contact_challenge_requests: HashMap<
            RecoveryRelationshipId,
            TrustedContactChallengeRequest,
        >,
        counter: u32,
    ) -> Self {
        Self {
            id: id.to_owned(),
            customer_account_id: customer_account_id.to_owned(),
            trusted_contact_challenge_requests,
            counter,
            responses: Vec::new(),
            created_at: OffsetDateTime::now_utc(),
            updated_at: OffsetDateTime::now_utc(),
        }
    }

    pub fn with_updated_at(&self, updated_at: OffsetDateTime) -> Self {
        Self {
            updated_at,
            ..self.to_owned()
        }
    }

    pub fn with_response(&self, response: SocialChallengeResponse) -> Self {
        let mut responses = self.responses.clone();
        responses.retain(|r| r.recovery_relationship_id != response.recovery_relationship_id);
        responses.push(response);

        Self {
            responses,
            ..self.to_owned()
        }
    }
}
