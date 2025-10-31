use std::fmt::{self, Display, Formatter};
use std::str::FromStr;

use crate::serde::{deserialize_ts, serialize_ts};
use errors::ApiError;
use external_identifier::ExternalIdentifier;
use serde::{Deserialize, Serialize};
use time::{serde::rfc3339, OffsetDateTime};
use ulid::Ulid;
use urn::Urn;

use crate::account::identifiers::AccountId;

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq, Hash)]
pub struct ScreenerId(urn::Urn);

impl FromStr for ScreenerId {
    type Err = urn::Error;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(Urn::from_str(s)?.into())
    }
}

impl From<urn::Urn> for ScreenerId {
    fn from(urn: urn::Urn) -> Self {
        Self(urn)
    }
}

impl ExternalIdentifier<Ulid> for ScreenerId {
    fn namespace() -> &'static str {
        "screener"
    }
}

impl ScreenerId {
    pub fn gen() -> Result<Self, external_identifier::Error> {
        Self::new(Ulid::new())
    }
}

impl Display for ScreenerId {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

#[derive(Deserialize, Serialize, Clone, Debug, PartialEq)]
#[serde(tag = "_ScreenerRow_type", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ScreenerRow {
    CosignHit(ScreenerCosignHit),
}

impl ScreenerRow {
    pub fn new_cosign_hit(
        account_id: AccountId,
        email_address: Option<String>,
        phone_number: Option<String>,
        sanctioned_addresses: Vec<String>,
    ) -> Result<Self, ApiError> {
        let now = OffsetDateTime::now_utc();
        let id = ScreenerId::gen().map_err(|_| {
            ApiError::GenericInternalApplicationError("Failed to generate ScreenerId".to_string())
        })?;

        Ok(Self::CosignHit(ScreenerCosignHit {
            id,
            account_id,
            email_address,
            phone_number,
            sanctioned_addresses,
            created_at: now,
            updated_at: now,
            expiring_at: now
                .replace_year(now.year().saturating_add(10))
                .map_err(|_| {
                    ApiError::GenericInternalApplicationError(
                        "Failed to determine screener expiring_at".to_string(),
                    )
                })?,
        }))
    }
}

#[derive(Deserialize, Serialize, Clone, Debug, PartialEq)]
pub struct ScreenerCosignHit {
    #[serde(rename = "partition_key")]
    pub id: ScreenerId,
    pub account_id: AccountId,
    pub email_address: Option<String>,
    pub phone_number: Option<String>,
    pub sanctioned_addresses: Vec<String>,
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
    #[serde(serialize_with = "serialize_ts", deserialize_with = "deserialize_ts")]
    pub expiring_at: OffsetDateTime,
}
