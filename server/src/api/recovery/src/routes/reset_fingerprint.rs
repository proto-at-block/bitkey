use crate::error::RecoveryError;
use crate::metrics;
use account::service::{FetchAccountInput, Service as AccountService};
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
        AuthenticationContext, AuthorizePrivilegedActionInput, AuthorizePrivilegedActionOutput,
        PrivilegedActionRequestValidatorBuilder,
    },
    Service as PrivilegedActionService,
};
use serde::{Deserialize, Serialize};
use serde_with::{base64::Base64, serde_as, DisplayFromStr};
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
use wsm_common::messages::enclave::GrantRequest;
use wsm_grant::fp_reset::verify_grant_request_signature;
use wsm_rust_client::{CreateGrantRequest, Grant, SigningService, WsmClient};

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub UserPoolService,
    pub PrivilegedActionService,
    pub WsmClient,
    pub AccountService,
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
    pub version: u8,
    pub action: u8,
    #[serde_as(as = "Base64")]
    pub device_id: Vec<u8>,
    #[serde_as(as = "Base64")]
    pub challenge: Vec<u8>,
    #[serde_as(as = "DisplayFromStr")]
    pub signature: Signature,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct ResetFingerprintResponse {
    #[serde(flatten)]
    pub grant: Grant,
}

#[instrument(
    fields(account_id),
    skip(privileged_action_service, key_proof, account_service, wsm_client)
)]
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
    State(account_service): State<AccountService>,
    key_proof: KeyClaims,
    Json(privileged_action_request): Json<PrivilegedActionRequest<ResetFingerprintRequest>>,
) -> Result<Json<PrivilegedActionResponse<ResetFingerprintResponse>>, ApiError> {
    let account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let authorize_result = privileged_action_service
        .authorize_privileged_action(AuthorizePrivilegedActionInput {
            account_id: &account_id,
            privileged_action_definition: &PrivilegedActionType::ResetFingerprint.into(),
            authentication: AuthenticationContext::KeyClaims(&key_proof),
            privileged_action_request: &privileged_action_request,
            request_validator: PrivilegedActionRequestValidatorBuilder::default()
                .on_initiate_delay_and_notify(Box::new(move |req: ResetFingerprintRequest| {
                    Box::pin(async move {
                        // Build the grant request and verify the signature before initiating D&N
                        let serialized_request = GrantRequest {
                            version: req.version,
                            action: req.action,
                            device_id: req.device_id,
                            challenge: req.challenge,
                            signature: req.signature,
                        }
                        .serialize(false);

                        verify_grant_request_signature(
                            serialized_request.as_slice(),
                            &req.signature,
                            &account.hardware_auth_pubkey,
                        )
                        .map_err(|_| {
                            ApiError::GenericUnauthorized(
                                "Grant request signature failed to verify".to_string(),
                            )
                        })?;

                        Ok::<(), ApiError>(())
                    })
                }))
                .build()?,
        })
        .await?;

    match authorize_result {
        AuthorizePrivilegedActionOutput::Authorized(request) => {
            let signed_grant =
                create_signed_grant(wsm_client, request, account.hardware_auth_pubkey).await?;
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
    hw_auth_public_key: PublicKey,
) -> Result<Grant, ApiError> {
    wsm_client
        .create_signed_grant(CreateGrantRequest {
            hw_auth_public_key,
            version: request.version,
            action: request.action,
            device_id: request.device_id,
            challenge: request.challenge,
            signature: request.signature,
        })
        .await
        .map_err(|e| ApiError::from(RecoveryError::WsmGrantError(e)))
}
