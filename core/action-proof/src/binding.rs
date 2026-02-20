//! Token binding - derives replay-resistant identifier from auth token.

use sha2::{Digest, Sha256};

const TB_PREFIX: &[u8] = b"ActionProof tb v1";

/// Returns 64-char hex binding from SHA-256 of auth token.
pub fn compute_token_binding(jwt: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(TB_PREFIX);
    hasher.update(jwt.as_bytes());
    hex::encode(hasher.finalize())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn binding_format() {
        let binding = compute_token_binding("test-jwt");
        assert_eq!(binding.len(), 64, "binding should be 64 chars");
        assert!(
            binding.chars().all(|c| c.is_ascii_hexdigit()),
            "binding should be hex"
        );
    }

    #[test]
    fn binding_is_deterministic() {
        assert_eq!(
            compute_token_binding("test-jwt"),
            compute_token_binding("test-jwt")
        );
    }

    #[test]
    fn different_jwt_produces_different_binding() {
        assert_ne!(
            compute_token_binding("jwt-1"),
            compute_token_binding("jwt-2")
        );
    }

    #[test]
    fn known_test_vector() {
        // Regression test: sha256("ActionProof tb v1" || "test-jwt")
        let binding = compute_token_binding("test-jwt");
        assert_eq!(
            binding,
            "2fb45fc7fe85ea3afcf1363d1364962b3eca08898ab024d243898080cb856364"
        );
    }
}
