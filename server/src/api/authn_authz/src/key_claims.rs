use std::str::FromStr;

use async_trait::async_trait;
pub use authn_authz_utils::{extract_account_id, get_jwt_from_headers, get_user_name_from_jwt};
use axum::extract::{FromRef, FromRequestParts};
use axum::http::request::Parts;
use axum::http::{HeaderMap, HeaderValue, StatusCode};
use secp256k1::ecdsa::Signature;
use secp256k1::hashes::{sha256, Hash};
use secp256k1::{Message, PublicKey, Secp256k1};
use types::account::identifiers::AccountId;
use types::authn_authz::cognito::{CognitoUser, CognitoUsername};
use userpool::userpool::{UserPoolError, UserPoolService};

pub const APP_SIG_HEADER: &str = "X-App-Signature";
pub const HW_SIG_HEADER: &str = "X-Hw-Signature";

#[derive(Debug, Clone)]
pub struct KeyClaims {
    pub account_id: String,
    pub username: CognitoUsername,
    pub app_signed: bool,
    pub hw_signed: bool,
}

#[async_trait]
impl<S> FromRequestParts<S> for KeyClaims
where
    S: Send + Sync,
    UserPoolService: FromRef<S>,
{
    type Rejection = StatusCode;

    async fn from_request_parts(parts: &mut Parts, state: &S) -> Result<Self, Self::Rejection> {
        let user_pool = UserPoolService::from_ref(state);
        let headers = parts.headers.clone();
        let app_sig_header = headers.get(APP_SIG_HEADER).cloned();
        let hw_sig_header = headers.get(HW_SIG_HEADER).cloned();

        let jwt = get_jwt_from_headers(&headers).ok_or(StatusCode::UNAUTHORIZED)?;

        let username = get_user_name_from_jwt(&jwt).ok_or(StatusCode::UNAUTHORIZED)?;
        let cognito_user =
            CognitoUser::from_str(username.as_ref()).map_err(|_| StatusCode::UNAUTHORIZED)?;
        let account_id = match cognito_user {
            CognitoUser::App(account_id) | CognitoUser::Hardware(account_id) => account_id,
            CognitoUser::Recovery(account_id) => {
                return Ok(Self {
                    account_id: account_id.to_string(),
                    username,
                    app_signed: false,
                    hw_signed: false,
                });
            }
        };
        let (app_pubkey, hw_pubkey, _) =
            get_pubkeys_from_cognito(&user_pool, account_id.clone()).await?;

        if authentication_factor_keys_are_equal(app_pubkey.as_deref(), hw_pubkey.as_deref()) {
            crate::metrics::KEY_CLAIMS_EQUAL_REGISTERED_FACTORS.add(1, &[]);
        }

        let app_signed = app_sig_header
            .and_then(|value| value.to_str().ok().map(String::from))
            .is_some_and(|app_sig_header| match app_pubkey {
                Some(app_pubkey) => verify_signature(&app_sig_header, jwt.clone(), app_pubkey),
                None => false,
            });

        let hw_signed = hw_sig_header
            .and_then(|value| value.to_str().ok().map(String::from))
            .is_some_and(|hw_sig_header| match hw_pubkey {
                Some(hw_pubkey) => verify_signature(&hw_sig_header, jwt.clone(), hw_pubkey),
                None => false,
            });

        Ok(Self {
            account_id: account_id.to_string(),
            username,
            app_signed,
            hw_signed,
        })
    }
}

fn authentication_factor_keys_are_equal(app_pubkey: Option<&str>, hw_pubkey: Option<&str>) -> bool {
    let (Some(app_pubkey), Some(hw_pubkey)) = (app_pubkey, hw_pubkey) else {
        return false;
    };

    match (
        PublicKey::from_str(app_pubkey),
        PublicKey::from_str(hw_pubkey),
    ) {
        (Ok(app_pubkey), Ok(hw_pubkey)) => app_pubkey == hw_pubkey,
        _ => false,
    }
}

pub fn verify_signature(signature: &str, message: String, pubkey: String) -> bool {
    let secp = Secp256k1::verification_only();
    let digest = sha256::Hash::hash(message.as_bytes());
    let message = Message::from_digest(digest.to_byte_array());
    let Ok(signature) = Signature::from_str(signature) else {
        return false;
    };
    let Ok(pubkey) = PublicKey::from_str(&pubkey) else {
        return false;
    };
    secp.verify_ecdsa(&message, &signature, &pubkey).is_ok()
}

pub async fn get_pubkeys_from_cognito(
    user_pool_service: &UserPoolService,
    account_id: AccountId,
) -> Result<(Option<String>, Option<String>, Option<String>), StatusCode> {
    user_pool_service
        .get_pubkeys_for_account(account_id)
        .await
        .map_err(|err| match err {
            UserPoolError::NonExistentUser => StatusCode::NOT_FOUND,
            _ => StatusCode::INTERNAL_SERVER_ERROR,
        })
}

#[cfg(test)]
mod tests {
    use std::str::FromStr;

    use axum::body::Body;
    use axum::extract::FromRequestParts;
    use axum::http::Request;
    use secp256k1::{PublicKey, Secp256k1};
    use types::account::identifiers::AccountId;
    use types::authn_authz::cognito::CognitoUsername;
    use userpool::userpool::{CognitoMode, UserPoolService};

    use crate::key_claims::{
        authentication_factor_keys_are_equal, KeyClaims, APP_SIG_HEADER, HW_SIG_HEADER,
    };
    use crate::test_utils::{
        get_test_access_token, get_test_app_key, get_test_app_pubkey, sign_with_test_app_key,
        sign_with_test_hw_key, TEST_USERNAME,
    };

    #[test]
    fn test_that_username_is_parsed_out_of_jwt() {
        let serialized_jwt = get_test_access_token();
        let username = super::get_user_name_from_jwt(&serialized_jwt);
        assert_eq!(
            username,
            Some(
                CognitoUsername::from_str("urn:wallet-account:000000000000000000000000000-app")
                    .expect("Could not parse username")
            )
        );
    }

    #[test]
    fn test_equal_authentication_factor_keys_are_detected_semantically() {
        let app_pubkey = get_test_app_pubkey();
        let uncompressed_app_pubkey = PublicKey::from_str(&app_pubkey)
            .unwrap()
            .serialize_uncompressed();
        let uncompressed_app_pubkey = hex::encode(uncompressed_app_pubkey);

        assert!(authentication_factor_keys_are_equal(
            Some(&app_pubkey),
            Some(&uncompressed_app_pubkey),
        ));
    }

    #[tokio::test]
    async fn test_equal_registered_keys_preserve_legacy_proof_flags() {
        let access_token = get_test_access_token();
        let signature = sign_with_test_app_key(&access_token);
        let account_id = AccountId::from_str(TEST_USERNAME).unwrap();
        let app_pubkey = get_test_app_key().public_key(&Secp256k1::new());
        let userpool = UserPoolService::new(
            userpool::userpool::Config {
                cognito: CognitoMode::Test,
            }
            .to_connection()
            .await,
        );
        userpool
            .create_or_update_account_users_if_necessary(
                &account_id,
                Some(app_pubkey),
                Some(app_pubkey),
                None,
            )
            .await
            .unwrap();

        let request = Request::builder()
            .uri("http://example.com/")
            .header("Authorization", format!("Bearer {}", access_token))
            .header(APP_SIG_HEADER, &signature)
            .header(HW_SIG_HEADER, signature)
            .body(Body::empty())
            .unwrap();
        let (mut request_parts, _) = request.into_parts();

        let key_claims = KeyClaims::from_request_parts(&mut request_parts, &userpool)
            .await
            .unwrap();

        assert!(key_claims.app_signed);
        assert!(key_claims.hw_signed);
    }

    #[tokio::test]
    async fn test_app_signature_in_header() {
        let access_token = get_test_access_token();
        let app_signature = sign_with_test_app_key(&access_token);

        let request = Request::builder()
            .uri("http://example.com/")
            .header("Authorization", format!("Bearer {}", access_token))
            .header(APP_SIG_HEADER, app_signature)
            .body(Body::empty())
            .unwrap();

        let (mut request_parts, _) = request.into_parts();

        let userpool = UserPoolService::new(
            userpool::userpool::Config {
                cognito: CognitoMode::Test,
            }
            .to_connection()
            .await,
        );

        let key_proof = KeyClaims::from_request_parts(&mut request_parts, &userpool)
            .await
            .unwrap();
        assert_eq!(key_proof.account_id, TEST_USERNAME);
        assert!(key_proof.app_signed);
        assert!(!key_proof.hw_signed);
    }

    #[tokio::test]
    async fn test_hw_signature_in_header() {
        let access_token = get_test_access_token();
        let hw_signature = sign_with_test_hw_key(&access_token);

        let request = Request::builder()
            .uri("http://example.com/")
            .header("Authorization", format!("Bearer {}", access_token))
            .header(HW_SIG_HEADER, hw_signature)
            .body(Body::empty())
            .unwrap();

        let (mut request_parts, _) = request.into_parts();

        let userpool = UserPoolService::new(
            userpool::userpool::Config {
                cognito: CognitoMode::Test,
            }
            .to_connection()
            .await,
        );

        let key_proof = KeyClaims::from_request_parts(&mut request_parts, &userpool)
            .await
            .unwrap();
        assert_eq!(key_proof.account_id, TEST_USERNAME);
        assert!(!key_proof.app_signed);
        assert!(key_proof.hw_signed);
    }

    #[tokio::test]
    async fn test_both_signatures_in_header() {
        let access_token = get_test_access_token();
        let app_signature = sign_with_test_app_key(&access_token);
        let hw_signature = sign_with_test_hw_key(&access_token);

        let request = Request::builder()
            .uri("http://example.com/")
            .header("Authorization", format!("Bearer {}", access_token))
            .header(APP_SIG_HEADER, app_signature)
            .header(HW_SIG_HEADER, hw_signature)
            .body(Body::empty())
            .unwrap();

        let (mut request_parts, _) = request.into_parts();

        let userpool = UserPoolService::new(
            userpool::userpool::Config {
                cognito: CognitoMode::Test,
            }
            .to_connection()
            .await,
        );

        let key_proof = KeyClaims::from_request_parts(&mut request_parts, &userpool)
            .await
            .unwrap();
        assert_eq!(key_proof.account_id, TEST_USERNAME);
        assert!(key_proof.app_signed);
        assert!(key_proof.hw_signed);
    }
}
