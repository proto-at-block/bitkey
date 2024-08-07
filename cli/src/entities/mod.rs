pub mod display;

use std::sync::Arc;

use bdk::{
    bitcoin::{
        secp256k1::{ecdsa::Signature, PublicKey},
        Network,
    },
    miniscript::DescriptorPublicKey,
    signer::TransactionSigner,
};
use serde::{Deserialize, Serialize};
use wca::pcsc::{NullTransactor, PCSCTransactor, Transactor, TransactorError};

use crate::{
    nfc::SafeTransactor,
    serde_helpers::{string as serde_string, AccountId, KeysetId},
    signers::{hardware::HardwareSigner, seed::SeedSigner, Authentication, Spending},
};

#[derive(Clone, Deserialize, Serialize)]
pub(crate) struct AuthenticationToken(pub(crate) String);

#[derive(Deserialize, Serialize)]
pub(crate) struct SignerHistory {
    pub(crate) active: SignerPair,
    pub(crate) inactive: Vec<SignerPair>,
}

#[derive(Deserialize, Serialize, PartialEq, Eq)]
pub(crate) struct SignerPair {
    pub(crate) network: Network,
    pub(crate) application: SeedSigner,
    pub(crate) hardware: HardwareSignerProxy,
}

#[derive(Deserialize, Serialize, PartialEq, Eq)]
pub(crate) enum HardwareSignerProxy {
    Fake(SeedSigner),
    Real(HardwareSigner),
}

impl HardwareSignerProxy {
    pub(crate) fn sign_context(&self) -> Result<SafeTransactor, TransactorError> {
        match self {
            HardwareSignerProxy::Fake(_) => Ok(SafeTransactor::new(NullTransactor)),
            HardwareSignerProxy::Real(_) => Ok(SafeTransactor::new(PCSCTransactor::new()?)),
        }
    }
}

impl Authentication for HardwareSignerProxy {
    fn public_key(&self) -> PublicKey {
        match self {
            HardwareSignerProxy::Fake(s) => Authentication::public_key(s),
            HardwareSignerProxy::Real(s) => Authentication::public_key(s),
        }
    }

    fn sign(
        &self,
        message: &[u8],
        context: &impl Transactor,
    ) -> Result<Signature, TransactorError> {
        match self {
            HardwareSignerProxy::Fake(s) => Authentication::sign(s, message, context),
            HardwareSignerProxy::Real(s) => Authentication::sign(s, message, context),
        }
    }
}

impl Spending for HardwareSignerProxy {
    fn public_key(&self) -> DescriptorPublicKey {
        match self {
            HardwareSignerProxy::Fake(s) => Spending::public_key(s),
            HardwareSignerProxy::Real(s) => Spending::public_key(s),
        }
    }

    fn next(
        &self,
        seen: impl Iterator<Item = DescriptorPublicKey>,
        context: &impl Transactor,
    ) -> Result<Self, TransactorError> {
        let proxy = match self {
            HardwareSignerProxy::Fake(s) => Self::Fake(Spending::next(s, seen, context)?),
            HardwareSignerProxy::Real(s) => Self::Real(Spending::next(s, seen, context)?),
        };
        Ok(proxy)
    }

    fn signer(&self, context: &SafeTransactor) -> Arc<dyn TransactionSigner> {
        match self {
            HardwareSignerProxy::Fake(s) => Spending::signer(s, context),
            HardwareSignerProxy::Real(s) => Spending::signer(s, context),
        }
    }
}

#[derive(Debug, Deserialize, Serialize)]
pub struct Account {
    pub id: AccountId,
    pub key_material: KeyMaterial,
}

#[derive(Debug, Deserialize, Serialize, PartialEq, Eq)]
pub(crate) enum KeyMaterial {
    Keyset(Vec<Keyset>),
    ShareDetail(Option<Shareset>),
}

#[derive(Debug, Deserialize, Serialize, Hash, PartialEq, Eq)]
pub struct Shareset {
    pub id: KeysetId,
    pub network: Network,
}

#[derive(Debug, Deserialize, Serialize, Hash, PartialEq, Eq)]
pub struct Keyset {
    pub id: KeysetId,
    pub network: Network,
    #[serde(flatten)]
    pub keys: DescriptorKeyset,
}

#[derive(Debug, Deserialize, Serialize, Hash, PartialEq, Eq)]
pub struct DescriptorKeyset {
    #[serde(with = "serde_string")]
    pub application: DescriptorPublicKey,
    #[serde(with = "serde_string")]
    pub hardware: DescriptorPublicKey,
    #[serde(with = "serde_string")]
    pub server: DescriptorPublicKey,
}
