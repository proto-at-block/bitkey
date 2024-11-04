use bitcoin::{bip32::Fingerprint, psbt::PartiallySignedTransaction, secp256k1::Secp256k1};
use miniscript::psbt::PsbtExt;
use next_gen::prelude::*;
use std::result::Result;

use crate::{
    command_interface::command,
    commands::SignedSighash,
    errors::CommandError,
    signing::{derived::DerivedKeySigner, sign, Signer},
    yield_from_,
};

use super::sign_sighash::derive_and_sign_sighash;

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn sign_transaction(
    mut psbt: PartiallySignedTransaction,
    origin_fingerprint: Fingerprint,
    async_sign: bool,
) -> Result<PartiallySignedTransaction, CommandError> {
    let derived_signables = DerivedKeySigner::new(origin_fingerprint).signables_for(&mut psbt)?;
    if derived_signables.is_empty() {
        return Err(CommandError::InvalidArguments);
    }

    for (public_key, signable) in derived_signables {
        let path = &signable.path;
        let signature = yield_from_!(derive_and_sign_sighash(signable.sighash, path, async_sign))?;
        let signed_sighash = SignedSighash {
            signature,
            public_key,
        };
        sign(&mut psbt, signable.input_index, signed_sighash)?
    }

    let _ = psbt.finalize_mut(&Secp256k1::verification_only()); // Optimistically finalize the PSBT; it's OK if this fails (e.g. if the application hasn't co-signed)

    Ok(psbt)
}

command!(SignTransaction = sign_transaction -> PartiallySignedTransaction,
    psbt: PartiallySignedTransaction,
    origin_fingerprint: Fingerprint,
    async_sign: bool
);
