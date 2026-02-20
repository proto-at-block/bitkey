//! Authorization extractor supporting Action Proof and legacy KeyClaims.
//!
//! `Authorization` is a unified wrapper that handles both authentication methods:
//! - Action Proof: Cryptographic signatures over action payloads
//! - KeyClaims: Legacy signature-based authentication
//!
//! # Authorization Architecture
//!
//! Authorization is split into two distinct concepts:
//!
//! - **Policy** (`AuthorizationRequirements`): Server-authoritative, action-dependent elements
//!   including the action, field, value, and signer requirements. Routes define what
//!   authorization is required.
//!
//! - **Credentials** (`Authorization`): Client-provided elements including JWT, signatures,
//!   nonce, and public keys. Clients provide proof they're authorized.
//!
//! The `check()` method combines policy + credentials to produce an authorized result or error.
//!
//! # Usage
//!
//! ```ignore
//! AuthorizationRequirements::new(Action::Add, Field::RecoveryEmail)
//!     .value(&email)
//!     .signers(Signers::All)
//!     .check(&auth)?;
//! ```

use std::str::FromStr;

use async_trait::async_trait;
use axum::extract::{FromRef, FromRequestParts};
use axum::http::request::Parts;
use axum::http::StatusCode;
use jsonwebtoken::TokenData;
use types::account::identifiers::AccountId;
use types::authn_authz::cognito::{CognitoUser, CognitoUsername};
use types::authn_authz::AccessTokenClaims;
use userpool::userpool::UserPoolService;

use action_proof::{Action, Field};
use errors::ApiError;

use crate::action_proof::{
    validate_signature_requirements, verify_action_proof, ActionProofHeader, ACTION_PROOF_HEADER,
};
use crate::key_claims::{get_jwt_from_headers, get_pubkeys_from_cognito, KeyClaims};
use crate::signers::{IntoSignerRequirements, SignerRequirements, Signers};

/// Unified authorization extractor for routes.
///
/// Automatically detects whether the request uses Action Proof or legacy KeyClaims
/// authentication and provides a unified interface for both.
#[derive(Debug, Clone)]
pub struct Authorization {
    pub jwt: String,
    pub account_id: AccountId,
    pub username: CognitoUsername,
    pub(crate) inner: AuthorizationInner,
}

#[derive(Debug, Clone)]
pub(crate) enum AuthorizationInner {
    /// Action Proof: signatures verified lazily in check()
    ActionProof {
        version: u8,
        signatures: Vec<String>,
        nonce: Option<String>,
        hw_pubkey: Option<String>,
        app_pubkey: Option<String>,
    },
    /// Legacy KeyClaims: signatures already verified in extractor
    KeyClaims { hw_signed: bool, app_signed: bool },
}

#[async_trait]
impl<S> FromRequestParts<S> for Authorization
where
    S: Send + Sync,
    UserPoolService: FromRef<S>,
{
    type Rejection = StatusCode;

    async fn from_request_parts(parts: &mut Parts, state: &S) -> Result<Self, Self::Rejection> {
        let user_pool = UserPoolService::from_ref(state);
        let headers = &parts.headers;

        let jwt = get_jwt_from_headers(headers).ok_or(StatusCode::UNAUTHORIZED)?;

        let token_data = parts
            .extensions
            .get::<TokenData<AccessTokenClaims>>()
            .ok_or(StatusCode::UNAUTHORIZED)?;
        let username = token_data.claims.username.clone();
        let cognito_user =
            CognitoUser::from_str(username.as_ref()).map_err(|_| StatusCode::UNAUTHORIZED)?;

        let account_id = cognito_user.get_account_id();

        if let Some(action_proof_header) = headers.get(ACTION_PROOF_HEADER) {
            let header_value = action_proof_header
                .to_str()
                .map_err(|_| StatusCode::BAD_REQUEST)?;

            let auth_header: ActionProofHeader =
                serde_json::from_str(header_value).map_err(|_| StatusCode::BAD_REQUEST)?;

            let (app_pubkey, hw_pubkey, _) =
                get_pubkeys_from_cognito(&user_pool, account_id.clone()).await?;

            Ok(Self {
                jwt,
                account_id,
                username,
                inner: AuthorizationInner::ActionProof {
                    version: auth_header.version,
                    signatures: auth_header.signatures,
                    nonce: auth_header.nonce,
                    hw_pubkey,
                    app_pubkey,
                },
            })
        } else {
            // Delegate to KeyClaims for legacy X-App-Signature / X-Hw-Signature auth
            let key_claims = KeyClaims::from_request_parts(parts, state).await?;

            Ok(Self {
                jwt,
                account_id: key_claims
                    .account_id
                    .parse()
                    .map_err(|_| StatusCode::BAD_REQUEST)?,
                username: key_claims.username,
                inner: AuthorizationInner::KeyClaims {
                    app_signed: key_claims.app_signed,
                    hw_signed: key_claims.hw_signed,
                },
            })
        }
    }
}

#[cfg(any(test, feature = "test-utils"))]
impl Default for Authorization {
    fn default() -> Self {
        Self {
            jwt: String::new(),
            account_id: AccountId::from_str("urn:wallet-account:000000000000000000000000000")
                .unwrap(),
            username: CognitoUsername::from_str(
                "urn:wallet-account:000000000000000000000000000-app",
            )
            .unwrap(),
            inner: AuthorizationInner::KeyClaims {
                hw_signed: false,
                app_signed: false,
            },
        }
    }
}

/// Server-authoritative authorization requirements.
///
/// Defines the policy for what authorization is required for an action.
/// Routes construct requirements and call `check(&auth)` to verify
/// client-provided credentials meet the policy.
///
/// # Example
///
/// ```ignore
/// AuthorizationRequirements::new(Action::Add, Field::RecoveryEmail)
///     .value(&email)
///     .signers(Signers::All)
///     .check(&auth)?;
/// ```
#[derive(Debug, Clone)]
pub struct AuthorizationRequirements {
    action: Action,
    field: Field,
    value: Option<String>,
    current: Option<String>,
    extra_bindings: Vec<(String, String)>,
    signers: SignerRequirements,
}

impl AuthorizationRequirements {
    /// Creates new authorization requirements for an action on a field.
    ///
    /// Defaults to `Signers::All` for both action_proof and key_claims.
    /// Use `.signers()` to specify different requirements.
    pub fn new(action: Action, field: Field) -> Self {
        Self {
            action,
            field,
            value: None,
            current: None,
            extra_bindings: vec![],
            signers: Signers::All.into_requirements(),
        }
    }

    /// Sets the expected value that was signed.
    pub fn value(mut self, v: impl AsRef<str>) -> Self {
        self.value = Some(v.as_ref().to_string());
        self
    }

    /// Sets the expected current value (for update operations).
    pub fn current(mut self, v: impl AsRef<str>) -> Self {
        self.current = Some(v.as_ref().to_string());
        self
    }

    /// Sets the expected value that was signed (Option variant).
    pub fn value_opt(mut self, v: Option<impl AsRef<str>>) -> Self {
        self.value = v.map(|s| s.as_ref().to_string());
        self
    }

    /// Sets the expected current value (Option variant).
    pub fn current_opt(mut self, v: Option<impl AsRef<str>>) -> Self {
        self.current = v.map(|s| s.as_ref().to_string());
        self
    }

    /// Adds an entity ID binding to the context.
    pub fn with_entity_id(mut self, id: impl AsRef<str>) -> Self {
        self.extra_bindings.push((
            action_proof::ContextBinding::EntityId.key().to_string(),
            id.as_ref().to_string(),
        ));
        self
    }

    /// Sets the signer requirements for authorization.
    ///
    /// Accepts:
    /// - `Signers::All` or `Signers::Any` for same requirement on both auth types
    /// - `SignerRequirements { action_proof: ..., key_claims: ... }` for different requirements
    pub fn signers(mut self, signers: impl IntoSignerRequirements) -> Self {
        self.signers = signers.into_requirements();
        self
    }

    /// Verify client-provided authorization meets these requirements.
    ///
    /// Combines policy (self) + credentials (auth) → authorized or error.
    pub fn check(self, auth: &Authorization) -> Result<AuthorizedRequest, ApiError> {
        match &auth.inner {
            AuthorizationInner::ActionProof {
                version,
                signatures,
                nonce,
                hw_pubkey,
                app_pubkey,
            } => {
                let (hw_signed, app_signed) = verify_action_proof(
                    *version,
                    signatures,
                    nonce.as_deref(),
                    hw_pubkey.as_deref(),
                    app_pubkey.as_deref(),
                    &auth.jwt,
                    self.action,
                    self.field,
                    self.value.as_deref(),
                    self.current.as_deref(),
                    &self.extra_bindings,
                )?;

                validate_signature_requirements(hw_signed, app_signed, self.signers.action_proof)?;

                Ok(AuthorizedRequest {
                    account_id: auth.account_id.clone(),
                    username: auth.username.clone(),
                    nonce: nonce.clone(),
                    hw_signed,
                    app_signed,
                })
            }

            AuthorizationInner::KeyClaims {
                hw_signed,
                app_signed,
            } => {
                validate_signature_requirements(*hw_signed, *app_signed, self.signers.key_claims)?;

                Ok(AuthorizedRequest {
                    account_id: auth.account_id.clone(),
                    username: auth.username.clone(),
                    nonce: None,
                    hw_signed: *hw_signed,
                    app_signed: *app_signed,
                })
            }
        }
    }
}

/// Result of successful authorization verification.
///
/// Contains the verified account identity and signature status.
/// Owns its data (copied from `Authorization`) for flexibility in downstream code.
pub struct AuthorizedRequest {
    account_id: AccountId,
    username: CognitoUsername,
    nonce: Option<String>,
    hw_signed: bool,
    app_signed: bool,
}

impl AuthorizedRequest {
    pub fn account_id(&self) -> &AccountId {
        &self.account_id
    }

    pub fn username(&self) -> &CognitoUsername {
        &self.username
    }

    /// Returns the nonce from the Action Proof request, if present.
    /// The nonce can be used by clients to generate fresh signatures for retries.
    pub fn nonce(&self) -> Option<&str> {
        self.nonce.as_deref()
    }

    pub fn hw_signed(&self) -> bool {
        self.hw_signed
    }

    pub fn app_signed(&self) -> bool {
        self.app_signed
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_key_claims_auth(hw_signed: bool, app_signed: bool) -> Authorization {
        Authorization {
            inner: AuthorizationInner::KeyClaims {
                hw_signed,
                app_signed,
            },
            ..Default::default()
        }
    }

    #[test]
    fn check_key_claims_all_signed_passes_signers_all() {
        let auth = make_key_claims_auth(true, true);
        let result = AuthorizationRequirements::new(Action::Add, Field::RecoveryEmail)
            .value("test@example.com")
            .signers(Signers::All)
            .check(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(authorized.hw_signed());
        assert!(authorized.app_signed());
    }

    #[test]
    fn check_key_claims_hw_only_fails_signers_all() {
        let auth = make_key_claims_auth(true, false);
        let result = AuthorizationRequirements::new(Action::Add, Field::RecoveryEmail)
            .value("test@example.com")
            .signers(Signers::All)
            .check(&auth);

        assert!(result.is_err());
    }

    #[test]
    fn check_key_claims_app_only_fails_signers_all() {
        let auth = make_key_claims_auth(false, true);
        let result = AuthorizationRequirements::new(Action::Add, Field::RecoveryEmail)
            .value("test@example.com")
            .signers(Signers::All)
            .check(&auth);

        assert!(result.is_err());
    }

    #[test]
    fn check_key_claims_hw_only_passes_signers_any() {
        let auth = make_key_claims_auth(true, false);
        let result = AuthorizationRequirements::new(Action::Add, Field::RecoveryEmail)
            .value("test@example.com")
            .signers(Signers::Any)
            .check(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(authorized.hw_signed());
        assert!(!authorized.app_signed());
    }

    #[test]
    fn check_key_claims_app_only_passes_signers_any() {
        let auth = make_key_claims_auth(false, true);
        let result = AuthorizationRequirements::new(Action::Add, Field::RecoveryEmail)
            .value("test@example.com")
            .signers(Signers::Any)
            .check(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(!authorized.hw_signed());
        assert!(authorized.app_signed());
    }

    #[test]
    fn check_key_claims_none_signed_fails_signers_any() {
        let auth = make_key_claims_auth(false, false);
        let result = AuthorizationRequirements::new(Action::Add, Field::RecoveryEmail)
            .value("test@example.com")
            .signers(Signers::Any)
            .check(&auth);

        assert!(result.is_err());
    }

    #[test]
    fn check_key_claims_with_different_requirements() {
        let auth = make_key_claims_auth(true, false);

        // ActionProof requires All but KeyClaims only requires Any
        let requirements = SignerRequirements {
            action_proof: Signers::All,
            key_claims: Signers::Any,
        };

        let result = AuthorizationRequirements::new(Action::Add, Field::RecoveryEmail)
            .value("test@example.com")
            .signers(requirements)
            .check(&auth);

        // Should pass because KeyClaims only requires Any
        assert!(result.is_ok());
    }

    #[test]
    fn check_key_claims_value_opt_none() {
        let auth = make_key_claims_auth(true, true);
        let result = AuthorizationRequirements::new(Action::Add, Field::RecoveryEmail)
            .value_opt(None::<String>)
            .signers(Signers::All)
            .check(&auth);

        assert!(result.is_ok());
    }

    #[test]
    fn check_key_claims_current_opt() {
        let auth = make_key_claims_auth(true, true);
        let result = AuthorizationRequirements::new(Action::Set, Field::RecoveryEmail)
            .value("new@example.com")
            .current_opt(Some("old@example.com"))
            .signers(Signers::All)
            .check(&auth);

        assert!(result.is_ok());
    }

    #[test]
    fn check_defaults_to_signers_all() {
        let auth = make_key_claims_auth(true, true);
        // Don't explicitly call .signers() - should default to Signers::All
        let result = AuthorizationRequirements::new(Action::Add, Field::RecoveryEmail)
            .value("test@example.com")
            .check(&auth);

        assert!(result.is_ok());

        // Verify that hw_only fails without explicit signers (defaults to All)
        let auth_hw_only = make_key_claims_auth(true, false);
        let result_fail = AuthorizationRequirements::new(Action::Add, Field::RecoveryEmail)
            .value("test@example.com")
            .check(&auth_hw_only);

        assert!(result_fail.is_err());
    }

    // ActionProof tests

    fn make_action_proof_auth(
        hw_sig: Option<String>,
        app_sig: Option<String>,
        hw_pubkey: Option<String>,
        app_pubkey: Option<String>,
        jwt: &str,
    ) -> Authorization {
        let mut signatures = Vec::new();
        if let Some(s) = hw_sig {
            signatures.push(s);
        }
        if let Some(s) = app_sig {
            signatures.push(s);
        }

        Authorization {
            jwt: jwt.to_string(),
            inner: AuthorizationInner::ActionProof {
                version: 1,
                signatures,
                nonce: None,
                hw_pubkey,
                app_pubkey,
            },
            ..Default::default()
        }
    }

    #[test]
    fn check_action_proof_both_signed_passes_signers_all() {
        use crate::test_utils::{
            get_test_access_token, get_test_app_key, get_test_app_pubkey, get_test_hw_key,
            get_test_hw_pubkey, sign_recoverable,
        };
        use action_proof::{build_payload, compute_token_binding, ContextBinding};

        let jwt = get_test_access_token();
        let action = Action::Add;
        let field = Field::RecoveryEmail;
        let value = "test@example.com";

        let token_binding = compute_token_binding(&jwt);
        let payload = build_payload(
            action,
            field,
            Some(value),
            None,
            &[(ContextBinding::TokenBinding.key(), &token_binding)],
        )
        .unwrap();

        let hw_sig = sign_recoverable(&payload, get_test_hw_key());
        let app_sig = sign_recoverable(&payload, get_test_app_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            Some(app_sig),
            Some(get_test_hw_pubkey()),
            Some(get_test_app_pubkey()),
            &jwt,
        );

        let result = AuthorizationRequirements::new(action, field)
            .value(value)
            .signers(Signers::All)
            .check(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(authorized.hw_signed());
        assert!(authorized.app_signed());
    }

    #[test]
    fn check_action_proof_hw_only_fails_signers_all() {
        use crate::test_utils::{
            get_test_access_token, get_test_app_pubkey, get_test_hw_key, get_test_hw_pubkey,
            sign_recoverable,
        };
        use action_proof::{build_payload, compute_token_binding, ContextBinding};

        let jwt = get_test_access_token();
        let action = Action::Add;
        let field = Field::RecoveryEmail;
        let value = "test@example.com";

        let token_binding = compute_token_binding(&jwt);
        let payload = build_payload(
            action,
            field,
            Some(value),
            None,
            &[(ContextBinding::TokenBinding.key(), &token_binding)],
        )
        .unwrap();

        let hw_sig = sign_recoverable(&payload, get_test_hw_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            None,
            Some(get_test_hw_pubkey()),
            Some(get_test_app_pubkey()),
            &jwt,
        );

        let result = AuthorizationRequirements::new(action, field)
            .value(value)
            .signers(Signers::All)
            .check(&auth);

        assert!(result.is_err());
    }

    #[test]
    fn check_action_proof_wrong_value_fails() {
        use crate::test_utils::{
            get_test_access_token, get_test_app_key, get_test_app_pubkey, get_test_hw_key,
            get_test_hw_pubkey, sign_recoverable,
        };
        use action_proof::{build_payload, compute_token_binding, ContextBinding};

        let jwt = get_test_access_token();
        let action = Action::Add;
        let field = Field::RecoveryEmail;
        let signed_value = "signed@example.com";
        let expected_value = "expected@example.com";

        // Sign with wrong value
        let token_binding = compute_token_binding(&jwt);
        let payload = build_payload(
            action,
            field,
            Some(signed_value),
            None,
            &[(ContextBinding::TokenBinding.key(), &token_binding)],
        )
        .unwrap();

        let hw_sig = sign_recoverable(&payload, get_test_hw_key());
        let app_sig = sign_recoverable(&payload, get_test_app_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            Some(app_sig),
            Some(get_test_hw_pubkey()),
            Some(get_test_app_pubkey()),
            &jwt,
        );

        // Verify with different expected value - should fail
        let result = AuthorizationRequirements::new(action, field)
            .value(expected_value)
            .signers(Signers::All)
            .check(&auth);

        assert!(result.is_err());
    }

    #[test]
    fn check_action_proof_hw_only_passes_signers_any() {
        use crate::test_utils::{
            get_test_access_token, get_test_app_pubkey, get_test_hw_key, get_test_hw_pubkey,
            sign_recoverable,
        };
        use action_proof::{build_payload, compute_token_binding, ContextBinding};

        let jwt = get_test_access_token();
        let action = Action::Add;
        let field = Field::RecoveryEmail;
        let value = "test@example.com";

        let token_binding = compute_token_binding(&jwt);
        let payload = build_payload(
            action,
            field,
            Some(value),
            None,
            &[(ContextBinding::TokenBinding.key(), &token_binding)],
        )
        .unwrap();

        let hw_sig = sign_recoverable(&payload, get_test_hw_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            None,
            Some(get_test_hw_pubkey()),
            Some(get_test_app_pubkey()),
            &jwt,
        );

        let result = AuthorizationRequirements::new(action, field)
            .value(value)
            .signers(Signers::Any)
            .check(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(authorized.hw_signed());
        assert!(!authorized.app_signed());
    }
}
