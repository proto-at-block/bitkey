#![forbid(unsafe_code)]

pub mod attestation;
pub mod command_interface;
pub mod commands;
pub mod errors;
#[cfg(feature = "pcsc")]
pub mod pcsc;
pub mod signing;
mod wca;

use std::{
    fmt::{Display, Write},
    str::FromStr,
};

use errors::DecodeError;
use hex::ToHex;
use miniscript::DescriptorPublicKey;

pub mod fwpb {
    include!(concat!(env!("OUT_DIR"), "/fwpb.rs"));
}

const BIP32_HARDENED_BIT: u32 = 1 << 31;

fn child_to_str(child: &u32) -> String {
    if (child & BIP32_HARDENED_BIT) == 0 {
        child.to_string()
    } else {
        format!("{}'", child ^ BIP32_HARDENED_BIT)
    }
}

impl Display for fwpb::KeyDescriptor {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        if !self.origin_fingerprint.is_empty() {
            f.write_char('[')?;
            f.write_str(&self.origin_fingerprint.encode_hex::<String>())?;
            if let Some(ref path) = self.origin_path {
                f.write_fmt(format_args!("/{path}"))?;
            }
            f.write_char(']')?;
        }

        f.write_str(&bitcoin::base58::encode_check(&self.bare_bip32_key))?;

        let derivation_path = self
            .xpub_path
            .as_ref()
            .filter(|p| !p.child.is_empty())
            .map(|p| p.to_string());
        let wildcard = match self.wildcard() {
            fwpb::Wildcard::None => match self.xpub_path.as_ref().map_or(false, |p| p.wildcard) {
                true => Some("*"),
                false => None,
            },
            fwpb::Wildcard::Unhardened => Some("*"),
            fwpb::Wildcard::Hardened => Some("*'"),
        }
        .map(|s| s.to_string());

        match [derivation_path, wildcard] {
            [None, Some(w)] | [Some(w), None] => f.write_fmt(format_args!("/{w}"))?,
            [Some(p), Some(w)] => f.write_fmt(format_args!("/{p}/{w}"))?,
            [None, None] => {}
        }

        Ok(())
    }
}

impl TryFrom<fwpb::KeyDescriptor> for DescriptorPublicKey {
    type Error = DecodeError;

    fn try_from(value: fwpb::KeyDescriptor) -> Result<Self, Self::Error> {
        let descriptor_str: String = value.to_string();
        let descriptor = DescriptorPublicKey::from_str(&descriptor_str)?;

        Ok(descriptor)
    }
}

impl From<&fwpb::DerivationPath> for Vec<String> {
    fn from(value: &fwpb::DerivationPath) -> Self {
        value.child.iter().map(child_to_str).collect::<Vec<_>>()
    }
}

impl std::fmt::Display for fwpb::DerivationPath {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&Into::<Vec<String>>::into(self).join("/"))
    }
}

impl From<&[bitcoin::bip32::ChildNumber]> for fwpb::DerivationPath {
    fn from(value: &[bitcoin::bip32::ChildNumber]) -> Self {
        Self {
            child: value.iter().map(|cn| u32::from(*cn)).collect(),
            wildcard: false,
        }
    }
}

impl From<&bitcoin::bip32::DerivationPath> for fwpb::DerivationPath {
    fn from(value: &bitcoin::bip32::DerivationPath) -> Self {
        Self {
            child: value.into_iter().map(|cn| u32::from(*cn)).collect(),
            wildcard: false,
        }
    }
}

impl From<bitcoin::network::constants::Network> for fwpb::BtcNetwork {
    fn from(value: bitcoin::network::constants::Network) -> Self {
        match value {
            bitcoin::network::constants::Network::Bitcoin => Self::Bitcoin,
            bitcoin::network::constants::Network::Testnet => Self::Testnet,
            bitcoin::network::constants::Network::Regtest => Self::Regtest,
            bitcoin::network::constants::Network::Signet => Self::Signet,
            _ => unimplemented!("Unsupported network"),
        }
    }
}

impl From<fwpb::BtcNetwork> for bitcoin::network::constants::Network {
    fn from(value: fwpb::BtcNetwork) -> Self {
        match value {
            fwpb::BtcNetwork::Bitcoin => Self::Bitcoin,
            fwpb::BtcNetwork::Testnet => Self::Testnet,
            fwpb::BtcNetwork::Regtest => Self::Regtest,
            fwpb::BtcNetwork::Signet => Self::Signet,
        }
    }
}

pub enum EllipticCurve {
    Secp256k1,
    P256,
    Ed25519,
}

pub enum KeyEncoding {
    Raw,
}

pub struct PublicKeyMetadata {
    pub curve: EllipticCurve,
    pub encoding: KeyEncoding,
}

pub struct PublicKeyHandle {
    pub metadata: PublicKeyMetadata,
    pub material: Vec<u8>,
}

pub struct SignatureContext {
    pub pubkey: Option<PublicKeyHandle>,
    pub signature: Vec<u8>,
}

#[cfg(test)]
mod tests {
    use bitcoin::base58;

    use super::{fwpb, BIP32_HARDENED_BIT};

    #[test]
    fn descriptors() {
        assert_eq!(
            fwpb::KeyDescriptor {
                origin_fingerprint: vec![],
                origin_path: None,
                xpub_path: None,
                bare_bip32_key: vec![],
                wildcard: fwpb::Wildcard::None.into(),
            }
            .to_string(),
            "3QJmnh" // Base58check empty string
        );

        assert_eq!(
            fwpb::KeyDescriptor {
                origin_fingerprint: vec![0xab, 0xcd, 0x12, 0x34],
                origin_path: None,
                xpub_path: None,
                bare_bip32_key: vec![],
                wildcard: fwpb::Wildcard::None.into(),
            }
            .to_string(),
            "[abcd1234]3QJmnh" // Base58check empty string
        );

        assert_eq!(
            fwpb::KeyDescriptor {
                origin_fingerprint: vec![],
                origin_path: Some(fwpb::DerivationPath {
                    child: vec![85, 0, 1],
                    wildcard: true,
                }),
                xpub_path: None,
                bare_bip32_key: vec![],
                wildcard: fwpb::Wildcard::None.into(),
            }
            .to_string(),
            "3QJmnh" // Base58check empty string
        );

        assert_eq!(
            fwpb::KeyDescriptor {
                origin_fingerprint: vec![0xab, 0xcd, 0x12, 0x34],
                origin_path: Some(fwpb::DerivationPath {
                    child: vec![85, 0, 1],
                    wildcard: true,
                }),
                xpub_path: Some(fwpb::DerivationPath {
                    child: vec![],
                    wildcard: false,
                }),
                bare_bip32_key: base58::from_check("tpubDFM3v2rdpgQG6crKcQoePQSAuARPAsGrdNsHB9zBXvTHyYVGKAKqeSCZzLjHVRDS6vkyqJoRKRHkc1mn5Mcvb6a1CqaFLAwwhuXBHaLLwhL").unwrap(),
                wildcard: fwpb::Wildcard::Unhardened.into(),
            }.to_string(),
            "[abcd1234/85/0/1]tpubDFM3v2rdpgQG6crKcQoePQSAuARPAsGrdNsHB9zBXvTHyYVGKAKqeSCZzLjHVRDS6vkyqJoRKRHkc1mn5Mcvb6a1CqaFLAwwhuXBHaLLwhL/*"    // Base58check empty string
        );

        assert_eq!(
            fwpb::KeyDescriptor {
                origin_fingerprint: vec![0xab, 0xcd, 0x12, 0x34],
                origin_path: Some(fwpb::DerivationPath {
                    child: vec![85, 0, 1],
                    wildcard: true,
                }),
                xpub_path: Some(fwpb::DerivationPath {
                    child: vec![0, 1, 2],
                    wildcard: false,
                }),
                bare_bip32_key: base58::from_check("tpubDFM3v2rdpgQG6crKcQoePQSAuARPAsGrdNsHB9zBXvTHyYVGKAKqeSCZzLjHVRDS6vkyqJoRKRHkc1mn5Mcvb6a1CqaFLAwwhuXBHaLLwhL").unwrap(),
                wildcard: fwpb::Wildcard::Unhardened.into(),
            }.to_string(),
            "[abcd1234/85/0/1]tpubDFM3v2rdpgQG6crKcQoePQSAuARPAsGrdNsHB9zBXvTHyYVGKAKqeSCZzLjHVRDS6vkyqJoRKRHkc1mn5Mcvb6a1CqaFLAwwhuXBHaLLwhL/0/1/2/*"    // Base58check empty string
        );

        assert_eq!(
            fwpb::KeyDescriptor {
                origin_fingerprint: vec![0xab, 0xcd, 0x12, 0x34],
                origin_path: Some(fwpb::DerivationPath {
                    child: vec![85, 0, 1],
                    wildcard: true,
                }),
                xpub_path: Some(fwpb::DerivationPath {
                    child: vec![0, 1, 2],
                    wildcard: false,
                }),
                bare_bip32_key: base58::from_check("tpubDFM3v2rdpgQG6crKcQoePQSAuARPAsGrdNsHB9zBXvTHyYVGKAKqeSCZzLjHVRDS6vkyqJoRKRHkc1mn5Mcvb6a1CqaFLAwwhuXBHaLLwhL").unwrap(),
                wildcard: fwpb::Wildcard::Hardened.into(),
            }.to_string(),
            "[abcd1234/85/0/1]tpubDFM3v2rdpgQG6crKcQoePQSAuARPAsGrdNsHB9zBXvTHyYVGKAKqeSCZzLjHVRDS6vkyqJoRKRHkc1mn5Mcvb6a1CqaFLAwwhuXBHaLLwhL/0/1/2/*'"    // Base58check empty string
        );
    }

    #[test]
    fn derivation_paths() {
        let empty_path = fwpb::DerivationPath {
            child: vec![],
            wildcard: false,
        };
        assert_eq!(empty_path.to_string(), "");

        let path_with_children = fwpb::DerivationPath {
            child: vec![1, 2, 3],
            wildcard: false,
        };
        assert_eq!(path_with_children.to_string(), "1/2/3");

        let hardened_wildcard_path_with_children = fwpb::DerivationPath {
            child: vec![1 | BIP32_HARDENED_BIT, 2 | BIP32_HARDENED_BIT, 3],
            wildcard: false,
        };
        assert_eq!(hardened_wildcard_path_with_children.to_string(), "1'/2'/3");
    }
}
