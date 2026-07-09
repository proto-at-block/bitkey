//! Hardware-verification enrollment gate.
//!
//! Two LD flags collaborate with the one-way per-account
//! [`FullAccount::hardware_verification_required`](types::account::entities::FullAccount::hardware_verification_required)
//! field:
//!
//! * [`HARDWARE_VERIFICATION_ENABLED`] — global kill switch, checked at
//!   every decision point.
//! * [`HARDWARE_VERIFICATION_REQUIRED`] — per-caller version eligibility,
//!   checked only at enrollment.
//!
//! Enrollment: `enabled && required` → flip the per-account flag.
//! Enforcement (later PRs): `enabled && account.hardware_verification_required`.
//! Enforcement never re-checks `required` — once enrolled, an account stays
//! subject to OOBA regardless of its current client's versions.

use feature_flags::flag::{evaluate_flag_value, ContextKey, Flag};
use feature_flags::service::Service as FeatureFlagsService;
use instrumentation::metrics::factory::{Counter, MetricsFactory};
use once_cell::sync::Lazy;
use tracing::error;

pub const FACTORY_NAME: &str = "hardware_verification";

pub static FACTORY: Lazy<MetricsFactory> = Lazy::new(|| MetricsFactory::new(FACTORY_NAME));

/// Global kill switch. Plain boolean, no targeting attributes. Default `false`.
pub const HARDWARE_VERIFICATION_ENABLED: Flag<'_, bool> =
    Flag::new("f8e-hardware-verification-enabled");

/// Per-caller version eligibility. Targeting rules use `application_version`,
/// `firmware_version`, and `hardware_type` attributes on an account-keyed
/// context (rollout bucketing is per-account so a reinstall can't shuffle a
/// caller out of an in-flight rollout). Default `false`.
pub const HARDWARE_VERIFICATION_REQUIRED: Flag<'_, bool> =
    Flag::new("f8e-hardware-verification-required");

pub const TRIGGER_KEY: &str = "trigger";
pub const TRIGGER_ONBOARDING: &str = "onboarding";
pub const TRIGGER_POST_CREATION: &str = "post_creation";

/// Counter: bumped once when an account's `hardware_verification_required`
/// flips `false` → `true`. Tagged by [`TRIGGER_KEY`]. Emitted as
/// `bitkey.hardware_verification.enrollment_flipped`.
pub static ENROLLMENT_FLIPPED: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("enrollment_flipped", None));

/// Resolve the combined enrollment gate for this caller. Returns `true` only
/// when both LD flags resolve `true`; any resolution failure → `false`.
///
/// Takes `ContextKey` rather than `ExperimentationClaims` to avoid a dep
/// cycle (`experimentation` already depends on `account`); callers build
/// the account-keyed context at the call site. Account-keyed (not
/// app-installation-keyed) so a dishonest client cannot suppress evaluation
/// by omitting the `Bitkey-App-Installation-Id` header.
pub fn should_enroll_in_hardware_verification(
    feature_flags: &FeatureFlagsService,
    context_key: &ContextKey,
) -> bool {
    eval_bool_flag(feature_flags, &HARDWARE_VERIFICATION_ENABLED, context_key)
        && eval_bool_flag(feature_flags, &HARDWARE_VERIFICATION_REQUIRED, context_key)
}

/// Resolve a single boolean LD flag, returning `false` (and logging) on any
/// resolution failure. Fails *open* (feature treated as off) — correct only
/// where the non-flag state says the feature is off, so "off" is the safe
/// direction (e.g. enrollment: a not-yet-enrolled account simply isn't
/// enrolled during the outage). Where the non-flag state says the feature is
/// on — any enforcement on an already-enrolled account — use
/// [`hardware_verification_enforced`] instead.
pub fn eval_bool_flag(
    feature_flags: &FeatureFlagsService,
    flag: &Flag<'_, bool>,
    context_key: &ContextKey,
) -> bool {
    match evaluate_flag_value::<bool>(feature_flags, flag.key, context_key) {
        Ok(v) => v,
        Err(e) => {
            error!("Failed to evaluate {}: {e}; defaulting to false", flag.key);
            false
        }
    }
}

/// Resolve the [`HARDWARE_VERIFICATION_ENABLED`] kill switch for an
/// *enforcement* decision on an already-enrolled account, failing *closed*.
///
/// Unlike [`eval_bool_flag`], a resolution failure returns `true` (keep
/// enforcing): only an affirmative `Ok(false)` — an operator deliberately
/// flipping the switch off — relaxes enforcement. Combine with the site's
/// non-flag predicate via `&&`, so on a resolution error the decision follows
/// that durable state: `required && enforced(..)` stays blocked when enrolled.
///
/// Rationale: during an LD outage the switch is unreadable either way, so
/// fail-open buys no operator control — it just silently drops the gate. The
/// per-account `hardware_verification_required` flag is the most trustworthy
/// signal, so an *unknown* switch value must not override it (e.g. mint an
/// unattested keyset, or let an unverified sweep through).
pub fn hardware_verification_enforced(
    feature_flags: &FeatureFlagsService,
    context_key: &ContextKey,
) -> bool {
    match evaluate_flag_value::<bool>(
        feature_flags,
        HARDWARE_VERIFICATION_ENABLED.key,
        context_key,
    ) {
        Ok(enabled) => enabled,
        Err(e) => {
            error!(
                "Failed to evaluate {}: {e}; failing closed (enforcing) for enrolled account",
                HARDWARE_VERIFICATION_ENABLED.key
            );
            true
        }
    }
}
