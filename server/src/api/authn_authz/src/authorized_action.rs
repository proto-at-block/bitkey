//! Compile-safe anti-replay protection for authorized actions.
//!
//! `AuthorizedAction` wraps a request payload with an `AntiReplayGuard`.
//! The only way to access the payload is through `execute()`, which:
//!
//! 1. Checks the anti-replay cache (`exists()`) -- returns cached response on replay
//! 2. Runs the closure (the actual state change)
//! 3. Burns the content hash with the serialized response
//!
//! This makes it **structurally impossible** to forget the burn.
//!
//! For non-ActionProof flows (KeyClaims, DelayAndNotify, OutOfBand), the guard
//! is a noop -- zero DynamoDB overhead.

use std::future::Future;

use errors::ApiError;
use repository::anti_replay::AntiReplayRepository;
use serde::{de::DeserializeOwned, Serialize};
use tracing::{event, Level};

/// Anti-replay guard that burns a content hash after a successful state change.
///
/// For Action Proof flows, holds the content hash hex and DynamoDB TTL.
/// For non-Action Proof flows, this is a no-op -- `content_hash_hex` is `None`
/// and all operations are skipped.
pub struct AntiReplayGuard {
    content_hash_hex: Option<String>,
    expiring_at: i64,
    created_at: String,
    /// `None` for noop guards, avoiding an unnecessary clone of the DynamoDB client.
    repository: Option<AntiReplayRepository>,
}

impl AntiReplayGuard {
    /// Creates a no-op guard for flows that don't use Action Proof anti-replay.
    pub fn noop() -> Self {
        Self {
            content_hash_hex: None,
            expiring_at: 0,
            created_at: String::new(),
            repository: None,
        }
    }

    /// Creates a guard with a real content hash for Action Proof anti-replay.
    pub fn new(
        content_hash_hex: String,
        expiring_at: i64,
        created_at: String,
        repository: AntiReplayRepository,
    ) -> Self {
        Self {
            content_hash_hex: Some(content_hash_hex),
            expiring_at,
            created_at,
            repository: Some(repository),
        }
    }

    /// Check if this content hash has been burned (replay detection).
    ///
    /// Returns `Ok(Some(cached_json))` on replay, `Ok(None)` if fresh.
    /// No-op (returns `None`) for noop guards.
    async fn check_replay(&self) -> Result<Option<String>, ApiError> {
        let (Some(ref hex), Some(ref repository)) = (&self.content_hash_hex, &self.repository)
        else {
            return Ok(None);
        };
        Ok(repository.exists(hex).await?)
    }

    /// Burns the content hash in the anti-replay cache alongside the serialized response.
    /// No-op if no content hash.
    async fn burn(self, response_json: &str) -> Result<(), ApiError> {
        let (Some(ref hex), Some(ref repository)) = (&self.content_hash_hex, &self.repository)
        else {
            return Ok(());
        };
        match repository
            .burn(hex, self.expiring_at, &self.created_at, response_json)
            .await?
        {
            true => {
                event!(
                    Level::DEBUG,
                    content_hash = %hex,
                    "Anti-replay: content hash burned with cached response"
                );
            }
            false => {
                event!(
                    Level::WARN,
                    content_hash = %hex,
                    "Anti-replay: concurrent burn detected (state change was idempotent)"
                );
            }
        }
        Ok(())
    }
}

/// Wraps an authorized request with anti-replay protection.
///
/// The only way to access the inner request (`ReqT`) is through `execute()`,
/// which handles the full anti-replay lifecycle:
///
/// 1. **Replay check**: If the content hash exists in the cache, deserializes and
///    returns the cached response -- the closure is never called.
/// 2. **Fresh request**: Runs the closure to perform the state change.
/// 3. **Burn**: On success, serializes the response and burns the content hash.
///
/// If the closure returns an error, the guard is NOT burned -- retry is allowed.
///
/// For noop guards (non-ActionProof flows), only the DynamoDB operations are
/// skipped (no cache lookup, no burn). `execute()` still serializes the response.
#[must_use = "Call .execute() to perform the state change and burn the anti-replay guard"]
pub struct AuthorizedAction<ReqT> {
    request: ReqT,
    guard: AntiReplayGuard,
}

impl<ReqT> AuthorizedAction<ReqT> {
    /// Create a new `AuthorizedAction` with the given request and anti-replay guard.
    pub(crate) fn new(request: ReqT, guard: AntiReplayGuard) -> Self {
        Self { request, guard }
    }

    /// Execute the state change with anti-replay protection.
    ///
    /// On replay (content hash already burned), returns the cached response
    /// without running the closure. On fresh requests, runs the closure and
    /// burns the content hash on success.
    pub async fn execute<F, Fut, T, E>(self, action: F) -> Result<T, E>
    where
        F: FnOnce(ReqT) -> Fut + Send,
        Fut: Future<Output = Result<T, E>> + Send,
        E: From<ApiError>,
        T: Serialize + DeserializeOwned,
    {
        // Check for replay
        if let Some(cached_json) = self.guard.check_replay().await.map_err(E::from)? {
            return serde_json::from_str(&cached_json).map_err(|e| {
                event!(
                    Level::ERROR,
                    error = %e,
                    "Anti-replay cache corrupted: failed to deserialize cached response"
                );
                E::from(ApiError::GenericInternalApplicationError(format!(
                    "Anti-replay cache corrupted: {e}"
                )))
            });
        }

        // Execute the closure
        let result = action(self.request).await?;

        // Serialize and burn
        let response_json = serde_json::to_string(&result).map_err(|e| {
            event!(
                Level::ERROR,
                error = %e,
                "Failed to serialize response for anti-replay cache. \
                 State change succeeded but cannot cache response. Not burning hash."
            );
            E::from(ApiError::GenericInternalApplicationError(
                "Failed to serialize response for anti-replay cache".to_string(),
            ))
        })?;
        self.guard.burn(&response_json).await.map_err(E::from)?;
        Ok(result)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[derive(Debug, Clone, serde::Serialize, serde::Deserialize, PartialEq)]
    struct TestResponse {
        value: String,
    }

    #[tokio::test]
    async fn execute_noop_guard_runs_closure() {
        let guard = AntiReplayGuard::noop();
        let action = AuthorizedAction::new("input", guard);

        let result: Result<TestResponse, ApiError> = action
            .execute(|req| async move {
                Ok(TestResponse {
                    value: req.to_string(),
                })
            })
            .await;

        assert!(result.is_ok());
        assert_eq!(
            result.unwrap(),
            TestResponse {
                value: "input".to_string()
            }
        );
    }

    #[tokio::test]
    async fn execute_noop_guard_propagates_closure_error() {
        let guard = AntiReplayGuard::noop();
        let action = AuthorizedAction::new("input", guard);

        let result: Result<TestResponse, ApiError> = action
            .execute(|_req| async move { Err(ApiError::GenericBadRequest("boom".to_string())) })
            .await;

        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            ApiError::GenericBadRequest(ref msg) if msg == "boom"
        ));
    }

    /// A type that deserializes fine but fails to serialize.
    /// Used to test the serialization-failure path in execute().
    #[derive(Debug, Clone, serde::Deserialize)]
    struct UnserializableResponse;

    impl serde::Serialize for UnserializableResponse {
        fn serialize<S: serde::Serializer>(&self, _s: S) -> Result<S::Ok, S::Error> {
            Err(serde::ser::Error::custom(
                "intentional serialization failure",
            ))
        }
    }

    #[tokio::test]
    async fn execute_serialization_failure_returns_error() {
        // execute() always serializes the response (even for noop guards)
        // to ensure the Serialize bound is exercised consistently.
        let guard = AntiReplayGuard::noop();
        let action = AuthorizedAction::new((), guard);

        let result: Result<UnserializableResponse, ApiError> = action
            .execute(|_| async move { Ok(UnserializableResponse) })
            .await;

        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            ApiError::GenericInternalApplicationError(ref msg) if msg.contains("serialize")
        ));
    }
}
