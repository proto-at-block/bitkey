use std::{
    fmt::{self, Display, Formatter},
    str::FromStr,
};

use crate::account::identifiers::AccountId;
use derive_builder::Builder;
use external_identifier::ExternalIdentifier;
use serde::{Deserialize, Serialize};
use time::{serde::rfc3339, OffsetDateTime};
use ulid::Ulid;
use urn::Urn;
use utoipa::ToSchema;

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq, Hash)]
pub struct RecoveryRelationshipId(urn::Urn);

impl FromStr for RecoveryRelationshipId {
    type Err = urn::Error;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(Urn::from_str(s)?.into())
    }
}

impl From<urn::Urn> for RecoveryRelationshipId {
    fn from(urn: urn::Urn) -> Self {
        Self(urn)
    }
}

impl ExternalIdentifier<Ulid> for RecoveryRelationshipId {
    fn namespace() -> &'static str {
        "recovery-relationship"
    }
}

impl RecoveryRelationshipId {
    pub fn gen() -> Result<Self, external_identifier::Error> {
        Self::new(Ulid::new())
    }
}

impl Display for RecoveryRelationshipId {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct RecoveryRelationshipCommonFields {
    #[serde(rename = "partition_key")]
    pub id: RecoveryRelationshipId,
    pub customer_account_id: AccountId,
    pub trusted_contact_alias: String, // This is the customer's alias for the trusted contact
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
}

impl RecoveryRelationshipCommonFields {
    pub fn with_updated_at(&self, updated_at: &OffsetDateTime) -> Self {
        Self {
            updated_at: updated_at.to_owned(),
            ..self.to_owned()
        }
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, Builder)]
pub struct RecoveryRelationshipConnectionFields {
    pub customer_alias: String, // This is the trusted contact's alias for the customer
    pub trusted_contact_account_id: AccountId,
    pub trusted_contact_identity_pubkey: String,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct RecoveryRelationshipInvitation {
    #[serde(flatten)]
    pub common_fields: RecoveryRelationshipCommonFields,
    pub code: String,
    #[serde(with = "rfc3339")]
    pub expires_at: OffsetDateTime,
    // The enrollment fields are thrown away once the customer has
    // endorsed the Trusted Contact
    pub customer_enrollment_pubkey: String,
}

impl RecoveryRelationshipInvitation {
    pub fn reissue(&self, code: &str, expires_at: &OffsetDateTime) -> Self {
        Self {
            common_fields: self.common_fields.to_owned(),
            customer_enrollment_pubkey: self.customer_enrollment_pubkey.to_owned(),
            code: code.to_owned(),
            expires_at: expires_at.to_owned(),
        }
    }

    pub fn is_expired(&self) -> bool {
        OffsetDateTime::now_utc() > self.expires_at
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, Builder)]
pub struct RecoveryRelationshipUnendorsed {
    #[serde(flatten)]
    pub common_fields: RecoveryRelationshipCommonFields,
    #[serde(flatten)]
    pub connection_fields: RecoveryRelationshipConnectionFields,
    // The enrollment fields are thrown away once the customer has
    // endorsed the Trusted Contact
    pub customer_enrollment_pubkey: String,
    pub trusted_contact_enrollment_pubkey: String,
    pub trusted_contact_identity_pubkey_mac: String,
    pub enrollment_key_confirmation: String,
}

#[derive(Serialize, Deserialize, Debug, Clone, Builder)]
pub struct RecoveryRelationshipEndorsed {
    #[serde(flatten)]
    pub common_fields: RecoveryRelationshipCommonFields,
    #[serde(flatten)]
    pub connection_fields: RecoveryRelationshipConnectionFields,
    pub endorsement_key_certificate: String,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(tag = "_RecoveryRelationship_type")]
pub enum RecoveryRelationship {
    Invitation(RecoveryRelationshipInvitation),
    Unendorsed(RecoveryRelationshipUnendorsed),
    Endorsed(RecoveryRelationshipEndorsed),
}

impl RecoveryRelationship {
    pub fn new_invitation(
        id: &RecoveryRelationshipId,
        customer_account_id: &AccountId,
        trusted_contact_alias: &str,
        customer_enrollment_pubkey: &str,
        code: &str,
        expires_at: &OffsetDateTime,
    ) -> Self {
        Self::Invitation(RecoveryRelationshipInvitation {
            common_fields: RecoveryRelationshipCommonFields {
                id: id.to_owned(),
                customer_account_id: customer_account_id.to_owned(),
                trusted_contact_alias: trusted_contact_alias.to_owned(),
                created_at: OffsetDateTime::now_utc(),
                updated_at: OffsetDateTime::now_utc(),
            },
            code: code.to_owned(),
            expires_at: expires_at.to_owned(),
            customer_enrollment_pubkey: customer_enrollment_pubkey.to_owned(),
        })
    }

    pub fn common_fields(&self) -> &RecoveryRelationshipCommonFields {
        match self {
            Self::Invitation(invitation) => &invitation.common_fields,
            Self::Unendorsed(connection) => &connection.common_fields,
            Self::Endorsed(connection) => &connection.common_fields,
        }
    }

    pub fn with_common_fields(&self, common_fields: &RecoveryRelationshipCommonFields) -> Self {
        match self {
            Self::Invitation(invitation) => Self::Invitation(RecoveryRelationshipInvitation {
                common_fields: common_fields.to_owned(),
                code: invitation.code.to_owned(),
                expires_at: invitation.expires_at.to_owned(),
                customer_enrollment_pubkey: invitation.customer_enrollment_pubkey.to_owned(),
            }),
            Self::Unendorsed(connection) => Self::Unendorsed(RecoveryRelationshipUnendorsed {
                common_fields: common_fields.to_owned(),
                connection_fields: connection.connection_fields.to_owned(),
                customer_enrollment_pubkey: connection.customer_enrollment_pubkey.to_owned(),
                trusted_contact_enrollment_pubkey: connection
                    .trusted_contact_enrollment_pubkey
                    .to_owned(),
                trusted_contact_identity_pubkey_mac: connection
                    .trusted_contact_identity_pubkey_mac
                    .to_owned(),
                enrollment_key_confirmation: connection.enrollment_key_confirmation.to_owned(),
            }),
            Self::Endorsed(connection) => Self::Endorsed(RecoveryRelationshipEndorsed {
                common_fields: common_fields.to_owned(),
                connection_fields: connection.connection_fields.to_owned(),
                endorsement_key_certificate: connection.endorsement_key_certificate.to_owned(),
            }),
        }
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema, PartialEq)]
pub struct RecoveryRelationshipEndorsement {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub endorsement_key_certificate: String,
}
