use account::service::{FetchAccountInput, Service as AccountService};
use axum::routing::put;
use axum::{
    extract::{Path, State},
    routing::post,
    Json, Router,
};
use errors::{ApiError, RouteError};
use http_server::router::RouterBuilder;
use http_server::swagger::{SwaggerEndpoint, Url};
use serde::{Deserialize, Serialize};
use serde_with::{base64::Base64, serde_as};
use tracing::{error, instrument};
use types::account::identifiers::AccountId;
use types::account::spending::SpendingKeyDefinition;
use utoipa::{OpenApi, ToSchema};
use wsm_rust_client::{SigningService, WsmClient};

use crate::metrics;

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(pub AccountService, pub WsmClient);

impl RouterBuilder for RouteState {
    fn account_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/self-sovereign-backup",
                post(create_self_sovereign_backup),
            )
            .route(
                "/api/accounts/:account_id/share-refresh",
                post(initiate_share_refresh),
            )
            .route(
                "/api/accounts/:account_id/share-refresh",
                put(continue_share_refresh),
            )
            .route_layer(metrics::FACTORY.route_layer("recovery".to_owned()))
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Distributed Keys", "/docs/distributed_keys/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        create_self_sovereign_backup,
        initiate_share_refresh,
        continue_share_refresh,
    ),
    components(
        schemas(
            CreateSelfSovereignBackupRequest,
            CreateSelfSovereignBackupResponse,
            InitiateShareRefreshRequest,
            InitiateShareRefreshResponse,
            ContinueShareRefreshRequest,
            ContinueShareRefreshResponse,
        )
    ),
    tags(
        (name = "Distributed Keys", description = "Endpoints related to maintenance of distributed keys as it relates to recovering or maintaining secure access to a wallet.")
    )
)]
struct ApiDoc;

#[serde_as]
#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CreateSelfSovereignBackupRequest {
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    #[serde_as(as = "Base64")]
    pub noise_session: Vec<u8>,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CreateSelfSovereignBackupResponse {
    #[serde_as(as = "Base64")]
    pub sealed_response: Vec<u8>,
}

#[instrument(skip(account_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/self-sovereign-backup",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("key_definition_id" = KeyDefinitionId, Path, description = "KeyDefinitionId"),
    ),
    request_body = CreateSelfSovereignBackupRequest,
    responses(
        (status = 200, description = "Created self-sovereign backup", body=CreateSelfSovereignBackupResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn create_self_sovereign_backup(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(wsm_client): State<WsmClient>,
    Json(request): Json<CreateSelfSovereignBackupRequest>,
) -> Result<Json<CreateSelfSovereignBackupResponse>, ApiError> {
    let account = account_service
        .fetch_software_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let key_definition_id = account
        .active_key_definition_id
        .ok_or(RouteError::NoActiveSpendKeyset)?;

    let Some(key_definition) = account.spending_key_definitions.get(&key_definition_id) else {
        return Err(ApiError::GenericNotFound(
            "Key definition not found".to_string(),
        ));
    };

    let SpendingKeyDefinition::DistributedKey(distributed_key) = key_definition else {
        return Err(ApiError::GenericBadRequest(
            "Key definition is not a distributed key".to_string(),
        ));
    };

    if !distributed_key.dkg_complete {
        return Err(ApiError::GenericConflict(
            "Keygen is not complete for key definition".to_string(),
        ));
    }

    let response = wsm_client
        .create_self_sovereign_backup(
            &key_definition_id.to_string(),
            distributed_key.network.into(),
            request.sealed_request,
            request.noise_session,
        )
        .await
        .map_err(|e| {
            let msg = "Failed to create self-sovereign backup in WSM";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;

    Ok(Json(CreateSelfSovereignBackupResponse {
        sealed_response: response.sealed_response,
    }))
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct InitiateShareRefreshRequest {
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    #[serde_as(as = "Base64")]
    pub noise_session: Vec<u8>,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct InitiateShareRefreshResponse {
    #[serde_as(as = "Base64")]
    pub sealed_response: Vec<u8>,
}

#[instrument(skip(account_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/share-refresh",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = InitiateShareRefreshRequest,
    responses(
        (status = 200, description = "Initiated distributed keygen", body=InitiateShareRefreshResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn initiate_share_refresh(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(wsm_client): State<WsmClient>,
    Json(request): Json<InitiateShareRefreshRequest>,
) -> Result<Json<InitiateShareRefreshResponse>, ApiError> {
    let account = account_service
        .fetch_software_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let key_definition_id = account
        .active_key_definition_id
        .ok_or(RouteError::NoActiveSpendKeyset)?;

    let Some(key_definition) = account.spending_key_definitions.get(&key_definition_id) else {
        return Err(ApiError::GenericNotFound(
            "Key definition not found".to_string(),
        ));
    };

    let SpendingKeyDefinition::DistributedKey(distributed_key) = key_definition else {
        return Err(ApiError::GenericBadRequest(
            "Key definition is not a distributed key".to_string(),
        ));
    };

    if !distributed_key.dkg_complete {
        return Err(ApiError::GenericConflict(
            "Keygen is not complete for key definition".to_string(),
        ));
    }

    let wsm_response = wsm_client
        .initiate_share_refresh(
            &key_definition_id.to_string(),
            distributed_key.network.into(),
            request.sealed_request,
            request.noise_session,
        )
        .await
        .map_err(|e| {
            let msg = "Failed to initiate share refresh in WSM";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;

    Ok(Json(InitiateShareRefreshResponse {
        sealed_response: wsm_response.sealed_response,
    }))
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct ContinueShareRefreshRequest {
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    #[serde_as(as = "Base64")]
    pub noise_session: Vec<u8>,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct ContinueShareRefreshResponse {}

#[instrument(skip(account_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/share-refresh",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = ContinueShareRefreshRequest,
    responses(
        (status = 200, description = "Continued distributed keygen", body=ContinueShareRefreshResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn continue_share_refresh(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(wsm_client): State<WsmClient>,
    Json(request): Json<ContinueShareRefreshRequest>,
) -> Result<Json<ContinueShareRefreshResponse>, ApiError> {
    let account = account_service
        .fetch_software_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let key_definition_id = account
        .active_key_definition_id
        .ok_or(RouteError::NoActiveSpendKeyset)?;

    let Some(key_definition) = account.spending_key_definitions.get(&key_definition_id) else {
        return Err(ApiError::GenericNotFound(
            "Key definition not found".to_string(),
        ));
    };

    let SpendingKeyDefinition::DistributedKey(distributed_key) = key_definition else {
        return Err(ApiError::GenericBadRequest(
            "Key definition is not a distributed key".to_string(),
        ));
    };

    if !distributed_key.dkg_complete {
        return Err(ApiError::GenericConflict(
            "Keygen is not complete for key definition".to_string(),
        ));
    }

    wsm_client
        .continue_share_refresh(
            &key_definition_id.to_string(),
            distributed_key.network.into(),
            request.sealed_request,
            request.noise_session,
        )
        .await
        .map_err(|e| {
            let msg = "Failed to continue share refresh in WSM";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;

    Ok(Json(ContinueShareRefreshResponse {}))
}
