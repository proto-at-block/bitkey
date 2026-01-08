use crate::error::{Bip32Error, Bip39Error, DescriptorKeyError};
use crate::{impl_from_core_type, impl_into_core_type};

use bdk_wallet::bitcoin::bip32::DerivationPath as BdkDerivationPath;
use bdk_wallet::bitcoin::key::Secp256k1;
use bdk_wallet::bitcoin::secp256k1::rand;
use bdk_wallet::bitcoin::secp256k1::rand::Rng;
use bdk_wallet::bitcoin::Network;
use bdk_wallet::keys::bip39::WordCount;
use bdk_wallet::keys::bip39::{Language, Mnemonic as BdkMnemonic};
use bdk_wallet::keys::{
    DerivableKey, DescriptorPublicKey as BdkDescriptorPublicKey,
    DescriptorSecretKey as BdkDescriptorSecretKey, ExtendedKey, GeneratableKey, GeneratedKey,
};
use bdk_wallet::miniscript::descriptor::{DescriptorXKey, Wildcard};
use bdk_wallet::miniscript::BareCtx;

use std::fmt::Display;
use std::str::FromStr;
use std::sync::Arc;

/// A mnemonic seed phrase to recover a BIP-32 wallet.
#[derive(uniffi::Object)]
#[uniffi::export(Display)]
pub struct Mnemonic(BdkMnemonic);

#[uniffi::export]
impl Mnemonic {
    /// Generate a mnemonic given a word count.
    #[uniffi::constructor]
    pub fn new(word_count: WordCount) -> Self {
        // TODO 4: I DON'T KNOW IF THIS IS A DECENT WAY TO GENERATE ENTROPY PLEASE CONFIRM
        let mut rng = rand::thread_rng();
        let mut entropy = [0u8; 32];
        rng.fill(&mut entropy);

        let generated_key: GeneratedKey<_, BareCtx> =
            BdkMnemonic::generate_with_entropy((word_count, Language::English), entropy).unwrap();
        let mnemonic = BdkMnemonic::parse_in(Language::English, generated_key.to_string()).unwrap();
        Mnemonic(mnemonic)
    }
    /// Parse a string as a mnemonic seed phrase.
    #[uniffi::constructor]
    pub fn from_string(mnemonic: String) -> Result<Self, Bip39Error> {
        BdkMnemonic::from_str(&mnemonic)
            .map(Mnemonic)
            .map_err(Bip39Error::from)
    }

    /// Construct a mnemonic given an array of bytes. Note that using weak entropy will result in a loss
    /// of funds. To ensure the entropy is generated properly, read about your operating
    /// system specific ways to generate secure random numbers.
    #[uniffi::constructor]
    pub fn from_entropy(entropy: Vec<u8>) -> Result<Self, Bip39Error> {
        BdkMnemonic::from_entropy(entropy.as_slice())
            .map(Mnemonic)
            .map_err(Bip39Error::from)
    }
}

impl Display for Mnemonic {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}

/// A BIP-32 derivation path.
#[derive(Clone, Debug, uniffi::Object)]
#[uniffi::export(Display)]
pub struct DerivationPath(pub(crate) BdkDerivationPath);

#[uniffi::export]
impl DerivationPath {
    /// Parse a string as a BIP-32 derivation path.
    #[uniffi::constructor]
    pub fn new(path: String) -> Result<Self, Bip32Error> {
        BdkDerivationPath::from_str(&path)
            .map(DerivationPath)
            .map_err(Bip32Error::from)
    }

    /// Returns derivation path for a master key (i.e. empty derivation path)
    #[uniffi::constructor]
    pub fn master() -> Arc<Self> {
        Arc::new(BdkDerivationPath::master().into())
    }

    /// Returns whether derivation path represents master key (i.e. it's length
    /// is empty). True for `m` path.
    pub fn is_master(&self) -> bool {
        self.0.is_master()
    }

    /// Returns length of the derivation path
    pub fn len(&self) -> u64 {
        self.0.len() as u64
    }

    /// Returns `true` if the derivation path is empty
    pub fn is_empty(&self) -> bool {
        self.0.is_empty()
    }
}

impl Display for DerivationPath {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}

impl_from_core_type!(BdkDerivationPath, DerivationPath);
impl_into_core_type!(DerivationPath, BdkDerivationPath);

/// A descriptor containing secret data.
#[derive(Debug, uniffi::Object)]
#[uniffi::export(Debug, Display)]
pub struct DescriptorSecretKey(pub(crate) BdkDescriptorSecretKey);

#[uniffi::export]
impl DescriptorSecretKey {
    /// Construct a secret descriptor using a mnemonic.
    #[uniffi::constructor]
    pub fn new(network: Network, mnemonic: &Mnemonic, password: Option<String>) -> Self {
        let mnemonic = mnemonic.0.clone();
        let xkey: ExtendedKey = (mnemonic, password).into_extended_key().unwrap();
        let descriptor_secret_key = BdkDescriptorSecretKey::XPrv(DescriptorXKey {
            origin: None,
            xkey: xkey.into_xprv(network).unwrap(),
            derivation_path: BdkDerivationPath::master(),
            wildcard: Wildcard::Unhardened,
        });
        Self(descriptor_secret_key)
    }

    /// Attempt to parse a string as a descriptor secret key.
    #[uniffi::constructor]
    pub fn from_string(private_key: String) -> Result<Self, DescriptorKeyError> {
        let descriptor_secret_key = BdkDescriptorSecretKey::from_str(private_key.as_str())
            .map_err(DescriptorKeyError::from)?;
        Ok(Self(descriptor_secret_key))
    }

    /// Derive a descriptor secret key at a given derivation path.
    pub fn derive(&self, path: &DerivationPath) -> Result<Arc<Self>, DescriptorKeyError> {
        let secp = Secp256k1::new();
        let descriptor_secret_key = &self.0;
        match descriptor_secret_key {
            BdkDescriptorSecretKey::Single(_) => Err(DescriptorKeyError::InvalidKeyType),
            BdkDescriptorSecretKey::XPrv(descriptor_x_key) => {
                let derived_xprv = descriptor_x_key
                    .xkey
                    .derive_priv(&secp, &path.0)
                    .map_err(DescriptorKeyError::from)?;
                let key_source = match descriptor_x_key.origin.clone() {
                    Some((fingerprint, origin_path)) => (fingerprint, origin_path.extend(&path.0)),
                    None => (descriptor_x_key.xkey.fingerprint(&secp), path.0.clone()),
                };
                let derived_descriptor_secret_key = BdkDescriptorSecretKey::XPrv(DescriptorXKey {
                    origin: Some(key_source),
                    xkey: derived_xprv,
                    derivation_path: BdkDerivationPath::default(),
                    wildcard: descriptor_x_key.wildcard,
                });
                Ok(Arc::new(Self(derived_descriptor_secret_key)))
            }
            BdkDescriptorSecretKey::MultiXPrv(_) => Err(DescriptorKeyError::InvalidKeyType),
        }
    }

    /// Extend the descriptor secret key by the derivation path.
    pub fn extend(&self, path: &DerivationPath) -> Result<Arc<Self>, DescriptorKeyError> {
        let descriptor_secret_key = &self.0;
        match descriptor_secret_key {
            BdkDescriptorSecretKey::Single(_) => Err(DescriptorKeyError::InvalidKeyType),
            BdkDescriptorSecretKey::XPrv(descriptor_x_key) => {
                let extended_path = descriptor_x_key.derivation_path.extend(&path.0);
                let extended_descriptor_secret_key = BdkDescriptorSecretKey::XPrv(DescriptorXKey {
                    origin: descriptor_x_key.origin.clone(),
                    xkey: descriptor_x_key.xkey,
                    derivation_path: extended_path,
                    wildcard: descriptor_x_key.wildcard,
                });
                Ok(Arc::new(Self(extended_descriptor_secret_key)))
            }
            BdkDescriptorSecretKey::MultiXPrv(_) => Err(DescriptorKeyError::InvalidKeyType),
        }
    }

    /// Return the descriptor public key corresponding to this secret.
    pub fn as_public(&self) -> Arc<DescriptorPublicKey> {
        let secp = Secp256k1::new();
        let descriptor_public_key = self.0.to_public(&secp).unwrap();
        Arc::new(DescriptorPublicKey(descriptor_public_key))
    }

    /// Return the bytes of this descriptor secret key.
    pub fn secret_bytes(&self) -> Vec<u8> {
        let inner = &self.0;
        let secret_bytes: Vec<u8> = match inner {
            BdkDescriptorSecretKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorSecretKey::XPrv(descriptor_x_key) => {
                descriptor_x_key.xkey.private_key.secret_bytes().to_vec()
            }
            BdkDescriptorSecretKey::MultiXPrv(_) => {
                unreachable!()
            }
        };

        secret_bytes
    }
}

impl Display for DescriptorSecretKey {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        self.0.fmt(f)
    }
}

/// A descriptor public key.
#[derive(Debug, uniffi::Object)]
#[uniffi::export(Debug, Display)]
pub struct DescriptorPublicKey(pub(crate) BdkDescriptorPublicKey);

#[uniffi::export]
impl DescriptorPublicKey {
    /// Attempt to parse a string as a descriptor public key.
    #[uniffi::constructor]
    pub fn from_string(public_key: String) -> Result<Self, DescriptorKeyError> {
        let descriptor_public_key = BdkDescriptorPublicKey::from_str(public_key.as_str())
            .map_err(DescriptorKeyError::from)?;
        Ok(Self(descriptor_public_key))
    }

    /// Derive the descriptor public key at the given derivation path.
    pub fn derive(&self, path: &DerivationPath) -> Result<Arc<Self>, DescriptorKeyError> {
        let secp = Secp256k1::new();
        let descriptor_public_key = &self.0;
        match descriptor_public_key {
            BdkDescriptorPublicKey::Single(_) => Err(DescriptorKeyError::InvalidKeyType),
            BdkDescriptorPublicKey::XPub(descriptor_x_key) => {
                let derived_xpub = descriptor_x_key
                    .xkey
                    .derive_pub(&secp, &path.0)
                    .map_err(DescriptorKeyError::from)?;
                let key_source = match descriptor_x_key.origin.clone() {
                    Some((fingerprint, origin_path)) => (fingerprint, origin_path.extend(&path.0)),
                    None => (descriptor_x_key.xkey.fingerprint(), path.0.clone()),
                };
                let derived_descriptor_public_key = BdkDescriptorPublicKey::XPub(DescriptorXKey {
                    origin: Some(key_source),
                    xkey: derived_xpub,
                    derivation_path: BdkDerivationPath::default(),
                    wildcard: descriptor_x_key.wildcard,
                });
                Ok(Arc::new(Self(derived_descriptor_public_key)))
            }
            BdkDescriptorPublicKey::MultiXPub(_) => Err(DescriptorKeyError::InvalidKeyType),
        }
    }

    /// Extend the descriptor public key by the given derivation path.
    pub fn extend(&self, path: &DerivationPath) -> Result<Arc<Self>, DescriptorKeyError> {
        let descriptor_public_key = &self.0;
        match descriptor_public_key {
            BdkDescriptorPublicKey::Single(_) => Err(DescriptorKeyError::InvalidKeyType),
            BdkDescriptorPublicKey::XPub(descriptor_x_key) => {
                let extended_path = descriptor_x_key.derivation_path.extend(&path.0);
                let extended_descriptor_public_key = BdkDescriptorPublicKey::XPub(DescriptorXKey {
                    origin: descriptor_x_key.origin.clone(),
                    xkey: descriptor_x_key.xkey,
                    derivation_path: extended_path,
                    wildcard: descriptor_x_key.wildcard,
                });
                Ok(Arc::new(Self(extended_descriptor_public_key)))
            }
            BdkDescriptorPublicKey::MultiXPub(_) => Err(DescriptorKeyError::InvalidKeyType),
        }
    }

    /// Whether or not this key has multiple derivation paths.
    pub fn is_multipath(&self) -> bool {
        self.0.is_multipath()
    }

    /// The fingerprint of the master key associated with this key, `0x00000000` if none.
    pub fn master_fingerprint(&self) -> String {
        self.0.master_fingerprint().to_string()
    }
}

impl Display for DescriptorPublicKey {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        self.0.fmt(f)
    }
}
