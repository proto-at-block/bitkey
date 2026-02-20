use std::str::FromStr;

use thiserror::Error;
use wsm_common::bitcoin::secp256k1::ecdsa::Signature as WsmSignature;
use wsm_common::bitcoin::secp256k1::PublicKey as WsmPublicKey;

#[derive(Debug, Error)]
pub enum CompatError {
    #[error("Invalid WSM public key bytes")]
    InvalidWsmPublicKey,
    #[error("Invalid WSM signature string")]
    InvalidWsmSignature,
    #[error("Invalid BDK public key bytes")]
    InvalidBdkPublicKey,
    #[error("Invalid BDK signature string")]
    InvalidBdkSignature,
    #[error("Invalid PSBT encoding")]
    InvalidPsbt,
    #[error("Invalid extended public key encoding")]
    InvalidXpub,
    #[error("Invalid fingerprint encoding")]
    InvalidFingerprint,
}

pub fn wsm_pubkey_from_bytes(bytes: &[u8]) -> Result<WsmPublicKey, CompatError> {
    WsmPublicKey::from_slice(bytes).map_err(|_| CompatError::InvalidWsmPublicKey)
}

pub fn wsm_signature_from_str(signature: &str) -> Result<WsmSignature, CompatError> {
    WsmSignature::from_str(signature).map_err(|_| CompatError::InvalidWsmSignature)
}

pub fn bdk_pubkey_from_wsm(
    pubkey: &WsmPublicKey,
) -> Result<bdk_utils::bdk::bitcoin::secp256k1::PublicKey, CompatError> {
    bdk_utils::bdk::bitcoin::secp256k1::PublicKey::from_slice(&pubkey.serialize())
        .map_err(|_| CompatError::InvalidBdkPublicKey)
}

pub fn bdk_signature_from_wsm(
    signature: &WsmSignature,
) -> Result<bdk_utils::bdk::bitcoin::secp256k1::ecdsa::Signature, CompatError> {
    bdk_utils::bdk::bitcoin::secp256k1::ecdsa::Signature::from_str(&signature.to_string())
        .map_err(|_| CompatError::InvalidBdkSignature)
}

pub fn wsm_pubkey_from_bdk(
    pubkey: &bdk_utils::bdk::bitcoin::secp256k1::PublicKey,
) -> Result<WsmPublicKey, CompatError> {
    wsm_pubkey_from_bytes(&pubkey.serialize())
}

pub fn wsm_signature_from_bdk(
    signature: &bdk_utils::bdk::bitcoin::secp256k1::ecdsa::Signature,
) -> Result<WsmSignature, CompatError> {
    wsm_signature_from_str(&signature.to_string())
}

pub fn xpub_0_32_to_0_30(
    xpub: bdk_utils::bdk::bitcoin::bip32::Xpub,
) -> Result<wsm_common::bitcoin::bip32::ExtendedPubKey, CompatError> {
    wsm_common::bitcoin::bip32::ExtendedPubKey::from_str(&xpub.to_string())
        .map_err(|_| CompatError::InvalidXpub)
}

pub fn fingerprint_0_32_to_0_30(
    fingerprint: bdk_utils::bdk::bitcoin::bip32::Fingerprint,
) -> Result<wsm_common::bitcoin::bip32::Fingerprint, CompatError> {
    wsm_common::bitcoin::bip32::Fingerprint::from_str(&fingerprint.to_string())
        .map_err(|_| CompatError::InvalidFingerprint)
}

pub fn psbt_0_32_to_0_30(
    psbt: &bdk_utils::bdk::bitcoin::psbt::Psbt,
) -> Result<wsm_common::bitcoin::psbt::Psbt, CompatError> {
    wsm_common::bitcoin::psbt::Psbt::deserialize(&psbt.serialize())
        .map_err(|_| CompatError::InvalidPsbt)
}

pub fn psbt_0_30_to_0_32(
    psbt: &wsm_common::bitcoin::psbt::Psbt,
) -> Result<bdk_utils::bdk::bitcoin::psbt::Psbt, CompatError> {
    bdk_utils::bdk::bitcoin::psbt::Psbt::deserialize(&psbt.serialize())
        .map_err(|_| CompatError::InvalidPsbt)
}

pub fn wsm_inputs_from_bdk_psbt(
    psbt: &bdk_utils::bdk::bitcoin::psbt::Psbt,
) -> Result<Vec<wsm_common::bitcoin::psbt::Input>, CompatError> {
    let wsm_psbt = psbt_0_32_to_0_30(psbt)?;
    Ok(wsm_psbt.inputs)
}

#[cfg(test)]
mod tests {
    use super::*;

    const PUBKEY_BYTES: [u8; 33] = [
        0x02, 0x79, 0xBE, 0x66, 0x7E, 0xF9, 0xDC, 0xBB, 0xAC, 0x55, 0xA0, 0x62, 0x95, 0xCE, 0x87,
        0x0B, 0x07, 0x02, 0x9B, 0xFC, 0xDB, 0x2D, 0xCE, 0x28, 0xD9, 0x59, 0xF2, 0x81, 0x5B, 0x16,
        0xF8, 0x17, 0x98,
    ];

    use std::str::FromStr;

    const PSBT_BASE64: &str = "cHNidP8BAHUCAAAAASaBcTce3/KF6Tet7qSze3gADAVmy7OtZGQXE8pCFxv2AAAAAAD+////AtPf9QUAAAAAGXapFNDFmQPFusKGh2DpD9UhpGZap2UgiKwA4fUFAAAAABepFDVF5uM7gyxHBQ8k0+65PJwDlIvHh7MuEwAAAQD9pQEBAAAAAAECiaPHHqtNIOA3G7ukzGmPopXJRjr6Ljl/hTPMti+VZ+UBAAAAFxYAFL4Y0VKpsBIDna89p95PUzSe7LmF/////4b4qkOnHf8USIk6UwpyN+9rRgi7st0tAXHmOuxqSJC0AQAAABcWABT+Pp7xp0XpdNkCxDVZQ6vLNL1TU/////8CAMLrCwAAAAAZdqkUhc/xCX/Z4Ai7NK9wnGIZeziXikiIrHL++E4sAAAAF6kUM5cluiHv1irHU6m80GfWx6ajnQWHAkcwRAIgJxK+IuAnDzlPVoMR3HyppolwuAJf3TskAinwf4pfOiQCIAGLONfc0xTnNMkna9b7QPZzMlvEuqFEyADS8vAtsnZcASED0uFWdJQbrUqZY3LLh+GFbTZSYG2YVi/jnF6efkE/IQUCSDBFAiEA0SuFLYXc2WHS9fSrZgZU327tzHlMDDPOXMMJ/7X85Y0CIGczio4OFyXBl/saiK9Z9R5E5CVbIBZ8hoQDHAXR8lkqASECI7cr7vCWXRC+B3jv7NYfysb3mk6haTkzgHNEZPhPKrMAAAAAAAAA";

    #[test]
    fn wsm_pubkey_round_trip() {
        let pubkey = wsm_pubkey_from_bytes(&PUBKEY_BYTES).expect("pubkey should parse");
        assert_eq!(pubkey.serialize(), PUBKEY_BYTES);
    }

    #[test]
    fn wsm_signature_round_trip() {
        use wsm_common::bitcoin::secp256k1::{Message, Secp256k1, SecretKey};

        let secp = Secp256k1::new();
        let secret_key = SecretKey::from_slice(&[1u8; 32]).expect("valid secret key");
        let message = Message::from_slice(&[2u8; 32]).expect("valid message");
        let signature = secp.sign_ecdsa(&message, &secret_key);

        let parsed =
            wsm_signature_from_str(&signature.to_string()).expect("signature should parse");
        assert_eq!(signature, parsed);
    }

    #[test]
    fn bdk_pubkey_round_trip() {
        let wsm_pubkey = wsm_pubkey_from_bytes(&PUBKEY_BYTES).expect("pubkey should parse");
        let bdk_pubkey = bdk_pubkey_from_wsm(&wsm_pubkey).expect("bdk pubkey should parse");
        let wsm_roundtrip = wsm_pubkey_from_bdk(&bdk_pubkey).expect("wsm pubkey should parse");
        assert_eq!(wsm_pubkey.serialize(), wsm_roundtrip.serialize());
    }

    #[test]
    fn bdk_signature_round_trip() {
        use wsm_common::bitcoin::secp256k1::{Message, Secp256k1, SecretKey};

        let secp = Secp256k1::new();
        let secret_key = SecretKey::from_slice(&[3u8; 32]).expect("valid secret key");
        let message = Message::from_slice(&[4u8; 32]).expect("valid message");
        let signature = secp.sign_ecdsa(&message, &secret_key);

        let bdk_signature = bdk_signature_from_wsm(&signature).expect("bdk signature should parse");
        let wsm_roundtrip =
            wsm_signature_from_bdk(&bdk_signature).expect("wsm signature should parse");
        assert_eq!(signature, wsm_roundtrip);
    }

    #[test]
    fn psbt_round_trip_between_versions() {
        let psbt = bdk_utils::bdk::bitcoin::psbt::Psbt::from_str(PSBT_BASE64).expect("valid psbt");
        let wsm_psbt = psbt_0_32_to_0_30(&psbt).expect("convert to 0.30");
        let roundtrip = psbt_0_30_to_0_32(&wsm_psbt).expect("convert back to 0.32");
        assert_eq!(psbt.serialize(), roundtrip.serialize());
    }

    #[test]
    fn psbt_inputs_match_legacy_conversion() {
        let psbt = bdk_utils::bdk::bitcoin::psbt::Psbt::from_str(PSBT_BASE64).expect("valid psbt");
        let wsm_psbt = psbt_0_32_to_0_30(&psbt).expect("convert to 0.30");
        let wsm_inputs = wsm_inputs_from_bdk_psbt(&psbt).expect("convert inputs");
        assert_eq!(wsm_psbt.inputs, wsm_inputs);
    }

    #[test]
    fn xpub_and_fingerprint_round_trip() {
        let xpub_str = "tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE";
        let xpub = bdk_utils::bdk::bitcoin::bip32::Xpub::from_str(xpub_str).expect("valid xpub");
        let wsm_xpub = xpub_0_32_to_0_30(xpub).expect("convert xpub");
        assert_eq!(wsm_xpub.to_string(), xpub_str);

        let fingerprint_str = "d90c6a4f";
        let fingerprint = bdk_utils::bdk::bitcoin::bip32::Fingerprint::from_str(fingerprint_str)
            .expect("valid fingerprint");
        let wsm_fingerprint = fingerprint_0_32_to_0_30(fingerprint).expect("convert fingerprint");
        assert_eq!(wsm_fingerprint.to_string(), fingerprint_str);
    }
}
