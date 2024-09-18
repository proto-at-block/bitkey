use bdk_utils::{
    bdk::{bitcoin::PublicKey, keys::DescriptorPublicKey},
    DescriptorKeyset,
};
use serde::{Deserialize, Serialize};

use super::bitcoin::Network;

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct SpendingKeyset {
    pub network: Network,

    // Public Keys
    #[serde(with = "bdk_utils::serde::descriptor_key")]
    pub app_dpub: DescriptorPublicKey,
    #[serde(with = "bdk_utils::serde::descriptor_key")]
    pub hardware_dpub: DescriptorPublicKey,
    #[serde(with = "bdk_utils::serde::descriptor_key")]
    pub server_dpub: DescriptorPublicKey,
}

impl SpendingKeyset {
    #[must_use]
    pub fn new(
        network: Network,
        app_xpub: DescriptorPublicKey,
        hardware_xpub: DescriptorPublicKey,
        server_xpub: DescriptorPublicKey,
    ) -> Self {
        Self {
            network,
            app_dpub: app_xpub,
            hardware_dpub: hardware_xpub,
            server_dpub: server_xpub,
        }
    }
}

impl From<SpendingKeyset> for DescriptorKeyset {
    fn from(k: SpendingKeyset) -> Self {
        DescriptorKeyset::new(k.network.into(), k.app_dpub, k.hardware_dpub, k.server_dpub)
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct SpendingDistributedKey {
    pub network: Network,

    // Public Key
    pub public_key: PublicKey,
    pub dkg_complete: bool,
}

impl SpendingDistributedKey {
    #[must_use]
    pub fn new(network: Network, public_key: PublicKey, dkg_complete: bool) -> Self {
        Self {
            network,
            public_key,
            dkg_complete,
        }
    }
}

#[derive(Deserialize, Serialize, Clone, Debug, PartialEq)]
#[serde(untagged)]
pub enum SpendingKeyDefinition {
    Keyset(SpendingKeyset),
    DistributedKey(SpendingDistributedKey),
}

impl SpendingKeyDefinition {
    pub fn network(&self) -> Network {
        match self {
            Self::Keyset(k) => k.network,
            Self::DistributedKey(k) => k.network,
        }
    }
}
