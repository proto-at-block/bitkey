use std::str::FromStr;

use secp256k1::Secp256k1;
use sha2::{Digest, Sha256};
use types::{account::identifiers::AccountId, authn_authz::cognito::CognitoUser};
use userpool::test_utils::get_test_access_token_for_cognito_user;

pub(crate) const TEST_USERNAME: &str = "urn:wallet-account:000000000000000000000000000";
const TEST_APP_AUTH_KEY: &[u8] = &[0xcd; 32];
const TEST_HW_AUTH_KEY: &[u8] = &[0xab; 32];

/// Get an access token signed by a static key that can be used for testing
pub fn get_test_access_token() -> String {
    get_test_access_token_for_cognito_user(&CognitoUser::Wallet(
        AccountId::from_str(TEST_USERNAME)
            .expect("converting TEST_USERNAME to AccountId should never fail"),
    ))
}

/// Sign a message with the app key
pub fn sign_with_app_key(message: &str) -> String {
    let secp = Secp256k1::new();
    let message = secp256k1::Message::from_slice(&Sha256::digest(message.as_bytes())).unwrap();
    let secret_key = secp256k1::SecretKey::from_slice(TEST_APP_AUTH_KEY).unwrap();
    let sig = secp.sign_ecdsa(&message, &secret_key);
    sig.to_string()
}

/// Sign a message with the hardware key
/// The real hardware outputs compact signatures, making this different from the sign_with_app_key function
pub fn sign_with_hw_key(message: &str) -> String {
    let secp = Secp256k1::new();
    let message = secp256k1::Message::from_slice(&Sha256::digest(message.as_bytes())).unwrap();
    let secret_key = secp256k1::SecretKey::from_slice(TEST_HW_AUTH_KEY).unwrap();
    let sig = secp.sign_ecdsa(&message, &secret_key);
    sig.to_string()
}

// tests
#[cfg(test)]
mod tests {
    use crate::authorizer::{authorize_token_for_path, AuthorizerConfig};
    use crate::key_claims::verify_signature;
    use crate::test_utils::{get_test_access_token, TEST_USERNAME};
    use axum::body::Body;
    use axum::http::{Request, StatusCode};
    use axum::routing::get;
    use axum::{middleware, Router};
    use jsonwebtoken::{decode, DecodingKey};
    use jwt_authorizer::IntoLayer;
    use tower::util::ServiceExt;
    use types::account::identifiers::AccountId;
    use types::authn_authz::AccessTokenClaims;
    use userpool::test_utils::TEST_JWT_SIGNING_SECRET;

    const TEST_APP_AUTH_PUBKEY: &str =
        "02b98a7fb8cc007048625b6446ad49a1b3a722df8c1ca975b87160023e14d19097";
    const TEST_HW_AUTH_PUBKEY: &str =
        "0381aaadc8a5e83f4576df823cf22a5b1969cf704a0d5f6f68bd757410c9917aac";

    #[test]
    fn test_get_test_jwt() {
        let jwt = super::get_test_access_token();
        let key = DecodingKey::from_secret(TEST_JWT_SIGNING_SECRET.as_ref());
        let token =
            decode::<AccessTokenClaims>(&jwt, &key, &jsonwebtoken::Validation::default()).unwrap();
        assert_eq!(
            token.claims.username.as_ref(),
            "urn:wallet-account:000000000000000000000000000"
        );
    }

    #[test]
    fn test_sign_with_app_key() {
        let message = get_test_access_token();
        let sig = super::sign_with_app_key(&message);
        assert!(verify_signature(
            &sig,
            message,
            TEST_APP_AUTH_PUBKEY.to_string()
        ));
    }

    #[test]
    fn test_sign_with_hw_key() {
        let message = get_test_access_token();
        let sig = super::sign_with_hw_key(&message);
        assert!(verify_signature(
            &sig,
            message,
            TEST_HW_AUTH_PUBKEY.to_string()
        ));
    }

    #[tokio::test]
    async fn test_auth_middlewares() {
        let authorizer = AuthorizerConfig::Test
            .into_authorizer()
            .build()
            .await
            .unwrap()
            .into_layer();

        let app = Router::new()
            .route("/no/account_id/in/path", get(|| async { StatusCode::OK }))
            .route(
                "/with/:account_id/in/path",
                get(|| async { StatusCode::OK }),
            )
            .route_layer(middleware::from_fn(authorize_token_for_path))
            .layer(authorizer);

        let token = get_test_access_token();
        let different_account_id = AccountId::gen().unwrap();

        // No auth header, no account ID in path
        let resp = app
            .to_owned()
            .oneshot(
                Request::builder()
                    .uri("/no/account_id/in/path")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(resp.status(), StatusCode::UNAUTHORIZED);

        // With auth header, no account ID in path
        let resp = app
            .to_owned()
            .oneshot(
                Request::builder()
                    .uri("/no/account_id/in/path")
                    .header("Authorization", format!("Bearer {}", token))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(resp.status(), StatusCode::UNAUTHORIZED);

        // No auth header, with account ID in path
        let resp = app
            .to_owned()
            .oneshot(
                Request::builder()
                    .uri(format!("/with/{}/in/path", TEST_USERNAME))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(resp.status(), StatusCode::UNAUTHORIZED);

        // With auth header, with different account ID in path
        let resp = app
            .to_owned()
            .oneshot(
                Request::builder()
                    .uri(format!("/with/{}/in/path", different_account_id))
                    .header("Authorization", format!("Bearer {}", token))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(resp.status(), StatusCode::UNAUTHORIZED);

        // With auth header, with same account ID in path
        let resp = app
            .to_owned()
            .oneshot(
                Request::builder()
                    .uri(format!("/with/{}/in/path", TEST_USERNAME))
                    .header("Authorization", format!("Bearer {}", token))
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(resp.status(), StatusCode::OK);
    }
}
