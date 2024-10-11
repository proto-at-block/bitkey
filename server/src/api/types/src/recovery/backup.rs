use crate::account::identifiers::AccountId;
use serde::{Deserialize, Serialize, Serializer};
use std::str::FromStr;
use time::{serde::rfc3339, OffsetDateTime};

const RECOVERY_BACKUP_PREFIX: &str = "RecoveryBackup#";

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Backup {
    #[serde(
        rename = "partition_key",
        serialize_with = "aid_to_pk",
        deserialize_with = "pk_to_aid"
    )]
    pub account_id: AccountId,
    pub material: String,

    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
}

pub trait ToRecoveryBackupPk {
    fn to_recovery_backup_pk(&self) -> String;
}

impl ToRecoveryBackupPk for AccountId {
    fn to_recovery_backup_pk(&self) -> String {
        format!("{}{}", RECOVERY_BACKUP_PREFIX, self)
    }
}

fn aid_to_pk<S>(r: &AccountId, s: S) -> Result<S::Ok, S::Error>
where
    S: Serializer,
{
    s.serialize_str(&r.to_recovery_backup_pk())
}

fn pk_to_aid<'de, D>(deserializer: D) -> Result<AccountId, D::Error>
where
    D: serde::Deserializer<'de>,
{
    let s: String = String::deserialize(deserializer)?;
    let id_str = s
        .strip_prefix(RECOVERY_BACKUP_PREFIX)
        .ok_or_else(|| serde::de::Error::custom("missing prefix"))?;

    let id = AccountId::from_str(id_str).map_err(serde::de::Error::custom)?;

    Ok(id)
}
