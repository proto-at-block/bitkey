//! Action Proof verification internals.
//!
//! This module contains the low-level signature verification logic for Action Proof.
//! For the high-level authorization API, see `authorization.rs`.

use std::str::FromStr;

use secp256k1::ecdsa::{RecoverableSignature, RecoveryId};
use secp256k1::hashes::{sha256, Hash};
use secp256k1::{Message, PublicKey, Secp256k1};
use serde::Deserialize;
use tracing::{event, Level};

use action_proof::{
    build_payload, compute_token_binding, Action, BuildError, ContextBinding, Field,
};
use errors::ApiError;

use crate::signers::Signers;

pub const ACTION_PROOF_HEADER: &str = "Action-Proof";

/// Recoverable ECDSA signature length: 64 bytes (r, s) + 1 byte recovery ID
const RECOVERABLE_SIGNATURE_LEN: usize = 65;

const ERR_APP_SIGNATURE_REQUIRED: &str = "app signature required";
const ERR_HARDWARE_SIGNATURE_REQUIRED: &str = "hardware signature required";
const ERR_AT_LEAST_ONE_SIGNATURE_REQUIRED: &str = "at least one signature required";
const ERR_DUPLICATE_SIGNATURE: &str = "duplicate signature from same key";
const ERR_UNKNOWN_KEY_SIGNATURE: &str = "signature from unknown key";
const ERR_SIGNATURE_NOT_LOW_S: &str = "signature must be low-S normalized";
const ERR_INVALID_SIGNATURE_LENGTH: &str = "signature must be 65 bytes";

#[derive(Debug, Clone, Deserialize)]
pub(crate) struct ActionProofHeader {
    pub version: u8,
    pub signatures: Vec<String>, // hex, 65 bytes each (compact + recovery_id)
    pub nonce: Option<String>,
}

/// Validates signature requirements against what was actually signed.
pub fn validate_signature_requirements(
    hw_signed: bool,
    app_signed: bool,
    signers: Signers,
) -> Result<(), ApiError> {
    match signers {
        Signers::All => {
            if !hw_signed {
                event!(Level::WARN, "hardware signature required but missing");
                return Err(ApiError::GenericForbidden(
                    ERR_HARDWARE_SIGNATURE_REQUIRED.to_string(),
                ));
            }
            if !app_signed {
                event!(Level::WARN, "app signature required but missing");
                return Err(ApiError::GenericForbidden(
                    ERR_APP_SIGNATURE_REQUIRED.to_string(),
                ));
            }
        }
        Signers::Any => {
            if !hw_signed && !app_signed {
                event!(
                    Level::WARN,
                    "at least one signature required but none present"
                );
                return Err(ApiError::GenericForbidden(
                    ERR_AT_LEAST_ONE_SIGNATURE_REQUIRED.to_string(),
                ));
            }
        }
    }
    Ok(())
}

/// Verifies an Action Proof and returns (hw_signed, app_signed).
#[allow(clippy::too_many_arguments)]
pub(crate) fn verify_action_proof(
    version: u8,
    signatures: &[String],
    nonce: Option<&str>,
    hw_pubkey: Option<&str>,
    app_pubkey: Option<&str>,
    jwt: &str,
    action: Action,
    field: Field,
    value: Option<&str>,
    current: Option<&str>,
    extra_bindings: &[(String, String)],
) -> Result<(bool, bool), ApiError> {
    if version != action_proof::CANONICAL_VERSION {
        event!(
            Level::WARN,
            "action-proof: version mismatch (expected={}, got={})",
            action_proof::CANONICAL_VERSION,
            version
        );
        return Err(ApiError::GenericBadRequest(
            "unsupported action-proof version".to_string(),
        ));
    }

    let token_binding = compute_token_binding(jwt);

    let mut bindings: Vec<(&str, &str)> = extra_bindings
        .iter()
        .map(|(k, v)| (k.as_str(), v.as_str()))
        .collect();
    if let Some(n) = nonce {
        bindings.push((ContextBinding::Nonce.key(), n));
    }
    bindings.push((ContextBinding::TokenBinding.key(), &token_binding));
    bindings.sort_by_key(|b| b.0);

    let canonical =
        build_payload(action, field, value, current, &bindings).map_err(|e| match e {
            BuildError::InvalidValue(err) => {
                event!(
                    Level::WARN,
                    "action-proof: invalid value in payload: {:?}",
                    err
                );
                ApiError::GenericBadRequest("invalid value".to_string())
            }
            other => {
                event!(
                    Level::ERROR,
                    "action-proof: failed to build canonical: {:?}",
                    other
                );
                ApiError::GenericInternalApplicationError(
                    "failed to build canonical payload".to_string(),
                )
            }
        })?;

    verify_recoverable_signatures(signatures, &canonical, hw_pubkey, app_pubkey).map_err(|msg| {
        event!(
            Level::WARN,
            "action-proof: signature verification failed: {}",
            msg
        );
        ApiError::GenericForbidden(msg.to_string())
    })
}

/// Returns true if the signature is already in low-S form (not malleable).
fn is_low_s(sig: &secp256k1::ecdsa::Signature) -> bool {
    let original = sig.serialize_compact();
    let mut normalized = *sig;
    normalized.normalize_s();
    original == normalized.serialize_compact()
}

fn verify_recoverable_signatures(
    sigs: &[String],
    message_bytes: &[u8],
    hw_pubkey: Option<&str>,
    app_pubkey: Option<&str>,
) -> Result<(bool, bool), &'static str> {
    if sigs.is_empty() {
        return Err(ERR_AT_LEAST_ONE_SIGNATURE_REQUIRED);
    }

    let secp = Secp256k1::verification_only();
    let digest = sha256::Hash::hash(message_bytes);
    let message = Message::from_digest(digest.to_byte_array());

    let hw_pk = hw_pubkey.and_then(|pk| match PublicKey::from_str(pk) {
        Ok(key) => Some(key),
        Err(e) => {
            event!(Level::ERROR, error = ?e, "Failed to parse hardware public key");
            None
        }
    });
    let app_pk = app_pubkey.and_then(|pk| match PublicKey::from_str(pk) {
        Ok(key) => Some(key),
        Err(e) => {
            event!(Level::ERROR, error = ?e, "Failed to parse app public key");
            None
        }
    });

    let mut hw_signed = false;
    let mut app_signed = false;

    for sig_hex in sigs {
        let sig_bytes = hex::decode(sig_hex).map_err(|_| "invalid signature hex")?;

        if sig_bytes.len() != RECOVERABLE_SIGNATURE_LEN {
            return Err(ERR_INVALID_SIGNATURE_LENGTH);
        }

        let recovery_id =
            RecoveryId::from_i32(sig_bytes[64] as i32).map_err(|_| "invalid recovery id")?;

        let sig = RecoverableSignature::from_compact(&sig_bytes[..64], recovery_id)
            .map_err(|_| "invalid signature format")?;

        // Reject high-S signatures to prevent malleability
        let standard_sig = sig.to_standard();
        if !is_low_s(&standard_sig) {
            event!(
                Level::WARN,
                "action-proof: signature has high-S value (malleable)"
            );
            return Err(ERR_SIGNATURE_NOT_LOW_S);
        }

        let recovered = secp
            .recover_ecdsa(&message, &sig)
            .map_err(|_| "signature recovery failed")?;

        let is_hw = hw_pk.as_ref() == Some(&recovered);
        let is_app = app_pk.as_ref() == Some(&recovered);

        if !is_hw && !is_app {
            event!(
                Level::WARN,
                "action-proof: signature from unknown key (recovered: {})",
                recovered
            );
            return Err(ERR_UNKNOWN_KEY_SIGNATURE);
        }

        if (is_hw && hw_signed) || (is_app && app_signed) {
            let key_type = if is_hw { "hardware" } else { "app" };
            event!(
                Level::WARN,
                "action-proof: duplicate {} signature detected",
                key_type
            );
            return Err(ERR_DUPLICATE_SIGNATURE);
        }

        hw_signed |= is_hw;
        app_signed |= is_app;
    }

    Ok((hw_signed, app_signed))
}

#[cfg(test)]
mod tests {
    use super::*;
    use secp256k1::SecretKey;

    const TEST_MESSAGE: &[u8] = b"test message";

    fn sign(secp: &Secp256k1<secp256k1::All>, sk: &SecretKey) -> String {
        let digest = sha256::Hash::hash(TEST_MESSAGE);
        let msg = Message::from_digest(digest.to_byte_array());
        let sig = secp.sign_ecdsa_recoverable(&msg, sk);
        let (rid, compact) = sig.serialize_compact();
        let mut bytes = compact.to_vec();
        bytes.push(rid.to_i32() as u8);
        hex::encode(bytes)
    }

    #[test]
    fn signature_verification_valid() {
        let secp = Secp256k1::new();
        let hw_sk = SecretKey::from_slice(&[1u8; 32]).unwrap();
        let app_sk = SecretKey::from_slice(&[2u8; 32]).unwrap();
        let (hw_pk, app_pk) = (
            hw_sk.public_key(&secp).to_string(),
            app_sk.public_key(&secp).to_string(),
        );
        let (hw_sig, app_sig) = (sign(&secp, &hw_sk), sign(&secp, &app_sk));

        // (sigs, hw_pk, app_pk) -> (expect_hw, expect_app)
        let cases: &[(&[&str], Option<&str>, Option<&str>, bool, bool)] = &[
            (&[&hw_sig], Some(&hw_pk), None, true, false), // hw only
            (&[&app_sig], None, Some(&app_pk), false, true), // app only
            (
                &[&hw_sig, &app_sig],
                Some(&hw_pk),
                Some(&app_pk),
                true,
                true,
            ), // both
        ];
        for (sigs, hw, app, exp_hw, exp_app) in cases {
            let sigs: Vec<String> = sigs.iter().map(|s| s.to_string()).collect();
            let (hw_signed, app_signed) =
                verify_recoverable_signatures(&sigs, TEST_MESSAGE, *hw, *app).unwrap();
            assert_eq!((hw_signed, app_signed), (*exp_hw, *exp_app));
        }
    }

    #[test]
    fn signature_verification_errors() {
        let secp = Secp256k1::new();
        let hw_sk = SecretKey::from_slice(&[1u8; 32]).unwrap();
        let app_sk = SecretKey::from_slice(&[2u8; 32]).unwrap();
        let unknown_sk = SecretKey::from_slice(&[99u8; 32]).unwrap();
        let (hw_pk, app_pk) = (
            hw_sk.public_key(&secp).to_string(),
            app_sk.public_key(&secp).to_string(),
        );
        let (hw_sig, app_sig, unknown_sig) = (
            sign(&secp, &hw_sk),
            sign(&secp, &app_sk),
            sign(&secp, &unknown_sk),
        );

        let cases: &[(&[&str], Option<&str>, Option<&str>, &str)] = &[
            (&[], None, None, ERR_AT_LEAST_ONE_SIGNATURE_REQUIRED),
            (&["deadbeef"], None, None, ERR_INVALID_SIGNATURE_LENGTH),
            (
                &[&unknown_sig],
                Some(&hw_pk),
                None,
                ERR_UNKNOWN_KEY_SIGNATURE,
            ),
            (
                &[&hw_sig, &hw_sig],
                Some(&hw_pk),
                None,
                ERR_DUPLICATE_SIGNATURE,
            ),
            (
                &[&app_sig, &app_sig],
                None,
                Some(&app_pk),
                ERR_DUPLICATE_SIGNATURE,
            ),
        ];
        for (sigs, hw, app, expected) in cases {
            let sigs: Vec<String> = sigs.iter().map(|s| s.to_string()).collect();
            assert_eq!(
                verify_recoverable_signatures(&sigs, TEST_MESSAGE, *hw, *app).unwrap_err(),
                *expected
            );
        }
    }

    #[test]
    fn signature_verification_rejects_high_s() {
        let secp = Secp256k1::new();
        let sk = SecretKey::from_slice(&[1u8; 32]).unwrap();
        let pk = sk.public_key(&secp).to_string();

        // Create a valid signature
        let digest = sha256::Hash::hash(TEST_MESSAGE);
        let msg = Message::from_digest(digest.to_byte_array());
        let sig = secp.sign_ecdsa_recoverable(&msg, &sk);
        let (rid, compact) = sig.serialize_compact();

        // Mallate to high-S form by computing S' = N - S where N is the curve order
        // The curve order N for secp256k1
        let n = [
            0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
            0xFF, 0xFE, 0xBA, 0xAE, 0xDC, 0xE6, 0xAF, 0x48, 0xA0, 0x3B, 0xBF, 0xD2, 0x5E, 0x8C,
            0xD0, 0x36, 0x41, 0x41,
        ];

        // S is in the second 32 bytes of the compact signature
        let mut s_bytes = [0u8; 32];
        s_bytes.copy_from_slice(&compact[32..64]);

        // Compute N - S (high-S)
        let mut high_s = [0u8; 32];
        let mut borrow = 0u16;
        for i in (0..32).rev() {
            let diff = (n[i] as u16)
                .wrapping_sub(s_bytes[i] as u16)
                .wrapping_sub(borrow);
            high_s[i] = diff as u8;
            borrow = if diff > 0xFF { 1 } else { 0 };
        }

        // Create mallated signature with high-S
        let mut mallated = compact.to_vec();
        mallated[32..64].copy_from_slice(&high_s);
        // Flip recovery ID
        let mallated_rid = 1 - rid.to_i32();
        mallated.push(mallated_rid as u8);

        let mallated_hex = hex::encode(&mallated);
        let sigs = vec![mallated_hex];

        let result = verify_recoverable_signatures(&sigs, TEST_MESSAGE, Some(&pk), None);
        assert_eq!(result.unwrap_err(), ERR_SIGNATURE_NOT_LOW_S);
    }
}
