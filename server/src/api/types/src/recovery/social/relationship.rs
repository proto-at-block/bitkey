use std::{
    fmt::{self, Display, Formatter},
    str::FromStr,
};

use crate::account::identifiers::AccountId;
use crate::recovery::trusted_contacts::{TrustedContactInfo, TrustedContactRole};
use derive_builder::Builder;
use external_identifier::ExternalIdentifier;
use serde::{Deserialize, Serialize};
use time::{serde::rfc3339, OffsetDateTime};
use ulid::Ulid;
use urn::Urn;
use utoipa::ToSchema;

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq, Default)]
pub enum RecoveryRelationshipRole {
    #[default]
    ProtectedCustomer,
    TrustedContact,
}

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
    #[serde(flatten)]
    pub trusted_contact_info: TrustedContactInfo,
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
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct RecoveryRelationshipInvitation {
    #[serde(flatten)]
    pub common_fields: RecoveryRelationshipCommonFields,
    pub code: String,
    pub code_bit_length: usize,
    #[serde(with = "rfc3339")]
    pub expires_at: OffsetDateTime,
    // The enrollment fields are thrown away once the customer has
    // endorsed the Trusted Contact
    pub protected_customer_enrollment_pake_pubkey: String,
}

impl RecoveryRelationshipInvitation {
    pub fn reissue(&self, code: &str, code_bit_length: usize, expires_at: &OffsetDateTime) -> Self {
        Self {
            common_fields: self.common_fields.to_owned(),
            protected_customer_enrollment_pake_pubkey: self
                .protected_customer_enrollment_pake_pubkey
                .to_owned(),
            code: code.to_owned(),
            code_bit_length,
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
    pub sealed_delegated_decryption_pubkey: String,
    pub trusted_contact_enrollment_pake_pubkey: String,
    pub enrollment_pake_confirmation: String,
    pub protected_customer_enrollment_pake_pubkey: String,
}

#[derive(Serialize, Deserialize, Debug, Clone, Builder)]
pub struct RecoveryRelationshipEndorsed {
    #[serde(flatten)]
    pub common_fields: RecoveryRelationshipCommonFields,
    #[serde(flatten)]
    pub connection_fields: RecoveryRelationshipConnectionFields,
    pub delegated_decryption_pubkey_certificate: String,
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
        trusted_contact: &TrustedContactInfo,
        protected_customer_enrollment_pake_pubkey: &str,
        code: &str,
        code_bit_length: usize,
        expires_at: &OffsetDateTime,
    ) -> Self {
        Self::Invitation(RecoveryRelationshipInvitation {
            common_fields: RecoveryRelationshipCommonFields {
                id: id.to_owned(),
                customer_account_id: customer_account_id.to_owned(),
                trusted_contact_info: trusted_contact.to_owned(),
                created_at: OffsetDateTime::now_utc(),
                updated_at: OffsetDateTime::now_utc(),
            },
            code: code.to_owned(),
            code_bit_length,
            expires_at: expires_at.to_owned(),
            protected_customer_enrollment_pake_pubkey: protected_customer_enrollment_pake_pubkey
                .to_owned(),
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
                code_bit_length: invitation.code_bit_length,
                expires_at: invitation.expires_at.to_owned(),
                protected_customer_enrollment_pake_pubkey: invitation
                    .protected_customer_enrollment_pake_pubkey
                    .to_owned(),
            }),
            Self::Unendorsed(connection) => Self::Unendorsed(RecoveryRelationshipUnendorsed {
                common_fields: common_fields.to_owned(),
                connection_fields: connection.connection_fields.to_owned(),
                sealed_delegated_decryption_pubkey: connection
                    .sealed_delegated_decryption_pubkey
                    .to_owned(),
                trusted_contact_enrollment_pake_pubkey: connection
                    .trusted_contact_enrollment_pake_pubkey
                    .to_owned(),
                enrollment_pake_confirmation: connection.enrollment_pake_confirmation.to_owned(),
                protected_customer_enrollment_pake_pubkey: connection
                    .protected_customer_enrollment_pake_pubkey
                    .to_owned(),
            }),
            Self::Endorsed(connection) => Self::Endorsed(RecoveryRelationshipEndorsed {
                common_fields: common_fields.to_owned(),
                connection_fields: connection.connection_fields.to_owned(),
                delegated_decryption_pubkey_certificate: connection
                    .delegated_decryption_pubkey_certificate
                    .to_owned(),
            }),
        }
    }

    pub fn has_role(&self, trusted_contact_role: &TrustedContactRole) -> bool {
        self.common_fields()
            .trusted_contact_info
            .roles
            .contains(trusted_contact_role)
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema, PartialEq)]
pub struct RecoveryRelationshipEndorsement {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub delegated_decryption_pubkey_certificate: String,
}
