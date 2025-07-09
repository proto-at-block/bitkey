use std::{fmt::Display, str::FromStr};

use crate::UniffiCustomTypeConverter;
use bitcoin::{psbt::Psbt, secp256k1::ecdsa::Signature, Network};
use crypto::frost::FrostShare;
use crypto::signature_utils::{CompactSignature, DERSignature};
use miniscript::DescriptorPublicKey;
use std::convert::TryFrom;

trait Stringable: Display + FromStr {}
impl Stringable for lightning_support::invoice::Sha256 {}
impl Stringable for crypto::keys::PublicKey {}
impl Stringable for Signature {}
impl Stringable for DescriptorPublicKey {}
impl Stringable for Psbt {}

impl<T> UniffiCustomTypeConverter for T
where
    T: Stringable,
    <T as FromStr>::Err: Send + Sync + std::error::Error + 'static,
{
    type Builtin = String;

    fn into_custom(val: Self::Builtin) -> uniffi::Result<Self> {
        Ok(Self::from_str(&val)?)
    }

    fn from_custom(obj: Self) -> Self::Builtin {
        obj.to_string()
    }
}

impl UniffiCustomTypeConverter for DERSignature {
    type Builtin = Vec<u8>;

    fn into_custom(val: Self::Builtin) -> uniffi::Result<Self> {
        DERSignature::try_from(val).map_err(Into::into)
    }

    fn from_custom(obj: Self) -> Self::Builtin {
        obj.0.to_vec()
    }
}

impl UniffiCustomTypeConverter for CompactSignature {
    type Builtin = Vec<u8>;

    fn into_custom(val: Self::Builtin) -> uniffi::Result<Self> {
        CompactSignature::try_from(val).map_err(Into::into)
    }

    fn from_custom(obj: Self) -> Self::Builtin {
        obj.0.to_vec()
    }
}

impl UniffiCustomTypeConverter for FrostShare {
    type Builtin = Vec<u8>;

    fn into_custom(val: Self::Builtin) -> uniffi::Result<Self> {
        // TODO [W-9921] Impl std::error:Error for FrostError
        Ok(FrostShare::from_slice(&val).unwrap())
    }

    fn from_custom(obj: Self) -> Self::Builtin {
        obj.serialize().to_vec()
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FfiNetwork {
    Bitcoin,
    Testnet,
    Signet,
    Regtest,
}

impl From<FfiNetwork> for Network {
    fn from(network: FfiNetwork) -> Self {
        match network {
            FfiNetwork::Bitcoin => Network::Bitcoin,
            FfiNetwork::Testnet => Network::Testnet,
            FfiNetwork::Signet => Network::Signet,
            FfiNetwork::Regtest => Network::Regtest,
        }
    }
}

impl From<Network> for FfiNetwork {
    fn from(network: Network) -> Self {
        match network {
            Network::Bitcoin => FfiNetwork::Bitcoin,
            Network::Testnet => FfiNetwork::Testnet,
            Network::Signet => FfiNetwork::Signet,
            _ => FfiNetwork::Regtest,
        }
    }
}

impl UniffiCustomTypeConverter for Network {
    type Builtin = FfiNetwork;

    fn into_custom(val: Self::Builtin) -> uniffi::Result<Self> {
        Ok(Network::from(val))
    }

    fn from_custom(obj: Self) -> Self::Builtin {
        FfiNetwork::from(obj)
    }
}
