use serde::{Deserialize, Serialize, Serializer};
use std::str::FromStr;
use time::{serde::rfc3339, OffsetDateTime};

use crate::recovery::social::relationship::RecoveryRelationshipId;

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Package {
    #[serde(
        rename = "partition_key",
        serialize_with = "rrid_to_pk",
        deserialize_with = "pk_to_rrid"
    )]
    pub recovery_relationship_id: RecoveryRelationshipId,

    // The dek is sealed using DH between an ephemeral key pair and the beneficiary's
    // delegated decryption key. The mobile key and descriptor are encrypted using the dek.
    pub sealed_dek: String,
    pub sealed_mobile_key: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub sealed_descriptor: Option<String>,

    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
}

pub trait ToPackagePk {
    fn to_package_pk(&self) -> String;
}

impl ToPackagePk for RecoveryRelationshipId {
    fn to_package_pk(&self) -> String {
        format!("Package#{}", self)
    }
}

fn rrid_to_pk<S>(r: &RecoveryRelationshipId, s: S) -> Result<S::Ok, S::Error>
where
    S: Serializer,
{
    s.serialize_str(&r.to_package_pk())
}

fn pk_to_rrid<'de, D>(deserializer: D) -> Result<RecoveryRelationshipId, D::Error>
where
    D: serde::Deserializer<'de>,
{
    let s: String = String::deserialize(deserializer)?;
    let id_str = s
        .strip_prefix("Package#")
        .ok_or_else(|| serde::de::Error::custom("missing prefix"))?;

    let id = RecoveryRelationshipId::from_str(id_str).map_err(serde::de::Error::custom)?;

    Ok(id)
}
