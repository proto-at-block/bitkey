use crate::error::RecoveryError;
use crate::metrics;
use authn_authz::key_claims::KeyClaims;
use axum::{
    extract::{Path, State},
    routing::post,
    Json, Router,
};
use bdk_utils::bdk::bitcoin::secp256k1::ecdsa::Signature;
use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use errors::ApiError;
use http_server::{
    router::RouterBuilder,
    swagger::{SwaggerEndpoint, Url},
};
use privileged_action::service::{
    authorize_privileged_action::{
        AuthorizePrivilegedActionInput, AuthorizePrivilegedActionOutput,
        PrivilegedActionRequestValidator, PrivilegedActionRequestValidatorBuilder,
    },
    Service as PrivilegedActionService,
};
use serde::{Deserialize, Serialize};
use serde_with::serde_as;
use serde_with::DisplayFromStr;
use tracing::instrument;
use types::{
    account::identifiers::AccountId,
    privileged_action::{
        router::generic::{PrivilegedActionRequest, PrivilegedActionResponse},
        shared::PrivilegedActionType,
    },
};
use userpool::userpool::UserPoolService;
use utoipa::{OpenApi, ToSchema};
use wsm_rust_client::{CreateGrantRequest, Grant, SigningService, WsmClient};

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub UserPoolService,
    pub PrivilegedActionService,
    pub WsmClient,
);

impl RouterBuilder for RouteState {
    fn account_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/fingerprint-reset",
                post(reset_fingerprint),
            )
            .route_layer(metrics::FACTORY.route_layer("reset_fingerprint".to_owned()))
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Fingerprint Reset", "/docs/fingerprint-reset/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        reset_fingerprint,
    ),
    components(
        schemas(
            ResetFingerprintRequest,
            ResetFingerprintResponse,
        )
    ),
    tags(
        (name = "Fingerprint Reset", description = "Endpoints related to resetting the fingerprint for an account."),
    )
)]
struct ApiDoc;

#[serde_as]
#[derive(Debug, Serialize, Deserialize, Clone, ToSchema)]
pub struct ResetFingerprintRequest {
    pub hw_auth_public_key: PublicKey,
    pub version: u8,
    pub action: String,
    pub device_id: String,
    pub challenge: Vec<u8>,
    #[serde_as(as = "DisplayFromStr")]
    pub signature: Signature,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct ResetFingerprintResponse {
    #[serde(flatten)]
    pub grant: Grant,
}

#[instrument(fields(account_id), skip(privileged_action_service, key_proof))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/fingerprint-reset",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "Fingerprint reset completed"),
        (status = 400, description = "Fingerprint reset not completed"),
        (status = 403, description = "Forbidden from initiating or completing fingerprint reset"),
        (status = 409, description = "Fingerprint reset request already exists and conflicts with new request"),
    ),
)]
pub async fn reset_fingerprint(
    Path(account_id): Path<AccountId>,
    State(privileged_action_service): State<PrivilegedActionService>,
    State(wsm_client): State<WsmClient>,
    key_proof: KeyClaims,
    Json(privileged_action_request): Json<PrivilegedActionRequest<ResetFingerprintRequest>>,
) -> Result<Json<PrivilegedActionResponse<ResetFingerprintResponse>>, ApiError> {
    let request_validator: PrivilegedActionRequestValidator<ResetFingerprintRequest, ApiError> =
        PrivilegedActionRequestValidatorBuilder::default().build()?;
    let authorize_result = privileged_action_service
        .authorize_privileged_action(AuthorizePrivilegedActionInput {
            account_id: &account_id,
            privileged_action_definition: &PrivilegedActionType::ResetFingerprint.into(),
            key_proof: &key_proof,
            privileged_action_request: &privileged_action_request,
            request_validator,
        })
        .await?;

    match authorize_result {
        AuthorizePrivilegedActionOutput::Authorized(request) => {
            let signed_grant = create_signed_grant(wsm_client, request).await?;
            let response = PrivilegedActionResponse::Completed(ResetFingerprintResponse {
                grant: signed_grant,
            });
            Ok(Json(response))
        }
        AuthorizePrivilegedActionOutput::Pending(response) => Ok(Json(response)),
    }
}

async fn create_signed_grant(
    wsm_client: WsmClient,
    request: ResetFingerprintRequest,
) -> Result<Grant, ApiError> {
    wsm_client
        .create_signed_grant(CreateGrantRequest {
            hw_auth_public_key: request.hw_auth_public_key,
            version: request.version,
            action: request.action,
            device_id: request.device_id,
            challenge: request.challenge,
            signature: request.signature,
        })
        .await
        .map_err(|e| ApiError::from(RecoveryError::WsmGrantError(e)))
}
