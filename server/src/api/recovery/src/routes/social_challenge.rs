use std::collections::HashMap;

use account::service::{FetchAccountInput, Service as AccountService};
use axum::routing::put;
use axum::{
    extract::{Path, State},
    routing::{get, post},
    Json, Router,
};
use errors::ApiError;
use feature_flags::service::Service as FeatureFlagsService;
use http_server::router::RouterBuilder;
use http_server::swagger::{SwaggerEndpoint, Url};
use serde::{Deserialize, Serialize};
use tracing::instrument;
use types::account::identifiers::AccountId;
use types::recovery::social::challenge::{
    SocialChallenge, SocialChallengeId, SocialChallengeResponse, TrustedContactChallengeRequest,
};
use types::recovery::social::relationship::RecoveryRelationshipId;
use utoipa::{OpenApi, ToSchema};

use crate::routes::deserialize_pake_pubkey;
use crate::service::social::challenge::create_social_challenge::CreateSocialChallengeInput;
use crate::service::social::challenge::fetch_social_challenge::{
    FetchSocialChallengeAsCustomerInput, FetchSocialChallengeAsTrustedContactInput,
};
use crate::service::social::challenge::respond_to_social_challenge::RespondToSocialChallengeInput;
use crate::{
    error::RecoveryError, metrics, service::social::challenge::Service as SocialChallengeService,
};

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub AccountService,
    pub SocialChallengeService,
    pub FeatureFlagsService,
);

impl RouterBuilder for RouteState {
    fn recovery_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/recovery/social-challenges",
                post(start_social_challenge),
            )
            .route(
                "/api/accounts/:account_id/recovery/verify-social-challenge",
                post(verify_social_challenge),
            )
            .route(
                "/api/accounts/:account_id/recovery/social-challenges/:social_challenge_id",
                put(respond_to_social_challenge),
            )
            .route(
                "/api/accounts/:account_id/recovery/social-challenges/:social_challenge_id",
                get(fetch_social_challenge),
            )
            .route_layer(metrics::FACTORY.route_layer("recovery".to_owned()))
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Social Challenge", "/docs/social_challenge/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        fetch_social_challenge,
        respond_to_social_challenge,
        start_social_challenge,
        verify_social_challenge,
    ),
    components(
        schemas(
            CustomerSocialChallenge,
            CustomerSocialChallengeResponse,
            FetchSocialChallengeResponse,
            RespondToSocialChallengeRequest,
            RespondToSocialChallengeResponse,
            StartChallengeTrustedContactRequest,
            StartSocialChallengeRequest,
            StartSocialChallengeResponse,
            TrustedContactChallengeRequest,
            TrustedContactSocialChallenge,
            VerifySocialChallengeRequest,
            VerifySocialChallengeResponse,
        )
    ),
    tags(
        (name = "Social Challenge", description = "Endpoints related to Social Challenges allowing a customer to get back into their account with the help of a Recovery Contact")
    )
)]
struct ApiDoc;

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CustomerSocialChallengeResponse {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub trusted_contact_recovery_pake_pubkey: String,
    pub recovery_pake_confirmation: String,
    pub resealed_dek: String,
}

impl From<SocialChallengeResponse> for CustomerSocialChallengeResponse {
    fn from(value: SocialChallengeResponse) -> Self {
        Self {
            recovery_relationship_id: value.recovery_relationship_id,
            trusted_contact_recovery_pake_pubkey: value.trusted_contact_recovery_pake_pubkey,
            recovery_pake_confirmation: value.recovery_pake_confirmation,
            resealed_dek: value.resealed_dek,
        }
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CustomerSocialChallenge {
    pub social_challenge_id: SocialChallengeId,
    pub counter: u32,
    pub responses: Vec<CustomerSocialChallengeResponse>,
}

impl From<SocialChallenge> for CustomerSocialChallenge {
    fn from(value: SocialChallenge) -> Self {
        Self {
            social_challenge_id: value.id,
            counter: value.counter,
            responses: value
                .responses
                .into_iter()
                .map(|r| r.into())
                .collect::<Vec<_>>(),
        }
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct TrustedContactSocialChallenge {
    pub social_challenge_id: SocialChallengeId,
    pub protected_customer_recovery_pake_pubkey: String,
    pub sealed_dek: String,
}

impl TryFrom<(RecoveryRelationshipId, SocialChallenge)> for TrustedContactSocialChallenge {
    type Error = RecoveryError;

    fn try_from(
        (recovery_relationship_id, challenge): (RecoveryRelationshipId, SocialChallenge),
    ) -> Result<Self, Self::Error> {
        let info = challenge
            .trusted_contact_challenge_requests
            .get(&recovery_relationship_id)
            .map(|r| r.to_owned())
            .ok_or(RecoveryError::ChallengeRequestNotFound)?;

        Ok(Self {
            social_challenge_id: challenge.id,
            protected_customer_recovery_pake_pubkey: info.protected_customer_recovery_pake_pubkey,
            sealed_dek: info.sealed_dek,
        })
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct StartChallengeTrustedContactRequest {
    pub recovery_relationship_id: RecoveryRelationshipId,
    #[serde(flatten)]
    pub challenge_request: TrustedContactChallengeRequest,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct StartSocialChallengeRequest {
    pub trusted_contacts: Vec<StartChallengeTrustedContactRequest>,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct StartSocialChallengeResponse {
    pub social_challenge: CustomerSocialChallenge,
}

///
/// Used by the Customer to initiate a Social challenge.
///
/// The customer must provide a valid recovery authentication token to start
/// the challenge
///
#[instrument(
    err,
    skip(account_service, social_challenge_service, _feature_flags_service)
)]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/recovery/social-challenges",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = StartSocialChallengeRequest,
    responses(
        (status = 200, description = "Social challenge started", body=StartSocialChallengeResponse),
    ),
)]
pub async fn start_social_challenge(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    Json(request): Json<StartSocialChallengeRequest>,
) -> Result<Json<StartSocialChallengeResponse>, ApiError> {
    let customer_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let requests = request
        .trusted_contacts
        .into_iter()
        .map(|t| {
            let recovery_relationship_id = t.recovery_relationship_id;
            let challenge = t.challenge_request.clone();
            (recovery_relationship_id, challenge)
        })
        .collect::<HashMap<_, _>>();
    let result = social_challenge_service
        .create_social_challenge(CreateSocialChallengeInput {
            customer_account: &customer_account,
            requests,
        })
        .await?;

    Ok(Json(StartSocialChallengeResponse {
        social_challenge: result.into(),
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct VerifySocialChallengeRequest {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub counter: u32,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct VerifySocialChallengeResponse {
    pub social_challenge: TrustedContactSocialChallenge,
}

///
/// This route is used by Trusted Contacts to retrieve the social challenge
/// given the code and the recovery relationship. The code was given to them
/// by the Customer who's account they're protecting.
///
#[instrument(err, skip(social_challenge_service, _feature_flags_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/recovery/verify-social-challenge",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = VerifySocialChallengeRequest,
    responses(
        (status = 200, description = "Social challenge code verified", body=VerifySocialChallengeResponse),
    ),
)]
pub async fn verify_social_challenge(
    Path(account_id): Path<AccountId>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    Json(request): Json<VerifySocialChallengeRequest>,
) -> Result<Json<VerifySocialChallengeResponse>, ApiError> {
    let challenge = social_challenge_service
        .fetch_social_challenge_as_trusted_contact(FetchSocialChallengeAsTrustedContactInput {
            trusted_contact_account_id: &account_id,
            recovery_relationship_id: &request.recovery_relationship_id,
            counter: request.counter,
        })
        .await?;
    let social_challenge = (request.recovery_relationship_id, challenge).try_into()?;
    Ok(Json(VerifySocialChallengeResponse { social_challenge }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct RespondToSocialChallengeRequest {
    #[serde(deserialize_with = "deserialize_pake_pubkey")]
    pub trusted_contact_recovery_pake_pubkey: String,
    pub recovery_pake_confirmation: String,
    pub resealed_dek: String,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct RespondToSocialChallengeResponse {}

///
/// This route is used by Trusted Contacts to attest the social challenge
/// and to provide the shared secret that the Customer will use to recover
/// their account.
///
#[instrument(err, skip(social_challenge_service, _feature_flags_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/recovery/social-challenges/{social_challenge_id}",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("social_challenge_id" = AccountId, Path, description = "SocialChallengeId"),
    ),
    request_body = RespondToSocialChallengeRequest,
    responses(
        (status = 200, description = "Responded to social challenge", body=RespondToSocialChallengeResponse),
    ),
)]
pub async fn respond_to_social_challenge(
    Path((account_id, social_challenge_id)): Path<(AccountId, SocialChallengeId)>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    Json(request): Json<RespondToSocialChallengeRequest>,
) -> Result<Json<RespondToSocialChallengeResponse>, ApiError> {
    social_challenge_service
        .respond_to_social_challenge(RespondToSocialChallengeInput {
            trusted_contact_account_id: &account_id,
            social_challenge_id: &social_challenge_id,
            trusted_contact_recovery_pake_pubkey: &request.trusted_contact_recovery_pake_pubkey,
            recovery_pake_confirmation: &request.recovery_pake_confirmation,
            resealed_dek: &request.resealed_dek,
        })
        .await?;

    Ok(Json(RespondToSocialChallengeResponse {}))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct FetchSocialChallengeResponse {
    pub social_challenge: CustomerSocialChallenge,
}

///
/// Used by the Customer to fetch a Pending Social challenge.
///
/// The customer must provide a valid recovery authentication token
/// to check the status of the challenge.
///
#[instrument(
    err,
    skip(account_service, social_challenge_service, _feature_flags_service)
)]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/recovery/social-challenges/{social_challenge_id}",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("social_challenge_id" = AccountId, Path, description = "SocialChallengeId"),
    ),
    responses(
        (status = 200, description = "Social challenge", body=FetchSocialChallengeResponse),
    ),
)]
pub async fn fetch_social_challenge(
    Path((account_id, social_challenge_id)): Path<(AccountId, SocialChallengeId)>,
    State(account_service): State<AccountService>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
) -> Result<Json<FetchSocialChallengeResponse>, ApiError> {
    let customer_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let result = social_challenge_service
        .fetch_social_challenge_as_customer(FetchSocialChallengeAsCustomerInput {
            customer_account_id: &customer_account.id,
            social_challenge_id: &social_challenge_id,
        })
        .await?;

    Ok(Json(FetchSocialChallengeResponse {
        social_challenge: result.into(),
    }))
}
