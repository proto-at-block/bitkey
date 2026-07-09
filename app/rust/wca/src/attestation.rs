//! App-side wrapper over [`device_attestation`]. Keeps the existing
//! UniFFI surface (`Attestation` interface, `AttestationError` enum)
//! intact while delegating the cert-chain and signature-verification
//! logic to the shared crate.

use rand_core::{OsRng, RngCore};

pub use device_attestation::AttestationError;

/// Domain-separation prefix for the app's hardware-challenge-response
/// payload. Device signs `ATV1_PREFIX || challenge`.
const ATV1_PREFIX: &[u8] = b"ATV1";

/// Domain-separation prefix for the spending-key attestation payload.
/// Wire format pinned by both firmware and verifier:
/// `b"HWV1" || compressed pubkey`. Firmware constructs the same bytes in
/// `populate_key_attestation()`; keep these in sync.
const SPENDING_KEY_ATTESTATION_CONTEXT: &[u8] = b"HWV1";

/// Build the spending-key attestation payload. Extracted so the byte
/// layout can be asserted in a unit test.
fn spending_key_attestation_payload(compressed_spending_pubkey: &[u8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(SPENDING_KEY_ATTESTATION_CONTEXT.len() + 33);
    out.extend_from_slice(SPENDING_KEY_ATTESTATION_CONTEXT);
    out.extend_from_slice(compressed_spending_pubkey);
    out
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

    /// Verify a certificate chain for a Bitkey. Returns the manufacturer
    /// serial if the chain is okay.
    pub fn verify_device_identity_cert_chain(
        &self,
        identity_cert_der: Vec<u8>,
        batch_cert_der: Vec<u8>,
    ) -> Result<String, AttestationError> {
        device_attestation::verify_device_identity_chain(&identity_cert_der, &batch_cert_der)
            .map(|cert| cert.serial().to_string())
    }

    /// Verify a hardware-attestation challenge response. The caller is
    /// expected to have already verified `identity_cert_der` via
    /// [`Attestation::verify_device_identity_cert_chain`].
    pub fn verify_challenge_response(
        &self,
        challenge: Vec<u8>,
        identity_cert_der: Vec<u8>,
        signature: Vec<u8>,
    ) -> Result<(), AttestationError> {
        let mut payload = Vec::with_capacity(ATV1_PREFIX.len() + challenge.len());
        payload.extend_from_slice(ATV1_PREFIX);
        payload.extend_from_slice(&challenge);
        device_attestation::verify_signature_with_identity_der(
            &identity_cert_der,
            &payload,
            &signature,
        )
    }

    pub fn verify_spending_key_attestation(
        &self,
        identity_cert_der: Vec<u8>,
        batch_cert_der: Vec<u8>,
        compressed_spending_pubkey: Vec<u8>,
        signature: Vec<u8>,
    ) -> Result<(), AttestationError> {
        if compressed_spending_pubkey.len() != 33 {
            return Err(AttestationError::ParseFailure);
        }

        let device =
            device_attestation::verify_device_identity_chain(&identity_cert_der, &batch_cert_der)?;

        let payload = spending_key_attestation_payload(&compressed_spending_pubkey);
        device.verify_signature(&payload, &signature)
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

    const W1_IDENTITY_CERT_HEX: &str = "308201d43082017aa00302010202146f7a8b1e6158fe6360d76acb00ab9fe98316cc23300a06082a8648ce3d04030230413116301406035504030c0d42617463682031313936313436311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3233303631313134353332315a180f32313233303631313134353332315a3057310b300906035504061302555331123010060355040a0c09426c6f636b20496e633134303206035504030c2b426c6f636b20496e63204555493a3338333938464646464544303831423620533a5345302049443a4d43553059301306072a8648ce3d020106082a8648ce3d03010703420004067795ee79e9618fed1d4a7f9b2e82c42c75536041daed0cf67d1ca88f33f270a05ccb561ec03b0bd18ceb1b1b3293ac60baf28575bac7627997fb5f4efe9067a3383036300c0603551d130101ff04023000300e0603551d0f0101ff0404030206c030160603551d250101ff040c300a06082b06010505070302300a06082a8648ce3d0403020348003045022100939e1fafb54e7cad973f9b3928f559c42142a5efb9827c9e7dc313c7b209482702202af4eb7b96d1f96fe93fabdd92d1870a6cf2580d634c636d862217cfd7515d7b";
    const W1_BATCH_CERT_HEX: &str = "308201db30820180a00302010202083c64f949fb4eee55300a06082a8648ce3d040302303b3110300e06035504030c07466163746f7279311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3233303532333038313530345a180f32313138303931363137333230305a30413116301406035504030c0d42617463682031313936313436311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533059301306072a8648ce3d020106082a8648ce3d03010703420004842cde422f7621b14cf28d906892556378ab8ebd32128420a65c53ea6966e0244715beb6eef2aa12254a1b4071c2c84a093ff852dc2549fcb8899f444d17849ea366306430120603551d130101ff040830060101ff020100301f0603551d2304183016801443628449686f3a697c76d01fe51d2af9d773d116301d0603551d0e041604141c894a78cbe2367f50f19aad236597de1ac8a7ff300e0603551d0f0101ff040403020284300a06082a8648ce3d040302034900304602210092348ae2ce70338dfca2cf078ea73bd50a002b27dbcd65ae2d1ea07ac76dde4d022100d661a5166fd1cb55da9310866f8445e3d148384a60494384d82eb05e4da1c6f8";
    const W3_DEV_BATCH_CERT_HEX: &str = "308201db30820180a00302010202081828eb444575d383300a06082a8648ce3d040302303b3110300e06035504030c07466163746f7279311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3235313131343232313634395a180f32313138303931363137333230305a30413116301406035504030c0d42617463682031323038343631311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533059301306072a8648ce3d020106082a8648ce3d03010703420004315245b3ed9169526d1613abd17f0b9b5dc4338e0c50e9b2e12807a025b74753cd5cb47b864159eda81978aec4e2832bf17d56a1d43f5773b5ffde16500a3e99a366306430120603551d130101ff040830060101ff020100301f0603551d2304183016801443628449686f3a697c76d01fe51d2af9d773d116301d0603551d0e0416041491fac4bfe157ae358753575c247982dab9bcb762300e0603551d0f0101ff040403020284300a06082a8648ce3d0403020349003046022100aeea56146d9277ca2b752320bdaa337620c342989ee3be5ef0e5a0a94a6287ca022100fc37bdb33aa02be0bfe3a4d29eee482574987197cb6bd7c876e7eb812768836f";
    const W3_DEV_MCU_CERT_HEX: &str = "308201eb30820190a0030201020214039ce5ce4f6f50cf31a2aa64f748a78d18c2a62a300a06082a8648ce3d04030230413116301406035504030c0d42617463682031323038343631311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3235313131373037313035315a180f32313235313032343037313035315a306d313f303d06035504030c364269746b65792057332c20426c6f636b20496e63204555493a3643413034324646464533433635454620533a5345302049443a4d4355310b3009060355040613025553311d301b060355040a0c144269746b65792057332c20426c6f636b20496e633059301306072a8648ce3d020106082a8648ce3d030107034200045aa92cea3af1f0d18db28bbebe11c36741952ff1eb1b5c93acb51fe48441816ab1ca30f5c87999d3206e309e44edd5b5e5385e2944250414ec859b8b82cde34fa3383036300c0603551d130101ff04023000300e0603551d0f0101ff0404030206c030160603551d250101ff040c300a06082b06010505070302300a06082a8648ce3d0403020349003046022100a634f35ed9db95a273c3c54a26abe8c93a240db4cf4d8c6194fa0abe9d2ac9840221009da959088981a9916c97bba964bf0258b476f7ea986458f96c1b1ae82ebaa7a8";
    const W3_PROD_BATCH_CERT_HEX: &str = "308201d930820180a00302010202083670ba898792149a300a06082a8648ce3d040302303b3110300e06035504030c07466163746f7279311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3235313131343232313634365a180f32313138303931363137333230305a30413116301406035504030c0d42617463682031323038343631311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533059301306072a8648ce3d020106082a8648ce3d0301070342000491a1bb3681d00ececc226fc4a656023d67baa7ac65fc748c8388937e0b76b757fe6334cf30016fab6cdaedcabaa52931553326841b87855e47c91946dd5d2e44a366306430120603551d130101ff040830060101ff020100301f0603551d2304183016801443628449686f3a697c76d01fe51d2af9d773d116301d0603551d0e04160414fbe8a89ccd6a2675355201a48fe553b0522d3762300e0603551d0f0101ff040403020284300a06082a8648ce3d0403020347003044022042049e569d63790f20c01687090908d774745c82b4fb4d38c30c433b8d4907db022034681a1b9734b0199cc4575fa74c39078192dd3c8cbb31c35b8f419fd5903b00";
    const W3_PROD_MCU_CERT_HEX: &str = "308201e930820190a003020102021445d2c69f95bbdbfaee7f4e1d187d0e2edb2eaf78300a06082a8648ce3d04030230413116301406035504030c0d42617463682031323038343631311a3018060355040a0c1153696c69636f6e204c61627320496e632e310b30090603550406130255533020170d3235313131373036333033325a180f32313235313032343036333033325a306d313f303d06035504030c364269746b65792057332c20426c6f636b20496e63204555493a3643413034324646464533433430393420533a5345302049443a4d4355310b3009060355040613025553311d301b060355040a0c144269746b65792057332c20426c6f636b20496e633059301306072a8648ce3d020106082a8648ce3d03010703420004589010179d049390cf6de57e1e32072be8a620dfb1435fdf7b3eaf110948400b28f98a6213b5d4e26eae9fb456087b62fc866ffe3e028aaafc14781036761c33a3383036300c0603551d130101ff04023000300e0603551d0f0101ff0404030206c030160603551d250101ff040c300a06082b06010505070302300a06082a8648ce3d0403020347003044022061850c2b4365c9beca2ae3632419930ab8e82ea317dc01f6903c7eb6d50e816102205d6bbff114a73131969184e86c0efd94174d3f1c079d26bbae592ee5c4acabfb";

    fn decode_hex(s: &str) -> Result<Vec<u8>, ParseIntError> {
        (0..s.len())
            .step_by(2)
            .map(|i| u8::from_str_radix(&s[i..i + 2], 16))
            .collect()
    }

    #[test]
    fn test_verify_valid_cert_chain_w3() {
        let dev_result = Attestation {}.verify_device_identity_cert_chain(
            decode_hex(W3_DEV_MCU_CERT_HEX).unwrap(),
            decode_hex(W3_DEV_BATCH_CERT_HEX).unwrap(),
        );
        assert_eq!(dev_result.unwrap(), "6CA042FFFE3C65EF".to_string());

        let prod_result = Attestation {}.verify_device_identity_cert_chain(
            decode_hex(W3_PROD_MCU_CERT_HEX).unwrap(),
            decode_hex(W3_PROD_BATCH_CERT_HEX).unwrap(),
        );
        assert_eq!(prod_result.unwrap(), "6CA042FFFE3C4094".to_string());
    }

    #[test]
    fn test_verify_valid_cert_chain_w1() {
        let identity_cert_der = decode_hex(W1_IDENTITY_CERT_HEX).unwrap();
        let batch_cert_der = decode_hex(W1_BATCH_CERT_HEX).unwrap();
        let result =
            Attestation {}.verify_device_identity_cert_chain(identity_cert_der, batch_cert_der);
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), "38398FFFFED081B6".to_string());
    }

    #[test]
    fn test_verify_cert_chain_not_for_block() {
        for (identity_cert_der, batch_cert_der) in [
            (
                decode_hex(W1_IDENTITY_CERT_HEX).unwrap(),
                decode_hex(W1_BATCH_CERT_HEX).unwrap(),
            ),
            (
                decode_hex(W3_DEV_MCU_CERT_HEX).unwrap(),
                decode_hex(W3_DEV_BATCH_CERT_HEX).unwrap(),
            ),
            (
                decode_hex(W3_PROD_MCU_CERT_HEX).unwrap(),
                decode_hex(W3_PROD_BATCH_CERT_HEX).unwrap(),
            ),
        ] {
            // Swap the batch and identity certs.
            let result =
                Attestation {}.verify_device_identity_cert_chain(batch_cert_der, identity_cert_der);
            assert_eq!(result.unwrap_err(), AttestationError::NotForBlock);
        }
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
    fn spending_key_attestation_payload_wire_format() {
        // Pin the exact bytes that firmware signs and the verifier checks:
        // "HWV1" (4 bytes) || compressed SEC1 pubkey (33 bytes) = 37 bytes total.
        // If this test fails, firmware (populate_key_attestation in
        // key_manager_task.c) and/or any server-side verifier likely drifted
        // from the app-side construction; align the three together.
        let mut pubkey = vec![0x02];
        pubkey.extend_from_slice(&[0u8; 32]);

        let payload = spending_key_attestation_payload(&pubkey);

        assert_eq!(payload.len(), 37);
        assert_eq!(&payload[..4], b"HWV1");
        assert_eq!(payload[4], 0x02);
        assert_eq!(&payload[5..], &[0u8; 32]);
    }

    #[test]
    fn verify_spending_key_attestation_rejects_wrong_pubkey_length() {
        let identity_cert_der = decode_hex(W1_IDENTITY_CERT_HEX).unwrap();
        let batch_cert_der = decode_hex(W1_BATCH_CERT_HEX).unwrap();
        // 32-byte X-only pubkey is wrong; we require 33-byte compressed SEC1.
        let bad_pubkey = vec![0u8; 32];
        let signature = vec![0u8; 64];

        let result = Attestation {}.verify_spending_key_attestation(
            identity_cert_der,
            batch_cert_der,
            bad_pubkey,
            signature,
        );
        assert_eq!(result.unwrap_err(), AttestationError::ParseFailure);
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
