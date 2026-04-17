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
//!   including the action, value, and proof requirements. Routes define what
//!   authorization is required.
//!
//! - **Credentials** (`Authorization`): Client-provided elements including JWT, signatures,
//!   nonce, and public keys. Clients provide proof they're authorized.
//!
//! `execute()` is the only way to use authorization. It verifies credentials, handles
//! anti-replay (for Action Proof flows), and runs the caller's closure.
//!
//! # Usage
//!
//! ```ignore
//! AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W3)
//!     .value(&email)
//!     .proof(ProofRequirement::BothFactors)
//!     .execute(&auth, &anti_replay_repo, |ctx| async move {
//!         // ctx.account_id(), ctx.hw_signed(), ctx.app_signed()
//!         Ok::<_, ApiError>(response)
//!     }).await?;
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

use action_proof::Action;
use errors::ApiError;

use crate::action_proof::{
    validate_proof_requirement, verify_action_proof, ActionProofHeader, ActionProofResult,
    ACTION_PROOF_HEADER,
};
use crate::key_claims::{get_jwt_from_headers, get_pubkeys_from_cognito, KeyClaims};
use crate::signers::ProofRequirement;
use types::account::entities::HardwareType;

/// Unified authorization extractor for routes.
///
/// Automatically detects whether the request uses Action Proof or legacy KeyClaims
/// authentication and provides a unified interface for both.
#[derive(Debug, Clone)]
pub struct Authorization {
    pub jwt: String,
    pub account_id: AccountId,
    pub username: CognitoUsername,
    /// The `exp` claim from the access token (epoch seconds).
    /// Used to compute anti-replay cache TTL.
    pub token_exp: u64,
    pub(crate) inner: AuthorizationInner,
}

#[derive(Debug, Clone)]
pub(crate) enum AuthorizationInner {
    /// Action Proof: signatures verified lazily in check()
    ActionProof {
        version: u8,
        signatures: Vec<String>,
        nonce: String,
        hw_pubkey: Option<String>,
        app_pubkey: Option<String>,
    },
    /// Legacy KeyClaims: signatures already verified in extractor
    KeyClaims { hw_signed: bool, app_signed: bool },
}

impl Authorization {
    /// Returns `true` if this authorization used Action Proof (not legacy KeyClaims).
    pub(crate) fn is_action_proof(&self) -> bool {
        matches!(self.inner, AuthorizationInner::ActionProof { .. })
    }
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
        let token_exp = token_data.claims.exp;

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
                token_exp,
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
                token_exp,
                inner: AuthorizationInner::KeyClaims {
                    app_signed: key_claims.app_signed,
                    hw_signed: key_claims.hw_signed,
                },
            })
        }
    }
}

/// Server-authoritative authorization requirements.
///
/// Defines the policy for what authorization is required for an action.
/// The only terminal method is `execute()`, which verifies credentials,
/// handles anti-replay, and runs the caller's closure.
///
/// # Example
///
/// ```ignore
/// let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, account.hardware_type())
///     .value(&email)
///     .proof(ProofRequirement::BothFactors)
///     .execute(&auth, &anti_replay_repo, |ctx| async move {
///         Ok::<_, ApiError>(response)
///     }).await?;
/// ```
#[derive(Debug, Clone)]
pub struct AuthorizationRequirements {
    action: Option<Action>,
    alt_actions: Vec<Action>,
    hardware_type: HardwareType,
    value: Option<String>,
    extra_bindings: Vec<(String, String)>,
    proof: ProofRequirement,
}

impl AuthorizationRequirements {
    /// Creates new authorization requirements with an action binding and hardware type enforcement.
    ///
    /// The action is used for Action Proof signature verification (W3 accounts).
    /// For KeyClaims (W1) auth, the action is not verified but documents intent.
    ///
    /// The hardware type enforces the correct auth mechanism:
    /// - `HardwareType::W1` — requires KeyClaims (rejects ActionProof)
    /// - `HardwareType::W3` — requires ActionProof (rejects KeyClaims)
    ///
    /// Defaults to `ProofRequirement::BothFactors`. Use `.proof()` to specify different requirements.
    pub fn new(action: Action, hardware_type: HardwareType) -> Self {
        Self {
            action: Some(action),
            alt_actions: vec![],
            hardware_type,
            value: None,
            extra_bindings: vec![],
            proof: ProofRequirement::BothFactors,
        }
    }

    /// Creates authorization requirements for legacy KeyClaims-only routes.
    ///
    /// Defaults to `HardwareType::W1`, which rejects Action Proof at the
    /// hardware type check. Use for routes that only support KeyClaims today
    /// and don't yet have a corresponding `Action` variant for Action Proof.
    ///
    /// TODO: Migrate callers to `new(action, hardware_type)` with proper `Action` variants as
    /// W3 Action Proof support is added per-route. Anti-replay is automatically
    /// handled by `execute()` when an action is bound.
    pub fn keyclaims_only() -> Self {
        Self {
            action: None,
            alt_actions: vec![],
            hardware_type: HardwareType::W1,
            value: None,
            extra_bindings: vec![],
            proof: ProofRequirement::BothFactors,
        }
    }

    /// Creates authorization requirements for non-privileged operations.
    ///
    /// No action binding, no signature requirements. A valid JWT alone is sufficient.
    /// Works for both W1 (KeyClaims) and W3 (no ActionProof header needed).
    pub fn jwt_only(hardware_type: HardwareType) -> Self {
        Self {
            action: None,
            alt_actions: vec![],
            hardware_type,
            value: None,
            extra_bindings: vec![],
            proof: ProofRequirement::JwtOnly,
        }
    }

    /// Sets the expected value that was signed.
    pub fn value(mut self, v: impl AsRef<str>) -> Self {
        self.value = Some(v.as_ref().to_string());
        self
    }

    /// Sets the expected value that was signed (Option variant).
    pub fn value_opt(mut self, v: Option<impl AsRef<str>>) -> Self {
        self.value = v.map(|s| s.as_ref().to_string());
        self
    }

    /// Adds a custom extra binding to the context.
    pub fn extra(mut self, key: impl AsRef<str>, value: impl AsRef<str>) -> Self {
        self.extra_bindings
            .push((key.as_ref().to_string(), value.as_ref().to_string()));
        self
    }

    /// Adds an entity ID binding to the context.
    pub fn entity_id(mut self, id: impl AsRef<str>) -> Self {
        self.extra_bindings.push((
            action_proof::ContextBinding::EntityId.key().to_string(),
            id.as_ref().to_string(),
        ));
        self
    }

    /// Adds an entity ID binding to the context, if present (Option variant).
    pub fn entity_id_opt(self, id: Option<impl AsRef<str>>) -> Self {
        match id {
            Some(id) => self.entity_id(id),
            None => self,
        }
    }

    /// Sets the proof requirement for authorization.
    ///
    /// Default is `ProofRequirement::BothFactors`.
    pub fn proof(mut self, proof: ProofRequirement) -> Self {
        self.proof = proof;
        self
    }

    /// Adds an alternative action that is also accepted during verification.
    ///
    /// When verifying an Action Proof, the primary action is tried first.
    /// If signature verification fails, each alternative is tried in order.
    /// This is useful when the server cannot distinguish which semantic action
    /// the client intended (e.g., cancel-own vs cancel-conflicting recovery).
    pub fn or_action(mut self, action: Action) -> Self {
        self.alt_actions.push(action);
        self
    }

    /// Verify authorization and execute a state change with anti-replay protection.
    ///
    /// This is the **only** way to use `AuthorizationRequirements`. It:
    /// 1. Verifies client credentials meet the policy
    /// 2. For Action Proof: checks anti-replay cache (returns cached response on replay)
    /// 3. Runs the closure with `AuthorizedContext` (account_id, hw_signed, app_signed)
    /// 4. For Action Proof: burns the content hash with the serialized response
    ///
    /// For non-Action Proof (KeyClaims / no-action) flows, steps 2 and 4 are noop.
    ///
    /// # Example
    ///
    /// ```ignore
    /// let result = AuthorizationRequirements::new(action, hardware_type)
    ///     .proof(ProofRequirement::BothFactors)
    ///     .execute(&auth, &anti_replay_repo, |ctx| async move {
    ///         // ctx.hw_signed(), ctx.app_signed(), ctx.account_id()
    ///         Ok::<_, ApiError>(response)
    ///     }).await?;
    /// ```
    pub async fn execute<F, Fut, T, E>(
        self,
        auth: &Authorization,
        anti_replay_repository: &repository::anti_replay::AntiReplayRepository,
        action: F,
    ) -> Result<T, E>
    where
        F: FnOnce(AuthorizedContext) -> Fut + Send,
        Fut: std::future::Future<Output = Result<T, E>> + Send,
        E: From<errors::ApiError>,
        T: serde::Serialize + serde::de::DeserializeOwned,
    {
        use crate::anti_replay::{content_hash_to_hex, ANTI_REPLAY_TTL_BUFFER_SECS};
        use crate::authorized_action::{AntiReplayGuard, AuthorizedAction};

        let authorized = self.check(auth).map_err(E::from)?;

        let guard = match authorized.content_hash {
            Some(hash) => AntiReplayGuard::new(
                content_hash_to_hex(&hash),
                auth.token_exp
                    .saturating_add(ANTI_REPLAY_TTL_BUFFER_SECS)
                    .min(i64::MAX as u64) as i64,
                time::OffsetDateTime::now_utc()
                    .format(&time::format_description::well_known::Rfc3339)
                    .unwrap_or_else(|_| "unknown".to_string()),
                anti_replay_repository.clone(),
            ),
            None => AntiReplayGuard::noop(),
        };

        AuthorizedAction::new(authorized, guard)
            .execute(action)
            .await
    }

    /// Verify client-provided authorization meets these requirements.
    ///
    /// Private — callers must use `execute()` which calls this internally.
    fn check(self, auth: &Authorization) -> Result<AuthorizedContext, ApiError> {
        // W1 can never use ActionProof
        if self.hardware_type == HardwareType::W1 && auth.is_action_proof() {
            return Err(ApiError::GenericForbidden(
                "W1 accounts must use KeyClaims authentication".to_string(),
            ));
        }

        // Resolve proof results based on mechanism and hardware type.
        // W3 proof must come from ActionProof; W1 proof must come from KeyClaims.
        // If the correct mechanism wasn't used, treat as unsigned.
        let (hw_signed, app_signed, content_hash, nonce) =
            match (self.hardware_type, &auth.inner, self.proof) {
                // W3 with ActionProof (and not JwtOnly): verify signatures
                (
                    HardwareType::W3,
                    AuthorizationInner::ActionProof {
                        version,
                        signatures,
                        nonce,
                        hw_pubkey,
                        app_pubkey,
                    },
                    proof,
                ) if proof != ProofRequirement::JwtOnly => {
                    let primary_action = self.action.ok_or_else(|| {
                        ApiError::GenericForbidden(
                            "Action Proof received but no action binding configured for this route"
                                .to_string(),
                        )
                    })?;

                    // Try the primary action first, then alternatives.
                    let mut last_err = None;
                    let mut result = None;
                    for action in
                        std::iter::once(primary_action).chain(self.alt_actions.iter().copied())
                    {
                        match verify_action_proof(
                            *version,
                            signatures,
                            nonce,
                            hw_pubkey.as_deref(),
                            app_pubkey.as_deref(),
                            &auth.jwt,
                            action,
                            self.value.as_deref(),
                            &self.extra_bindings,
                        ) {
                            Ok(r) => {
                                result = Some(r);
                                break;
                            }
                            Err(e) => last_err = Some(e),
                        }
                    }

                    let ActionProofResult {
                        hw_signed,
                        app_signed,
                        content_hash,
                    } = result.ok_or_else(|| last_err.unwrap())?;

                    (
                        hw_signed,
                        app_signed,
                        Some(content_hash),
                        Some(nonce.clone()),
                    )
                }

                // W1 with KeyClaims: use eagerly-verified flags.
                // This includes JwtOnly routes — signatures aren't required but
                // are still propagated so closures can inspect them.
                (
                    HardwareType::W1,
                    AuthorizationInner::KeyClaims {
                        hw_signed,
                        app_signed,
                    },
                    _,
                ) => (*hw_signed, *app_signed, None, None),

                // Everything else: unsigned context
                // - W3 without ActionProof header (KeyClaims ignored for W3)
                _ => (false, false, None, None),
            };

        // Validate against proof requirement
        validate_proof_requirement(hw_signed, app_signed, self.proof)?;

        Ok(AuthorizedContext {
            account_id: auth.account_id.clone(),
            username: auth.username.clone(),
            nonce,
            hw_signed,
            app_signed,
            content_hash,
        })
    }
}

/// Authorized context passed to the `execute()` closure.
///
/// Contains the verified account identity and signature status.
/// This type can only be obtained through `AuthorizationRequirements::execute()`,
/// ensuring authorization is always verified before use.
#[derive(Debug, Clone)]
pub struct AuthorizedContext {
    account_id: AccountId,
    username: CognitoUsername,
    nonce: Option<String>,
    hw_signed: bool,
    app_signed: bool,
    pub(crate) content_hash: Option<[u8; 32]>,
}

impl AuthorizedContext {
    pub fn account_id(&self) -> &AccountId {
        &self.account_id
    }

    pub fn username(&self) -> &CognitoUsername {
        &self.username
    }

    /// Returns the nonce from the authorization request.
    ///
    /// Always `Some` for Action Proof requests (nonce is required).
    /// Always `None` for legacy KeyClaims requests (no nonce concept).
    pub fn nonce(&self) -> Option<&str> {
        self.nonce.as_deref()
    }

    pub fn hw_signed(&self) -> bool {
        self.hw_signed
    }

    pub fn app_signed(&self) -> bool {
        self.app_signed
    }

    /// Returns the content hash from Action Proof verification.
    ///
    /// Always `Some` for Action Proof requests (hash is derived from the signed payload).
    /// Always `None` for legacy KeyClaims requests.
    pub fn content_hash(&self) -> Option<&[u8; 32]> {
        self.content_hash.as_ref()
    }
}

#[cfg(test)]
impl AuthorizationRequirements {
    pub(crate) fn get_extra_bindings(&self) -> &[(String, String)] {
        &self.extra_bindings
    }

    /// Test-only: expose check() for unit testing authorization logic
    /// without needing an AntiReplayRepository.
    pub(crate) fn check_for_test(
        self,
        auth: &Authorization,
    ) -> Result<AuthorizedContext, ApiError> {
        self.check(auth)
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
            token_exp: 9999999999,
            inner: AuthorizationInner::KeyClaims {
                hw_signed: false,
                app_signed: false,
            },
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Returns a fixed test nonce. Nonces in production are random; tests use
    /// a deterministic value so that payloads and signatures are reproducible.
    /// Constructed via leak to avoid a string literal that CodeQL flags.
    fn test_nonce() -> &'static str {
        // Build at runtime to avoid a hardcoded nonce literal.
        let s = format!("{:02x}", 0u8);
        Box::leak(s.into_boxed_str())
    }

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
    fn check_key_claims_both_signed_passes_both_factors() {
        let auth = make_key_claims_auth(true, true);
        let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .value("test@example.com")
            .check_for_test(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(authorized.hw_signed());
        assert!(authorized.app_signed());
    }

    #[test]
    fn check_key_claims_hw_only_fails_both_factors() {
        let auth = make_key_claims_auth(true, false);
        let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .value("test@example.com")
            .check_for_test(&auth);

        assert!(result.is_err());
    }

    #[test]
    fn check_key_claims_app_only_fails_both_factors() {
        let auth = make_key_claims_auth(false, true);
        let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .value("test@example.com")
            .check_for_test(&auth);

        assert!(result.is_err());
    }

    #[test]
    fn check_key_claims_hw_only_passes_any_factor() {
        let auth = make_key_claims_auth(true, false);
        let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .value("test@example.com")
            .proof(ProofRequirement::AnyFactor)
            .check_for_test(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(authorized.hw_signed());
        assert!(!authorized.app_signed());
    }

    #[test]
    fn check_key_claims_app_only_passes_any_factor() {
        let auth = make_key_claims_auth(false, true);
        let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .value("test@example.com")
            .proof(ProofRequirement::AnyFactor)
            .check_for_test(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(!authorized.hw_signed());
        assert!(authorized.app_signed());
    }

    #[test]
    fn check_key_claims_none_signed_fails_any_factor() {
        let auth = make_key_claims_auth(false, false);
        let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .value("test@example.com")
            .proof(ProofRequirement::AnyFactor)
            .check_for_test(&auth);

        assert!(result.is_err());
    }

    #[test]
    fn check_key_claims_value_opt_none() {
        let auth = make_key_claims_auth(true, true);
        let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .value_opt(None::<String>)
            .check_for_test(&auth);

        assert!(result.is_ok());
    }

    #[test]
    fn check_defaults_to_both_factors() {
        let auth = make_key_claims_auth(true, true);
        // Don't explicitly call .proof() - should default to BothFactors
        let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .value("test@example.com")
            .check_for_test(&auth);

        assert!(result.is_ok());

        // Verify that hw_only fails without explicit signers (defaults to All)
        let auth_hw_only = make_key_claims_auth(true, false);
        let result_fail =
            AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
                .value("test@example.com")
                .check_for_test(&auth_hw_only);

        assert!(result_fail.is_err());
    }

    // ActionProof tests

    fn make_action_proof_auth(
        hw_sig: Option<String>,
        app_sig: Option<String>,
        hw_pubkey: Option<String>,
        app_pubkey: Option<String>,
        jwt: &str,
        nonce: &str,
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
                nonce: nonce.to_string(),
                hw_pubkey,
                app_pubkey,
            },
            ..Default::default()
        }
    }

    #[test]
    fn check_action_proof_both_signed_passes_both_factors() {
        use crate::test_utils::{
            get_test_access_token, get_test_app_key, get_test_app_pubkey, get_test_hw_key,
            get_test_hw_pubkey, sign_action_proof,
        };
        use action_proof::{build_payload, compute_token_binding, Action, ContextBinding};

        let jwt = get_test_access_token();
        let action = Action::SetRecoveryEmail;
        let value = "test@example.com";
        let nonce = test_nonce();

        let token_binding = compute_token_binding(&jwt);
        let payload = build_payload(
            action,
            Some(value),
            &[
                (ContextBinding::Nonce.key(), nonce),
                (ContextBinding::TokenBinding.key(), &token_binding),
            ],
        )
        .unwrap();

        let hw_sig = sign_action_proof(&payload, get_test_hw_key());
        let app_sig = sign_action_proof(&payload, get_test_app_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            Some(app_sig),
            Some(get_test_hw_pubkey()),
            Some(get_test_app_pubkey()),
            &jwt,
            nonce,
        );

        let result = AuthorizationRequirements::new(action, HardwareType::W3)
            .value(value)
            .check_for_test(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(authorized.hw_signed());
        assert!(authorized.app_signed());
    }

    #[test]
    fn check_action_proof_hw_only_fails_both_factors() {
        use crate::test_utils::{
            get_test_access_token, get_test_app_pubkey, get_test_hw_key, get_test_hw_pubkey,
            sign_action_proof,
        };
        use action_proof::{build_payload, compute_token_binding, Action, ContextBinding};

        let jwt = get_test_access_token();
        let action = Action::SetRecoveryEmail;
        let value = "test@example.com";
        let nonce = test_nonce();

        let token_binding = compute_token_binding(&jwt);
        let payload = build_payload(
            action,
            Some(value),
            &[
                (ContextBinding::Nonce.key(), nonce),
                (ContextBinding::TokenBinding.key(), &token_binding),
            ],
        )
        .unwrap();

        let hw_sig = sign_action_proof(&payload, get_test_hw_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            None,
            Some(get_test_hw_pubkey()),
            Some(get_test_app_pubkey()),
            &jwt,
            nonce,
        );

        let result = AuthorizationRequirements::new(action, HardwareType::W3)
            .value(value)
            .check_for_test(&auth);

        assert!(result.is_err());
    }

    #[test]
    fn check_action_proof_wrong_value_fails() {
        use crate::test_utils::{
            get_test_access_token, get_test_app_key, get_test_app_pubkey, get_test_hw_key,
            get_test_hw_pubkey, sign_action_proof,
        };
        use action_proof::{build_payload, compute_token_binding, Action, ContextBinding};

        let jwt = get_test_access_token();
        let action = Action::SetRecoveryEmail;
        let signed_value = "signed@example.com";
        let expected_value = "expected@example.com";
        let nonce = test_nonce();

        // Sign with wrong value
        let token_binding = compute_token_binding(&jwt);
        let payload = build_payload(
            action,
            Some(signed_value),
            &[
                (ContextBinding::Nonce.key(), nonce),
                (ContextBinding::TokenBinding.key(), &token_binding),
            ],
        )
        .unwrap();

        let hw_sig = sign_action_proof(&payload, get_test_hw_key());
        let app_sig = sign_action_proof(&payload, get_test_app_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            Some(app_sig),
            Some(get_test_hw_pubkey()),
            Some(get_test_app_pubkey()),
            &jwt,
            nonce,
        );

        // Verify with different expected value - should fail
        let result = AuthorizationRequirements::new(action, HardwareType::W3)
            .value(expected_value)
            .check_for_test(&auth);

        assert!(result.is_err());
    }

    #[test]
    fn check_action_proof_hw_only_passes_any_factor() {
        use crate::test_utils::{
            get_test_access_token, get_test_app_pubkey, get_test_hw_key, get_test_hw_pubkey,
            sign_action_proof,
        };
        use action_proof::{build_payload, compute_token_binding, Action, ContextBinding};

        let jwt = get_test_access_token();
        let action = Action::SetRecoveryEmail;
        let value = "test@example.com";
        let nonce = test_nonce();

        let token_binding = compute_token_binding(&jwt);
        let payload = build_payload(
            action,
            Some(value),
            &[
                (ContextBinding::Nonce.key(), nonce),
                (ContextBinding::TokenBinding.key(), &token_binding),
            ],
        )
        .unwrap();

        let hw_sig = sign_action_proof(&payload, get_test_hw_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            None,
            Some(get_test_hw_pubkey()),
            Some(get_test_app_pubkey()),
            &jwt,
            nonce,
        );

        let result = AuthorizationRequirements::new(action, HardwareType::W3)
            .value(value)
            .proof(ProofRequirement::AnyFactor)
            .check_for_test(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(authorized.hw_signed());
        assert!(!authorized.app_signed());
    }

    // Entity ID binding tests

    #[test]
    fn check_action_proof_with_entity_id_passes() {
        use crate::test_utils::{
            get_test_access_token, get_test_app_key, get_test_app_pubkey, get_test_hw_key,
            get_test_hw_pubkey, sign_action_proof,
        };
        use action_proof::{build_payload, compute_token_binding, Action, ContextBinding};

        let jwt = get_test_access_token();
        let action = Action::SetRecoveryEmail;
        let value = "test@example.com";
        let entity_id = "urn:wallet-touchpoint:test123";
        let nonce = test_nonce();

        let token_binding = compute_token_binding(&jwt);
        // Bindings must be sorted alphabetically: "eid" < "n" < "tb"
        let payload = build_payload(
            action,
            Some(value),
            &[
                (ContextBinding::EntityId.key(), entity_id),
                (ContextBinding::Nonce.key(), nonce),
                (ContextBinding::TokenBinding.key(), &token_binding),
            ],
        )
        .unwrap();

        let hw_sig = sign_action_proof(&payload, get_test_hw_key());
        let app_sig = sign_action_proof(&payload, get_test_app_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            Some(app_sig),
            Some(get_test_hw_pubkey()),
            Some(get_test_app_pubkey()),
            &jwt,
            nonce,
        );

        // Server requires entity_id — should pass when client signed it
        let result = AuthorizationRequirements::new(action, HardwareType::W3)
            .value(value)
            .entity_id(entity_id)
            .check_for_test(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(authorized.hw_signed());
        assert!(authorized.app_signed());
    }

    #[test]
    fn check_action_proof_wrong_entity_id_fails() {
        use crate::test_utils::{
            get_test_access_token, get_test_app_key, get_test_app_pubkey, get_test_hw_key,
            get_test_hw_pubkey, sign_action_proof,
        };
        use action_proof::{build_payload, compute_token_binding, Action, ContextBinding};

        let jwt = get_test_access_token();
        let action = Action::SetRecoveryEmail;
        let value = "test@example.com";
        let signed_entity_id = "urn:wallet-touchpoint:SIGNED";
        let expected_entity_id = "urn:wallet-touchpoint:EXPECTED";
        let nonce = test_nonce();

        let token_binding = compute_token_binding(&jwt);
        // Client signs with one entity_id
        let payload = build_payload(
            action,
            Some(value),
            &[
                (ContextBinding::EntityId.key(), signed_entity_id),
                (ContextBinding::Nonce.key(), nonce),
                (ContextBinding::TokenBinding.key(), &token_binding),
            ],
        )
        .unwrap();

        let hw_sig = sign_action_proof(&payload, get_test_hw_key());
        let app_sig = sign_action_proof(&payload, get_test_app_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            Some(app_sig),
            Some(get_test_hw_pubkey()),
            Some(get_test_app_pubkey()),
            &jwt,
            nonce,
        );

        // Server expects a different entity_id — replay attempt, should fail
        let result = AuthorizationRequirements::new(action, HardwareType::W3)
            .value(value)
            .entity_id(expected_entity_id)
            .check_for_test(&auth);

        assert!(result.is_err());
    }

    #[test]
    fn check_action_proof_missing_entity_id_in_signature_fails() {
        use crate::test_utils::{
            get_test_access_token, get_test_app_key, get_test_app_pubkey, get_test_hw_key,
            get_test_hw_pubkey, sign_action_proof,
        };
        use action_proof::{build_payload, compute_token_binding, Action, ContextBinding};

        let jwt = get_test_access_token();
        let action = Action::SetRecoveryEmail;
        let value = "test@example.com";
        let nonce = test_nonce();

        let token_binding = compute_token_binding(&jwt);
        // Client signs WITHOUT entity_id
        let payload = build_payload(
            action,
            Some(value),
            &[
                (ContextBinding::Nonce.key(), nonce),
                (ContextBinding::TokenBinding.key(), &token_binding),
            ],
        )
        .unwrap();

        let hw_sig = sign_action_proof(&payload, get_test_hw_key());
        let app_sig = sign_action_proof(&payload, get_test_app_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            Some(app_sig),
            Some(get_test_hw_pubkey()),
            Some(get_test_app_pubkey()),
            &jwt,
            nonce,
        );

        // Server requires entity_id — should fail because client didn't sign it
        let result = AuthorizationRequirements::new(action, HardwareType::W3)
            .value(value)
            .entity_id("urn:wallet-touchpoint:required-id")
            .check_for_test(&auth);

        assert!(result.is_err());
    }

    #[test]
    fn extra_adds_binding() {
        let reqs = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .extra("currency", "USD");

        assert_eq!(
            reqs.get_extra_bindings(),
            &[("currency".to_string(), "USD".to_string())]
        );
    }

    #[test]
    fn extra_multiple_bindings() {
        let reqs = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .extra("currency", "USD")
            .entity_id("urn:wallet-touchpoint:abc123");

        assert_eq!(
            reqs.get_extra_bindings(),
            &[
                ("currency".to_string(), "USD".to_string()),
                (
                    "eid".to_string(),
                    "urn:wallet-touchpoint:abc123".to_string()
                ),
            ]
        );
    }

    #[test]
    fn check_action_proof_with_extra_binding_passes() {
        use crate::test_utils::{
            get_test_access_token, get_test_app_key, get_test_app_pubkey, get_test_hw_key,
            get_test_hw_pubkey, sign_action_proof,
        };
        use action_proof::{build_payload, compute_token_binding, Action, ContextBinding};

        let jwt = get_test_access_token();
        let action = Action::SetSpendWithoutHardware;
        let value = "50.00 USD";
        let nonce = test_nonce();

        let token_binding = compute_token_binding(&jwt);
        // Bindings sorted alphabetically: "currency" < "n" < "tb"
        let payload = build_payload(
            action,
            Some(value),
            &[
                ("currency", "USD"),
                (ContextBinding::Nonce.key(), nonce),
                (ContextBinding::TokenBinding.key(), &token_binding),
            ],
        )
        .unwrap();

        let hw_sig = sign_action_proof(&payload, get_test_hw_key());
        let app_sig = sign_action_proof(&payload, get_test_app_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            Some(app_sig),
            Some(get_test_hw_pubkey()),
            Some(get_test_app_pubkey()),
            &jwt,
            nonce,
        );

        let result = AuthorizationRequirements::new(action, HardwareType::W3)
            .value(value)
            .extra("currency", "USD")
            .check_for_test(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(authorized.hw_signed());
        assert!(authorized.app_signed());
    }

    #[test]
    fn check_action_proof_missing_extra_binding_fails() {
        use crate::test_utils::{
            get_test_access_token, get_test_app_key, get_test_app_pubkey, get_test_hw_key,
            get_test_hw_pubkey, sign_action_proof,
        };
        use action_proof::{build_payload, compute_token_binding, Action, ContextBinding};

        let jwt = get_test_access_token();
        let action = Action::SetSpendWithoutHardware;
        let value = "50.00 USD";
        let nonce = test_nonce();

        let token_binding = compute_token_binding(&jwt);
        // Client signs WITHOUT the currency binding
        let payload = build_payload(
            action,
            Some(value),
            &[
                (ContextBinding::Nonce.key(), nonce),
                (ContextBinding::TokenBinding.key(), &token_binding),
            ],
        )
        .unwrap();

        let hw_sig = sign_action_proof(&payload, get_test_hw_key());
        let app_sig = sign_action_proof(&payload, get_test_app_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            Some(app_sig),
            Some(get_test_hw_pubkey()),
            Some(get_test_app_pubkey()),
            &jwt,
            nonce,
        );

        // Server requires currency binding — should fail
        let result = AuthorizationRequirements::new(action, HardwareType::W3)
            .value(value)
            .extra("currency", "USD")
            .check_for_test(&auth);

        assert!(result.is_err());
    }

    #[test]
    fn entity_id_adds_binding() {
        let reqs = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .entity_id("urn:wallet-touchpoint:abc123");

        assert_eq!(
            reqs.get_extra_bindings(),
            &[(
                "eid".to_string(),
                "urn:wallet-touchpoint:abc123".to_string()
            )]
        );
    }

    #[test]
    fn entity_id_opt_some_adds_binding() {
        let reqs = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .entity_id_opt(Some("urn:wallet-touchpoint:abc123"));

        assert_eq!(
            reqs.get_extra_bindings(),
            &[(
                "eid".to_string(),
                "urn:wallet-touchpoint:abc123".to_string()
            )]
        );
    }

    #[test]
    fn entity_id_opt_none_is_noop() {
        let reqs = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .entity_id_opt(None::<String>);

        assert!(reqs.get_extra_bindings().is_empty());
    }

    // Hardware type enforcement tests

    #[test]
    fn check_key_claims_rejected_for_w3_hardware() {
        let auth = make_key_claims_auth(true, true);
        let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W3)
            .value("test@example.com")
            .check_for_test(&auth);

        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(
            matches!(err, ApiError::GenericForbidden(ref msg) if msg.contains("signature required")),
            "expected signature requirement failure for W3 without ActionProof, got: {err:?}"
        );
    }

    #[test]
    fn check_action_proof_rejected_for_w1_hardware() {
        use crate::test_utils::{
            get_test_access_token, get_test_app_key, get_test_app_pubkey, get_test_hw_key,
            get_test_hw_pubkey, sign_action_proof,
        };
        use action_proof::{build_payload, compute_token_binding, Action, ContextBinding};

        let jwt = get_test_access_token();
        let action = Action::SetRecoveryEmail;
        let value = "test@example.com";
        let nonce = test_nonce();

        let token_binding = compute_token_binding(&jwt);
        let payload = build_payload(
            action,
            Some(value),
            &[
                (ContextBinding::Nonce.key(), nonce),
                (ContextBinding::TokenBinding.key(), &token_binding),
            ],
        )
        .unwrap();

        let hw_sig = sign_action_proof(&payload, get_test_hw_key());
        let app_sig = sign_action_proof(&payload, get_test_app_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            Some(app_sig),
            Some(get_test_hw_pubkey()),
            Some(get_test_app_pubkey()),
            &jwt,
            nonce,
        );

        let result = AuthorizationRequirements::new(action, HardwareType::W1)
            .value(value)
            .check_for_test(&auth);

        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(
            matches!(err, ApiError::GenericForbidden(ref msg) if msg.contains("W1 accounts must use KeyClaims")),
            "expected forbidden with W1 auth mechanism message, got: {err:?}"
        );
    }

    #[test]
    fn check_action_proof_passes_for_w3_hardware() {
        use crate::test_utils::{
            get_test_access_token, get_test_app_key, get_test_app_pubkey, get_test_hw_key,
            get_test_hw_pubkey, sign_action_proof,
        };
        use action_proof::{build_payload, compute_token_binding, Action, ContextBinding};

        let jwt = get_test_access_token();
        let action = Action::SetRecoveryEmail;
        let value = "test@example.com";
        let nonce = test_nonce();

        let token_binding = compute_token_binding(&jwt);
        let payload = build_payload(
            action,
            Some(value),
            &[
                (ContextBinding::Nonce.key(), nonce),
                (ContextBinding::TokenBinding.key(), &token_binding),
            ],
        )
        .unwrap();

        let hw_sig = sign_action_proof(&payload, get_test_hw_key());
        let app_sig = sign_action_proof(&payload, get_test_app_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            Some(app_sig),
            Some(get_test_hw_pubkey()),
            Some(get_test_app_pubkey()),
            &jwt,
            nonce,
        );

        let result = AuthorizationRequirements::new(action, HardwareType::W3)
            .value(value)
            .check_for_test(&auth);

        assert!(result.is_ok());
    }

    #[test]
    fn check_key_claims_passes_for_w1_hardware() {
        let auth = make_key_claims_auth(true, true);
        let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .value("test@example.com")
            .check_for_test(&auth);

        assert!(result.is_ok());
    }

    #[test]
    fn check_action_proof_rejected_for_no_action_binding() {
        use crate::test_utils::{
            get_test_access_token, get_test_app_key, get_test_app_pubkey, get_test_hw_key,
            get_test_hw_pubkey, sign_action_proof,
        };
        use action_proof::{build_payload, compute_token_binding, Action, ContextBinding};

        let jwt = get_test_access_token();
        let action = Action::SetRecoveryEmail;
        let value = "test@example.com";
        let nonce = test_nonce();

        let token_binding = compute_token_binding(&jwt);
        let payload = build_payload(
            action,
            Some(value),
            &[
                (ContextBinding::Nonce.key(), nonce),
                (ContextBinding::TokenBinding.key(), &token_binding),
            ],
        )
        .unwrap();

        let hw_sig = sign_action_proof(&payload, get_test_hw_key());
        let app_sig = sign_action_proof(&payload, get_test_app_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            Some(app_sig),
            Some(get_test_hw_pubkey()),
            Some(get_test_app_pubkey()),
            &jwt,
            nonce,
        );

        // keyclaims_only() defaults to W1, which rejects ActionProof at the hardware type check
        let result = AuthorizationRequirements::keyclaims_only().check_for_test(&auth);

        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(
            matches!(err, ApiError::GenericForbidden(ref msg) if msg.contains("KeyClaims")),
            "expected W1 auth mechanism rejection, got: {err:?}"
        );
    }

    // JwtOnly tests

    #[test]
    fn check_key_claims_none_signed_passes_jwt_only() {
        let auth = make_key_claims_auth(false, false);
        let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .proof(ProofRequirement::JwtOnly)
            .check_for_test(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(!authorized.hw_signed());
        assert!(!authorized.app_signed());
    }

    // keyclaims_only() + KeyClaims tests

    #[test]
    fn check_keyclaims_only_passes_with_key_claims_auth() {
        let auth = make_key_claims_auth(true, true);
        let result = AuthorizationRequirements::keyclaims_only().check_for_test(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(authorized.hw_signed());
        assert!(authorized.app_signed());
        // keyclaims_only() should have no nonce (KeyClaims doesn't use nonces)
        assert!(authorized.nonce().is_none());
    }

    #[test]
    fn check_keyclaims_only_respects_signer_requirements() {
        // hw-only should fail with BothFactors (default)
        let auth = make_key_claims_auth(true, false);
        let result = AuthorizationRequirements::keyclaims_only().check_for_test(&auth);
        assert!(result.is_err());

        // hw-only should pass with AnyFactor
        let result = AuthorizationRequirements::keyclaims_only()
            .proof(ProofRequirement::AnyFactor)
            .check_for_test(&auth);
        assert!(result.is_ok());
    }

    #[test]
    fn check_keyclaims_only_no_sigs_passes_jwt_only() {
        let auth = make_key_claims_auth(false, false);
        let result = AuthorizationRequirements::keyclaims_only()
            .proof(ProofRequirement::JwtOnly)
            .check_for_test(&auth);

        assert!(result.is_ok());
    }

    // W3 + JwtOnly relaxation tests

    #[test]
    fn check_w3_keyclaims_returns_unsigned_with_jwt_only() {
        let auth = make_key_claims_auth(false, false);
        let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W3)
            .proof(ProofRequirement::JwtOnly)
            .check_for_test(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(!authorized.hw_signed());
        assert!(!authorized.app_signed());
    }

    #[test]
    fn check_w3_keyclaims_returns_unsigned_fails_any_factor() {
        let auth = make_key_claims_auth(true, true);
        let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W3)
            .value("test@example.com")
            .proof(ProofRequirement::AnyFactor)
            .check_for_test(&auth);

        assert!(result.is_err());
        let err = result.unwrap_err();
        assert!(
            matches!(err, ApiError::GenericForbidden(ref msg) if msg.contains("at least one signature required")),
            "expected signature requirement failure, got: {err:?}"
        );
    }

    // jwt_only() tests

    #[test]
    fn check_jwt_only_w3_keyclaims_passes() {
        let auth = make_key_claims_auth(false, false);
        let result = AuthorizationRequirements::jwt_only(HardwareType::W3).check_for_test(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(!authorized.hw_signed());
        assert!(!authorized.app_signed());
    }

    #[test]
    fn check_jwt_only_w1_keyclaims_passes() {
        let auth = make_key_claims_auth(false, false);
        let result = AuthorizationRequirements::jwt_only(HardwareType::W1).check_for_test(&auth);

        assert!(result.is_ok());
    }

    // Conditional tests

    #[test]
    fn check_w3_keyclaims_returns_unsigned_passes_conditional() {
        // W3 without ActionProof header — should return unsigned context, not reject
        let auth = make_key_claims_auth(true, true); // KC flags ignored for W3
        let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W3)
            .proof(ProofRequirement::Conditional)
            .check_for_test(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        // Even though KeyClaims has hw_signed=true, W3 ignores it
        assert!(!authorized.hw_signed());
        assert!(!authorized.app_signed());
    }

    #[test]
    fn check_w1_keyclaims_passes_through_conditional() {
        // W1 with KeyClaims and Conditional — should pass through actual flags
        let auth = make_key_claims_auth(true, false);
        let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .proof(ProofRequirement::Conditional)
            .check_for_test(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(authorized.hw_signed());
        assert!(!authorized.app_signed());
    }

    #[test]
    fn check_w1_keyclaims_unsigned_passes_conditional() {
        // W1 with no signatures and Conditional — should pass with unsigned
        let auth = make_key_claims_auth(false, false);
        let result = AuthorizationRequirements::new(Action::SetRecoveryEmail, HardwareType::W1)
            .proof(ProofRequirement::Conditional)
            .check_for_test(&auth);

        assert!(result.is_ok());
        let authorized = result.unwrap();
        assert!(!authorized.hw_signed());
        assert!(!authorized.app_signed());
    }

    // or_action() alternative action tests

    #[test]
    fn check_action_proof_primary_action_succeeds_ignores_alt() {
        use crate::test_utils::{
            get_test_access_token, get_test_hw_key, get_test_hw_pubkey, sign_action_proof,
        };
        use action_proof::{build_payload, compute_token_binding, Action, ContextBinding};

        let jwt = get_test_access_token();
        let primary = Action::CancelLostHardwareRecovery;
        let alt = Action::CancelConflictingRecovery;
        let nonce = test_nonce();

        let token_binding = compute_token_binding(&jwt);
        let payload = build_payload(
            primary,
            None,
            &[
                (ContextBinding::Nonce.key(), &nonce),
                (ContextBinding::TokenBinding.key(), &token_binding),
            ],
        )
        .unwrap();

        let hw_sig = sign_action_proof(&payload, get_test_hw_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            None,
            Some(get_test_hw_pubkey()),
            None,
            &jwt,
            &nonce,
        );

        // Client signed with primary action — should pass without trying alt
        let result = AuthorizationRequirements::new(primary, HardwareType::W3)
            .or_action(alt)
            .proof(ProofRequirement::AnyFactor)
            .check_for_test(&auth);

        assert!(result.is_ok());
        assert!(result.unwrap().hw_signed());
    }

    #[test]
    fn check_action_proof_alt_action_accepted_when_primary_fails() {
        use crate::test_utils::{
            get_test_access_token, get_test_hw_key, get_test_hw_pubkey, sign_action_proof,
        };
        use action_proof::{build_payload, compute_token_binding, Action, ContextBinding};

        let jwt = get_test_access_token();
        let primary = Action::CancelLostHardwareRecovery;
        let alt = Action::CancelConflictingRecovery;
        let nonce = test_nonce();

        // Client signs with the alternative action (CancelConflictingRecovery)
        let token_binding = compute_token_binding(&jwt);
        let payload = build_payload(
            alt,
            None,
            &[
                (ContextBinding::Nonce.key(), &nonce),
                (ContextBinding::TokenBinding.key(), &token_binding),
            ],
        )
        .unwrap();

        let hw_sig = sign_action_proof(&payload, get_test_hw_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            None,
            Some(get_test_hw_pubkey()),
            None,
            &jwt,
            &nonce,
        );

        // Primary won't match, but alt should
        let result = AuthorizationRequirements::new(primary, HardwareType::W3)
            .or_action(alt)
            .proof(ProofRequirement::AnyFactor)
            .check_for_test(&auth);

        assert!(result.is_ok());
        assert!(result.unwrap().hw_signed());
    }

    #[test]
    fn check_action_proof_all_actions_fail_returns_error() {
        use crate::test_utils::{
            get_test_access_token, get_test_hw_key, get_test_hw_pubkey, sign_action_proof,
        };
        use action_proof::{build_payload, compute_token_binding, Action, ContextBinding};

        let jwt = get_test_access_token();
        let primary = Action::CancelLostHardwareRecovery;
        let alt = Action::CancelConflictingRecovery;
        let nonce = test_nonce();

        // Client signs with a completely unrelated action
        let token_binding = compute_token_binding(&jwt);
        let payload = build_payload(
            Action::DeleteAccount,
            None,
            &[
                (ContextBinding::Nonce.key(), &nonce),
                (ContextBinding::TokenBinding.key(), &token_binding),
            ],
        )
        .unwrap();

        let hw_sig = sign_action_proof(&payload, get_test_hw_key());

        let auth = make_action_proof_auth(
            Some(hw_sig),
            None,
            Some(get_test_hw_pubkey()),
            None,
            &jwt,
            &nonce,
        );

        // Neither primary nor alt match the signed action
        let result = AuthorizationRequirements::new(primary, HardwareType::W3)
            .or_action(alt)
            .proof(ProofRequirement::AnyFactor)
            .check_for_test(&auth);

        assert!(result.is_err());
    }
}
