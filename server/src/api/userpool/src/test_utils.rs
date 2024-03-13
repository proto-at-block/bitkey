use jsonwebtoken::{EncodingKey, Header};
use types::authn_authz::{cognito::CognitoUser, AccessTokenClaims};

pub const TEST_JWT_SIGNING_SECRET: &str = "super_secret";

pub fn get_test_access_token_for_cognito_user(cognito_user: &CognitoUser) -> String {
    let key = EncodingKey::from_secret(TEST_JWT_SIGNING_SECRET.as_ref());
    let claims = AccessTokenClaims {
        sub: "TEST".to_string(),
        iss: "TEST".to_string(),
        client_id: "TEST".to_string(),
        origin_jti: "TEST".to_string(),
        event_id: "TEST".to_string(),
        token_use: "access".to_string(),
        scope: "aws.cognito.signin.user.admin".to_string(),
        auth_time: jsonwebtoken::get_current_timestamp(),
        exp: jsonwebtoken::get_current_timestamp() + 300, // now + 5 minutes
        iat: jsonwebtoken::get_current_timestamp(),
        jti: "TEST".to_string(),
        username: cognito_user.into(),
    };
    jsonwebtoken::encode(&Header::default(), &claims, &key).unwrap()
}
