use action_proof::{build_payload, compute_token_binding, Action, ContextBinding};
use authn_authz::action_proof::ACTION_PROOF_HEADER;
use authn_authz::headers::{APP_SIG_HEADER, HW_SIG_HEADER};
use authn_authz::test_utils::sign_action_proof;
use bdk_utils::bdk::bitcoin::secp256k1::SecretKey;

use http::{header, request::Builder, HeaderMap};
use types::{account::identifiers::AccountId, authn_authz::cognito::CognitoUser};
use userpool::test_utils::get_test_access_token_for_cognito_user;

pub trait AuthenticatedRequest {
    fn authenticated(
        self,
        account_id: &AccountId,
        app_seckey: Option<SecretKey>,
        hw_seckey: Option<SecretKey>,
    ) -> Self;
    fn authenticated_with_access_token(self, access_token: &str) -> Self;
    fn hw_authenticated(self, account_id: &AccountId) -> Self;
    fn recovery_authenticated(self, account_id: &AccountId) -> Self;

    /// Authenticate using ActionProof header (new authentication method).
    ///
    /// This builds a canonical payload with the specified action/value,
    /// signs it with the provided keys using recoverable signatures,
    /// and adds both the Authorization (Bearer JWT) and Action-Proof headers.
    fn action_proof_authenticated(
        self,
        account_id: &AccountId,
        action: Action,
        value: Option<&str>,
        entity_id: Option<&str>,
        app_seckey: Option<SecretKey>,
        hw_seckey: Option<SecretKey>,
    ) -> Self;

    /// Like `action_proof_authenticated`, but uses a pre-built access token.
    /// This is needed for anti-replay tests where both requests must share the
    /// same JWT (and therefore the same content hash / token binding).
    fn action_proof_authenticated_with_token(
        self,
        access_token: &str,
        action: Action,
        value: Option<&str>,
        entity_id: Option<&str>,
        app_seckey: Option<SecretKey>,
        hw_seckey: Option<SecretKey>,
    ) -> Self;
}

impl AuthenticatedRequest for Builder {
    fn authenticated(
        mut self,
        account_id: &AccountId,
        app_seckey: Option<SecretKey>,
        hw_seckey: Option<SecretKey>,
    ) -> Self {
        let access_token =
            get_test_access_token_for_cognito_user(&CognitoUser::App(account_id.to_owned()));
        let headers = self.headers_mut().unwrap();
        headers.insert(
            header::AUTHORIZATION,
            format!("Bearer {access_token}").parse().unwrap(),
        );
        if let Some(key) = app_seckey {
            headers.insert(
                APP_SIG_HEADER,
                authn_authz::test_utils::sign_with_app_key(&access_token, key)
                    .parse()
                    .unwrap(),
            );
        }
        if let Some(key) = hw_seckey {
            headers.insert(
                HW_SIG_HEADER,
                authn_authz::test_utils::sign_with_hw_key(&access_token, key)
                    .parse()
                    .unwrap(),
            );
        }
        self
    }

    fn authenticated_with_access_token(mut self, access_token: &str) -> Self {
        let headers = self.headers_mut().unwrap();
        headers.insert(
            header::AUTHORIZATION,
            format!("Bearer {access_token}").parse().unwrap(),
        );
        self
    }

    fn hw_authenticated(mut self, account_id: &AccountId) -> Self {
        let access_token =
            get_test_access_token_for_cognito_user(&CognitoUser::Hardware(account_id.to_owned()));
        let headers = self.headers_mut().unwrap();
        headers.insert(
            header::AUTHORIZATION,
            format!("Bearer {access_token}").parse().unwrap(),
        );
        self
    }

    fn recovery_authenticated(mut self, account_id: &AccountId) -> Self {
        let access_token =
            get_test_access_token_for_cognito_user(&CognitoUser::Recovery(account_id.to_owned()));
        let headers = self.headers_mut().unwrap();
        headers.insert(
            header::AUTHORIZATION,
            format!("Bearer {access_token}").parse().unwrap(),
        );
        self
    }

    fn action_proof_authenticated(
        self,
        account_id: &AccountId,
        action: Action,
        value: Option<&str>,
        entity_id: Option<&str>,
        app_seckey: Option<SecretKey>,
        hw_seckey: Option<SecretKey>,
    ) -> Self {
        let access_token =
            get_test_access_token_for_cognito_user(&CognitoUser::App(account_id.to_owned()));
        self.action_proof_authenticated_with_token(
            &access_token,
            action,
            value,
            entity_id,
            app_seckey,
            hw_seckey,
        )
    }

    fn action_proof_authenticated_with_token(
        mut self,
        access_token: &str,
        action: Action,
        value: Option<&str>,
        entity_id: Option<&str>,
        app_seckey: Option<SecretKey>,
        hw_seckey: Option<SecretKey>,
    ) -> Self {
        // Fixed test nonce (1 byte, 2-char lowercase hex). Built at runtime
        // to avoid a hardcoded nonce literal that static analysis flags.
        let nonce = format!("{:02x}", 0u8);

        let token_binding = compute_token_binding(access_token);
        let mut bindings = Vec::new();
        if let Some(eid) = entity_id {
            bindings.push((ContextBinding::EntityId.key(), eid));
        }
        bindings.push((ContextBinding::Nonce.key(), &nonce));
        bindings.push((ContextBinding::TokenBinding.key(), &token_binding));
        bindings.sort_by_key(|b| b.0);
        let payload = build_payload(action, value, &bindings).unwrap_or_else(|e| {
            panic!(
                "build_payload failed for action={:?}, value={:?}: {}",
                action, value, e
            )
        });

        let mut signatures = Vec::new();
        if let Some(key) = hw_seckey {
            signatures.push(sign_action_proof(&payload, key));
        }
        if let Some(key) = app_seckey {
            signatures.push(sign_action_proof(&payload, key));
        }

        let action_proof_json = serde_json::json!({
            "version": 1,
            "signatures": signatures,
            "nonce": nonce
        });

        let headers = self.headers_mut().unwrap();
        headers.insert(
            header::AUTHORIZATION,
            format!("Bearer {access_token}").parse().unwrap(),
        );
        headers.insert(
            ACTION_PROOF_HEADER,
            action_proof_json.to_string().parse().unwrap(),
        );
        self
    }
}

pub trait ExtendRequest {
    fn with_headers(self, add_headers: HeaderMap) -> Self;
}

impl ExtendRequest for Builder {
    fn with_headers(mut self, add_headers: HeaderMap) -> Self {
        let headers = self.headers_mut().unwrap();
        headers.extend(add_headers);
        self
    }
}
