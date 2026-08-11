use instrumentation::metrics::factory::{Counter, MetricsFactory};
use once_cell::sync::Lazy;

pub const FACTORY_NAME: &str = "authn_authz";

pub static FACTORY: Lazy<MetricsFactory> = Lazy::new(|| MetricsFactory::new(FACTORY_NAME));

/// Counts Action Proof verification attempts whose registered app and hardware
/// keys resolve to the same secp256k1 point. This is observation-only until all
/// affected accounts have a repair path.
pub(crate) static ACTION_PROOF_EQUAL_REGISTERED_FACTORS: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("action_proof_equal_registered_factors", None));

/// Counts individual Action Proof signatures that validate for both registered
/// roles. This is expected when the registered factor keys are equal.
pub(crate) static ACTION_PROOF_SIGNATURE_MATCHES_BOTH_FACTORS: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("action_proof_signature_matches_both_factors", None));

/// Counts KeyClaims requests whose registered app and hardware keys resolve to
/// the same secp256k1 point. No account or key identifiers are recorded.
pub(crate) static KEY_CLAIMS_EQUAL_REGISTERED_FACTORS: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("key_claims_equal_registered_factors", None));
