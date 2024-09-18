use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use serde::{Deserialize, Serialize};
use strum_macros::Display;
use utoipa::ToSchema;

use self::identifiers::AccountId;

pub mod bitcoin;
pub mod identifiers;
pub mod spending;

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema, PartialEq, Eq, Hash, Display)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AccountType {
    Full,
    Lite,
    Software,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct PubkeysToAccount {
    pub application_auth_pubkey: Option<PublicKey>,
    pub hardware_auth_pubkey: Option<PublicKey>,
    pub recovery_auth_pubkey: Option<PublicKey>,
    #[serde(rename = "partition_key")]
    pub id: AccountId,
}
