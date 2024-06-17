use axum::routing::post;
use axum::Router;
use axum::{extract::State, Json};
use serde::{Deserialize, Serialize};
use tracing::{error, instrument};
use types::authn_authz::cognito::{CognitoUser, CognitoUsername};
use userpool::userpool::{AuthTokens, UserPoolError, UserPoolService};
use utoipa::{OpenApi, ToSchema};

use account::service::{FetchAccountByAuthKeyInput, Service as AccountService};
use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use errors::ApiError;
use http_server::swagger::{SwaggerEndpoint, Url};
use types::account::identifiers::AccountId;

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(pub UserPoolService, pub AccountService);

impl RouteState {
    pub fn unauthed_router(&self) -> Router {
        Router::new()
            .route("/api/recovery-auth", post(authenticate_with_recovery))
            .route("/api/hw-auth", post(authenticate_with_hardware))
            .route("/api/authenticate", post(authenticate))
            .route("/api/authenticate/tokens", post(get_tokens))
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Authentication", "/docs/authentication/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        authenticate,
        authenticate_with_hardware,
        authenticate_with_recovery,
        get_tokens,
    ),
    components(
        schemas(
            AuthRequestKey,
            AuthenticateWithHardwareRequest,
            AuthenticateWithHardwareResponse,
            AuthenticateWithRecoveryAuthkeyRequest,
            AuthenticateWithRecoveryResponse,
            AuthenticationRequest,
            AuthenticationResponse,
            ChallengeResponseParameters,
            CognitoUsername,
            GetTokensRequest,
            GetTokensResponse,
        ),
    ),
    tags(
        (name = "Authentication", description = "Account Authentication")
    )
)]
struct ApiDoc;

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct AuthenticateWithRecoveryAuthkeyRequest {
    pub recovery_auth_pubkey: PublicKey,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct AuthenticateWithRecoveryResponse {
    pub username: CognitoUsername,
    pub account_id: AccountId,
    pub challenge: String,
    pub session: String,
}

//TODO[BKR-608]: Remove this once we're using /api/authenticate
#[instrument(fields(username), skip(account_service, user_pool_service))]
#[utoipa::path(
    post,
    path = "/api/recovery-auth",
    request_body = AuthenticateWithRecoveryAuthkeyRequest,
    responses(
        (status = 200, description = "Authentication Challenge and Session", body=AuthenticateWithRecoveryResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn authenticate_with_recovery(
    State(account_service): State<AccountService>,
    State(user_pool_service): State<UserPoolService>,
    Json(request): Json<AuthenticateWithRecoveryAuthkeyRequest>,
) -> Result<Json<AuthenticateWithRecoveryResponse>, ApiError> {
    let pubkeys_to_account = account_service
        .fetch_account_id_by_recovery_pubkey(FetchAccountByAuthKeyInput {
            pubkey: request.recovery_auth_pubkey,
        })
        .await?;

    tracing::Span::current().record("account_id", &pubkeys_to_account.id.to_string());

    let username: CognitoUsername = CognitoUser::Recovery(pubkeys_to_account.id.clone()).into();
    let auth_challenge = user_pool_service
        .initiate_auth_for_user(&username, &pubkeys_to_account)
        .await
        .map_err(|e| {
            let msg = "Failed to initiate authentication with pubkey";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;
    Ok(Json(AuthenticateWithRecoveryResponse {
        username: CognitoUser::Recovery(pubkeys_to_account.id.clone()).into(),
        account_id: pubkeys_to_account.id,
        challenge: auth_challenge.challenge,
        session: auth_challenge.session,
    }))
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct AuthenticateWithHardwareRequest {
    pub hw_auth_pubkey: PublicKey,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct AuthenticateWithHardwareResponse {
    pub account_id: AccountId,
    pub challenge: String,
    pub session: String,
}

#[instrument(fields(account_id), skip(account_service, user_pool_service))]
#[utoipa::path(
    post,
    path = "/api/hw-auth",
    request_body = AuthenticateWithHardwareRequest,
    responses(
        (status = 200, description = "Authentication Challenge and Session", body=AuthenticateWithHardwareResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn authenticate_with_hardware(
    State(account_service): State<AccountService>,
    State(user_pool_service): State<UserPoolService>,
    Json(request): Json<AuthenticateWithHardwareRequest>,
) -> Result<Json<AuthenticateWithHardwareResponse>, ApiError> {
    let pubkeys_to_account = account_service
        .fetch_account_id_by_hw_pubkey(FetchAccountByAuthKeyInput {
            pubkey: request.hw_auth_pubkey,
        })
        .await?;

    tracing::Span::current().record("account_id", &pubkeys_to_account.id.to_string());

    let username: CognitoUsername = CognitoUser::Hardware(pubkeys_to_account.id.clone()).into();
    let auth_challenge = user_pool_service
        .initiate_auth_for_user(&username, &pubkeys_to_account)
        .await
        .map_err(|e| {
            let msg = "Failed to initiate authentication with pubkey";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;
    Ok(Json(AuthenticateWithHardwareResponse {
        account_id: pubkeys_to_account.id,
        challenge: auth_challenge.challenge,
        session: auth_challenge.session,
    }))
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
pub enum AuthRequestKey {
    HwPubkey(PublicKey),
    AppPubkey(PublicKey),
    RecoveryPubkey(PublicKey),
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct AuthenticationRequest {
    pub auth_request_key: AuthRequestKey,
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct AuthenticationResponse {
    pub username: CognitoUsername,
    pub account_id: AccountId,
    pub challenge: String,
    pub session: String,
}

#[instrument(fields(account_id), skip(account_service, user_pool_service))]
#[utoipa::path(
    post,
    path = "/api/authenticate",
    request_body = AuthenticationRequest,
    responses(
        (status = 200, description = "Authentication Challenge and Session", body=AuthenticateWithHardwareResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn authenticate(
    State(account_service): State<AccountService>,
    State(user_pool_service): State<UserPoolService>,
    Json(request): Json<AuthenticationRequest>,
) -> Result<Json<AuthenticationResponse>, ApiError> {
    let (requested_cognito_user, pubkeys_to_account) = match request.auth_request_key {
        AuthRequestKey::HwPubkey(pubkey) => {
            let pubkeys_to_account = account_service
                .fetch_account_id_by_hw_pubkey(FetchAccountByAuthKeyInput { pubkey })
                .await?;
            (
                CognitoUser::Hardware(pubkeys_to_account.id.clone()),
                pubkeys_to_account,
            )
        }
        AuthRequestKey::AppPubkey(pubkey) => {
            let pubkeys_to_account = account_service
                .fetch_account_id_by_app_pubkey(FetchAccountByAuthKeyInput { pubkey })
                .await?;
            (
                CognitoUser::App(pubkeys_to_account.id.clone()),
                pubkeys_to_account,
            )
        }
        AuthRequestKey::RecoveryPubkey(pubkey) => {
            let pubkeys_to_account = account_service
                .fetch_account_id_by_recovery_pubkey(FetchAccountByAuthKeyInput { pubkey })
                .await?;
            (
                CognitoUser::Recovery(pubkeys_to_account.id.clone()),
                pubkeys_to_account,
            )
        }
    };

    tracing::Span::current().record("account_id", &pubkeys_to_account.id.to_string());

    let auth_challenge = user_pool_service
        .initiate_auth_for_user(&requested_cognito_user.into(), &pubkeys_to_account)
        .await
        .map_err(|e| {
            let msg = "Failed to initiate authentication with pubkey";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;

    Ok(Json(AuthenticationResponse {
        username: auth_challenge.username,
        account_id: pubkeys_to_account.id,
        challenge: auth_challenge.challenge,
        session: auth_challenge.session,
    }))
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
pub struct ChallengeResponseParameters {
    pub username: CognitoUsername,
    pub challenge_response: String,
    pub session: String,
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
pub struct GetTokensRequest {
    pub challenge: Option<ChallengeResponseParameters>,
    pub refresh_token: Option<String>,
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
pub struct GetTokensResponse {
    pub access_token: String,
    pub refresh_token: String,
}

#[instrument(fields(account_id), skip(user_pool_service))]
#[utoipa::path(
    post,
    path = "/api/authenticate/tokens",
    request_body = GetTokensRequest,
    responses(
        (status = 200, description = "Authentication Tokens", body=GetTokensResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn get_tokens(
    State(user_pool_service): State<UserPoolService>,
    Json(request): Json<GetTokensRequest>,
) -> Result<Json<GetTokensResponse>, ApiError> {
    let (challenge_present, refresh_token_present) =
        (request.challenge.is_some(), request.refresh_token.is_some());
    if challenge_present && refresh_token_present {
        return Err(ApiError::GenericBadRequest(
            "Cannot provide a challenge response and a refresh token".to_string(),
        ));
    }
    if !(challenge_present || refresh_token_present) {
        return Err(ApiError::GenericBadRequest(
            "Must provide a challenge response or a refresh token".to_string(),
        ));
    }

    let tokens: AuthTokens = if let Some(refresh_token) = request.refresh_token {
        user_pool_service
            .refresh_access_token(refresh_token)
            .await
            .map_err(|e: UserPoolError| {
                let msg = "failed to refresh access tokens";
                error!("{msg}: {e}");
                ApiError::from(e)
            })?
    } else if let Some(params) = request.challenge {
        user_pool_service
            .respond_to_auth_challenge(&params.username, params.session, params.challenge_response)
            .await
            .map_err(|e: UserPoolError| {
                let msg = "failed to complete auth challenge";
                error!("{msg}: {e}");
                ApiError::from(e)
            })?
    } else {
        return Err(ApiError::GenericInternalApplicationError(
            "Could not deserialize refresh token or challenge response".to_string(),
        ));
    };

    Ok(Json(GetTokensResponse {
        access_token: tokens.access_token,
        refresh_token: tokens.refresh_token,
    }))
}
