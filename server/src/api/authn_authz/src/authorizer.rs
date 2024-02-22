use std::collections::HashMap;
use std::str::FromStr;

use axum::extract::Path;
use axum::extract::Request;
use axum::http::StatusCode;
use axum::middleware::Next;
use axum::response::Response;
use jwt_authorizer::{JwtAuthorizer, JwtClaims, Refresh, RefreshStrategy, Validation};
use types::account::identifiers::AccountId;

use crate::key_claims::AccessTokenClaims;
use crate::test_utils::TEST_JWT_SIGNING_SECRET;
use crate::userpool::{self};
use crate::userpool::{cognito_user::CognitoUser, CognitoMode};

pub enum AuthorizerConfig {
    Test,
    Cognito,
}

impl From<userpool::Config> for AuthorizerConfig {
    fn from(value: userpool::Config) -> Self {
        match value.cognito {
            CognitoMode::Environment => Self::Cognito,
            CognitoMode::Test => Self::Test,
        }
    }
}

impl AuthorizerConfig {
    pub fn into_authorizer(self) -> JwtAuthorizer<AccessTokenClaims> {
        match self {
            AuthorizerConfig::Test => {
                let validation = Validation::new();
                JwtAuthorizer::from_secret(TEST_JWT_SIGNING_SECRET).validation(validation)
            }
            AuthorizerConfig::Cognito => {
                let pool_id = std::env::var("COGNITO_USER_POOL")
                    .expect("Could not get value of COGNITO_USER_POOL env variable");
                let pool_parts: Vec<&str> = pool_id.split('_').collect();
                let region = pool_parts
                    .first()
                    .expect("could not parse region out of cognito user pool id");
                let idp_url = format!("https://cognito-idp.{region}.amazonaws.com/{pool_id}");
                let validation = Validation::new()
                    // ensure that the issuer is the cognito user pool
                    .iss(&[&idp_url]);
                JwtAuthorizer::from_oidc(&idp_url)
                    .validation(validation)
                    .refresh(Refresh {
                        // cognito rotates its signing keys roughly every 24 hours
                        // so our jwt authorizer will periodically refresh the verification keys
                        strategy: RefreshStrategy::Interval,
                        ..Default::default()
                    })
            }
        }
    }
}

pub async fn authorize_token_for_path(
    Path(path): Path<HashMap<String, String>>,
    JwtClaims(claims): JwtClaims<AccessTokenClaims>,
    request: Request,
    next: Next,
) -> Result<Response, StatusCode> {
    path.get("account_id")
        .map_or(Err(StatusCode::UNAUTHORIZED), |account_id| {
            let account_id =
                AccountId::from_str(account_id.as_str()).map_err(|_| StatusCode::BAD_REQUEST)?;
            let cognito_user = CognitoUser::from_str(claims.username.as_ref())
                .map_err(|_| StatusCode::UNAUTHORIZED)?;
            if cognito_user != CognitoUser::Wallet(account_id) {
                return Err(StatusCode::UNAUTHORIZED);
            }
            Ok(())
        })?;

    Ok(next.run(request).await)
}

pub async fn authorize_recovery_token_for_path(
    Path(path): Path<HashMap<String, String>>,
    JwtClaims(claims): JwtClaims<AccessTokenClaims>,
    request: Request,
    next: Next,
) -> Result<Response, StatusCode> {
    path.get("account_id")
        .map_or(Err(StatusCode::UNAUTHORIZED), |account_id| {
            let account_id =
                AccountId::from_str(account_id.as_str()).map_err(|_| StatusCode::BAD_REQUEST)?;
            let cognito_user = CognitoUser::from_str(claims.username.as_ref())
                .map_err(|_| StatusCode::UNAUTHORIZED)?;
            if cognito_user != CognitoUser::Recovery(account_id) {
                return Err(StatusCode::UNAUTHORIZED);
            }
            Ok(())
        })?;

    Ok(next.run(request).await)
}

pub async fn authorize_account_or_recovery_token_for_path(
    Path(path): Path<HashMap<String, String>>,
    JwtClaims(claims): JwtClaims<AccessTokenClaims>,
    mut request: Request,
    next: Next,
) -> Result<Response, StatusCode> {
    path.get("account_id")
        .map_or(Err(StatusCode::UNAUTHORIZED), |account_id| {
            let account_id =
                AccountId::from_str(account_id.as_str()).map_err(|_| StatusCode::BAD_REQUEST)?;
            let cognito_user = CognitoUser::from_str(claims.username.as_ref())
                .map_err(|_| StatusCode::UNAUTHORIZED)?;
            if cognito_user.get_account_id() != account_id {
                return Err(StatusCode::UNAUTHORIZED);
            }
            request.extensions_mut().insert(cognito_user);
            Ok(())
        })?;

    Ok(next.run(request).await)
}
