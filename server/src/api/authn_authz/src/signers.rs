//! Proof requirement types for authorization policies.
//!
//! `ProofRequirement` expresses the route's intent for what level of
//! cryptographic proof of possession is needed, independent of the
//! underlying mechanism (ActionProof for W3, KeyClaims for W1).

/// Specifies what level of proof of possession is required.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ProofRequirement {
    /// Both app AND hardware must prove possession.
    /// Reject if either signature is missing.
    BothFactors,

    /// At least one factor must prove possession.
    /// Reject if neither signature is present.
    /// Used in recovery flows where the required factor depends on which key was lost.
    AnyFactor,

    /// Verify signatures through the correct mechanism if present,
    /// but don't reject if absent. The closure uses `ctx.hw_signed()` /
    /// `ctx.app_signed()` to make conditional decisions.
    ///
    /// For W3: only ActionProof counts. If no ActionProof header is sent,
    /// `hw_signed=false` and `app_signed=false` — KeyClaims signatures are
    /// never used for W3 accounts.
    Conditional,

    /// No proof needed. JWT authentication alone is sufficient.
    /// Signatures are neither expected nor verified.
    JwtOnly,
}
