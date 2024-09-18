use bitcoin::{bip32::DerivationPath, psbt::PartiallySignedTransaction, secp256k1::Secp256k1};
use miniscript::{psbt::PsbtExt, DescriptorPublicKey};
use next_gen::prelude::*;
use std::result::Result;

use crate::{
    command_interface::command,
    commands::SignedSighash,
    errors::CommandError,
    signing::{derived::DerivedKeySigner, sign, Signer},
    yield_from_,
};

use super::generate_keys::derive;
use super::sign_sighash::derive_and_sign_sighash;

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn sign_transaction(
    mut psbt: PartiallySignedTransaction,
    async_sign: bool,
) -> Result<PartiallySignedTransaction, CommandError> {
    let default_path = DerivationPath::default();
    let derived_signables = match yield_from_!(derive(Default::default(), &default_path)) {
        Ok(master_dpub) => {
            let xpub = match master_dpub {
                DescriptorPublicKey::XPub(xpub) => xpub,
                _ => return Err(CommandError::KeyGenerationFailed),
            };

            DerivedKeySigner::new(xpub).signables_for(&mut psbt)?
        }
        Err(err @ CommandError::Unauthenticated) => return Err(err),
        _ => vec![],
    };

    if derived_signables.is_empty() {
        return Err(CommandError::InvalidArguments);
    }

    for signable in derived_signables {
        let path = &signable.path;
        let descriptor = yield_from_!(derive(Default::default(), path))?;
        let signature = yield_from_!(derive_and_sign_sighash(signable.sighash, path, async_sign))?;
        let signed_sighash = SignedSighash {
            signature,
            descriptor,
        };
        sign(&mut psbt, signable.input_index, signed_sighash)?
    }

    let _ = psbt.finalize_mut(&Secp256k1::verification_only()); // Optimistically finalize the PSBT; it's OK if this fails (e.g. if the application hasn't co-signed)

    Ok(psbt)
}

command!(SignTransaction = sign_transaction -> PartiallySignedTransaction,
    psbt: PartiallySignedTransaction,
    async_sign: bool
);
