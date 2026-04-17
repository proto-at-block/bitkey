use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use serde::{Deserialize, Serialize};
use utoipa::ToSchema;

use super::entities::HardwareType;

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, ToSchema, Copy)]
pub struct FullAccountAuthKeys {
    // Keys
    pub app_pubkey: PublicKey,
    pub hardware_pubkey: PublicKey,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub recovery_pubkey: Option<PublicKey>,
    #[serde(default)]
    pub hardware_type: HardwareType,
}

impl FullAccountAuthKeys {
    #[must_use]
    pub fn new(
        app_pubkey: PublicKey,
        hardware_pubkey: PublicKey,
        recovery_pubkey: Option<PublicKey>,
        hardware_type: HardwareType,
    ) -> Self {
        Self {
            app_pubkey,
            hardware_pubkey,
            recovery_pubkey,
            hardware_type,
        }
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct LiteAccountAuthKeys {
    pub recovery_pubkey: PublicKey,
}

impl LiteAccountAuthKeys {
    #[must_use]
    pub fn new(recovery_pubkey: PublicKey) -> Self {
        Self { recovery_pubkey }
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct SoftwareAccountAuthKeys {
    pub app_pubkey: PublicKey,
    pub recovery_pubkey: PublicKey,
}

impl SoftwareAccountAuthKeys {
    #[must_use]
    pub fn new(app_pubkey: PublicKey, recovery_pubkey: PublicKey) -> Self {
        Self {
            app_pubkey,
            recovery_pubkey,
        }
    }
}
