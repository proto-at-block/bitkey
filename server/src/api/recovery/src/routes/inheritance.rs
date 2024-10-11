use account::service::{FetchAccountInput, Service as AccountService};

use axum::{
    extract::{Path, State},
    routing::{get, post, put},
    Json, Router,
};
use errors::ApiError;
use experimentation::claims::ExperimentationClaims;
use http_server::router::RouterBuilder;
use http_server::swagger::{SwaggerEndpoint, Url};
use notification::service::Service as NotificationService;
use serde::{Deserialize, Serialize};
use time::OffsetDateTime;
use tracing::instrument;
use types::account::entities::Account;
use types::account::identifiers::AccountId;
use types::account::keys::FullAccountAuthKeys;
use types::recovery::inheritance::claim::{
    InheritanceClaim, InheritanceClaimAuthKeys, InheritanceClaimCanceledBy, InheritanceClaimId,
    InheritanceDestination,
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
use utoipa::{OpenApi, ToSchema};

use crate::metrics;
use crate::service::inheritance::cancel_inheritance_claim::CancelInheritanceClaimInput;
use crate::service::inheritance::create_inheritance_claim::CreateInheritanceClaimInput;
use crate::service::inheritance::get_inheritance_claims::GetInheritanceClaimsInput;
use crate::service::inheritance::lock_inheritance_claim::LockInheritanceClaimInput;
use crate::service::inheritance::update_inheritance_claim_destination::UpdateInheritanceProcessWithDestinationInput;
use crate::service::inheritance::Service as InheritanceService;

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub AccountService,
    pub NotificationService,
    pub InheritanceService,
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
            .route_layer(metrics::FACTORY.route_layer("recovery".to_owned()))
            .with_state(self.to_owned())
    }

    fn recovery_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/recovery/inheritance/claims",
                get(get_inheritance_claims),
            )
            .route(
                "/api/accounts/:account_id/recovery/inheritance/claims/:inheritance_claim_id",
                put(update_inheritance_claim),
            )
            .route_layer(metrics::FACTORY.route_layer("recovery".to_owned()))
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
        update_inheritance_claim,
        upload_inheritance_packages,
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
            UploadInheritancePackagesRequest,
            UploadInheritancePackagesResponse,
            GetInheritanceClaimsResponse,
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
    Json(request): Json<CreateInheritanceClaimRequest>,
) -> Result<Json<CreateInheritanceClaimResponse>, ApiError> {
    let beneficiary_account = account_service
        .fetch_account(FetchAccountInput {
            account_id: &beneficiary_account_id,
        })
        .await?;
    if !matches!(beneficiary_account, Account::Full(_)) {
        return Err(ApiError::GenericForbidden(
            "Incorrect calling account type".to_string(),
        ));
    }

    let claim = inheritance_service
        .create_claim(CreateInheritanceClaimInput {
            beneficiary_account: &beneficiary_account,
            recovery_relationship_id: request.recovery_relationship_id,
            auth_keys: request.auth,
        })
        .await?;

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
    pub sealed_dek: String,
    pub sealed_mobile_key: String,
}
impl From<InheritancePackage> for Package {
    fn from(value: InheritancePackage) -> Self {
        Self {
            recovery_relationship_id: value.recovery_relationship_id,
            sealed_dek: value.sealed_dek,
            sealed_mobile_key: value.sealed_mobile_key,

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

    let Account::Full(_) = account else {
        return Err(ApiError::GenericForbidden(
            "Incorrect calling account type".to_string(),
        ));
    };

    inheritance_service
        .upload_packages(request.packages.into_iter().map(Package::from).collect())
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
    Json(request): Json<CancelInheritanceClaimRequest>,
) -> Result<Json<CancelInheritanceClaimResponse>, ApiError> {
    let account = account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    if !matches!(account, Account::Full(_)) {
        return Err(ApiError::GenericForbidden(
            "Incorrect calling account type".to_string(),
        ));
    }
    let (canceled_by, claim) = inheritance_service
        .cancel_claim(CancelInheritanceClaimInput {
            account: &account,
            inheritance_claim_id,
        })
        .await?;

    match canceled_by {
        InheritanceClaimCanceledBy::Benefactor => {
            return Ok(Json(CancelInheritanceClaimResponse::Benefactor {
                claim: claim.into(),
            }));
        }
        InheritanceClaimCanceledBy::Beneficiary => {
            return Ok(Json(CancelInheritanceClaimResponse::Beneficiary {
                claim: claim.into(),
            }));
        }
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct UpdateInheritanceProcessWithDestinationRequest {
    pub destination: InheritanceDestination,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct UpdateInheritanceProcessWithDestinationResponse {
    pub claim: InheritanceClaim,
}

///
/// This route is used by a beneficiary to update the destination for a
/// pending inheritance claim. To update it, the caller must:
/// 1) be the beneficiary
/// 2) the claim must be valid
/// 3) the claim must be pending
/// 4) the beneficiary must have a valid recovery relationship with the benefactor.
///
#[instrument(err, skip(account_service, inheritance_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/recovery/inheritance/claims/{inheritance_id}",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("inheritance_id" = InheritanceId, Path, description = "InheritanceId"),
    ),
    request_body = UpdateInheritanceProcessWithDestinationRequest,
    responses(
        (status = 200, description = "Updated inheritance process with new destination", body=UpdateInheritanceProcessWithDestinationResponse),
    ),
)]
pub async fn update_inheritance_claim(
    Path((beneficiary_account_id, inheritance_id)): Path<(AccountId, InheritanceClaimId)>,
    State(account_service): State<AccountService>,
    State(inheritance_service): State<InheritanceService>,
    experimentation_claims: ExperimentationClaims,
    Json(request): Json<UpdateInheritanceProcessWithDestinationRequest>,
) -> Result<Json<UpdateInheritanceProcessWithDestinationResponse>, ApiError> {
    let beneficiary_account = account_service
        .fetch_account(FetchAccountInput {
            account_id: &beneficiary_account_id,
        })
        .await?;

    if !matches!(beneficiary_account, Account::Full(_)) {
        return Err(ApiError::GenericForbidden(
            "Incorrect calling account type".to_string(),
        ));
    }

    let claim = inheritance_service
        .update_claim(UpdateInheritanceProcessWithDestinationInput {
            beneficiary_account: &beneficiary_account,
            inheritance_claim_id: inheritance_id,
            destination: request.destination,
            context_key: experimentation_claims.account_context_key()?,
        })
        .await?;

    Ok(Json(UpdateInheritanceProcessWithDestinationResponse {
        claim,
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct LockInheritanceClaimRequest {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub challenge: String,
    pub app_signature: String,
    pub hardware_signature: String,
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
/// 4) the beneficiary must have a valid recovery relationship with the benefactor.
/// 5) provide a valid challenge
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
    let beneficiary_account = account_service
        .fetch_account(FetchAccountInput {
            account_id: &beneficiary_account_id,
        })
        .await?;

    if !matches!(beneficiary_account, Account::Full(_)) {
        return Err(ApiError::GenericForbidden(
            "Incorrect calling account type".to_string(),
        ));
    }

    let claim = inheritance_service
        .lock(LockInheritanceClaimInput {
            inheritance_claim_id,
            beneficiary_account,
            challenge: request.challenge,
            app_signature: request.app_signature,
            hardware_signature: request.hardware_signature,
        })
        .await?;

    Ok(Json(LockInheritanceClaimResponse {
        claim: InheritanceClaim::Locked(claim).into(),
    }))
}
