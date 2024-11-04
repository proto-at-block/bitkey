use bitcoin::secp256k1::{PublicKey, ThirtyTwoByteHash};
use bitcoin::{bip32::DerivationPath, secp256k1::ecdsa::Signature};
use next_gen::generator;

use crate::signing::async_signer::derive_and_sign;
use crate::signing::Sighash;
use crate::{errors::CommandError, yield_from_};

pub struct SignedSighash {
    pub signature: Signature,
    pub public_key: PublicKey,
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
pub(crate) fn derive_and_sign_sighash(
    sighash: Sighash,
    derivation_path: &DerivationPath,
    async_sign: bool,
) -> Result<Signature, CommandError> {
    let sighash = match sighash {
        Sighash::Legacy(sighash) => sighash.into_32(),
        Sighash::SegwitV0(sighash) => sighash.into_32(),
    }
    .to_vec();

    yield_from_!(derive_and_sign(sighash, derivation_path.into(), async_sign))
}
