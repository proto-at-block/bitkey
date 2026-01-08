use std::str::FromStr;
use std::sync::Arc;

use axum::{
    extract::{Path, State},
    routing::{get, post, put},
    Json, Router,
};

use account::service::{FetchAccountInput, Service as AccountService};
use analytics::{
    destination::tracker::Tracker,
    log_server_event,
    routes::definitions::{ActionServer, ServerEvent, ServerInheritanceInfo},
};
use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::{generate_electrum_rpc_uris, TransactionBroadcasterTrait};
use errors::ApiError;
use experimentation::claims::ExperimentationClaims;
use feature_flags::service::Service as FeatureFlagsService;
use http_server::router::RouterBuilder;
use http_server::swagger::{SwaggerEndpoint, Url};
use mobile_pay::signing_processor::SigningProcessor;
use notification::service::Service as NotificationService;
use serde::{Deserialize, Serialize};
use time::OffsetDateTime;
use tracing::instrument;
use types::account::identifiers::AccountId;
use types::account::keys::FullAccountAuthKeys;
use types::recovery::inheritance::claim::{
    InheritanceClaim, InheritanceClaimAuthKeys, InheritanceClaimId,
};
use types::recovery::inheritance::package::Package;
use types::recovery::inheritance::router::{
    BenefactorInheritanceClaimView, BenefactorInheritanceClaimViewCanceled,
    BenefactorInheritanceClaimViewLocked, BenefactorInheritanceClaimViewPending,
    BeneficiaryInheritanceClaimView, BeneficiaryInheritanceClaimViewCanceled,
    BeneficiaryInheritanceClaimViewLocked, BeneficiaryInheritanceClaimViewPending,
    InheritanceClaimViewCommonFields, InheritanceClaimViewPendingCommonFields,
};
use types::recovery::social::relationship::RecoveryRelationshipId;
use types::{account::entities::Account, recovery::inheritance::claim::InheritanceRole};
use utoipa::{OpenApi, ToSchema};
use wsm_rust_client::WsmClient;

use crate::service::inheritance::get_inheritance_claims::GetInheritanceClaimsInput;
use crate::service::inheritance::lock_inheritance_claim::LockInheritanceClaimInput;
use crate::service::inheritance::Service as InheritanceService;
use crate::service::inheritance::{
    cancel_inheritance_claim::CancelInheritanceClaimInput,
    complete_inheritance_claim::{CompleteWithoutPsbtInput, SignAndCompleteInheritanceClaimInput},
};
use crate::service::inheritance::{
    create_inheritance_claim::CreateInheritanceClaimInput, packages::UploadPackagesInput,
};
use crate::{metrics, service::inheritance::shorten_delay::ShortenDelayForBeneficiaryInput};

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub AccountService,
    pub NotificationService,
    pub InheritanceService,
    pub FeatureFlagsService,
    pub WsmClient,
    pub Arc<dyn TransactionBroadcasterTrait>,
    pub Tracker,
);

impl RouterBuilder for RouteState {
    fn unauthed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/recovery/inheritance/claims/:inheritance_claim_id/lock",
                put(lock_inheritance_claim),
            )
            .route_layer(metrics::FACTORY.route_layer("recovery".to_owned()))
            .with_state(self.to_owned())
    }

    fn account_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/recovery/inheritance/claims",
                post(create_inheritance_claim),
            )
            .route(
                "/api/accounts/:account_id/recovery/inheritance/packages",
                post(upload_inheritance_packages),
            )
            .route(
                "/api/accounts/:account_id/recovery/inheritance/claims/:inheritance_claim_id/cancel",
                post(cancel_inheritance_claim),
            )
            .route(
                "/api/accounts/:account_id/recovery/inheritance/claims/:inheritance_claim_id/complete",
                put(complete_inheritance_claim),
            )
            .route(
                "/api/accounts/:account_id/recovery/inheritance/claims/:inheritance_claim_id/complete-without-psbt",
                put(complete_without_psbt_inheritance_claim),
            )
            .route(
                "/api/accounts/:account_id/recovery/inheritance/claims/:inheritance_claim_id/shorten",
                put(shorten_delay_for_test_beneficiary_account),
            )
            .route_layer(metrics::FACTORY.route_layer("inheritance".to_owned()))
            .with_state(self.to_owned())
    }

    fn recovery_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/recovery/inheritance/claims",
                get(get_inheritance_claims),
            )
            .route_layer(metrics::FACTORY.route_layer("inheritance".to_owned()))
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Inheritance", "/docs/inheritance/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        cancel_inheritance_claim,
        create_inheritance_claim,
        get_inheritance_claims,
        upload_inheritance_packages,
        lock_inheritance_claim,
        complete_inheritance_claim,
    ),
    components(
        schemas(
            BenefactorInheritanceClaimView,
            BenefactorInheritanceClaimViewCanceled,
            BenefactorInheritanceClaimViewPending,
            BenefactorInheritanceClaimViewLocked,
            BeneficiaryInheritanceClaimView,
            BeneficiaryInheritanceClaimViewPending,
            BeneficiaryInheritanceClaimViewCanceled,
            BeneficiaryInheritanceClaimViewLocked,
            CancelInheritanceClaimRequest,
            CancelInheritanceClaimResponse,
            CreateInheritanceClaimRequest,
            CreateInheritanceClaimResponse,
            GetInheritanceClaimsResponse,
            LockInheritanceClaimRequest,
            LockInheritanceClaimResponse,
            CompleteInheritanceClaimRequest,
            CompleteInheritanceClaimResponse,
            FullAccountAuthKeys,
            InheritanceClaimAuthKeys,
            InheritanceClaimViewCommonFields,
            InheritanceClaimViewPendingCommonFields,
            InheritancePackage,
        )
    ),
    tags(
        (name = "Inheritance", description = "Endpoints related to inheritance claims by a beneficiary to receive their disbursement after a benefactor has passed away."),
    )
)]
struct ApiDoc;

async fn log_inheritance_event(tracker: Tracker, account_id: AccountId, claim: &InheritanceClaim) {
    let inheritance_info = ServerInheritanceInfo {
        relationship_id: claim.common_fields().recovery_relationship_id.to_string(),
        claim_id: claim.common_fields().id.to_string(),
    };
    let action = match claim {
        InheritanceClaim::Pending(_) => ActionServer::InheritanceClaimSubmitted,
        InheritanceClaim::Completed(_) => ActionServer::InheritanceClaimCompleted,
        InheritanceClaim::Canceled(_) => ActionServer::InheritanceClaimDenied,
        _ => ActionServer::Unspecified,
    };
    let event = ServerEvent {
        action: action.into(),
        account_id: account_id.to_string(),
        event_time: OffsetDateTime::now_utc().unix_timestamp().to_string(),
        inheritance_info: Some(inheritance_info),
    };
    log_server_event(tracker, event).await;
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct CreateInheritanceClaimRequest {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub auth: InheritanceClaimAuthKeys,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct CreateInheritanceClaimResponse {
    pub claim: BeneficiaryInheritanceClaimView,
}

///
/// This route is used by a beneficiary to start an inheritance claim
/// to claim funds from a deceased benefactor. The beneficiary must provide
/// the recovery relationship id and the auth keys to start the claim.
///
#[instrument(err, skip(account_service, inheritance_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/recovery/inheritance/claims",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = CreateInheritanceClaimRequest,
    responses(
        (status = 200, description = "Created a new inheritance claim", body=CreateInheritanceClaimResponse),
    ),
)]
pub async fn create_inheritance_claim(
    Path(beneficiary_account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(inheritance_service): State<InheritanceService>,
    State(tracker): State<Tracker>,
    Json(request): Json<CreateInheritanceClaimRequest>,
) -> Result<Json<CreateInheritanceClaimResponse>, ApiError> {
    let Account::Full(beneficiary_account) = &account_service
        .fetch_account(FetchAccountInput {
            account_id: &beneficiary_account_id,
        })
        .await?
    else {
        return Err(ApiError::GenericForbidden(
            "Incorrect calling account type".to_string(),
        ));
    };

    let claim = inheritance_service
        .create_claim(CreateInheritanceClaimInput {
            beneficiary_account,
            recovery_relationship_id: request.recovery_relationship_id,
            auth_keys: request.auth,
        })
        .await?;

    log_inheritance_event(tracker, beneficiary_account_id, &claim).await;
    Ok(Json(CreateInheritanceClaimResponse {
        claim: claim.into(),
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetInheritanceClaimsResponse {
    pub claims_as_benefactor: Vec<BenefactorInheritanceClaimView>,
    pub claims_as_beneficiary: Vec<BeneficiaryInheritanceClaimView>,
}

///
/// This route is used by both Benefactors and Beneficiaries to retrieve
/// inheritance claims.
///
/// For Benefactors, we will show:
/// - All the inheritance claims for which they are a benefactor
///
/// For Trusted Contacts, we will show:
/// - All the inheritance claims for which they are a beneficiary
///
#[instrument(err, skip(inheritance_service))]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/recovery/inheritance/claims",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "Inheritance claims", body=GetInheritanceClaimsResponse),
    ),
)]
pub async fn get_inheritance_claims(
    Path(account_id): Path<AccountId>,
    State(inheritance_service): State<InheritanceService>,
) -> Result<Json<GetInheritanceClaimsResponse>, ApiError> {
    let result = inheritance_service
        .get_inheritance_claims(GetInheritanceClaimsInput {
            account_id: &account_id,
        })
        .await?;

    Ok(Json(GetInheritanceClaimsResponse {
        claims_as_benefactor: result
            .claims_as_benefactor
            .into_iter()
            .map(|c| c.into())
            .collect(),
        claims_as_beneficiary: result
            .claims_as_beneficiary
            .into_iter()
            .map(|c| c.into())
            .collect(),
    }))
}

#[derive(Serialize, Deserialize, ToSchema, Debug, Clone)]
pub struct InheritancePackage {
    pub recovery_relationship_id: RecoveryRelationshipId,
    // The dek is sealed using DH between an ephemeral key pair and the beneficiary's
    // delegated decryption key. The app(mobile) key and descriptor are encrypted using the dek.
    pub sealed_dek: String,
    pub sealed_mobile_key: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub sealed_descriptor: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub sealed_server_root_xpub: Option<String>,
}
impl From<InheritancePackage> for Package {
    fn from(value: InheritancePackage) -> Self {
        Self {
            recovery_relationship_id: value.recovery_relationship_id,
            sealed_dek: value.sealed_dek,
            sealed_mobile_key: value.sealed_mobile_key,
            sealed_descriptor: value.sealed_descriptor,
            sealed_server_root_xpub: value.sealed_server_root_xpub,

            updated_at: OffsetDateTime::now_utc(),
            created_at: OffsetDateTime::now_utc(),
        }
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct UploadInheritancePackagesRequest {
    pub packages: Vec<InheritancePackage>,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct UploadInheritancePackagesResponse {}

#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/recovery/inheritance/packages",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = UploadInheritancePackagesRequest,
    responses(
        (status = 200, description = "Upload successful", body=UploadInheritancePackagesResponse),
    ),
)]
pub async fn upload_inheritance_packages(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(inheritance_service): State<InheritanceService>,
    Json(request): Json<UploadInheritancePackagesRequest>,
) -> Result<Json<UploadInheritancePackagesResponse>, ApiError> {
    let account = account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let Account::Full(full_account) = &account else {
        return Err(ApiError::GenericForbidden(
            "Incorrect calling account type".to_string(),
        ));
    };

    let packages = request.packages.into_iter().map(Package::from).collect();

    inheritance_service
        .upload_packages(UploadPackagesInput {
            benefactor_full_account: full_account,
            packages,
        })
        .await?;

    Ok(Json(UploadInheritancePackagesResponse {}))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct CancelInheritanceClaimRequest {}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(untagged)]
pub enum CancelInheritanceClaimResponse {
    Benefactor {
        claim: BenefactorInheritanceClaimView,
    },
    Beneficiary {
        claim: BeneficiaryInheritanceClaimView,
    },
}

///
/// This route is used by the benefactor or beneficiary to cancel an
/// inheritance claim. For both, an inheritance claim id must be provided.
///
#[instrument(err, skip(account_service, inheritance_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/recovery/inheritance/claims/{inheritance_claim_id}/cancel",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("inheritance_claim_id" = InheritanceClaimId, Path, description = "Identifier for the inheritance claim"),
    ),
    request_body = CancelInheritanceClaimRequest,
    responses(
        (status = 200, description = "Cancel the inheritance claim", body=CancelInheritanceClaimResponse),
    ),
)]
pub async fn cancel_inheritance_claim(
    Path((account_id, inheritance_claim_id)): Path<(AccountId, InheritanceClaimId)>,
    State(account_service): State<AccountService>,
    State(inheritance_service): State<InheritanceService>,
    State(tracker): State<Tracker>,
    Json(request): Json<CancelInheritanceClaimRequest>,
) -> Result<Json<CancelInheritanceClaimResponse>, ApiError> {
    let Account::Full(account) = account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?
    else {
        return Err(ApiError::GenericForbidden(
            "Incorrect calling account type".to_string(),
        ));
    };
    let (canceled_by, claim) = inheritance_service
        .cancel_claim(CancelInheritanceClaimInput {
            account: &account,
            inheritance_claim_id,
        })
        .await?;

    log_inheritance_event(tracker, account_id, &claim).await;

    match canceled_by {
        InheritanceRole::Benefactor => {
            return Ok(Json(CancelInheritanceClaimResponse::Benefactor {
                claim: claim.into(),
            }));
        }
        InheritanceRole::Beneficiary => {
            return Ok(Json(CancelInheritanceClaimResponse::Beneficiary {
                claim: claim.into(),
            }));
        }
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct LockInheritanceClaimRequest {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub challenge: String,
    pub app_signature: String,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct LockInheritanceClaimResponse {
    pub claim: BeneficiaryInheritanceClaimView,
}

///
/// This route is used by a beneficiary to lock the pending inheritance claim.
/// To lock it, the caller must:
/// 1) be the beneficiary
/// 2) the claim must be valid
/// 3) the claim must be pending
/// 4) it must be after the delay_end_time of the pending claim
/// 5) the beneficiary must have a valid recovery relationship with the benefactor.
/// 6) provide a valid challenge
///
#[instrument(err, skip(account_service, inheritance_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/recovery/inheritance/claims/{inheritance_claim_id}/lock",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("inheritance_id" = InheritanceId, Path, description = "InheritanceId"),
    ),
    request_body = LockInheritanceClaimRequest,
    responses(
        (status = 200, description = "Locked inheritance claim", body=LockInheritanceClaimResponse),
    ),
)]
pub async fn lock_inheritance_claim(
    Path((beneficiary_account_id, inheritance_claim_id)): Path<(AccountId, InheritanceClaimId)>,
    State(account_service): State<AccountService>,
    State(inheritance_service): State<InheritanceService>,
    Json(request): Json<LockInheritanceClaimRequest>,
) -> Result<Json<LockInheritanceClaimResponse>, ApiError> {
    let Account::Full(beneficiary_account) = account_service
        .fetch_account(FetchAccountInput {
            account_id: &beneficiary_account_id,
        })
        .await?
    else {
        return Err(ApiError::GenericForbidden(
            "Incorrect calling account type".to_string(),
        ));
    };
    let claim = inheritance_service
        .lock(LockInheritanceClaimInput {
            inheritance_claim_id,
            beneficiary_account,
            challenge: request.challenge,
            app_signature: request.app_signature,
        })
        .await?;

    Ok(Json(LockInheritanceClaimResponse {
        claim: InheritanceClaim::Locked(claim).into(),
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct CompleteInheritanceClaimRequest {
    pub psbt: String,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct CompleteInheritanceClaimResponse {
    pub claim: BeneficiaryInheritanceClaimView,
}

///
/// This route is used by a beneficiary to complete the locked inheritance claim.
/// To complete it, the caller must:
/// 1) be the beneficiary
/// 2) the claim must be valid & locked
/// 3) the beneficiary must have a valid recovery relationship with the benefactor.
/// 4) provide a valid singly signed psbt
/// 5) In case of RBF, the PSBT must have the same receiver address.
///
#[instrument(err, skip(account_service, inheritance_service, feature_flags_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/recovery/inheritance/claims/{inheritance_claim_id}/complete",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("inheritance_id" = InheritanceId, Path, description = "InheritanceId"),
    ),
    request_body = CompleteInheritanceClaimRequest,
    responses(
        (status = 200, description = "Completed inheritance claim", body=CompleteInheritanceClaimResponse),
    ),
)]
pub async fn complete_inheritance_claim(
    Path((beneficiary_account_id, inheritance_claim_id)): Path<(AccountId, InheritanceClaimId)>,
    State(account_service): State<AccountService>,
    State(inheritance_service): State<InheritanceService>,
    State(wsm_client): State<WsmClient>,
    State(transaction_broadcaster): State<Arc<dyn TransactionBroadcasterTrait>>,
    State(feature_flags_service): State<FeatureFlagsService>,
    State(tracker): State<Tracker>,
    experimentation_claims: ExperimentationClaims,
    Json(request): Json<CompleteInheritanceClaimRequest>,
) -> Result<Json<CompleteInheritanceClaimResponse>, ApiError> {
    let Account::Full(beneficiary_account) = &account_service
        .fetch_account(FetchAccountInput {
            account_id: &beneficiary_account_id,
        })
        .await?
    else {
        return Err(ApiError::GenericForbidden(
            "Incorrect calling account type".to_string(),
        ));
    };
    let context_key = experimentation_claims.app_installation_context_key().ok();
    let rpc_uris = generate_electrum_rpc_uris(&feature_flags_service, context_key);

    let signing_processor = SigningProcessor::new(
        Arc::new(wsm_client),
        feature_flags_service,
        transaction_broadcaster,
    );

    let psbt = Psbt::from_str(&request.psbt)
        .map_err(|_| ApiError::GenericBadRequest("Invalid PSBT".to_string()))?;

    let completed_claim = inheritance_service
        .sign_and_complete(SignAndCompleteInheritanceClaimInput {
            signing_processor,
            rpc_uris,
            inheritance_claim_id,
            beneficiary_account,
            psbt,
            context_key: experimentation_claims.account_context_key().ok(),
        })
        .await?;

    let claim = InheritanceClaim::Completed(completed_claim);
    log_inheritance_event(tracker, beneficiary_account_id, &claim).await;

    Ok(Json(CompleteInheritanceClaimResponse {
        claim: claim.into(),
    }))
}

///
/// This route is used by a beneficiary to complete the locked inheritance claim without providing a PSBT.
/// This is used when the beneficiary has no funds to claim.
///
/// To complete it, the caller must:
/// 1) be the beneficiary
/// 2) the claim must be valid & locked
/// 3) the beneficiary must have a valid recovery relationship with the benefactor.
///
#[instrument(err, skip(account_service, inheritance_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/recovery/inheritance/claims/{inheritance_claim_id}/complete-without-psbt",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("inheritance_id" = InheritanceId, Path, description = "InheritanceId"),
    ),
    request_body = CompleteInheritanceClaimRequest,
    responses(
        (status = 200, description = "Completed inheritance claim", body=CompleteInheritanceClaimResponse),
    ),
)]
pub async fn complete_without_psbt_inheritance_claim(
    Path((beneficiary_account_id, inheritance_claim_id)): Path<(AccountId, InheritanceClaimId)>,
    State(account_service): State<AccountService>,
    State(inheritance_service): State<InheritanceService>,
) -> Result<Json<CompleteInheritanceClaimResponse>, ApiError> {
    let beneficiary_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &beneficiary_account_id,
        })
        .await?;
    let claim = inheritance_service
        .complete_without_psbt(CompleteWithoutPsbtInput {
            inheritance_claim_id,
            beneficiary_account: &beneficiary_account,
        })
        .await?;

    Ok(Json(CompleteInheritanceClaimResponse {
        claim: InheritanceClaim::Completed(claim).into(),
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct ShortenDelayForBeneficiaryRequest {
    pub delay_period_seconds: i64,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct ShortenDelayForBeneficiaryResponse {
    pub claim: BeneficiaryInheritanceClaimView,
}

#[instrument(err, skip(inheritance_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/recovery/inheritance/claims/{inheritance_claim_id}/shorten",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("inheritance_id" = InheritanceId, Path, description = "InheritanceId"),
    ),
    request_body = ShortenDelayForBeneficiaryRequest,
    responses(
        (status = 200, description = "Shortened inheritance claim delay", body=ShortenDelayForBeneficiaryResponse),
    ),
)]
pub async fn shorten_delay_for_test_beneficiary_account(
    Path((beneficiary_account_id, inheritance_claim_id)): Path<(AccountId, InheritanceClaimId)>,
    State(inheritance_service): State<InheritanceService>,
    Json(request): Json<ShortenDelayForBeneficiaryRequest>,
) -> Result<Json<ShortenDelayForBeneficiaryResponse>, ApiError> {
    let claim = inheritance_service
        .shorten_delay_for_beneficiary(ShortenDelayForBeneficiaryInput {
            inheritance_claim_id,
            beneficiary_account_id,
            delay_period_seconds: request.delay_period_seconds,
        })
        .await?;
    Ok(Json(ShortenDelayForBeneficiaryResponse {
        claim: claim.into(),
    }))
}
