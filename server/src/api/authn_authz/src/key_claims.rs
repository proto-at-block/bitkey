use std::str::FromStr;

use async_trait::async_trait;
use axum::extract::{FromRef, FromRequestParts};
use axum::http::request::Parts;
use axum::http::{header, HeaderMap, HeaderValue, StatusCode};
use jsonwebtoken::{DecodingKey, Validation};
use secp256k1::ecdsa::Signature;
use secp256k1::hashes::sha256;
use secp256k1::{Message, PublicKey, Secp256k1};
use types::account::identifiers::AccountId;
use types::authn_authz::cognito::{CognitoUser, CognitoUsername};
use types::authn_authz::AccessTokenClaims;
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

pub fn verify_signature(signature: &str, message: String, pubkey: String) -> bool {
    let secp = Secp256k1::verification_only();
    let message = Message::from_hashed_data::<sha256::Hash>(message.as_bytes());
    let Ok(signature) = Signature::from_str(signature) else {
        return false;
    };
    let Ok(pubkey) = PublicKey::from_str(&pubkey) else {
        return false;
    };
    secp.verify_ecdsa(&message, &signature, &pubkey).is_ok()
}

pub fn extract_account_id(headers: &HeaderMap<HeaderValue>) -> Option<String> {
    get_jwt_from_headers(headers)
        .and_then(|jwt| get_user_name_from_jwt(&jwt))
        .and_then(|u| CognitoUser::from_str(u.as_ref()).ok())
        .map(|cognito_user| cognito_user.get_account_id().to_string())
}

pub fn get_user_name_from_jwt(jwt: &str) -> Option<CognitoUsername> {
    let mut validation = Validation::new(jsonwebtoken::Algorithm::RS256);
    // We already validate the signature on the token in [authorizer.rs:23].
    // By the time we get to here, a 401 has already been returned if the signature is invalid.
    // Because cognito rotates in signing keys every 24 hours, we don't want to have to fetch
    // and cache it in multiple places.
    // Since we're just pulling out the user name, we skip signature validation here.
    validation.insecure_disable_signature_validation();
    if let Ok(token) =
        jsonwebtoken::decode::<AccessTokenClaims>(jwt, &DecodingKey::from_secret(&[]), &validation)
    {
        Some(token.claims.username)
    } else {
        None
    }
}

fn get_jwt_from_headers(headers: &HeaderMap<HeaderValue>) -> Option<String> {
    headers
        .get(header::AUTHORIZATION)
        .cloned()
        .and_then(|value| value.to_str().ok().map(String::from))
        // The contents of the `Authorization` header should be "Bearer [JWT token]" so strip out out the "Bearer " prefix
        .and_then(|value| value.strip_prefix("Bearer ").map(String::from))
}

async fn get_pubkeys_from_cognito(
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
    use types::authn_authz::cognito::CognitoUsername;
    use userpool::userpool::{CognitoMode, UserPoolService};

    use crate::key_claims::{KeyClaims, APP_SIG_HEADER, HW_SIG_HEADER};
    use crate::test_utils::{
        get_test_access_token, sign_with_test_app_key, sign_with_test_hw_key, TEST_USERNAME,
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
