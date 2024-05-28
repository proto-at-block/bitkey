pub(crate) mod derived;

use bitcoin::sighash::{LegacySighash, SegwitV0Sighash};
use bitcoin::{
    bip32::{ChildNumber, DerivationPath, ExtendedPubKey},
    ecdsa::Signature as EcdsaSig,
    psbt::{Input, PartiallySignedTransaction},
    sighash::NonStandardSighashType,
    sighash::SighashCache,
    Transaction,
};
use miniscript::{
    descriptor::{DescriptorSecretKey, DescriptorXKey},
    psbt::{PsbtExt, PsbtSighashMsg, SighashError},
    DescriptorPublicKey,
};

use crate::commands::SignedSighash;

type DescriptorExtendedKey = DescriptorXKey<ExtendedPubKey>;

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("missing HD keypath for signature")]
    MissingHdKeypath,
    #[error(transparent)]
    InvalidSighash(#[from] SighashError),
    #[error("no taproot signing support, yet!")]
    TapRootUnsupported,
    #[error("attempted sign with a descriptor lacking an xpub")]
    InvalidDescriptor,
    #[error("non-standard ECDSA sighash type")]
    NonStandardSighashType(#[from] NonStandardSighashType),
}

pub(crate) struct Signable {
    pub(crate) path: DerivationPath,
    pub(crate) sighash: Sighash,
    pub(crate) input_index: usize,
}

pub(crate) trait Signer {
    fn signables_for(&self, psbt: &PartiallySignedTransaction) -> Result<Vec<Signable>, Error>;
}

fn is_finalised(input: &Input) -> bool {
    input.final_script_sig.is_some() || input.final_script_witness.is_some()
}

pub(crate) fn sign(
    psbt: &mut PartiallySignedTransaction,
    input_index: usize,
    signed_sighash: SignedSighash,
) -> Result<(), Error> {
    let input = &mut psbt.inputs[input_index];

    let public_key = match signed_sighash.descriptor {
        DescriptorPublicKey::XPub(xpub) => xpub.xkey.public_key,
        _ => return Err(Error::InvalidDescriptor),
    };
    assert!(input.bip32_derivation.contains_key(&public_key));

    input.partial_sigs.insert(
        bitcoin::PublicKey::new(public_key),
        EcdsaSig {
            sig: signed_sighash.signature,
            hash_ty: input.ecdsa_hash_ty()?,
        },
    );

    Ok(())
}

pub(crate) fn sighash(
    cache: &mut SighashCache<&Transaction>,
    psbt: &PartiallySignedTransaction,
    input_index: usize,
) -> Result<Sighash, Error> {
    match psbt.sighash_msg(input_index, cache, None)? {
        PsbtSighashMsg::TapSighash(_) => Err(Error::TapRootUnsupported),
        PsbtSighashMsg::LegacySighash(sighash) => Ok(Sighash::Legacy(sighash)),
        PsbtSighashMsg::SegwitV0Sighash(sighash) => Ok(Sighash::SegwitV0(sighash)),
    }
}

#[derive(Clone, Copy)]
pub enum Sighash {
    Legacy(LegacySighash),
    SegwitV0(SegwitV0Sighash),
}

pub trait ExtendDerivationPath {
    fn extend_derivation_path(&self, path: &[ChildNumber]) -> Self;
}

impl ExtendDerivationPath for DescriptorPublicKey {
    fn extend_derivation_path(&self, path: &[ChildNumber]) -> Self {
        match self {
            DescriptorPublicKey::Single(_) => unimplemented!(),
            DescriptorPublicKey::MultiXPub(_) => unimplemented!(),
            DescriptorPublicKey::XPub(xpub) => DescriptorPublicKey::XPub(DescriptorXKey {
                derivation_path: xpub.derivation_path.extend(path),
                origin: xpub.origin.clone(),
                ..*xpub
            }),
        }
    }
}

impl ExtendDerivationPath for DescriptorSecretKey {
    fn extend_derivation_path(&self, path: &[ChildNumber]) -> Self {
        match self {
            DescriptorSecretKey::Single(_) => unimplemented!(),
            DescriptorSecretKey::MultiXPrv(_) => unimplemented!(),
            DescriptorSecretKey::XPrv(xprv) => DescriptorSecretKey::XPrv(DescriptorXKey {
                derivation_path: xprv.derivation_path.extend(path),
                origin: xprv.origin.clone(),
                ..*xprv
            }),
        }
    }
}
