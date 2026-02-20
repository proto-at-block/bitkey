//! Signature requirement types for authorization policies.
//!
//! These types define which signers are required for authorization and are
//! shared between action-proof and key-claims authentication mechanisms.

/// Specifies which signers are required for authorization.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Signers {
    /// Require both app AND hardware signatures
    All,
    /// Require at least one signature (app OR hardware)
    Any,
}

/// Signer requirements that can differ between action-proof and key-claims.
#[derive(Debug, Clone, Copy)]
pub struct SignerRequirements {
    pub action_proof: Signers,
    pub key_claims: Signers,
}

/// Trait for converting values into signer requirements.
pub trait IntoSignerRequirements {
    fn into_requirements(self) -> SignerRequirements;
}

impl IntoSignerRequirements for Signers {
    fn into_requirements(self) -> SignerRequirements {
        SignerRequirements {
            action_proof: self,
            key_claims: self,
        }
    }
}

impl IntoSignerRequirements for SignerRequirements {
    fn into_requirements(self) -> SignerRequirements {
        self
    }
}
