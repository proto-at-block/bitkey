//! Pure JWT-decoding helpers shared by `authn_authz` and `experimentation`.
//! Extracted into a leaf crate to avoid a dependency cycle between them.

use std::str::FromStr;

use http::header::{self, HeaderMap, HeaderValue};
use jsonwebtoken::{DecodingKey, Validation};
use types::authn_authz::cognito::{CognitoUser, CognitoUsername};
use types::authn_authz::AccessTokenClaims;

/// Pull the bearer token out of an `Authorization: Bearer <jwt>` header.
pub fn get_jwt_from_headers(headers: &HeaderMap<HeaderValue>) -> Option<String> {
    headers
        .get(header::AUTHORIZATION)
        .cloned()
        .and_then(|value| value.to_str().ok().map(String::from))
        .and_then(|value| value.strip_prefix("Bearer ").map(String::from))
}

/// Decode the `username` claim from a Cognito access token.
///
/// Signature verification is intentionally disabled: the caller has either
/// already verified upstream (auth middleware), or just received the token
/// from our own Cognito. Cognito rotates signing keys every ~24h, so we
/// avoid re-fetching here.
pub fn get_user_name_from_jwt(jwt: &str) -> Option<CognitoUsername> {
    let mut validation = Validation::new(jsonwebtoken::Algorithm::RS256);
    validation.insecure_disable_signature_validation();
    if let Ok(token) =
        jsonwebtoken::decode::<AccessTokenClaims>(jwt, &DecodingKey::from_secret(&[]), &validation)
    {
        Some(token.claims.username)
    } else {
        None
    }
}

/// Extract the account id from the `Authorization` header's JWT, if present.
pub fn extract_account_id(headers: &HeaderMap<HeaderValue>) -> Option<String> {
    get_jwt_from_headers(headers)
        .and_then(|jwt| get_user_name_from_jwt(&jwt))
        .and_then(|u| CognitoUser::from_str(u.as_ref()).ok())
        .map(|cognito_user| cognito_user.get_account_id().to_string())
}
