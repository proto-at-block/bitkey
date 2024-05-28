use rand_core::{OsRng, RngCore};
use ring::signature::{UnparsedPublicKey, ECDSA_P256_SHA256_FIXED};
use thiserror::Error;
use x509_parser::certificate::X509Certificate;
use x509_parser::prelude::FromDer;
use x509_parser::public_key::PublicKey;

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

const SILABS_FACTORY_INTERMEDIATE: &[u8] =
    include_bytes!("../../../../firmware/config/keys/silabs-certs/factory-prod.der");
const SILABS_DEVICE_ROOT: &[u8] =
    include_bytes!("../../../../firmware/config/keys/silabs-certs/device-root-prod.der");

fn extract_serial_from_cn(cn: &str) -> Result<String, AttestationError> {
    let start_substring = "EUI:";
    match cn.find(start_substring) {
        Some(start) => {
            let serial_begin = start + start_substring.len();
            let serial_length = 16;
            let value = &cn[serial_begin..serial_begin + serial_length];
            Ok(value.to_string())
        }
        None => Err(AttestationError::ParseFailure),
    }
}

/// Check that the device certificate is for Block, and return the cert's serial number.
fn check_device_cert_is_for_block(cert: &X509Certificate) -> Result<String, AttestationError> {
    let block = "Block Inc";

    let subject = cert.subject();

    // Check that the organization name is "Block Inc"
    if let Some(organization) = subject.iter_organization().next() {
        match organization.as_str() {
            Ok(o) => {
                if o != block {
                    return Err(AttestationError::NotForBlock);
                }
            }
            Err(_) => {
                return Err(AttestationError::NotForBlock);
            }
        }
    }

    // Check that the common name contains "Block Inc" and "ID:MCU"
    if let Some(common_name) = subject.iter_common_name().next() {
        match common_name.as_str() {
            Ok(cn) => {
                if cn.contains(block) && cn.contains("ID:MCU") {
                    return extract_serial_from_cn(cn);
                } else {
                    return Err(AttestationError::NotForBlock);
                }
            }
            Err(_) => {
                return Err(AttestationError::NotForBlock);
            }
        }
    }

    Err(AttestationError::NotForBlock)
}

fn verify_directly_issued_by(cert: &X509Certificate, issuer: &X509Certificate) -> bool {
    if cert.issuer() != issuer.subject() {
        return false;
    }

    if cert.verify_signature(Some(issuer.public_key())).is_err() {
        return false;
    }

    true
}

fn verify_cert_chain(chain: Vec<&X509Certificate>) -> bool {
    if chain.is_empty() {
        return false;
    }

    for i in 0..chain.len() - 1 {
        // The issuer is the next cert in the chain.
        if !verify_directly_issued_by(chain[i], chain[i + 1]) {
            return false;
        }
    }

    // The root is self-signed.
    verify_directly_issued_by(chain[chain.len() - 1], chain[chain.len() - 1])
}

pub struct Attestation {}

impl Default for Attestation {
    fn default() -> Self {
        Self::new()
    }
}

impl Attestation {
    pub fn new() -> Attestation {
        Self {}
    }

    /// Verify a certificate chain for a Bitkey. Return the unique serial if the chain is okay.
    pub fn verify_device_identity_cert_chain(
        &self,
        identity_cert_der: Vec<u8>,
        batch_cert_der: Vec<u8>,
    ) -> Result<String, AttestationError> {
        let Ok((_, identity_cert)) = X509Certificate::from_der(&identity_cert_der) else {
            return Err(AttestationError::ParseFailure);
        };

        let Ok((_, batch_cert)) = X509Certificate::from_der(&batch_cert_der) else {
            return Err(AttestationError::ParseFailure);
        };

        let Ok((_, silabs_factory_intermediate)) =
            X509Certificate::from_der(SILABS_FACTORY_INTERMEDIATE)
        else {
            return Err(AttestationError::ParseFailure);
        };

        let Ok((_, silabs_root)) = X509Certificate::from_der(SILABS_DEVICE_ROOT) else {
            return Err(AttestationError::ParseFailure);
        };

        let serial = check_device_cert_is_for_block(&identity_cert)?;

        if verify_cert_chain(vec![
            &identity_cert,
            &batch_cert,
            &silabs_factory_intermediate,
            &silabs_root,
        ]) {
            Ok(serial)
        } else {
            Err(AttestationError::InvalidChain)
        }
    }

    pub fn verify_challenge_response(
        &self,
        challenge: Vec<u8>,
        identity_cert_der: Vec<u8>,
        signature: Vec<u8>,
    ) -> Result<(), AttestationError> {
        let mut digest_input = vec![b'A', b'T', b'V', b'1'];
        digest_input.extend_from_slice(&challenge);

        let Ok((_, identity_cert)) = X509Certificate::from_der(&identity_cert_der) else {
            return Err(AttestationError::ParseFailure);
        };

        let Ok(pubkey) = identity_cert.public_key().parsed() else {
            return Err(AttestationError::ParseFailure);
        };

        match pubkey {
            PublicKey::EC(ec) => {
                let public_key = UnparsedPublicKey::new(&ECDSA_P256_SHA256_FIXED, ec.data());
                public_key
                    .verify(&digest_input, &signature)
                    .map_err(|_| AttestationError::VerificationFailure)
            }
            _ => Err(AttestationError::ParseFailure),
        }
    }

    pub fn generate_challenge(&self) -> Result<Vec<u8>, AttestationError> {
        let mut challenge = vec![0u8; 16];
        let rng = &mut OsRng;
        rng.fill_bytes(challenge.as_mut_slice());
        Ok(challenge)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    use std::collections::HashSet;
    use std::num::ParseIntError;

    fn decode_hex(s: &str) -> Result<Vec<u8>, ParseIntError> {
        (0..s.len())
            .step_by(2)
            .map(|i| u8::from_str_radix(&s[i..i + 2], 16))
            .collect()
    }

    #[test]
    fn test_check_device_cert_is_for_block() {
        let identity_cert = decode_hex("308201d43082017aa00302010202146f7a8b1e6158fe6360d76acb00ab9fe98316cc23300a06082a8648ce3d04030230413116301406035504030c0d42617463682031313936313436311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3233303631313134353332315a180f32313233303631313134353332315a3057310b300906035504061302555331123010060355040a0c09426c6f636b20496e633134303206035504030c2b426c6f636b20496e63204555493a3338333938464646464544303831423620533a5345302049443a4d43553059301306072a8648ce3d020106082a8648ce3d03010703420004067795ee79e9618fed1d4a7f9b2e82c42c75536041daed0cf67d1ca88f33f270a05ccb561ec03b0bd18ceb1b1b3293ac60baf28575bac7627997fb5f4efe9067a3383036300c0603551d130101ff04023000300e0603551d0f0101ff0404030206c030160603551d250101ff040c300a06082b06010505070302300a06082a8648ce3d0403020348003045022100939e1fafb54e7cad973f9b3928f559c42142a5efb9827c9e7dc313c7b209482702202af4eb7b96d1f96fe93fabdd92d1870a6cf2580d634c636d862217cfd7515d7b").unwrap();
        let identity_cert = X509Certificate::from_der(&identity_cert).unwrap();
        assert!(check_device_cert_is_for_block(&identity_cert.1).is_ok());

        let batch_cert = decode_hex("308201db30820180a00302010202083c64f949fb4eee55300a06082a8648ce3d040302303b3110300e06035504030c07466163746f7279311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3233303532333038313530345a180f32313138303931363137333230305a30413116301406035504030c0d42617463682031313936313436311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533059301306072a8648ce3d020106082a8648ce3d03010703420004842cde422f7621b14cf28d906892556378ab8ebd32128420a65c53ea6966e0244715beb6eef2aa12254a1b4071c2c84a093ff852dc2549fcb8899f444d17849ea366306430120603551d130101ff040830060101ff020100301f0603551d2304183016801443628449686f3a697c76d01fe51d2af9d773d116301d0603551d0e041604141c894a78cbe2367f50f19aad236597de1ac8a7ff300e0603551d0f0101ff040403020284300a06082a8648ce3d040302034900304602210092348ae2ce70338dfca2cf078ea73bd50a002b27dbcd65ae2d1ea07ac76dde4d022100d661a5166fd1cb55da9310866f8445e3d148384a60494384d82eb05e4da1c6f8").unwrap();
        let batch_cert = X509Certificate::from_der(&batch_cert).unwrap();

        let result = check_device_cert_is_for_block(&batch_cert.1);
        assert_eq!(result.unwrap_err(), AttestationError::NotForBlock);
    }

    #[test]
    fn test_verify_good_cert_chain() {
        let identity_cert_der = decode_hex("308201d43082017aa00302010202146f7a8b1e6158fe6360d76acb00ab9fe98316cc23300a06082a8648ce3d04030230413116301406035504030c0d42617463682031313936313436311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3233303631313134353332315a180f32313233303631313134353332315a3057310b300906035504061302555331123010060355040a0c09426c6f636b20496e633134303206035504030c2b426c6f636b20496e63204555493a3338333938464646464544303831423620533a5345302049443a4d43553059301306072a8648ce3d020106082a8648ce3d03010703420004067795ee79e9618fed1d4a7f9b2e82c42c75536041daed0cf67d1ca88f33f270a05ccb561ec03b0bd18ceb1b1b3293ac60baf28575bac7627997fb5f4efe9067a3383036300c0603551d130101ff04023000300e0603551d0f0101ff0404030206c030160603551d250101ff040c300a06082b06010505070302300a06082a8648ce3d0403020348003045022100939e1fafb54e7cad973f9b3928f559c42142a5efb9827c9e7dc313c7b209482702202af4eb7b96d1f96fe93fabdd92d1870a6cf2580d634c636d862217cfd7515d7b").unwrap();
        let batch_cert_der = decode_hex("308201db30820180a00302010202083c64f949fb4eee55300a06082a8648ce3d040302303b3110300e06035504030c07466163746f7279311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3233303532333038313530345a180f32313138303931363137333230305a30413116301406035504030c0d42617463682031313936313436311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533059301306072a8648ce3d020106082a8648ce3d03010703420004842cde422f7621b14cf28d906892556378ab8ebd32128420a65c53ea6966e0244715beb6eef2aa12254a1b4071c2c84a093ff852dc2549fcb8899f444d17849ea366306430120603551d130101ff040830060101ff020100301f0603551d2304183016801443628449686f3a697c76d01fe51d2af9d773d116301d0603551d0e041604141c894a78cbe2367f50f19aad236597de1ac8a7ff300e0603551d0f0101ff040403020284300a06082a8648ce3d040302034900304602210092348ae2ce70338dfca2cf078ea73bd50a002b27dbcd65ae2d1ea07ac76dde4d022100d661a5166fd1cb55da9310866f8445e3d148384a60494384d82eb05e4da1c6f8").unwrap();
        let result =
            Attestation {}.verify_device_identity_cert_chain(identity_cert_der, batch_cert_der);
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), "38398FFFFED081B6".to_string());
    }

    #[test]
    fn test_verify_cert_chain_not_for_block() {
        let identity_cert_der = decode_hex("308201d43082017aa00302010202146f7a8b1e6158fe6360d76acb00ab9fe98316cc23300a06082a8648ce3d04030230413116301406035504030c0d42617463682031313936313436311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3233303631313134353332315a180f32313233303631313134353332315a3057310b300906035504061302555331123010060355040a0c09426c6f636b20496e633134303206035504030c2b426c6f636b20496e63204555493a3338333938464646464544303831423620533a5345302049443a4d43553059301306072a8648ce3d020106082a8648ce3d03010703420004067795ee79e9618fed1d4a7f9b2e82c42c75536041daed0cf67d1ca88f33f270a05ccb561ec03b0bd18ceb1b1b3293ac60baf28575bac7627997fb5f4efe9067a3383036300c0603551d130101ff04023000300e0603551d0f0101ff0404030206c030160603551d250101ff040c300a06082b06010505070302300a06082a8648ce3d0403020348003045022100939e1fafb54e7cad973f9b3928f559c42142a5efb9827c9e7dc313c7b209482702202af4eb7b96d1f96fe93fabdd92d1870a6cf2580d634c636d862217cfd7515d7b").unwrap();
        let batch_cert_der = decode_hex("308201db30820180a00302010202083c64f949fb4eee55300a06082a8648ce3d040302303b3110300e06035504030c07466163746f7279311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3233303532333038313530345a180f32313138303931363137333230305a30413116301406035504030c0d42617463682031313936313436311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533059301306072a8648ce3d020106082a8648ce3d03010703420004842cde422f7621b14cf28d906892556378ab8ebd32128420a65c53ea6966e0244715beb6eef2aa12254a1b4071c2c84a093ff852dc2549fcb8899f444d17849ea366306430120603551d130101ff040830060101ff020100301f0603551d2304183016801443628449686f3a697c76d01fe51d2af9d773d116301d0603551d0e041604141c894a78cbe2367f50f19aad236597de1ac8a7ff300e0603551d0f0101ff040403020284300a06082a8648ce3d040302034900304602210092348ae2ce70338dfca2cf078ea73bd50a002b27dbcd65ae2d1ea07ac76dde4d022100d661a5166fd1cb55da9310866f8445e3d148384a60494384d82eb05e4da1c6f8").unwrap();

        // Swap the batch and identity certs
        let result =
            Attestation {}.verify_device_identity_cert_chain(batch_cert_der, identity_cert_der);
        assert_eq!(result.unwrap_err(), AttestationError::NotForBlock);
    }

    #[test]
    fn test_verify_bad_cert_chain() {
        let identity_cert_der = decode_hex("308201d43082017aa00302010202146f7a8b1e6158fe6360d76acb00ab9fe98316cc23300a06082a8648ce3d04030230413116301406035504030c0d42617463682031313936313436311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3233303631313134353332315a180f32313233303631313134353332315a3057310b300906035504061302555331123010060355040a0c09426c6f636b20496e633134303206035504030c2b426c6f636b20496e63204555493a3338333938464646464544303831423620533a5345302049443a4d43553059301306072a8648ce3d020106082a8648ce3d03010703420004067795ee79e9618fed1d4a7f9b2e82c42c75536041daed0cf67d1ca88f33f270a05ccb561ec03b0bd18ceb1b1b3293ac60baf28575bac7627997fb5f4efe9067a3383036300c0603551d130101ff04023000300e0603551d0f0101ff0404030206c030160603551d250101ff040c300a06082b06010505070302300a06082a8648ce3d0403020348003045022100939e1fafb54e7cad973f9b3928f559c42142a5efb9827c9e7dc313c7b209482702202af4eb7b96d1f96fe93fabdd92d1870a6cf2580d634c636d862217cfd7515d7b").unwrap();
        let bad_batch_cert_der = SILABS_DEVICE_ROOT;
        let result = Attestation {}
            .verify_device_identity_cert_chain(identity_cert_der, bad_batch_cert_der.to_vec());
        assert_eq!(result.unwrap_err(), AttestationError::InvalidChain);
    }

    #[test]
    fn test_verify_cert_invalid_chain_size_two() {
        let identity_cert_der = decode_hex("308201d43082017aa00302010202146f7a8b1e6158fe6360d76acb00ab9fe98316cc23300a06082a8648ce3d04030230413116301406035504030c0d42617463682031313936313436311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3233303631313134353332315a180f32313233303631313134353332315a3057310b300906035504061302555331123010060355040a0c09426c6f636b20496e633134303206035504030c2b426c6f636b20496e63204555493a3338333938464646464544303831423620533a5345302049443a4d43553059301306072a8648ce3d020106082a8648ce3d03010703420004067795ee79e9618fed1d4a7f9b2e82c42c75536041daed0cf67d1ca88f33f270a05ccb561ec03b0bd18ceb1b1b3293ac60baf28575bac7627997fb5f4efe9067a3383036300c0603551d130101ff04023000300e0603551d0f0101ff0404030206c030160603551d250101ff040c300a06082b06010505070302300a06082a8648ce3d0403020348003045022100939e1fafb54e7cad973f9b3928f559c42142a5efb9827c9e7dc313c7b209482702202af4eb7b96d1f96fe93fabdd92d1870a6cf2580d634c636d862217cfd7515d7b").unwrap();
        assert!(!verify_cert_chain(vec![
            &X509Certificate::from_der(&identity_cert_der).unwrap().1,
            &X509Certificate::from_der(SILABS_DEVICE_ROOT).unwrap().1,
        ]));
    }

    #[test]
    fn test_challenge_response_good() {
        let identity_cert_der = decode_hex("308201d43082017aa00302010202146f7a8b1e6158fe6360d76acb00ab9fe98316cc23300a06082a8648ce3d04030230413116301406035504030c0d42617463682031313936313436311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3233303631313134353332315a180f32313233303631313134353332315a3057310b300906035504061302555331123010060355040a0c09426c6f636b20496e633134303206035504030c2b426c6f636b20496e63204555493a3338333938464646464544303831423620533a5345302049443a4d43553059301306072a8648ce3d020106082a8648ce3d03010703420004067795ee79e9618fed1d4a7f9b2e82c42c75536041daed0cf67d1ca88f33f270a05ccb561ec03b0bd18ceb1b1b3293ac60baf28575bac7627997fb5f4efe9067a3383036300c0603551d130101ff04023000300e0603551d0f0101ff0404030206c030160603551d250101ff040c300a06082b06010505070302300a06082a8648ce3d0403020348003045022100939e1fafb54e7cad973f9b3928f559c42142a5efb9827c9e7dc313c7b209482702202af4eb7b96d1f96fe93fabdd92d1870a6cf2580d634c636d862217cfd7515d7b").unwrap();
        let challenge = decode_hex("0b05c5ef411f36354219036afa3e3a02").unwrap();
        let signature = decode_hex("ff3be827b5e8e0bd1b55d24c94783992ab05b7f5dcf110ad0eccb2054b83987e3910a9013c72fbbb6300e6f96e255b7e06c74483ccf5f27a1ffa338ea93a7a82").unwrap();
        assert_eq!(
            Attestation {}
                .verify_challenge_response(challenge, identity_cert_der, signature)
                .unwrap(),
            ()
        );
    }

    #[test]
    fn test_challenge_response_invalid_signature() {
        let identity_cert_der = decode_hex("308201d43082017aa00302010202146f7a8b1e6158fe6360d76acb00ab9fe98316cc23300a06082a8648ce3d04030230413116301406035504030c0d42617463682031313936313436311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3233303631313134353332315a180f32313233303631313134353332315a3057310b300906035504061302555331123010060355040a0c09426c6f636b20496e633134303206035504030c2b426c6f636b20496e63204555493a3338333938464646464544303831423620533a5345302049443a4d43553059301306072a8648ce3d020106082a8648ce3d03010703420004067795ee79e9618fed1d4a7f9b2e82c42c75536041daed0cf67d1ca88f33f270a05ccb561ec03b0bd18ceb1b1b3293ac60baf28575bac7627997fb5f4efe9067a3383036300c0603551d130101ff04023000300e0603551d0f0101ff0404030206c030160603551d250101ff040c300a06082b06010505070302300a06082a8648ce3d0403020348003045022100939e1fafb54e7cad973f9b3928f559c42142a5efb9827c9e7dc313c7b209482702202af4eb7b96d1f96fe93fabdd92d1870a6cf2580d634c636d862217cfd7515d7b").unwrap();
        let challenge = decode_hex("0b05c5ef411f36354219036afa3e3a02").unwrap();
        let signature = decode_hex("0f3be827b5e8e0bd1b55d24c94783992ab05b7f5dcf110ad0eccb2054b83987e3910a9013c72fbbb6300e6f96e255b7e06c74483ccf5f27a1ffa338ea93a7a82").unwrap();
        assert_eq!(
            Attestation {}
                .verify_challenge_response(challenge, identity_cert_der, signature)
                .unwrap_err(),
            AttestationError::VerificationFailure
        );
    }

    #[test]
    fn generate_challenge() {
        let mut set = HashSet::new();
        // lightweight check: ensure each result is different
        for _ in 0..100 {
            let challenge = Attestation {}.generate_challenge().unwrap();
            assert!(set.insert(challenge));
        }
    }
}
