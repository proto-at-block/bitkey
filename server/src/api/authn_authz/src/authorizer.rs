use std::collections::HashMap;
use std::str::FromStr;

use async_trait::async_trait;
use axum::extract::FromRequestParts;
use axum::extract::Path;
use axum::extract::Request;
use axum::http::request::Parts;
use axum::http::StatusCode;
use axum::middleware::Next;
use axum::response::Response;
use jsonwebtoken::TokenData;
use jwt_authorizer::{JwtAuthorizer, JwtClaims, Refresh, RefreshStrategy, Validation};
use types::account::identifiers::AccountId;
use types::authn_authz::cognito::CognitoUser;
use types::authn_authz::AccessTokenClaims;
use userpool::test_utils::TEST_JWT_SIGNING_SECRET;
use userpool::userpool::CognitoMode;

#[derive(Debug, Clone)]
pub struct AccountIdFromAccessToken(AccountId);

impl From<AccountIdFromAccessToken> for AccountId {
    fn from(value: AccountIdFromAccessToken) -> Self {
        value.0
    }
}

#[async_trait]
impl<S> FromRequestParts<S> for AccountIdFromAccessToken
where
    S: Send + Sync,
{
    type Rejection = (StatusCode, &'static str);

    async fn from_request_parts(parts: &mut Parts, _state: &S) -> Result<Self, Self::Rejection> {
        let token_data = parts
            .extensions
            .get::<TokenData<AccessTokenClaims>>()
            .ok_or((StatusCode::UNAUTHORIZED, "No access token found"))?;
        let cognito_user = CognitoUser::from_str(token_data.claims.username.as_ref())
            .map_err(|_| (StatusCode::UNAUTHORIZED, "Invalid cognito user"))?;
        Ok(AccountIdFromAccessToken(cognito_user.get_account_id()))
    }
}

pub enum AuthorizerConfig {
    Test,
    Cognito,
}

impl From<userpool::userpool::Config> for AuthorizerConfig {
    fn from(value: userpool::userpool::Config) -> Self {
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
            if !cognito_user.is_app(&account_id) && !cognito_user.is_hardware(&account_id) {
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
            if !cognito_user.is_recovery(&account_id) {
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
