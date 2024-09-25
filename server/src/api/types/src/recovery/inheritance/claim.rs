use external_identifier::ExternalIdentifier;
use serde::{Deserialize, Serialize};
use std::{
    fmt::{self, Display, Formatter},
    str::FromStr,
};
use time::{serde::rfc3339, Duration, OffsetDateTime};
use ulid::Ulid;
use urn::Urn;

use crate::account::keys::FullAccountAuthKeys;
use crate::recovery::social::relationship::RecoveryRelationshipId;

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq, Hash)]
pub struct InheritanceClaimId(urn::Urn);

impl FromStr for InheritanceClaimId {
    type Err = urn::Error;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(Urn::from_str(s)?.into())
    }
}

impl From<urn::Urn> for InheritanceClaimId {
    fn from(urn: urn::Urn) -> Self {
        Self(urn)
    }
}

impl ExternalIdentifier<Ulid> for InheritanceClaimId {
    fn namespace() -> &'static str {
        "inheritance-claim"
    }
}

impl InheritanceClaimId {
    pub fn gen() -> Result<Self, external_identifier::Error> {
        Self::new(Ulid::new())
    }
}

impl Display for InheritanceClaimId {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(untagged)]
pub enum InheritanceClaimAuthKeys {
    FullAccount(FullAccountAuthKeys),
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct InheritanceClaimCommonFields {
    #[serde(rename = "partition_key")]
    pub id: InheritanceClaimId,
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub auth_keys: InheritanceClaimAuthKeys,
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
}

impl InheritanceClaimCommonFields {
    pub fn with_updated_at(&self, updated_at: &OffsetDateTime) -> Self {
        Self {
            updated_at: updated_at.to_owned(),
            ..self.to_owned()
        }
    }
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct InheritanceClaimPending {
    #[serde(flatten)]
    pub common_fields: InheritanceClaimCommonFields,
    #[serde(with = "rfc3339")]
    pub delay_end_time: OffsetDateTime,
}

impl InheritanceClaimPending {
    pub fn is_delay_complete(&self) -> bool {
        OffsetDateTime::now_utc() > self.delay_end_time
    }
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum InheritanceClaimCanceledBy {
    Benefactor,
    Beneficiary,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct InheritanceClaimCanceled {
    #[serde(flatten)]
    pub common_fields: InheritanceClaimCommonFields,
    pub canceled_by: InheritanceClaimCanceledBy,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(tag = "status", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum InheritanceClaim {
    Pending(InheritanceClaimPending),
    Canceled(InheritanceClaimCanceled),
}

impl InheritanceClaim {
    pub fn new_claim(
        id: InheritanceClaimId,
        recovery_relationship_id: RecoveryRelationshipId,
        auth_keys: InheritanceClaimAuthKeys,
    ) -> Self {
        let common_fields = InheritanceClaimCommonFields {
            id,
            recovery_relationship_id,
            auth_keys,
            created_at: OffsetDateTime::now_utc(),
            updated_at: OffsetDateTime::now_utc(),
        };
        InheritanceClaim::Pending(InheritanceClaimPending {
            common_fields,
            delay_end_time: OffsetDateTime::now_utc() + Duration::days(180),
        })
    }

    pub fn common_fields(&self) -> &InheritanceClaimCommonFields {
        match self {
            Self::Pending(pending) => &pending.common_fields,
            Self::Canceled(canceled) => &canceled.common_fields,
        }
    }

    pub fn with_common_fields(&self, common_fields: &InheritanceClaimCommonFields) -> Self {
        match self {
            Self::Pending(pending) => Self::Pending(InheritanceClaimPending {
                common_fields: common_fields.to_owned(),
                delay_end_time: pending.delay_end_time,
            }),
            Self::Canceled(canceled) => Self::Canceled(InheritanceClaimCanceled {
                common_fields: common_fields.to_owned(),
                canceled_by: canceled.canceled_by.clone(),
            }),
        }
    }
}
