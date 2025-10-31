use crate::account::identifiers::AccountId;
use crate::recovery::social::relationship::RecoveryRelationshipId;
use crate::{
    account::keys::FullAccountAuthKeys, recovery::social::relationship::RecoveryRelationshipRole,
};
use external_identifier::ExternalIdentifier;
use serde::{Deserialize, Serialize};

use bdk_utils::bdk::bitcoin::Txid;
use bdk_utils::bdk::descriptor::ExtendedDescriptor;
use std::{
    fmt::{self, Display, Formatter},
    str::FromStr,
};
use time::{serde::rfc3339, Duration, OffsetDateTime};
use ulid::Ulid;
use urn::Urn;
use utoipa::ToSchema;

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

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema, PartialEq, Copy)]
#[serde(untagged)]
pub enum InheritanceClaimAuthKeys {
    FullAccount(FullAccountAuthKeys),
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct InheritanceClaimCommonFields {
    #[serde(rename = "partition_key")]
    pub id: InheritanceClaimId,
    pub destination: Option<InheritanceDestination>,
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub benefactor_account_id: AccountId,
    pub beneficiary_account_id: AccountId,
    pub auth_keys: InheritanceClaimAuthKeys,
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub enum InheritanceDestinationType {
    Internal,
    External,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
#[serde(tag = "destination_type")]
pub enum InheritanceDestination {
    Internal { destination_address: String },
    External { destination_address: String },
}

impl InheritanceDestination {
    pub fn destination_address(&self) -> &str {
        match self {
            Self::Internal {
                destination_address,
            } => destination_address,
            Self::External {
                destination_address,
            } => destination_address,
        }
    }
}

impl InheritanceClaimCommonFields {
    pub fn with_updated_at(&self, updated_at: &OffsetDateTime) -> Self {
        Self {
            updated_at: updated_at.to_owned(),
            ..self.to_owned()
        }
    }
}

const INHERITANCE_CLAIM_DELAY_PERIOD_DAYS: i64 = 180;
const TEST_INHERITANCE_CLAIM_DELAY_PERIOD_MINUTES: i64 = 10;

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct InheritanceClaimPending {
    #[serde(flatten)]
    pub common_fields: InheritanceClaimCommonFields,
    #[serde(with = "rfc3339")]
    pub delay_end_time: OffsetDateTime,
}

impl InheritanceClaimPending {
    pub fn new(
        id: InheritanceClaimId,
        recovery_relationship_id: RecoveryRelationshipId,
        benefactor_account_id: AccountId,
        beneficiary_account_id: AccountId,
        auth_keys: InheritanceClaimAuthKeys,
        use_test_delay_end_time: bool,
    ) -> Self {
        let current_time = OffsetDateTime::now_utc();
        let delay_end_time = if use_test_delay_end_time {
            current_time + Duration::minutes(TEST_INHERITANCE_CLAIM_DELAY_PERIOD_MINUTES)
        } else {
            current_time + Duration::days(INHERITANCE_CLAIM_DELAY_PERIOD_DAYS)
        };
        let common_fields = InheritanceClaimCommonFields {
            id,
            destination: None,
            recovery_relationship_id,
            benefactor_account_id,
            beneficiary_account_id,
            auth_keys,
            created_at: current_time,
            updated_at: current_time,
        };
        Self {
            common_fields,
            delay_end_time,
        }
    }

    /// This function recreates a pending inheritance claim for a beneficiary
    /// The new claim will have different auth keys but the same delay end time
    ///
    /// # Arguments
    ///
    /// * `pending_claim` - The pending inheritance claim to be recreated
    /// * `auth_keys` - The authentication keys for the recreated claim
    /// * `delay_end_time` - The delay end time for the recreated claim
    ///
    pub fn recreate(
        pending_claim: &InheritanceClaimPending,
        auth_keys: InheritanceClaimAuthKeys,
    ) -> Result<Self, external_identifier::Error> {
        let id = InheritanceClaimId::gen()?;
        let common_fields = InheritanceClaimCommonFields {
            id,
            auth_keys,
            created_at: OffsetDateTime::now_utc(),
            updated_at: OffsetDateTime::now_utc(),
            ..pending_claim.common_fields.to_owned()
        };
        Ok(Self {
            common_fields,
            delay_end_time: pending_claim.delay_end_time,
        })
    }

    pub fn is_delay_complete(&self) -> bool {
        OffsetDateTime::now_utc() > self.delay_end_time
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct InheritanceClaimLocked {
    #[serde(flatten)]
    pub common_fields: InheritanceClaimCommonFields,
    // The dek is sealed using DH between an ephemeral key pair and the beneficiary's
    // delegated decryption key. The app(mobile) key and descriptor are encrypted using the dek.
    pub sealed_dek: String,
    pub sealed_mobile_key: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub sealed_descriptor: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub sealed_server_root_xpub: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub benefactor_descriptor_keyset: Option<ExtendedDescriptor>,
    #[serde(with = "rfc3339")]
    pub locked_at: OffsetDateTime,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum InheritanceRole {
    Benefactor,
    Beneficiary,
}

impl From<InheritanceRole> for RecoveryRelationshipRole {
    fn from(canceled_by: InheritanceRole) -> Self {
        match canceled_by {
            InheritanceRole::Benefactor => RecoveryRelationshipRole::ProtectedCustomer,
            InheritanceRole::Beneficiary => RecoveryRelationshipRole::TrustedContact,
        }
    }
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct InheritanceClaimCanceled {
    #[serde(flatten)]
    pub common_fields: InheritanceClaimCommonFields,
    pub canceled_by: InheritanceRole,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
#[serde(tag = "completion_method", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum InheritanceCompletionMethod {
    WithPsbt { txid: Txid },
    EmptyBalance,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct InheritanceClaimCompleted {
    #[serde(flatten)]
    pub common_fields: InheritanceClaimCommonFields,
    #[serde(flatten)]
    pub completion_method: InheritanceCompletionMethod,
    #[serde(with = "rfc3339")]
    pub completed_at: OffsetDateTime,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(tag = "status", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum InheritanceClaim {
    Pending(InheritanceClaimPending),
    Canceled(InheritanceClaimCanceled),
    Locked(InheritanceClaimLocked),
    Completed(InheritanceClaimCompleted),
}

impl InheritanceClaim {
    pub fn common_fields(&self) -> &InheritanceClaimCommonFields {
        match self {
            Self::Pending(pending) => &pending.common_fields,
            Self::Canceled(canceled) => &canceled.common_fields,
            Self::Locked(locked) => &locked.common_fields,
            Self::Completed(completed) => &completed.common_fields,
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
            Self::Locked(locked) => Self::Locked(InheritanceClaimLocked {
                common_fields: common_fields.to_owned(),
                benefactor_descriptor_keyset: locked.benefactor_descriptor_keyset.to_owned(),
                sealed_dek: locked.sealed_dek.to_owned(),
                sealed_mobile_key: locked.sealed_mobile_key.to_owned(),
                sealed_descriptor: locked.sealed_descriptor.to_owned(),
                sealed_server_root_xpub: locked.sealed_server_root_xpub.to_owned(),
                locked_at: locked.locked_at,
            }),
            Self::Completed(completed) => Self::Completed(InheritanceClaimCompleted {
                common_fields: common_fields.to_owned(),
                completion_method: completed.completion_method.to_owned(),
                completed_at: completed.completed_at,
            }),
        }
    }
}
