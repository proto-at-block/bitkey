use bdk_utils::{
    bdk::{bitcoin::secp256k1::PublicKey, keys::DescriptorPublicKey},
    DescriptorKeyset,
};
use serde::{Deserialize, Serialize};

use super::bitcoin::Network;

#[derive(Serialize, Debug, Clone, PartialEq)]
#[serde(tag = "type", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum SpendingKeyset {
    LegacyMultiSig(LegacyMultiSigSpendingKeyset),
    PrivateMultiSig(PrivateMultiSigSpendingKeyset),
}

impl SpendingKeyset {
    pub fn new_legacy_multi_sig(
        network: Network,
        app_xpub: DescriptorPublicKey,
        hardware_xpub: DescriptorPublicKey,
        server_xpub: DescriptorPublicKey,
    ) -> Self {
        Self::LegacyMultiSig(LegacyMultiSigSpendingKeyset::new(
            network,
            app_xpub,
            hardware_xpub,
            server_xpub,
        ))
    }

    pub fn new_private_multi_sig(
        network: Network,
        app_pub: PublicKey,
        hardware_pub: PublicKey,
        server_pub: PublicKey,
        server_pub_integrity_sig: String,
    ) -> Self {
        Self::PrivateMultiSig(PrivateMultiSigSpendingKeyset::new(
            network,
            app_pub,
            hardware_pub,
            server_pub,
            server_pub_integrity_sig,
        ))
    }

    pub fn network(&self) -> Network {
        match self {
            SpendingKeyset::LegacyMultiSig(k) => k.network,
            SpendingKeyset::PrivateMultiSig(k) => k.network,
        }
    }

    pub fn legacy_multi_sig_or<T>(&self, e: T) -> Result<&LegacyMultiSigSpendingKeyset, T> {
        match self {
            SpendingKeyset::LegacyMultiSig(k) => Ok(k),
            SpendingKeyset::PrivateMultiSig(_) => Err(e),
        }
    }

    pub fn optional_legacy_multi_sig(&self) -> Option<&LegacyMultiSigSpendingKeyset> {
        match self {
            SpendingKeyset::LegacyMultiSig(k) => Some(k),
            SpendingKeyset::PrivateMultiSig(_) => None,
        }
    }

    pub fn private_multi_sig_or<T>(&self, e: T) -> Result<&PrivateMultiSigSpendingKeyset, T> {
        match self {
            SpendingKeyset::LegacyMultiSig(_) => Err(e),
            SpendingKeyset::PrivateMultiSig(k) => Ok(k),
        }
    }

    pub fn optional_private_multi_sig(&self) -> Option<&PrivateMultiSigSpendingKeyset> {
        match self {
            SpendingKeyset::LegacyMultiSig(_) => None,
            SpendingKeyset::PrivateMultiSig(k) => Some(k),
        }
    }
}

// Backcompat deserializer
impl<'de> Deserialize<'de> for SpendingKeyset {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: serde::Deserializer<'de>,
    {
        #[derive(Deserialize)]
        #[serde(tag = "type", rename_all = "SCREAMING_SNAKE_CASE")]
        enum Tagged {
            LegacyMultiSig(LegacyMultiSigSpendingKeyset),
            PrivateMultiSig(PrivateMultiSigSpendingKeyset),
        }

        #[derive(Deserialize)]
        #[serde(untagged)]
        enum Backcompat {
            Legacy(LegacyMultiSigSpendingKeyset),
            Tagged(Tagged),
        }

        match Backcompat::deserialize(deserializer) {
            Ok(Backcompat::Legacy(k)) => Ok(SpendingKeyset::LegacyMultiSig(k)),
            Ok(Backcompat::Tagged(Tagged::LegacyMultiSig(k))) => {
                Ok(SpendingKeyset::LegacyMultiSig(k))
            }
            Ok(Backcompat::Tagged(Tagged::PrivateMultiSig(k))) => {
                Ok(SpendingKeyset::PrivateMultiSig(k))
            }
            Err(e) => Err(e),
        }
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct PrivateMultiSigSpendingKeyset {
    pub network: Network,

    // Public Keys
    pub app_pub: PublicKey,
    pub hardware_pub: PublicKey,
    pub server_pub: PublicKey,

    // Signature of `server_pub`, signed with the WSM integrity key
    pub server_pub_integrity_sig: String,
}

impl PrivateMultiSigSpendingKeyset {
    #[must_use]
    pub fn new(
        network: Network,
        app_pub: PublicKey,
        hardware_pub: PublicKey,
        server_pub: PublicKey,
        server_pub_integrity_sig: String,
    ) -> Self {
        Self {
            network,
            app_pub,
            hardware_pub,
            server_pub,
            server_pub_integrity_sig,
        }
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct LegacyMultiSigSpendingKeyset {
    pub network: Network,

    // Public Keys
    #[serde(with = "bdk_utils::serde::descriptor_key")]
    pub app_dpub: DescriptorPublicKey,
    #[serde(with = "bdk_utils::serde::descriptor_key")]
    pub hardware_dpub: DescriptorPublicKey,
    #[serde(with = "bdk_utils::serde::descriptor_key")]
    pub server_dpub: DescriptorPublicKey,
}

impl LegacyMultiSigSpendingKeyset {
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

impl From<LegacyMultiSigSpendingKeyset> for DescriptorKeyset {
    fn from(k: LegacyMultiSigSpendingKeyset) -> Self {
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
    Keyset(LegacyMultiSigSpendingKeyset),
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

#[cfg(test)]
mod tests {
    use std::str::FromStr;

    use bdk_utils::bdk::keys::DescriptorPublicKey;

    use crate::account::{
        bitcoin::Network,
        spending::{LegacyMultiSigSpendingKeyset, SpendingKeyset},
    };

    #[test]
    fn test_spending_keyset_deserialization() {
        let inner = LegacyMultiSigSpendingKeyset {
            network: Network::BitcoinMain,
            app_dpub: DescriptorPublicKey::from_str("[0c5f9a1e/84'/1'/0']tpubDCxzhZZE31g2EqSv1UajMAw5Hd62htydz9r2XBkrccHgBh8uw3n62zr6Zjmj64tfTk8Tjxo6VctjUMAh5DXWTErfQPC6RmQhTdtNnXuTXTQ/*").unwrap(),
            hardware_dpub: DescriptorPublicKey::from_str("[0c5f9a1e/84'/1'/1']tpubDCxzhZZE31g2GPc7WcCG4gEwMMTxB9uAcLKuGtbi4n5uQKGLaaNAbTZmcK4Rq6pCesEitB7PV9k1hXs7qU8YTXXfd2LpVXmpUT9FcsvEXC3/*").unwrap(),
            server_dpub: DescriptorPublicKey::from_str("[0c5f9a1e/84'/1'/2']tpubDCxzhZZE31g2HvAVfbRdbkwV8kssfkzgqB25yaHNRLgLLyQBam5qzcNknfMBEPhDUjnDKa9PqUxvFy5zhAGaorhpNWioB7m3w8WZhUn3Mig/*").unwrap(),
        };

        let outer = SpendingKeyset::LegacyMultiSig(inner.clone());

        let inner_json = serde_json::to_string(&inner).unwrap();
        let outer_json = serde_json::to_string(&outer).unwrap();

        let deserialized_from_inner: SpendingKeyset = serde_json::from_str(&inner_json).unwrap();
        let deserialized_from_outer: SpendingKeyset = serde_json::from_str(&outer_json).unwrap();

        assert_eq!(outer, deserialized_from_inner);
        assert_eq!(outer, deserialized_from_outer);
    }
}
