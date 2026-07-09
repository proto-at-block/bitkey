//! Shared primitives for verifying Bitkey hardware attestations: cert
//! chain walking to the Silicon Labs root, Block-device identity check,
//! and signature verification. Used by both `server` and `app` with
//! their own domain-separated payloads.

use ring::signature::{UnparsedPublicKey, ECDSA_P256_SHA256_FIXED};
use thiserror::Error;
use x509_parser::certificate::X509Certificate;
use x509_parser::prelude::FromDer;
use x509_parser::public_key::PublicKey as X509PublicKey;

const SILABS_FACTORY_INTERMEDIATE: &[u8] =
    include_bytes!("../../../firmware/config/keys/silabs-certs/factory-prod.der");
const SILABS_DEVICE_ROOT: &[u8] =
    include_bytes!("../../../firmware/config/keys/silabs-certs/device-root-prod.der");

pub const ORG_W1: &str = "Block Inc";
pub const ORG_W3: &str = "Bitkey W3, Block Inc";

#[derive(Error, Debug, PartialEq)]
pub enum AttestationError {
    #[error("certificate is not for Block")]
    NotForBlock,
    #[error("certificate chain is invalid")]
    InvalidChain,
    #[error("failed to parse certificate")]
    ParseFailure,
    #[error("failed to verify signature")]
    VerificationFailure,
}

/// A device identity cert that's been verified to chain to the Silicon
/// Labs root and is for a genuine Block-manufactured Bitkey. Owns its DER
/// bytes so callers don't track lifetimes.
#[derive(Debug, Clone)]
pub struct DeviceCert {
    identity_der: Vec<u8>,
    serial: String,
}

impl DeviceCert {
    pub fn serial(&self) -> &str {
        &self.serial
    }

    pub fn verify_signature(
        &self,
        payload: &[u8],
        signature: &[u8],
    ) -> Result<(), AttestationError> {
        verify_signature_with_identity_der(&self.identity_der, payload, signature)
    }
}

/// Verify a signature against the identity pubkey in `identity_cert_der`.
/// Does NOT walk the cert chain — the caller must have already validated
/// the cert via [`verify_device_identity_chain`].
pub fn verify_signature_with_identity_der(
    identity_cert_der: &[u8],
    payload: &[u8],
    signature: &[u8],
) -> Result<(), AttestationError> {
    let (_, cert) =
        X509Certificate::from_der(identity_cert_der).map_err(|_| AttestationError::ParseFailure)?;
    let parsed = cert
        .public_key()
        .parsed()
        .map_err(|_| AttestationError::ParseFailure)?;
    let X509PublicKey::EC(ec) = parsed else {
        return Err(AttestationError::ParseFailure);
    };
    UnparsedPublicKey::new(&ECDSA_P256_SHA256_FIXED, ec.data())
        .verify(payload, signature)
        .map_err(|_| AttestationError::VerificationFailure)
}

/// Walk the supplied identity + batch cert pair to the baked-in Silicon
/// Labs root and check that the identity is for a Block-manufactured
/// Bitkey (W1 or W3). On success returns a [`DeviceCert`] handle.
pub fn verify_device_identity_chain(
    identity_cert_der: &[u8],
    batch_cert_der: &[u8],
) -> Result<DeviceCert, AttestationError> {
    let (_, identity_cert) =
        X509Certificate::from_der(identity_cert_der).map_err(|_| AttestationError::ParseFailure)?;
    let (_, batch_cert) =
        X509Certificate::from_der(batch_cert_der).map_err(|_| AttestationError::ParseFailure)?;
    let (_, silabs_factory_intermediate) = X509Certificate::from_der(SILABS_FACTORY_INTERMEDIATE)
        .map_err(|_| AttestationError::ParseFailure)?;
    let (_, silabs_root) =
        X509Certificate::from_der(SILABS_DEVICE_ROOT).map_err(|_| AttestationError::ParseFailure)?;

    let serial = check_device_cert_is_for_block(&identity_cert)?;

    if !verify_cert_chain(&[
        &identity_cert,
        &batch_cert,
        &silabs_factory_intermediate,
        &silabs_root,
    ]) {
        return Err(AttestationError::InvalidChain);
    }

    Ok(DeviceCert {
        identity_der: identity_cert_der.to_vec(),
        serial,
    })
}

fn check_device_cert_is_for_block(cert: &X509Certificate) -> Result<String, AttestationError> {
    let subject = cert.subject();
    let organization = subject
        .iter_organization()
        .next()
        .and_then(|o| o.as_str().ok())
        .ok_or(AttestationError::NotForBlock)?;
    let common_name = subject
        .iter_common_name()
        .next()
        .and_then(|cn| cn.as_str().ok())
        .ok_or(AttestationError::NotForBlock)?;

    let is_block_w1 = organization == ORG_W1 && common_name.contains(ORG_W1);
    let is_block_w3 = organization == ORG_W3 && common_name.contains(ORG_W3);

    if (is_block_w1 || is_block_w3) && common_name.contains("ID:MCU") {
        extract_serial_from_cn(common_name)
    } else {
        Err(AttestationError::NotForBlock)
    }
}

fn extract_serial_from_cn(cn: &str) -> Result<String, AttestationError> {
    let start = cn.find("EUI:").ok_or(AttestationError::ParseFailure)?;
    let serial_begin = start + "EUI:".len();
    cn.get(serial_begin..serial_begin + 16)
        .map(|s| s.to_string())
        .ok_or(AttestationError::ParseFailure)
}

fn verify_directly_issued_by(cert: &X509Certificate, issuer: &X509Certificate) -> bool {
    cert.issuer() == issuer.subject()
        && cert.verify_signature(Some(issuer.public_key())).is_ok()
}

fn verify_cert_chain(chain: &[&X509Certificate]) -> bool {
    if chain.is_empty() {
        return false;
    }
    for i in 0..chain.len() - 1 {
        if !verify_directly_issued_by(chain[i], chain[i + 1]) {
            return false;
        }
    }
    // The root is self-signed.
    let root = chain[chain.len() - 1];
    verify_directly_issued_by(root, root)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::num::ParseIntError;

    // Real Silicon Labs–issued device certs (W1 + W3 prod).
    const W1_IDENTITY_CERT_HEX: &str = "308201d43082017aa00302010202146f7a8b1e6158fe6360d76acb00ab9fe98316cc23300a06082a8648ce3d04030230413116301406035504030c0d42617463682031313936313436311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3233303631313134353332315a180f32313233303631313134353332315a3057310b300906035504061302555331123010060355040a0c09426c6f636b20496e633134303206035504030c2b426c6f636b20496e63204555493a3338333938464646464544303831423620533a5345302049443a4d43553059301306072a8648ce3d020106082a8648ce3d03010703420004067795ee79e9618fed1d4a7f9b2e82c42c75536041daed0cf67d1ca88f33f270a05ccb561ec03b0bd18ceb1b1b3293ac60baf28575bac7627997fb5f4efe9067a3383036300c0603551d130101ff04023000300e0603551d0f0101ff0404030206c030160603551d250101ff040c300a06082b06010505070302300a06082a8648ce3d0403020348003045022100939e1fafb54e7cad973f9b3928f559c42142a5efb9827c9e7dc313c7b209482702202af4eb7b96d1f96fe93fabdd92d1870a6cf2580d634c636d862217cfd7515d7b";
    const W1_BATCH_CERT_HEX: &str = "308201db30820180a00302010202083c64f949fb4eee55300a06082a8648ce3d040302303b3110300e06035504030c07466163746f7279311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3233303532333038313530345a180f32313138303931363137333230305a30413116301406035504030c0d42617463682031313936313436311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533059301306072a8648ce3d020106082a8648ce3d03010703420004842cde422f7621b14cf28d906892556378ab8ebd32128420a65c53ea6966e0244715beb6eef2aa12254a1b4071c2c84a093ff852dc2549fcb8899f444d17849ea366306430120603551d130101ff040830060101ff020100301f0603551d2304183016801443628449686f3a697c76d01fe51d2af9d773d116301d0603551d0e041604141c894a78cbe2367f50f19aad236597de1ac8a7ff300e0603551d0f0101ff040403020284300a06082a8648ce3d040302034900304602210092348ae2ce70338dfca2cf078ea73bd50a002b27dbcd65ae2d1ea07ac76dde4d022100d661a5166fd1cb55da9310866f8445e3d148384a60494384d82eb05e4da1c6f8";
    const W3_PROD_MCU_CERT_HEX: &str = "308201e930820190a003020102021445d2c69f95bbdbfaee7f4e1d187d0e2edb2eaf78300a06082a8648ce3d04030230413116301406035504030c0d42617463682031323038343631311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3235313131373036333033325a180f32313235313032343036333033325a306d313f303d06035504030c364269746b65792057332c20426c6f636b20496e63204555493a3643413034324646464533433430393420533a5345302049443a4d4355310b3009060355040613025553311d301b060355040a0c144269746b65792057332c20426c6f636b20496e633059301306072a8648ce3d020106082a8648ce3d03010703420004589010179d049390cf6de57e1e32072be8a620dfb1435fdf7b3eaf110948400b28f98a6213b5d4e26eae9fb456087b62fc866ffe3e028aaafc14781036761c33a3383036300c0603551d130101ff04023000300e0603551d0f0101ff0404030206c030160603551d250101ff040c300a06082b06010505070302300a06082a8648ce3d0403020347003044022061850c2b4365c9beca2ae3632419930ab8e82ea317dc01f6903c7eb6d50e816102205d6bbff114a73131969184e86c0efd94174d3f1c079d26bbae592ee5c4acabfb";
    const W3_PROD_BATCH_CERT_HEX: &str = "308201d930820180a00302010202083670ba898792149a300a06082a8648ce3d040302303b3110300e06035504030c07466163746f7279311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3235313131343232313634365a180f32313138303931363137333230305a30413116301406035504030c0d42617463682031323038343631311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533059301306072a8648ce3d020106082a8648ce3d0301070342000491a1bb3681d00ececc226fc4a656023d67baa7ac65fc748c8388937e0b76b757fe6334cf30016fab6cdaedcabaa52931553326841b87855e47c91946dd5d2e44a366306430120603551d130101ff040830060101ff020100301f0603551d2304183016801443628449686f3a697c76d01fe51d2af9d773d116301d0603551d0e04160414fbe8a89ccd6a2675355201a48fe553b0522d3762300e0603551d0f0101ff040403020284300a06082a8648ce3d0403020347003044022042049e569d63790f20c01687090908d774745c82b4fb4d38c30c433b8d4907db022034681a1b9734b0199cc4575fa74c39078192dd3c8cbb31c35b8f419fd5903b00";

    fn decode_hex(s: &str) -> Result<Vec<u8>, ParseIntError> {
        (0..s.len())
            .step_by(2)
            .map(|i| u8::from_str_radix(&s[i..i + 2], 16))
            .collect()
    }

    #[test]
    fn verifies_real_w1_chain_and_extracts_serial() {
        let identity = decode_hex(W1_IDENTITY_CERT_HEX).unwrap();
        let batch = decode_hex(W1_BATCH_CERT_HEX).unwrap();
        let device = verify_device_identity_chain(&identity, &batch).unwrap();
        assert_eq!(device.serial(), "38398FFFFED081B6");
    }

    #[test]
    fn verifies_real_w3_chain_and_extracts_serial() {
        let identity = decode_hex(W3_PROD_MCU_CERT_HEX).unwrap();
        let batch = decode_hex(W3_PROD_BATCH_CERT_HEX).unwrap();
        let device = verify_device_identity_chain(&identity, &batch).unwrap();
        assert_eq!(device.serial(), "6CA042FFFE3C4094");
    }

    #[test]
    fn rejects_swapped_chain_order() {
        let identity = decode_hex(W1_IDENTITY_CERT_HEX).unwrap();
        let batch = decode_hex(W1_BATCH_CERT_HEX).unwrap();
        assert_eq!(
            verify_device_identity_chain(&batch, &identity).unwrap_err(),
            AttestationError::NotForBlock,
        );
    }

    #[test]
    fn rejects_garbage_cert_bytes() {
        let err = verify_device_identity_chain(&[0u8; 32], &[0u8; 32]).unwrap_err();
        assert_eq!(err, AttestationError::ParseFailure);
    }

    #[test]
    fn rejects_invalid_signature() {
        let identity = decode_hex(W1_IDENTITY_CERT_HEX).unwrap();
        let batch = decode_hex(W1_BATCH_CERT_HEX).unwrap();
        let device = verify_device_identity_chain(&identity, &batch).unwrap();
        let err = device.verify_signature(b"HWV1payload", &[0u8; 64]).unwrap_err();
        assert_eq!(err, AttestationError::VerificationFailure);
    }
}
