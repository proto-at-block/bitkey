use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use serde::{Deserialize, Serialize};

use self::identifiers::AccountId;

pub mod identifiers;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct PubkeysToAccount {
    pub application_auth_pubkey: Option<PublicKey>,
    pub hardware_auth_pubkey: Option<PublicKey>,
    pub recovery_auth_pubkey: Option<PublicKey>,
    #[serde(rename = "partition_key")]
    pub id: AccountId,
}
