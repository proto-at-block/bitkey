//! Anti-replay protection for Action Proof.
//!
//! Prevents replay of action proofs within the auth token validity window.
//! Content hashes (SHA-256 of the canonical payload) are "burned" in a
//! DynamoDB cache after a successful server-side state change. Subsequent
//! requests with the same content hash are rejected as replays.
//!
//! # Architecture
//!
//! - **Content hash**: SHA-256 of the canonical payload (what gets signed).
//!   Keying on content hash rather than signature eliminates the need for
//!   low-S normalization, since signature malleability doesn't matter.
//!
//! - **Check**: Before processing a request, check if the content hash
//!   exists in the cache. If it does, return the cached_response as replay.
//!
//! - **Burn**: After a successful state change, insert the content hash
//!   into the cache with TTL = token_ttl + buffer.
//!
//! - **TTL**: Entries expire at the JWT's `exp` claim + a 5-minute buffer.
//!   The access token validity is 5 minutes (Cognito config), so entries
//!   live ~10 minutes total. DynamoDB TTL handles automatic cleanup.
//!
//! # Usage
//!
//! The anti-replay check and burn are performed by the service layer
//! (e.g., `PrivilegedActionService`) using `AntiReplayRepository` directly.
//! The content hash from `AuthorizedContext` provides the key needed
//! for these operations.

/// Buffer added to token TTL for anti-replay cache entry expiration.
/// Accounts for clock skew between servers and DynamoDB TTL resolution.
pub const ANTI_REPLAY_TTL_BUFFER_SECS: u64 = 300; // 5 minutes

/// Encode a content hash as hex for use as a DynamoDB key.
pub fn content_hash_to_hex(hash: &[u8; 32]) -> String {
    hex::encode(hash)
}
