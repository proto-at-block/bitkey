use crate::chaincode_delegation::{ChaincodeDelegationError, Result};
use bitcoin::bip32::{ChildNumber, ExtendedPubKey};
use bitcoin::secp256k1::{All, Scalar, Secp256k1};

pub const PROPRIETARY_KEY_PREFIX: &[u8] = b"CCDT";
pub const PROPRIETARY_KEY_SUBTYPE: u8 = 0;

/// Computes the tweak for a given path, and returns the final, tweaked public key.
pub fn tweak_from_path(
    secp: &Secp256k1<All>,
    xpub: ExtendedPubKey,
    path: &[ChildNumber],
) -> Result<(Scalar, ExtendedPubKey)> {
    let mut pk: ExtendedPubKey = xpub;
    let tweak: Scalar = path
        .iter()
        .try_fold(Scalar::ZERO, |acc, i| -> Result<Scalar> {
            // First, compute the tweak for the current level, and then tweak our XPUB.
            let (step_tweak, _) =
                pk.ckd_pub_tweak(*i)
                    .map_err(|e| ChaincodeDelegationError::TweakComputation {
                        reason: format!("Failed to compute tweak: {e}"),
                    })?;
            pk = pk
                .ckd_pub(secp, *i)
                .map_err(|e| ChaincodeDelegationError::KeyDerivation {
                    reason: format!("Failed to compute public key: {e}"),
                })?;

            // Then, we add the tweak to the accumulator, so we know the summation of all the
            // tweaks.
            step_tweak.add_tweak(&acc).map(|sk| sk.into()).map_err(|e| {
                ChaincodeDelegationError::TweakComputation {
                    reason: format!("Failed to add tweak: {e}"),
                }
            })
        })?;

    Ok((tweak, pk))
}
