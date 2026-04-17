pub mod action_proof;
pub mod anti_replay;
pub mod authorization;
pub(crate) mod authorized_action;
pub mod authorizer;
mod debug_utils;
pub(crate) mod key_claims;
mod metrics;
pub mod routes;
pub mod signers;
pub mod test_utils;

pub use ::action_proof::Action;
pub use authorization::{Authorization, AuthorizationRequirements, AuthorizedContext};
pub use key_claims::extract_account_id;
pub use signers::ProofRequirement;

/// Legacy signature header constants. Exposed for test utilities that construct
/// requests with W1 KeyClaims signatures.
pub mod headers {
    pub use crate::key_claims::{APP_SIG_HEADER, HW_SIG_HEADER};
}
