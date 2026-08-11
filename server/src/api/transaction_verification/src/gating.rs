//! Server-side kill switch for transaction verification.
//!
//! The mobile transaction-verification flow is gated client-side by
//! `mobile-tx-verification-enabled`, but the server routes and the
//! spend-rule enforcement stayed reachable regardless. A custom
//! authenticated client could therefore drive the verification API
//! outside the official app flow, including installing a policy the
//! disabled mobile flow can never satisfy — which would strand the
//! account behind an approval path it cannot complete.
//!
//! [`TRANSACTION_VERIFICATION_ENABLED`] turns the whole feature off as one
//! unit. Both halves must read the same switch, because the pairing
//! "routes blocked, enforcement on" is exactly the stranding state we are
//! avoiding:
//!
//! * `transaction_verification::routes` — the account-authed `/tx-verify`
//!   routes reject with `403` when the switch is off.
//! * `mobile_pay::signing_strategies` — no
//!   `TransactionVerificationFeatures` are built when the switch is off,
//!   so a stored [`TransactionVerificationPolicy`] does not block
//!   co-signing.
//!
//! [`TransactionVerificationPolicy`]: types::account::entities::TransactionVerificationPolicy

use std::collections::HashMap;

use feature_flags::{
    flag::{evaluate_flag_value, ContextKey, Flag},
    service::Service as FeatureFlagsService,
};
use tracing::error;
use types::account::identifiers::AccountId;

/// Kill switch for the server side of transaction verification. Account-keyed
/// contexts, so targeting rules can stage a re-enable per account alongside
/// the mobile rollout.
pub const TRANSACTION_VERIFICATION_ENABLED: Flag<'_, bool> =
    Flag::new("f8e-transaction-verification-enabled");

/// Resolve [`TRANSACTION_VERIFICATION_ENABLED`], failing *safe* — an
/// unresolvable switch returns `false` (feature off).
///
/// Nothing can be verified while the mobile flow is disabled, so "off" is the
/// state that cannot break a customer: co-signing keeps working and no account
/// is held behind an approval path it has no way to complete. It also means an
/// absent flag disables the feature, so this takes effect on deploy rather
/// than waiting on LD configuration.
///
/// The cost is that an LD outage would also drop enforcement for an account
/// with a genuine policy on file. That is acceptable only while the feature is
/// not rolled out. **Flip this to fail closed (error → `true`) before enabling
/// the mobile flow**, so an unreadable switch can never silently relax a
/// policy a customer is relying on — see `hardware_verification_enforced` for
/// that shape.
pub fn transaction_verification_enabled(
    feature_flags: &FeatureFlagsService,
    context_key: &ContextKey,
) -> bool {
    match evaluate_flag_value::<bool>(
        feature_flags,
        TRANSACTION_VERIFICATION_ENABLED.key,
        context_key,
    ) {
        Ok(enabled) => enabled,
        Err(e) => {
            error!(
                "Failed to evaluate {}: {e}; failing safe (disabled)",
                TRANSACTION_VERIFICATION_ENABLED.key
            );
            false
        }
    }
}

/// Account-keyed context carrying no client attributes, for call sites that
/// have an [`AccountId`] but no [`ExperimentationClaims`] to draw headers
/// from. Account-keyed (not app-installation-keyed) so a client cannot dodge
/// the switch by omitting the `Bitkey-App-Installation-Id` header.
///
/// [`ExperimentationClaims`]: experimentation::claims::ExperimentationClaims
pub fn account_context_key(account_id: &AccountId) -> ContextKey {
    ContextKey::Account(account_id.to_string(), HashMap::new())
}

#[cfg(test)]
mod tests {
    use super::*;
    use feature_flags::config::Config;

    async fn service(overrides: HashMap<String, String>) -> FeatureFlagsService {
        Config::new_with_overrides(overrides)
            .to_service()
            .await
            .expect("feature flags service")
    }

    fn context() -> ContextKey {
        ContextKey::Account("test".to_string(), HashMap::new())
    }

    #[tokio::test]
    async fn unresolvable_switch_disables_the_feature() {
        // No value on file, as when the flag has not been created in
        // LaunchDarkly or LD cannot be reached.
        let service = service(HashMap::new()).await;
        assert!(!transaction_verification_enabled(&service, &context()));
    }

    #[tokio::test]
    async fn resolved_switch_is_honoured() {
        let key = TRANSACTION_VERIFICATION_ENABLED.key.to_string();

        let enabled = service(HashMap::from([(key.clone(), "true".to_string())])).await;
        assert!(transaction_verification_enabled(&enabled, &context()));

        let disabled = service(HashMap::from([(key, "false".to_string())])).await;
        assert!(!transaction_verification_enabled(&disabled, &context()));
    }
}
