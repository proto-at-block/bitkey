use serde::{Deserialize, Serialize};
use strum_macros::EnumString;
use time::{serde::rfc3339, OffsetDateTime};
use types::account::identifiers::AccountId;

#[derive(Clone, Copy, Debug, Deserialize, PartialEq, Serialize, EnumString)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
#[strum(serialize_all = "SCREAMING_SNAKE_CASE")]
pub enum CodeType {
    InheritanceBenefactor,
    InheritanceBeneficiary,
}

#[derive(Clone, Debug, Deserialize, Serialize, PartialEq)]
#[serde(untagged)]
pub enum CodeUniqueBy {
    Account(AccountId),
}

impl std::fmt::Display for CodeUniqueBy {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Account(id) => write!(f, "{}", id),
        }
    }
}

#[derive(Clone, Debug, Serialize)]
pub struct CodeKey {
    pub unique_by: CodeUniqueBy,
    pub code_type: CodeType,
}

impl CodeKey {
    pub fn new(unique_by: CodeUniqueBy, code_type: CodeType) -> Self {
        Self {
            unique_by,
            code_type,
        }
    }

    pub fn inheritance_benefactor(account_id: AccountId) -> Self {
        Self::new(
            CodeUniqueBy::Account(account_id),
            CodeType::InheritanceBenefactor,
        )
    }

    pub fn inheritance_beneficiary(account_id: AccountId) -> Self {
        Self::new(
            CodeUniqueBy::Account(account_id),
            CodeType::InheritanceBeneficiary,
        )
    }
}

#[derive(Clone, Debug, Deserialize, PartialEq, Serialize)]
pub struct Code {
    #[serde(rename = "partition_key")]
    pub code_unique_by: CodeUniqueBy,
    #[serde(rename = "sort_key")]
    pub code_type: CodeType,
    pub code: String,
    pub creator_account_id: AccountId,
    pub is_redeemed: bool,
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
}

impl Code {
    pub fn new(key: CodeKey, code: String, creator_account_id: AccountId) -> Self {
        let cur_time = OffsetDateTime::now_utc();
        Self {
            code_unique_by: key.unique_by,
            code_type: key.code_type,
            code,
            creator_account_id,
            is_redeemed: false,
            created_at: cur_time,
            updated_at: cur_time,
        }
    }
}

#[derive(Clone, Debug, Serialize)]
pub struct GenerateCodeRequest {
    #[serde(rename = "discountType")]
    pub code_type: CodeType,
    #[serde(rename = "uniqueIdentifier")]
    pub unique_identifier: CodeUniqueBy,
}

impl From<&CodeKey> for GenerateCodeRequest {
    fn from(key: &CodeKey) -> Self {
        Self {
            code_type: key.code_type,
            unique_identifier: key.unique_by.to_owned(),
        }
    }
}

#[derive(Deserialize, Debug, Clone, PartialEq)]
pub(crate) struct GenerateCodeResponse {
    #[serde(rename = "promoCode")]
    pub(crate) code: String,
}
