//! Magic attestation fixture accepted on accounts where
//! [`is_test_account`] is true. Bypasses the real Silicon-Labs-rooted
//! verifier so emulator hardware and integration tests can exercise the
//! "attestation required" code path end-to-end.
//!
//! Risk: a mainnet account with `is_test_account = true` can submit this
//! fixture to bypass real attestation. Accepted because `is_test_account`
//! already weakens accounts in other ways and is gated by the same
//! `allow_test_accounts_with_mainnet_keysets` toggle.
//!
//! [`is_test_account`]: types::account::entities::AccountProperties::is_test_account

/// Base64 of `b"good"`; the magic signature accepted on test accounts.
pub const TEST_ACCOUNT_ATTESTATION_SIGNATURE_B64: &str = "Z29vZA==";

/// Single-element cert chain accepted on test accounts: base64 of `b"good"`.
pub const TEST_ACCOUNT_ATTESTATION_CERT_B64: &str = "Z29vZA==";

/// Serial number persisted as [`AttestedHardwareSerial::Pending`] when the
/// magic fixture is accepted.
///
/// [`AttestedHardwareSerial::Pending`]: types::account::spending::AttestedHardwareSerial::Pending
pub const TEST_ACCOUNT_ATTESTED_SERIAL: &str = "000WS27100000000";

/// Returns true iff `signature` and `cert_chain` match the magic fixture.
pub fn matches(signature: &[u8], cert_chain: &[Vec<u8>]) -> bool {
    let magic = b"good";
    signature == magic && cert_chain.len() == 1 && cert_chain[0] == magic
}

#[cfg(test)]
mod tests {
    use super::*;

    // Base64("good") = "Z29vZA==". Pinned to lock the wire shape that
    // clients (mobile, CLI, internal QA) need to send.
    #[test]
    fn constants_match_documented_encoding() {
        assert_eq!(TEST_ACCOUNT_ATTESTATION_SIGNATURE_B64, "Z29vZA==");
        assert_eq!(TEST_ACCOUNT_ATTESTATION_CERT_B64, "Z29vZA==");
        assert_eq!(TEST_ACCOUNT_ATTESTED_SERIAL, "000WS27100000000");
    }

    #[test]
    fn matches_magic_fixture() {
        assert!(matches(b"good", &[b"good".to_vec()]));
    }

    #[test]
    fn rejects_extra_chain_elements() {
        assert!(!matches(b"good", &[b"good".to_vec(), b"good".to_vec()]));
    }

    #[test]
    fn rejects_garbage() {
        assert!(!matches(&[0u8; 64], &[vec![0u8; 32], vec![0u8; 32]]));
    }
}
