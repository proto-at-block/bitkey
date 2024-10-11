use axum::extract::Path;
use axum::routing::post;
use axum::Router;
use axum::{extract::State, Json};
use http_server::router::RouterBuilder;
use serde::{Deserialize, Serialize};

use tracing::instrument;
use utoipa::{OpenApi, ToSchema};

use account::service::Service as AccountService;
use errors::ApiError;
use feature_flags::{
    flag::{evaluate_flags, FeatureFlag},
    service::Service as FeatureFlagsService,
};
use http_server::swagger::{SwaggerEndpoint, Url};
use types::account::identifiers::AccountId;

use crate::claims::ExperimentationClaims;
use crate::error::ExperimentationError;

#[derive(Clone, Deserialize)]
pub struct Config {}

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(pub Config, pub AccountService, pub FeatureFlagsService);

impl RouterBuilder for RouteState {
    fn account_or_recovery_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/feature-flags",
                post(get_account_feature_flags),
            )
            .with_state(self.to_owned())
    }

    fn unauthed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/feature-flags",
                post(get_app_installation_feature_flags),
            )
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Experimentation", "/docs/experimentation/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        get_account_feature_flags,
        get_app_installation_feature_flags,
    ),
    components(
        schemas(
            GetAccountFeatureFlagsRequest,
            GetAppInstallationFeatureFlagsRequest,
            GetFeatureFlagsResponse,
        ),
    ),
    tags(
        (name = "Experimentation", description = "Experimentation endpoints")
    )
)]
struct ApiDoc;

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct GetAccountFeatureFlagsRequest {
    pub flag_keys: Vec<String>,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct GetFeatureFlagsResponse {
    pub flags: Vec<FeatureFlag>,
}

#[instrument(fields(account_id), skip(request, feature_flags_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/feature-flags",
    request_body = GetAccountFeatureFlagsRequest,
    responses(
        (status = 200, description = "Returns the feature flags for the account", body=GetAccountFeatureFlagsResponse),
        (status = 500, description = "Internal server error when retrieving feature flags")
    ),
)]
async fn get_account_feature_flags(
    Path(_account_id): Path<AccountId>,
    State(feature_flags_service): State<FeatureFlagsService>,
    experimentation_claims: ExperimentationClaims,
    Json(request): Json<GetAccountFeatureFlagsRequest>,
) -> Result<Json<GetFeatureFlagsResponse>, ApiError> {
    let context_key = experimentation_claims.account_context_key()?;
    let flags = evaluate_flags(&feature_flags_service, request.flag_keys, &context_key)
        .map_err(ExperimentationError::from)?;
    Ok(Json(GetFeatureFlagsResponse { flags }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct GetAppInstallationFeatureFlagsRequest {
    pub flag_keys: Vec<String>,
}

#[instrument(skip(request, feature_flags_service))]
#[utoipa::path(
    post,
    path = "/api/feature-flags",
    request_body = GetAppInstallationFeatureFlagsRequest,
    responses(
        (status = 200, description = "Returns the feature flags for the app installation", body=GetFeatureFlagsResponse),
        (status = 500, description = "Internal server error when retrieving feature flags")
    ),
)]
async fn get_app_installation_feature_flags(
    State(feature_flags_service): State<FeatureFlagsService>,
    experimentation_claims: ExperimentationClaims,
    Json(request): Json<GetAppInstallationFeatureFlagsRequest>,
) -> Result<Json<GetFeatureFlagsResponse>, ApiError> {
    let context_key = experimentation_claims.app_installation_context_key()?;
    let flags = evaluate_flags(&feature_flags_service, request.flag_keys, &context_key)
        .map_err(ExperimentationError::from)?;
    Ok(Json(GetFeatureFlagsResponse { flags }))
}
