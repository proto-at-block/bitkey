use crate::bitcoin::DescriptorId;
use crate::bitcoin::DescriptorType;
use crate::error::DescriptorError;
use crate::error::MiniscriptError;
use crate::keys::DescriptorPublicKey;
use crate::keys::DescriptorSecretKey;

use bdk_wallet::bitcoin::bip32::Fingerprint;
use bdk_wallet::bitcoin::key::Secp256k1;
use bdk_wallet::bitcoin::Network;
use bdk_wallet::chain::DescriptorExt;
use bdk_wallet::descriptor::{ExtendedDescriptor, IntoWalletDescriptor};
use bdk_wallet::keys::DescriptorPublicKey as BdkDescriptorPublicKey;
use bdk_wallet::keys::{DescriptorSecretKey as BdkDescriptorSecretKey, KeyMap};
use bdk_wallet::template::{
    Bip44, Bip44Public, Bip49, Bip49Public, Bip84, Bip84Public, Bip86, Bip86Public,
    DescriptorTemplate,
};
use bdk_wallet::KeychainKind;

use std::fmt::Display;
use std::str::FromStr;
use std::sync::Arc;

/// An expression of how to derive output scripts: https://github.com/bitcoin/bitcoin/blob/master/doc/descriptors.md
#[derive(Debug, uniffi::Object)]
#[uniffi::export(Debug, Display)]
pub struct Descriptor {
    pub extended_descriptor: ExtendedDescriptor,
    pub key_map: KeyMap,
}

#[uniffi::export]
impl Descriptor {
    /// Parse a string as a descriptor for the given network.
    #[uniffi::constructor]
    pub fn new(descriptor: String, network: Network) -> Result<Self, DescriptorError> {
        let secp = Secp256k1::new();
        let (extended_descriptor, key_map) = descriptor.into_wallet_descriptor(&secp, network)?;
        Ok(Self {
            extended_descriptor,
            key_map,
        })
    }

    /// Multi-account hierarchy descriptor: https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki
    #[uniffi::constructor]
    pub fn new_bip44(
        secret_key: &DescriptorSecretKey,
        keychain_kind: KeychainKind,
        network: Network,
    ) -> Self {
        let derivable_key = &secret_key.0;

        match derivable_key {
            BdkDescriptorSecretKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorSecretKey::XPrv(descriptor_x_key) => {
                let derivable_key = descriptor_x_key.xkey;
                let (extended_descriptor, key_map, _) =
                    Bip44(derivable_key, keychain_kind).build(network).unwrap();
                Self {
                    extended_descriptor,
                    key_map,
                }
            }
            BdkDescriptorSecretKey::MultiXPrv(_) => {
                unreachable!()
            }
        }
    }

    /// Multi-account hierarchy descriptor: https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki
    #[uniffi::constructor]
    pub fn new_bip44_public(
        public_key: &DescriptorPublicKey,
        fingerprint: String,
        keychain_kind: KeychainKind,
        network: Network,
    ) -> Result<Self, DescriptorError> {
        let fingerprint = Fingerprint::from_str(fingerprint.as_str()).map_err(|error| {
            DescriptorError::Bip32 {
                error_message: error.to_string(),
            }
        })?;
        let derivable_key = &public_key.0;

        match derivable_key {
            BdkDescriptorPublicKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorPublicKey::XPub(descriptor_x_key) => {
                let derivable_key = descriptor_x_key.xkey;
                let (extended_descriptor, key_map, _) =
                    Bip44Public(derivable_key, fingerprint, keychain_kind)
                        .build(network)
                        .map_err(DescriptorError::from)?;

                Ok(Self {
                    extended_descriptor,
                    key_map,
                })
            }
            BdkDescriptorPublicKey::MultiXPub(_) => {
                unreachable!()
            }
        }
    }

    /// P2SH nested P2WSH descriptor: https://github.com/bitcoin/bips/blob/master/bip-0049.mediawiki
    #[uniffi::constructor]
    pub fn new_bip49(
        secret_key: &DescriptorSecretKey,
        keychain_kind: KeychainKind,
        network: Network,
    ) -> Self {
        let derivable_key = &secret_key.0;

        match derivable_key {
            BdkDescriptorSecretKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorSecretKey::XPrv(descriptor_x_key) => {
                let derivable_key = descriptor_x_key.xkey;
                let (extended_descriptor, key_map, _) =
                    Bip49(derivable_key, keychain_kind).build(network).unwrap();
                Self {
                    extended_descriptor,
                    key_map,
                }
            }
            BdkDescriptorSecretKey::MultiXPrv(_) => {
                unreachable!()
            }
        }
    }

    /// P2SH nested P2WSH descriptor: https://github.com/bitcoin/bips/blob/master/bip-0049.mediawiki
    #[uniffi::constructor]
    pub fn new_bip49_public(
        public_key: &DescriptorPublicKey,
        fingerprint: String,
        keychain_kind: KeychainKind,
        network: Network,
    ) -> Result<Self, DescriptorError> {
        let fingerprint = Fingerprint::from_str(fingerprint.as_str()).map_err(|error| {
            DescriptorError::Bip32 {
                error_message: error.to_string(),
            }
        })?;
        let derivable_key = &public_key.0;

        match derivable_key {
            BdkDescriptorPublicKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorPublicKey::XPub(descriptor_x_key) => {
                let derivable_key = descriptor_x_key.xkey;
                let (extended_descriptor, key_map, _) =
                    Bip49Public(derivable_key, fingerprint, keychain_kind)
                        .build(network)
                        .map_err(DescriptorError::from)?;

                Ok(Self {
                    extended_descriptor,
                    key_map,
                })
            }
            BdkDescriptorPublicKey::MultiXPub(_) => {
                unreachable!()
            }
        }
    }

    /// Pay to witness PKH descriptor: https://github.com/bitcoin/bips/blob/master/bip-0084.mediawiki
    #[uniffi::constructor]
    pub fn new_bip84(
        secret_key: &DescriptorSecretKey,
        keychain_kind: KeychainKind,
        network: Network,
    ) -> Self {
        let derivable_key = &secret_key.0;

        match derivable_key {
            BdkDescriptorSecretKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorSecretKey::XPrv(descriptor_x_key) => {
                let derivable_key = descriptor_x_key.xkey;
                let (extended_descriptor, key_map, _) =
                    Bip84(derivable_key, keychain_kind).build(network).unwrap();
                Self {
                    extended_descriptor,
                    key_map,
                }
            }
            BdkDescriptorSecretKey::MultiXPrv(_) => {
                unreachable!()
            }
        }
    }

    /// Pay to witness PKH descriptor: https://github.com/bitcoin/bips/blob/master/bip-0084.mediawiki
    #[uniffi::constructor]
    pub fn new_bip84_public(
        public_key: &DescriptorPublicKey,
        fingerprint: String,
        keychain_kind: KeychainKind,
        network: Network,
    ) -> Result<Self, DescriptorError> {
        let fingerprint = Fingerprint::from_str(fingerprint.as_str()).map_err(|error| {
            DescriptorError::Bip32 {
                error_message: error.to_string(),
            }
        })?;
        let derivable_key = &public_key.0;

        match derivable_key {
            BdkDescriptorPublicKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorPublicKey::XPub(descriptor_x_key) => {
                let derivable_key = descriptor_x_key.xkey;
                let (extended_descriptor, key_map, _) =
                    Bip84Public(derivable_key, fingerprint, keychain_kind)
                        .build(network)
                        .map_err(DescriptorError::from)?;

                Ok(Self {
                    extended_descriptor,
                    key_map,
                })
            }
            BdkDescriptorPublicKey::MultiXPub(_) => {
                unreachable!()
            }
        }
    }

    /// Single key P2TR descriptor: https://github.com/bitcoin/bips/blob/master/bip-0086.mediawiki
    #[uniffi::constructor]
    pub fn new_bip86(
        secret_key: &DescriptorSecretKey,
        keychain_kind: KeychainKind,
        network: Network,
    ) -> Self {
        let derivable_key = &secret_key.0;

        match derivable_key {
            BdkDescriptorSecretKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorSecretKey::XPrv(descriptor_x_key) => {
                let derivable_key = descriptor_x_key.xkey;
                let (extended_descriptor, key_map, _) =
                    Bip86(derivable_key, keychain_kind).build(network).unwrap();
                Self {
                    extended_descriptor,
                    key_map,
                }
            }
            BdkDescriptorSecretKey::MultiXPrv(_) => {
                unreachable!()
            }
        }
    }

    /// Single key P2TR descriptor: https://github.com/bitcoin/bips/blob/master/bip-0086.mediawiki
    #[uniffi::constructor]
    pub fn new_bip86_public(
        public_key: &DescriptorPublicKey,
        fingerprint: String,
        keychain_kind: KeychainKind,
        network: Network,
    ) -> Result<Self, DescriptorError> {
        let fingerprint = Fingerprint::from_str(fingerprint.as_str()).map_err(|error| {
            DescriptorError::Bip32 {
                error_message: error.to_string(),
            }
        })?;
        let derivable_key = &public_key.0;

        match derivable_key {
            BdkDescriptorPublicKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorPublicKey::XPub(descriptor_x_key) => {
                let derivable_key = descriptor_x_key.xkey;
                let (extended_descriptor, key_map, _) =
                    Bip86Public(derivable_key, fingerprint, keychain_kind)
                        .build(network)
                        .map_err(DescriptorError::from)?;

                Ok(Self {
                    extended_descriptor,
                    key_map,
                })
            }
            BdkDescriptorPublicKey::MultiXPub(_) => {
                unreachable!()
            }
        }
    }

    /// Dangerously convert the descriptor to a string.
    pub fn to_string_with_secret(&self) -> String {
        let descriptor = &self.extended_descriptor;
        let key_map = &self.key_map;
        descriptor.to_string_with_secret(key_map)
    }

    /// Does this descriptor contain paths: https://github.com/bitcoin/bips/blob/master/bip-0389.mediawiki
    pub fn is_multipath(&self) -> bool {
        self.extended_descriptor.is_multipath()
    }

    /// A unique identifier for the descriptor.
    pub fn descriptor_id(&self) -> Arc<DescriptorId> {
        let d_id = self.extended_descriptor.descriptor_id();
        Arc::new(DescriptorId(d_id.0))
    }

    /// Return descriptors for all valid paths.
    pub fn to_single_descriptors(&self) -> Result<Vec<Arc<Descriptor>>, MiniscriptError> {
        self.extended_descriptor
            .clone()
            .into_single_descriptors()
            .map_err(MiniscriptError::from)
            .map(|descriptors| {
                descriptors
                    .into_iter()
                    .map(|desc| {
                        Arc::new(Descriptor {
                            extended_descriptor: desc,
                            key_map: self.key_map.clone(),
                        })
                    })
                    .collect()
            })
    }

    /// Computes an upper bound on the difference between a non-satisfied `TxIn`'s
    /// `segwit_weight` and a satisfied `TxIn`'s `segwit_weight`.
    pub fn max_weight_to_satisfy(&self) -> Result<u64, DescriptorError> {
        let weight = self
            .extended_descriptor
            .max_weight_to_satisfy()
            .map_err(|e| DescriptorError::Miniscript {
                error_message: e.to_string(),
            })?;
        Ok(weight.to_wu())
    }

    pub fn desc_type(&self) -> DescriptorType {
        self.extended_descriptor.desc_type()
    }
}

impl Display for Descriptor {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.extended_descriptor)
    }
}
