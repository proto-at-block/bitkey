//! Authorization framework for privileged Bitkey operations.
//!
//! Provides canonical payload format for actions requiring cryptographic authorization.
//! The canonical bytes (0x1F delimited) are signed by app and/or hardware keys.
//!
//! # Example
//!
//! ```rust
//! use action_proof::{Action, Field, build_payload, compute_token_binding};
//!
//! let jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
//! let token_binding = compute_token_binding(jwt);
//!
//! let payload = build_payload(
//!     Action::Add,
//!     Field::RecoveryContacts,
//!     Some("Alice"),
//!     None,
//!     &[("tb", &token_binding)],
//! );
//! ```

macro_rules! define_enum {
    (
        $(#[$meta:meta])*
        $name:ident, $err_name:ident, $err_msg:literal {
            $($(#[$variant_meta:meta])* $variant:ident),* $(,)?
        }
    ) => {
        $(#[$meta])*
        #[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
        pub enum $name {
            $($(#[$variant_meta])* $variant),*
        }

        impl $name {
            pub const fn as_str(&self) -> &'static str {
                match self {
                    $(Self::$variant => stringify!($variant)),*
                }
            }

            pub const fn all() -> &'static [$name] {
                &[$($name::$variant),*]
            }
        }

        impl core::fmt::Display for $name {
            fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
                f.write_str(self.as_str())
            }
        }

        impl core::str::FromStr for $name {
            type Err = $err_name;

            fn from_str(s: &str) -> Result<Self, Self::Err> {
                Self::all()
                    .iter()
                    .find(|v| v.as_str() == s)
                    .copied()
                    .ok_or_else(|| $err_name(s.to_string()))
            }
        }

        #[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
        #[error($err_msg)]
        pub struct $err_name(pub String);
    };
}

pub mod actions;
pub mod binding;
pub mod fields;
pub mod payload;
pub mod validation;

pub use actions::{Action, ParseActionError};
pub use binding::compute_token_binding;
pub use fields::{ContextBinding, Field, ParseFieldError, ValueFormat};
pub use payload::{
    build_payload, parse_payload, BuildError, ParseError, ParsedPayload, CANONICAL_MAGIC,
    CANONICAL_VERSION, UNIT_SEPARATOR,
};
pub use validation::{
    is_valid_value, validate_if_present, validate_value, ValidationError, MAX_VALUE_LENGTH,
};

#[cfg(test)]
mod tests {
    use super::*;

    /// Integration test: demonstrates full workflow from JWT → token binding → payload.
    #[test]
    fn full_workflow_integration() {
        let jwt = "test-jwt-token";
        let token_binding = compute_token_binding(jwt);

        // Token binding should be 64-char lowercase hex
        assert_eq!(token_binding.len(), 64);
        assert!(token_binding.chars().all(|c| c.is_ascii_hexdigit()));

        // Build payload with multiple bindings (alphabetically sorted)
        let payload = build_payload(
            Action::Add,
            Field::RecoveryContacts,
            Some("Alice"),
            None,
            &[("eid", "01HQXYZ123"), ("tb", &token_binding)],
        )
        .expect("should build");

        let parsed = parse_payload(&payload).expect("should parse");
        assert_eq!(parsed.action, Action::Add);
        assert_eq!(parsed.field, Field::RecoveryContacts);
        assert_eq!(parsed.value, Some("Alice"));
        assert_eq!(
            parsed.bindings,
            vec![("eid", "01HQXYZ123"), ("tb", token_binding.as_str())]
        );
    }
}
