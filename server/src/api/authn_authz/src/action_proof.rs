//! Action Proof verification internals.
//!
//! This module contains the low-level signature verification logic for Action Proof.
//! For the high-level authorization API, see `authorization.rs`.

use std::str::FromStr;

use secp256k1::ecdsa::Signature;
use secp256k1::hashes::{sha256, Hash};
use secp256k1::{Message, PublicKey, Secp256k1};
use serde::Deserialize;
use tracing::{event, Level};

use action_proof::{build_payload, compute_token_binding, Action, BuildError, ContextBinding};
use errors::ApiError;

use crate::signers::ProofRequirement;

pub const ACTION_PROOF_HEADER: &str = "Action-Proof";

/// Standard ECDSA signature length: 32 bytes (r) + 32 bytes (s)
const SIGNATURE_LEN: usize = 64;

/// Result of Action Proof verification.
/// Contains signature status and the content hash for anti-replay.
pub(crate) struct ActionProofResult {
    pub hw_signed: bool,
    pub app_signed: bool,
    /// SHA-256 of the canonical payload, used as the anti-replay cache key.
    pub content_hash: [u8; 32],
}

const ERR_APP_SIGNATURE_REQUIRED: &str = "app signature required";
const ERR_HARDWARE_SIGNATURE_REQUIRED: &str = "hardware signature required";
const ERR_AT_LEAST_ONE_SIGNATURE_REQUIRED: &str = "at least one signature required";
const ERR_DUPLICATE_SIGNATURE: &str = "duplicate signature from same key";
const ERR_UNKNOWN_KEY_SIGNATURE: &str = "signature from unknown key";
const ERR_INVALID_SIGNATURE_LENGTH: &str = "signature must be 64 bytes";
const ERR_INVALID_NONCE: &str = "nonce must be a 2-character lowercase hex string (1 byte)";

#[derive(Debug, Clone, Deserialize)]
pub(crate) struct ActionProofHeader {
    pub version: u8,
    pub signatures: Vec<String>, // hex, 64 bytes each (r + s)
    pub nonce: String,           // required, 2-char lowercase hex (1 byte, "00"–"ff")
}

/// Validates that the resolved proof flags meet the requirement.
pub fn validate_proof_requirement(
    hw_signed: bool,
    app_signed: bool,
    requirement: ProofRequirement,
) -> Result<(), ApiError> {
    match requirement {
        ProofRequirement::BothFactors => {
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
        ProofRequirement::AnyFactor => {
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
        ProofRequirement::Conditional | ProofRequirement::JwtOnly => {}
    }
    Ok(())
}

/// Verifies an Action Proof and returns an `ActionProofResult`.
#[allow(clippy::too_many_arguments)]
pub(crate) fn verify_action_proof(
    version: u8,
    signatures: &[String],
    nonce: &str,
    hw_pubkey: Option<&str>,
    app_pubkey: Option<&str>,
    jwt: &str,
    action: Action,
    value: Option<&str>,
    extra_bindings: &[(String, String)],
) -> Result<ActionProofResult, ApiError> {
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

    // Validate nonce: must be exactly 2 lowercase hex chars (1 byte)
    if !is_valid_nonce(nonce) {
        event!(Level::WARN, "action-proof: invalid nonce: {:?}", nonce);
        return Err(ApiError::GenericBadRequest(ERR_INVALID_NONCE.to_string()));
    }

    let token_binding = compute_token_binding(jwt);

    let mut bindings: Vec<(&str, &str)> = extra_bindings
        .iter()
        .map(|(k, v)| (k.as_str(), v.as_str()))
        .collect();
    bindings.push((ContextBinding::Nonce.key(), nonce));
    bindings.push((ContextBinding::TokenBinding.key(), &token_binding));
    bindings.sort_by_key(|b| b.0);

    let canonical = build_payload(action, value, &bindings).map_err(|e| match e {
        BuildError::InvalidValue(err) => {
            event!(
                Level::WARN,
                "action-proof: invalid value in payload: {:?}",
                err
            );
            ApiError::GenericBadRequest("invalid value".to_string())
        }
        BuildError::InvalidBindingValue(_) => {
            event!(
                Level::WARN,
                "action-proof: binding value contains reserved character"
            );
            ApiError::GenericBadRequest("invalid binding".to_string())
        }
        BuildError::InvalidBindingKey(_) => {
            event!(
                Level::WARN,
                "action-proof: binding key contains reserved character"
            );
            ApiError::GenericBadRequest("invalid binding".to_string())
        }
        BuildError::EmptyBindingKey => {
            event!(Level::WARN, "action-proof: empty binding key");
            ApiError::GenericBadRequest("invalid binding".to_string())
        }
        BuildError::DuplicateBindingKey(_) => {
            event!(Level::WARN, "action-proof: duplicate binding key");
            ApiError::GenericBadRequest("invalid binding".to_string())
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

    // Content hash: SHA-256 of the canonical payload.
    // Used as anti-replay cache key. Keying on content hash rather than
    // signature eliminates signature malleability concerns.
    let content_hash = sha256::Hash::hash(&canonical).to_byte_array();

    let (hw_signed, app_signed) = verify_signatures(signatures, &canonical, hw_pubkey, app_pubkey)
        .map_err(|msg| {
            event!(
                Level::WARN,
                "action-proof: signature verification failed: {}",
                msg
            );
            ApiError::GenericForbidden(msg.to_string())
        })?;

    Ok(ActionProofResult {
        hw_signed,
        app_signed,
        content_hash,
    })
}

/// Validates that a nonce is exactly 2 lowercase hex characters (1 byte, "00"–"ff").
fn is_valid_nonce(nonce: &str) -> bool {
    nonce.len() == 2
        && nonce
            .bytes()
            .all(|b| b.is_ascii_hexdigit() && !b.is_ascii_uppercase())
}

/// Verifies standard ECDSA signatures against registered public keys.
fn verify_signatures(
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

    if hw_pk.is_some() && hw_pk == app_pk {
        crate::metrics::ACTION_PROOF_EQUAL_REGISTERED_FACTORS.add(1, &[]);
    }

    let mut hw_signed = false;
    let mut app_signed = false;

    for sig_hex in sigs {
        let sig_bytes = hex::decode(sig_hex).map_err(|_| "invalid signature hex")?;

        if sig_bytes.len() != SIGNATURE_LEN {
            return Err(ERR_INVALID_SIGNATURE_LENGTH);
        }

        let sig = Signature::from_compact(&sig_bytes).map_err(|_| "invalid signature format")?;

        // Try to verify against each registered key
        let is_hw = hw_pk
            .as_ref()
            .is_some_and(|pk| secp.verify_ecdsa(&message, &sig, pk).is_ok());
        let is_app = app_pk
            .as_ref()
            .is_some_and(|pk| secp.verify_ecdsa(&message, &sig, pk).is_ok());

        if !is_hw && !is_app {
            event!(
                Level::WARN,
                "action-proof: signature does not match any registered key"
            );
            return Err(ERR_UNKNOWN_KEY_SIGNATURE);
        }

        if is_hw && is_app {
            crate::metrics::ACTION_PROOF_SIGNATURE_MATCHES_BOTH_FACTORS.add(1, &[]);
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
    use action_proof::Action;
    use secp256k1::SecretKey;

    const TEST_MESSAGE: &[u8] = b"test message";

    /// Sign with standard ECDSA, returning 64-byte hex.
    fn sign(secp: &Secp256k1<secp256k1::All>, sk: &SecretKey) -> String {
        let digest = sha256::Hash::hash(TEST_MESSAGE);
        let msg = Message::from_digest(digest.to_byte_array());
        let sig = secp.sign_ecdsa(&msg, sk);
        hex::encode(sig.serialize_compact())
    }

    /// Helper: call verify_action_proof with a given nonce and extra bindings.
    /// Uses a dummy signature—binding validation fires before sig verification.
    fn verify_with_bindings(
        nonce: &str,
        extra_bindings: &[(String, String)],
    ) -> Result<(bool, bool), ApiError> {
        verify_action_proof(
            action_proof::CANONICAL_VERSION,
            &["aa".repeat(65)], // dummy sig, wrong length but doesn't matter
            nonce,
            None,
            None,
            "fake.jwt.token",
            Action::AddRecoveryContact,
            Some("Alice"),
            extra_bindings,
        )
        .map(|r| (r.hw_signed, r.app_signed))
    }

    // -- InvalidBindingValue: nonce with reserved characters ----------------

    #[test]
    fn nonce_with_comma_returns_bad_request() {
        let err = verify_with_bindings("bad,nonce", &[]).unwrap_err();
        assert!(
            matches!(err, ApiError::GenericBadRequest(_)),
            "expected 400 Bad Request, got: {err:?}"
        );
    }

    #[test]
    fn nonce_with_equals_returns_bad_request() {
        let err = verify_with_bindings("bad=nonce", &[]).unwrap_err();
        assert!(
            matches!(err, ApiError::GenericBadRequest(_)),
            "expected 400 Bad Request, got: {err:?}"
        );
    }

    #[test]
    fn nonce_with_unit_separator_returns_bad_request() {
        let err = verify_with_bindings("bad\x1Fnonce", &[]).unwrap_err();
        assert!(
            matches!(err, ApiError::GenericBadRequest(_)),
            "expected 400 Bad Request, got: {err:?}"
        );
    }

    // -- DuplicateBindingKey: extra_bindings duplicates nonce key -----------

    #[test]
    fn duplicate_binding_key_returns_bad_request() {
        // "n" is the nonce binding key; supplying it via extra_bindings too
        // creates a duplicate after sort.
        let extra = vec![("n".to_string(), "collision".to_string())];
        let err = verify_with_bindings("00", &extra).unwrap_err();
        assert!(
            matches!(err, ApiError::GenericBadRequest(_)),
            "expected 400 Bad Request, got: {err:?}"
        );
    }

    // -- InvalidBindingKey: extra_bindings key with reserved character ------

    #[test]
    fn invalid_binding_key_returns_bad_request() {
        let extra = vec![("bad=key".to_string(), "value".to_string())];
        let err = verify_with_bindings("00", &extra).unwrap_err();
        assert!(
            matches!(err, ApiError::GenericBadRequest(_)),
            "expected 400 Bad Request, got: {err:?}"
        );
    }

    // -- EmptyBindingKey: extra_bindings with empty key --------------------

    #[test]
    fn empty_binding_key_returns_bad_request() {
        let extra = vec![("".to_string(), "value".to_string())];
        let err = verify_with_bindings("00", &extra).unwrap_err();
        assert!(
            matches!(err, ApiError::GenericBadRequest(_)),
            "expected 400 Bad Request, got: {err:?}"
        );
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
                verify_signatures(&sigs, TEST_MESSAGE, *hw, *app).unwrap();
            assert_eq!((hw_signed, app_signed), (*exp_hw, *exp_app));
        }
    }

    #[test]
    fn nonce_validation() {
        // Valid nonces
        assert!(is_valid_nonce("00"));
        assert!(is_valid_nonce("ff"));
        assert!(is_valid_nonce("ab"));
        assert!(is_valid_nonce("7f"));

        // Invalid: uppercase
        assert!(!is_valid_nonce("FF"));
        assert!(!is_valid_nonce("Ab"));
        assert!(!is_valid_nonce("0F"));

        // Invalid: wrong length
        assert!(!is_valid_nonce(""));
        assert!(!is_valid_nonce("0"));
        assert!(!is_valid_nonce("000"));
        assert!(!is_valid_nonce("abcd"));

        // Invalid: non-hex
        assert!(!is_valid_nonce("gg"));
        assert!(!is_valid_nonce("zz"));
        assert!(!is_valid_nonce("0x"));
    }

    #[test]
    fn action_proof_header_rejects_missing_or_null_nonce() {
        // Missing nonce field entirely
        let missing = r#"{"version":1,"signatures":[]}"#;
        assert!(
            serde_json::from_str::<ActionProofHeader>(missing).is_err(),
            "missing nonce should fail deserialization"
        );

        // Explicit null nonce
        let null_nonce = r#"{"version":1,"signatures":[],"nonce":null}"#;
        assert!(
            serde_json::from_str::<ActionProofHeader>(null_nonce).is_err(),
            "null nonce should fail deserialization"
        );

        // Valid nonce deserializes successfully
        let valid = r#"{"version":1,"signatures":[],"nonce":"0a"}"#;
        let header = serde_json::from_str::<ActionProofHeader>(valid)
            .expect("valid nonce should deserialize");
        assert_eq!(header.nonce, "0a");
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
                verify_signatures(&sigs, TEST_MESSAGE, *hw, *app).unwrap_err(),
                *expected
            );
        }
    }

    #[test]
    fn equal_registered_keys_preserve_legacy_proof_flags() {
        let secp = Secp256k1::new();
        let app_sk = SecretKey::from_slice(&[2u8; 32]).unwrap();
        let app_pk = app_sk.public_key(&secp).to_string();
        let app_sig = sign(&secp, &app_sk);

        let result = verify_signatures(&[app_sig], TEST_MESSAGE, Some(&app_pk), Some(&app_pk));

        assert_eq!(result, Ok((true, true)));
    }
}
