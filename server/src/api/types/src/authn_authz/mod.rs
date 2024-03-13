use serde::{Deserialize, Serialize};

use self::cognito::CognitoUsername;

pub mod cognito;

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct AccessTokenClaims {
    pub sub: String,
    pub iss: String,
    pub client_id: String,
    pub origin_jti: String,
    pub event_id: String,
    pub token_use: String,
    pub scope: String,
    pub auth_time: u64,
    pub exp: u64,
    pub iat: u64,
    pub jti: String,
    pub username: CognitoUsername,
}
