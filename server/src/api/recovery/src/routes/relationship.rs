use axum::extract::Query;
use axum::routing::{delete, put};
use axum::Extension;
use axum::{
    extract::{Path, State},
    routing::{get, post},
    Json, Router,
};
use experimentation::claims::ExperimentationClaims;
use feature_flags::flag::evaluate_flag_value;
use serde::{Deserialize, Serialize};
use time::serde::rfc3339;
use time::OffsetDateTime;
use tracing::{event, instrument, Level};
use utoipa::{OpenApi, ToSchema};

use account::service::{FetchAccountInput, Service as AccountService};
use authn_authz::{Action, Authorization, AuthorizationRequirements};
use errors::ApiError;
use feature_flags::service::Service as FeatureFlagsService;
use http_server::router::RouterBuilder;
use http_server::swagger::{SwaggerEndpoint, Url};
use promotion_code::entities::CodeKey;
use promotion_code::service::Service as PromotionCodeService;
use types::account::entities::{Account, FullAccount, HardwareType};
use types::account::identifiers::AccountId;
use types::authn_authz::cognito::CognitoUser;
use types::recovery::social::relationship::RecoveryRelationshipEndorsement;
use types::recovery::social::relationship::{
    RecoveryRelationship, RecoveryRelationshipCommonFields, RecoveryRelationshipId,
};
use types::recovery::trusted_contacts::TrustedContactRole::SocialRecoveryContact;
use types::recovery::trusted_contacts::{TrustedContactInfo, TrustedContactRole};
use userpool::userpool::UserPoolService;

use crate::routes::{deserialize_pake_pubkey, INHERITANCE_ENABLED_FLAG_KEY};
use crate::service::inheritance::has_incompleted_claim::HasIncompletedClaimInput;
use crate::service::inheritance::Service as InheritanceService;
use crate::service::social::relationship::accept_recovery_relationship_invitation::AcceptRecoveryRelationshipInvitationInput;
use crate::service::social::relationship::create_recovery_relationship_invitation::CreateRecoveryRelationshipInvitationInput;
use crate::service::social::relationship::delete_recovery_relationship::DeleteRecoveryRelationshipInput;
use crate::service::social::relationship::endorse_recovery_relationships::EndorseRecoveryRelationshipsInput;
use crate::service::social::relationship::error::ServiceError;
use crate::service::social::relationship::get_recovery_relationship_invitation_for_code::GetRecoveryRelationshipInvitationForCodeInput;
use crate::service::social::relationship::get_recovery_relationships::GetRecoveryRelationshipsInput;
use crate::service::social::relationship::reissue_recovery_relationship_invitation::ReissueRecoveryRelationshipInvitationInput;
use crate::{
    error::RecoveryError, metrics,
    service::social::relationship::Service as RecoveryRelationshipService,
};

/// Maps trusted contact roles to the corresponding "add" action proof action.
fn add_action_for_roles(roles: &[TrustedContactRole]) -> Action {
    if roles.contains(&TrustedContactRole::Beneficiary) {
        Action::AddBeneficiary
    } else {
        Action::AddRecoveryContact
    }
}

/// Maps trusted contact roles to the corresponding "remove" action proof action
/// when the customer (relationship owner) is the one removing.
fn customer_remove_action_for_roles(roles: &[TrustedContactRole]) -> Action {
    if roles.contains(&TrustedContactRole::Beneficiary) {
        Action::RemoveBeneficiary
    } else {
        Action::RemoveRecoveryContact
    }
}

/// Maps trusted contact roles to the corresponding "remove" action proof action
/// when the trusted contact is the one removing themselves.
fn tc_remove_action_for_roles(roles: &[TrustedContactRole]) -> Action {
    if roles.contains(&TrustedContactRole::Beneficiary) {
        Action::RemoveBenefactor
    } else {
        Action::RemoveRecoveryCustomer
    }
}

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub AccountService,
    pub UserPoolService,
    pub RecoveryRelationshipService,
    pub FeatureFlagsService,
    pub PromotionCodeService,
    pub InheritanceService,
    pub repository::anti_replay::AntiReplayRepository,
);

impl RouterBuilder for RouteState {
    fn account_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/recovery/relationships",
                post(create_recovery_relationship),
            )
            .route(
                "/api/accounts/:account_id/relationships",
                post(create_relationship),
            )
            .route(
                "/api/accounts/:account_id/recovery/relationships",
                put(endorse_recovery_relationships),
            )
            .route(
                "/api/accounts/:account_id/recovery/backup",
                post(upload_recovery_backup),
            )
            .route(
                "/api/accounts/:account_id/recovery/backup",
                get(get_recovery_backup),
            )
            .route_layer(metrics::FACTORY.route_layer("recovery_relationship".to_owned()))
            .with_state(self.to_owned())
    }

    fn account_or_recovery_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/recovery/relationships/:recovery_relationship_id",
                delete(delete_recovery_relationship),
            )
            .route(
                "/api/accounts/:account_id/recovery/relationships",
                get(get_recovery_relationships),
            )
            .route(
                "/api/accounts/:account_id/relationships",
                get(get_relationships),
            )
            .route(
                "/api/accounts/:account_id/recovery/relationships/:recovery_relationship_id",
                put(update_recovery_relationship),
            )
            .route(
                "/api/accounts/:account_id/recovery/relationship-invitations/:code",
                get(get_recovery_relationship_invitation_for_code),
            )
            .route(
                "/api/accounts/:account_id/recovery/relationship-invitations/:code/promotion-code",
                get(get_promotion_code_for_invite_code),
            )
            .route_layer(metrics::FACTORY.route_layer("recovery_relationship".to_owned()))
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new(
                "Recovery Relationships",
                "/docs/recovery_relationship/openapi.json",
            ),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        create_recovery_relationship,
        create_relationship,
        delete_recovery_relationship,
        endorse_recovery_relationships,
        get_promotion_code_for_invite_code,
        get_recovery_backup,
        get_recovery_relationship_invitation_for_code,
        get_recovery_relationships,
        get_relationships,
        update_recovery_relationship,
        upload_recovery_backup,
    ),
    components(
        schemas(
            CreateRecoveryRelationshipRequest,
            CreateRelationshipRequest,
            CreateRelationshipResponse,
            CustomerRecoveryRelationshipView,
            CustomerRecoveryRelationshipStatus,
            EndorseRecoveryRelationshipsRequest,
            EndorseRecoveryRelationshipsResponse,
            EndorsedTrustedContact,
            GetPromotionCodeForInviteCodeResponse,
            GetRecoveryRelationshipInvitationForCodeResponse,
            GetRecoveryRelationshipsResponse,
            InboundInvitation,
            OutboundInvitation,
            RecoveryRelationshipEndorsement,
            TrustedContactRecoveryRelationshipView,
            TrustedContactRole,
            UnendorsedTrustedContact,
            UpdateRecoveryRelationshipRequest,
            UpdateRecoveryRelationshipResponse,
            UploadRecoveryBackupRequest,
            UploadRecoveryBackupResponse,
        )
    ),
    tags(
        (name = "Recovery Relationships", description = "Endpoints related to creating and managing recovery relationships used in Social Recovery and Inheritance"),
    )
)]
struct ApiDoc;

/// Returns the alias as a proof value if it passes action-proof validation,
/// or empty string if it doesn't (legacy aliases may contain mixed scripts,
/// exceed 128 bytes, etc.). Both app and server must use this same fallback
/// so the signed payload matches during verification. When the value is empty,
/// the hardware shows a generic "Confirm action" screen instead of the name.
fn alias_for_proof(alias: &str) -> &str {
    let trimmed = alias.trim();
    if trimmed.is_empty() || trimmed.chars().count() > 64 {
        return "";
    }
    for c in trimmed.chars() {
        if !is_hw_displayable(c) {
            return "";
        }
    }
    trimmed
}

/// Mirrors `TrustedContactAlias.isHwDisplayable()` in the app.
fn is_hw_displayable(c: char) -> bool {
    matches!(c,
        ' '..='~'                  // ASCII printable
        | '\u{00C0}'..='\u{00FF}'  // Latin-1 Supplement letters (À-ÿ)
        | '\u{0100}'..='\u{017F}'  // Latin Extended-A
        | '\u{0180}'..='\u{024F}'  // Latin Extended-B
    )
}

#[derive(Serialize, Deserialize, Debug, ToSchema, PartialEq)]
pub struct OutboundInvitation {
    #[serde(flatten)]
    pub recovery_relationship_info: TrustedContactRecoveryRelationshipView,
    pub code: String,
    pub code_bit_length: usize,
    #[serde(with = "rfc3339")]
    pub expires_at: OffsetDateTime,
}

impl TryFrom<RecoveryRelationship> for OutboundInvitation {
    type Error = RecoveryError;

    fn try_from(value: RecoveryRelationship) -> Result<Self, Self::Error> {
        match value {
            RecoveryRelationship::Invitation(invitation) => Ok(Self {
                recovery_relationship_info: invitation.common_fields.into(),
                code: invitation.code,
                code_bit_length: invitation.code_bit_length,
                expires_at: invitation.expires_at,
            }),
            _ => Err(RecoveryError::InvalidRecoveryRelationshipType),
        }
    }
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
pub struct InboundInvitation {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub recovery_relationship_roles: Vec<TrustedContactRole>,
    #[serde(with = "rfc3339")]
    pub expires_at: OffsetDateTime,
    pub protected_customer_enrollment_pake_pubkey: String,
}

impl TryFrom<RecoveryRelationship> for InboundInvitation {
    type Error = RecoveryError;

    fn try_from(value: RecoveryRelationship) -> Result<Self, Self::Error> {
        match value {
            RecoveryRelationship::Invitation(invitation) => Ok(Self {
                recovery_relationship_id: invitation.common_fields.id,
                recovery_relationship_roles: invitation.common_fields.trusted_contact_info.roles,
                expires_at: invitation.expires_at,
                protected_customer_enrollment_pake_pubkey: invitation
                    .protected_customer_enrollment_pake_pubkey,
            }),
            _ => Err(RecoveryError::InvalidRecoveryRelationshipType),
        }
    }
}

#[derive(Serialize, Deserialize, Debug, ToSchema, PartialEq)]
pub struct TrustedContactRecoveryRelationshipView {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub trusted_contact_alias: String,
    pub trusted_contact_roles: Vec<TrustedContactRole>,
}

impl From<RecoveryRelationshipCommonFields> for TrustedContactRecoveryRelationshipView {
    fn from(value: RecoveryRelationshipCommonFields) -> Self {
        Self {
            recovery_relationship_id: value.id,
            trusted_contact_alias: value.trusted_contact_info.alias,
            trusted_contact_roles: value.trusted_contact_info.roles,
        }
    }
}

#[derive(Serialize, Deserialize, Debug, ToSchema, PartialEq)]
pub struct UnendorsedTrustedContact {
    #[serde(flatten)]
    pub recovery_relationship_info: TrustedContactRecoveryRelationshipView,
    pub sealed_delegated_decryption_pubkey: String,
    pub trusted_contact_enrollment_pake_pubkey: String,
    pub enrollment_pake_confirmation: String,
}

impl TryFrom<RecoveryRelationship> for UnendorsedTrustedContact {
    type Error = RecoveryError;

    fn try_from(value: RecoveryRelationship) -> Result<Self, Self::Error> {
        match value {
            RecoveryRelationship::Unendorsed(connection) => Ok(Self {
                recovery_relationship_info: connection.common_fields.into(),
                sealed_delegated_decryption_pubkey: connection.sealed_delegated_decryption_pubkey,
                trusted_contact_enrollment_pake_pubkey: connection
                    .trusted_contact_enrollment_pake_pubkey,
                enrollment_pake_confirmation: connection.enrollment_pake_confirmation,
            }),
            _ => Err(RecoveryError::InvalidRecoveryRelationshipType),
        }
    }
}

#[derive(Serialize, Deserialize, Debug, ToSchema, PartialEq)]
pub struct EndorsedTrustedContact {
    #[serde(flatten)]
    pub recovery_relationship_info: TrustedContactRecoveryRelationshipView,
    pub delegated_decryption_pubkey_certificate: String,
}

impl TryFrom<RecoveryRelationship> for EndorsedTrustedContact {
    type Error = RecoveryError;

    fn try_from(value: RecoveryRelationship) -> Result<Self, Self::Error> {
        match value {
            RecoveryRelationship::Endorsed(connection) => Ok(Self {
                recovery_relationship_info: connection.common_fields.into(),
                delegated_decryption_pubkey_certificate: connection
                    .delegated_decryption_pubkey_certificate,
            }),
            _ => Err(RecoveryError::InvalidRecoveryRelationshipType),
        }
    }
}

#[derive(Serialize, Deserialize, Debug, ToSchema, PartialEq)]
pub struct CustomerRecoveryRelationshipView {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub customer_alias: String,
    pub trusted_contact_roles: Vec<TrustedContactRole>,
    pub relationship_status: CustomerRecoveryRelationshipStatus,
}

#[derive(Serialize, Deserialize, Debug, ToSchema, PartialEq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum CustomerRecoveryRelationshipStatus {
    Endorsed,
    Unendorsed,
}

impl TryFrom<RecoveryRelationship> for CustomerRecoveryRelationshipView {
    type Error = RecoveryError;

    fn try_from(value: RecoveryRelationship) -> Result<Self, Self::Error> {
        match value {
            RecoveryRelationship::Endorsed(connection) => Ok(Self {
                recovery_relationship_id: connection.common_fields.id,
                customer_alias: connection.connection_fields.customer_alias,
                trusted_contact_roles: connection.common_fields.trusted_contact_info.roles,
                relationship_status: CustomerRecoveryRelationshipStatus::Endorsed,
            }),
            RecoveryRelationship::Unendorsed(connection) => Ok(Self {
                recovery_relationship_id: connection.common_fields.id,
                customer_alias: connection.connection_fields.customer_alias,
                trusted_contact_roles: connection.common_fields.trusted_contact_info.roles,
                relationship_status: CustomerRecoveryRelationshipStatus::Unendorsed,
            }),
            _ => Err(RecoveryError::InvalidRecoveryRelationshipType),
        }
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
#[deprecated = "use CreateRelationshipRequest instead"]
pub struct CreateRecoveryRelationshipRequest {
    pub trusted_contact_alias: String,
    #[serde(deserialize_with = "deserialize_pake_pubkey")]
    pub protected_customer_enrollment_pake_pubkey: String,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CreateRelationshipRequest {
    pub trusted_contact_alias: String,
    pub trusted_contact_roles: Vec<TrustedContactRole>,
    #[serde(deserialize_with = "deserialize_pake_pubkey")]
    pub protected_customer_enrollment_pake_pubkey: String,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CreateRelationshipResponse {
    pub invitation: OutboundInvitation,
}

///
/// Used by FullAccounts to create a recovery relationship which is sent to
/// a Trusted Contact (either a FullAccount or a LiteAccount). The trusted contact
/// can then accept the relationship and become a trusted contact for the account.
///
#[instrument(
    err,
    skip(
        account_service,
        recovery_relationship_service,
        _feature_flags_service,
        anti_replay_repository
    )
)]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/relationships",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = CreateRecoveryRelationshipRequest,
    responses(
    (status = 200, description = "Account creates a recovery relationship", body=CreateRelationshipResponse),
    ),
)]
pub async fn create_relationship(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    State(anti_replay_repository): State<repository::anti_replay::AntiReplayRepository>,
    auth: Authorization,
    Json(request): Json<CreateRelationshipRequest>,
) -> Result<Json<CreateRelationshipResponse>, ApiError> {
    let full_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    let hardware_type = full_account
        .active_hardware_type()
        .map_err(|e| ApiError::GenericInternalApplicationError(e.to_string()))?;
    let action = add_action_for_roles(&request.trusted_contact_roles);

    let result = AuthorizationRequirements::new(action, hardware_type)
        .value(alias_for_proof(&request.trusted_contact_alias))
        .execute(&auth, &anti_replay_repository, |_ctx| async move {
            let trusted_contact = TrustedContactInfo::new(
                request.trusted_contact_alias,
                request.trusted_contact_roles,
            )
            .map_err(ServiceError::from)?;

            create_relationship_common(
                &full_account,
                &recovery_relationship_service,
                trusted_contact,
                &request.protected_customer_enrollment_pake_pubkey,
            )
            .await
        })
        .await?;

    Ok(Json(result))
}

#[instrument(
    err,
    skip(
        account_service,
        recovery_relationship_service,
        _feature_flags_service,
        anti_replay_repository
    )
)]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/recovery/relationships",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = CreateRecoveryRelationshipRequestForSocrec,
    responses(
        (status = 200, description = "Account creates a recovery relationship", body=CreateRecoveryRelationshipResponse),
    ),
)]
#[deprecated = "use create_relationship instead"]
pub async fn create_recovery_relationship(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    State(anti_replay_repository): State<repository::anti_replay::AntiReplayRepository>,
    auth: Authorization,
    Json(request): Json<CreateRecoveryRelationshipRequest>,
) -> Result<Json<CreateRelationshipResponse>, ApiError> {
    let full_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    let hardware_type = full_account
        .active_hardware_type()
        .map_err(|e| ApiError::GenericInternalApplicationError(e.to_string()))?;

    let response = AuthorizationRequirements::new(Action::AddRecoveryContact, hardware_type)
        .value(alias_for_proof(&request.trusted_contact_alias))
        .execute(&auth, &anti_replay_repository, |_ctx| async move {
            let trusted_contact =
                TrustedContactInfo::new(request.trusted_contact_alias, vec![SocialRecoveryContact])
                    .map_err(ServiceError::from)?;

            create_relationship_common(
                &full_account,
                &recovery_relationship_service,
                trusted_contact,
                &request.protected_customer_enrollment_pake_pubkey,
            )
            .await
        })
        .await?;

    Ok(Json(response))
}

async fn create_relationship_common(
    full_account: &FullAccount,
    recovery_relationship_service: &RecoveryRelationshipService,
    trusted_contact: TrustedContactInfo,
    protected_customer_enrollment_pake_pubkey: &str,
) -> Result<CreateRelationshipResponse, ApiError> {
    let result = recovery_relationship_service
        .create_recovery_relationship_invitation(CreateRecoveryRelationshipInvitationInput {
            customer_account: full_account,
            trusted_contact: &trusted_contact,
            protected_customer_enrollment_pake_pubkey,
        })
        .await?;
    Ok(CreateRelationshipResponse {
        invitation: result.try_into()?,
    })
}

///
/// This route is used by either the Customer or the Trusted Contact to delete a pending
/// or an established recovery relationship.
///
/// For Customers, they will need to provide:
/// - Account access token
/// - Both App and Hardware keyproofs
///
/// For Trusted Contacts, they will need to provide:
/// - Recovery access token
///
#[instrument(
    err,
    skip(
        account_service,
        recovery_relationship_service,
        feature_flags_service,
        inheritance_service,
        anti_replay_repository,
    )
)]
#[utoipa::path(
    delete,
    path = "/api/accounts/{account_id}/recovery/relationships/{recovery_relationship_id}",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("recovery_relationship_id" = AccountId, Path, description = "RecoveryRelationshipId"),
    ),
    responses(
        (status = 200, description = "Recovery relationship deleted"),
    ),
)]
pub async fn delete_recovery_relationship(
    Path((account_id, recovery_relationship_id)): Path<(AccountId, RecoveryRelationshipId)>,
    State(account_service): State<AccountService>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(feature_flags_service): State<FeatureFlagsService>,
    State(inheritance_service): State<InheritanceService>,
    State(anti_replay_repository): State<repository::anti_replay::AntiReplayRepository>,
    auth: Authorization,
    experimentation_claims: ExperimentationClaims,
    Extension(cognito_user): Extension<CognitoUser>,
) -> Result<(), ApiError> {
    let is_inheritance_enabled = experimentation_claims
        .account_context_key()
        .ok()
        .and_then(|context_key| {
            evaluate_flag_value(
                &feature_flags_service,
                INHERITANCE_ENABLED_FLAG_KEY,
                &context_key,
            )
            .ok()
        })
        .unwrap_or(false);

    if is_inheritance_enabled {
        let has_incomplete_claims = inheritance_service
            .has_incompleted_claim(HasIncompletedClaimInput {
                actor_account_id: &account_id,
                recovery_relationship_id: &recovery_relationship_id,
            })
            .await?;
        if has_incomplete_claims.as_benefactor {
            return Err(ApiError::GenericBadRequest(
                "Cannot delete relationship to beneficiary with pending claim".to_string(),
            ));
        } else if has_incomplete_claims.as_beneficiary {
            return Err(ApiError::GenericBadRequest(
                "Cannot delete relationship to benefactor with pending claim".to_string(),
            ));
        }
    }

    // Determine authorization based on account type.
    // Full accounts (customers) get action proof with contact name binding.
    // W3 TCs also need action proof for self-removal; W1 TCs use basic auth only.
    let account = account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    match account {
        Account::Full(full_account) => {
            let hardware_type = full_account
                .active_hardware_type()
                .map_err(|e| ApiError::GenericInternalApplicationError(e.to_string()))?;

            let relationship = recovery_relationship_service
                .get_recovery_relationship(&recovery_relationship_id)
                .await?;
            let roles = &relationship.common_fields().trusted_contact_info.roles;
            let is_customer = relationship.common_fields().customer_account_id == account_id;

            // Action and alias both depend on who is acting:
            //   - Customer → removes the TC, signs the TC's alias
            //   - TC → removes themselves, signs the customer's alias
            let (action, proof_alias) = if is_customer {
                (
                    customer_remove_action_for_roles(roles),
                    alias_for_proof(&relationship.common_fields().trusted_contact_info.alias),
                )
            } else {
                let customer_alias = match &relationship {
                    RecoveryRelationship::Unendorsed(r) => &r.connection_fields.customer_alias,
                    RecoveryRelationship::Endorsed(r) => &r.connection_fields.customer_alias,
                    RecoveryRelationship::Invitation(_) => {
                        // A TC cannot delete an invitation — they haven't accepted yet,
                        // so they have no relationship to delete.
                        return Err(ApiError::GenericBadRequest(
                            "Trusted contact cannot delete an invitation".to_string(),
                        ));
                    }
                };
                (
                    tc_remove_action_for_roles(roles),
                    alias_for_proof(customer_alias),
                )
            };

            // Conditional: The service conditionally enforces both-factor auth for active
            // relationship deletes when the actor has hardware.
            AuthorizationRequirements::new(action, hardware_type)
                .value(proof_alias)
                .entity_id(recovery_relationship_id.to_string())
                .proof(authn_authz::ProofRequirement::Conditional)
                .execute(&auth, &anti_replay_repository, |ctx| async move {
                    recovery_relationship_service
                        .delete_recovery_relationship(DeleteRecoveryRelationshipInput {
                            acting_account_id: &account_id,
                            recovery_relationship_id: &recovery_relationship_id,
                            signed_by_both_factors: ctx.app_signed() && ctx.hw_signed(),
                            acting_account_is_w3: matches!(hardware_type, HardwareType::W3),
                            cognito_user: &cognito_user,
                        })
                        .await?;
                    Ok::<_, ApiError>(())
                })
                .await?;
        }
        Account::Lite(_) | Account::Software(_) => {
            // Non-full accounts (trusted contacts) have no proof mechanism.
            recovery_relationship_service
                .delete_recovery_relationship(DeleteRecoveryRelationshipInput {
                    acting_account_id: &account_id,
                    recovery_relationship_id: &recovery_relationship_id,
                    signed_by_both_factors: false,
                    acting_account_is_w3: false,
                    cognito_user: &cognito_user,
                })
                .await?;
        }
    }

    Ok(())
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetRecoveryRelationshipsResponse {
    pub invitations: Vec<OutboundInvitation>,
    pub unendorsed_trusted_contacts: Vec<UnendorsedTrustedContact>,
    pub endorsed_trusted_contacts: Vec<EndorsedTrustedContact>,
    pub customers: Vec<CustomerRecoveryRelationshipView>,
}

///
/// This route is used by both Customers and Trusted Contacts to retrieve
/// recovery relationships.
///
/// Only returns relationships having a trusted_contact_role of SocialRecoveryContact
///
/// For Customers, we will show:
/// - All the Trusted Contacts that are protecting their account
/// - All the pending outbound invitations
/// - All the accounts that they are protecting as a Trusted Contact
///
/// For Trusted Contacts, we will show:
/// - All the accounts that they are protecting
///
#[instrument(err, skip(recovery_relationship_service, _feature_flags_service))]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/recovery/relationships",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "Recovery relationships", body=GetRecoveryRelationshipsResponse),
    ),
)]
#[deprecated = "use get_relationships instead"]
pub async fn get_recovery_relationships(
    Path(account_id): Path<AccountId>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
) -> Result<Json<GetRecoveryRelationshipsResponse>, ApiError> {
    let result = recovery_relationship_service
        .get_recovery_relationships(GetRecoveryRelationshipsInput {
            account_id: &account_id,
            trusted_contact_role_filter: Some(SocialRecoveryContact),
        })
        .await?;

    let unendorsed_trusted_contacts = result
        .unendorsed_trusted_contacts
        .into_iter()
        .map(|i| i.try_into())
        .collect::<Result<Vec<UnendorsedTrustedContact>, _>>()?;
    Ok(Json(GetRecoveryRelationshipsResponse {
        invitations: result
            .invitations
            .into_iter()
            .map(|i| i.try_into())
            .collect::<Result<Vec<_>, _>>()?,
        unendorsed_trusted_contacts,
        endorsed_trusted_contacts: result
            .endorsed_trusted_contacts
            .into_iter()
            .map(|i| i.try_into())
            .collect::<Result<Vec<_>, _>>()?,
        customers: result
            .customers
            .into_iter()
            .map(|i| i.try_into())
            .collect::<Result<Vec<_>, _>>()?,
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct GetRelationshipsRequest {
    trusted_contact_role: Option<TrustedContactRole>,
}

///
/// This route is used by both Customers and Trusted Contacts to retrieve relationships.
///
#[instrument(err, skip(recovery_relationship_service, _feature_flags_service))]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/relationships",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("trusted_contact_role" = TrustedContactRole, Query, description = "filter by trusted_contact_role"),
    ),
    responses(
        (status = 200, description = "Relationships", body=GetRecoveryRelationshipsResponse),
    ),
)]
pub async fn get_relationships(
    Path(account_id): Path<AccountId>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    request: Query<GetRelationshipsRequest>,
) -> Result<Json<GetRecoveryRelationshipsResponse>, ApiError> {
    let result = recovery_relationship_service
        .get_recovery_relationships(GetRecoveryRelationshipsInput {
            account_id: &account_id,
            trusted_contact_role_filter: request.trusted_contact_role,
        })
        .await?;

    Ok(Json(GetRecoveryRelationshipsResponse {
        invitations: try_into_vec(result.invitations)?,
        unendorsed_trusted_contacts: try_into_vec(result.unendorsed_trusted_contacts)?,
        endorsed_trusted_contacts: try_into_vec(result.endorsed_trusted_contacts)?,
        customers: try_into_vec(result.customers)?,
    }))
}

fn try_into_vec<T, U>(items: Vec<T>) -> Result<Vec<U>, <T as TryInto<U>>::Error>
where
    T: TryInto<U>,
{
    items.into_iter().map(TryInto::try_into).collect()
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(tag = "action")]
pub enum UpdateRecoveryRelationshipRequest {
    Accept {
        code: String,
        customer_alias: String,
        #[serde(deserialize_with = "deserialize_pake_pubkey")]
        trusted_contact_enrollment_pake_pubkey: String,
        enrollment_pake_confirmation: String,
        sealed_delegated_decryption_pubkey: String,
    },
    Reissue,
}

#[derive(Debug, Serialize, Deserialize, ToSchema, PartialEq)]
#[serde(untagged)]
pub enum UpdateRecoveryRelationshipResponse {
    Accept {
        customer: CustomerRecoveryRelationshipView,
    },
    Reissue {
        invitation: OutboundInvitation,
    },
}

///
/// This route is used by either Full Accounts or LiteAccounts to accept
/// an pending outbound invitation and to become a Trusted Contact.
///
#[instrument(
    err,
    skip(
        account_service,
        recovery_relationship_service,
        _feature_flags_service,
        anti_replay_repository
    )
)]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/recovery/relationships/{recovery_relationship_id}",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("recovery_relationship_id" = AccountId, Path, description = "RecoveryRelationshipId"),
    ),
    request_body = UpdateRecoveryRelationshipRequest,
    responses(
        (status = 200, description = "Recovery relationship updated", body=UpdateRecoveryRelationshipResponse),
    ),
)]
pub async fn update_recovery_relationship(
    Path((account_id, recovery_relationship_id)): Path<(AccountId, RecoveryRelationshipId)>,
    State(account_service): State<AccountService>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    State(anti_replay_repository): State<repository::anti_replay::AntiReplayRepository>,
    auth: Authorization,
    Extension(cognito_user): Extension<CognitoUser>,
    Json(request): Json<UpdateRecoveryRelationshipRequest>,
) -> Result<Json<UpdateRecoveryRelationshipResponse>, ApiError> {
    let account = account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    match request {
        UpdateRecoveryRelationshipRequest::Accept {
            code,
            customer_alias,
            trusted_contact_enrollment_pake_pubkey,
            enrollment_pake_confirmation,
            sealed_delegated_decryption_pubkey,
        } => {
            if CognitoUser::Recovery(account_id.clone()) != cognito_user {
                event!(
                    Level::ERROR,
                    "The provided access token is for the incorrect domain."
                );
                return Err(ApiError::GenericForbidden(
                    "The provided access token is for the incorrect domain.".to_string(),
                ));
            }
            let result = recovery_relationship_service
                .accept_recovery_relationship_invitation(
                    AcceptRecoveryRelationshipInvitationInput {
                        trusted_contact_account_id: &account_id,
                        recovery_relationship_id: &recovery_relationship_id,
                        code: &code,
                        customer_alias: &customer_alias,
                        trusted_contact_enrollment_pake_pubkey:
                            &trusted_contact_enrollment_pake_pubkey,
                        enrollment_pake_confirmation: &enrollment_pake_confirmation,
                        sealed_delegated_decryption_pubkey: &sealed_delegated_decryption_pubkey,
                    },
                )
                .await?;

            Ok(Json(UpdateRecoveryRelationshipResponse::Accept {
                customer: result.try_into()?,
            }))
        }
        UpdateRecoveryRelationshipRequest::Reissue => {
            let Account::Full(full_account) = account else {
                return Err(ApiError::GenericForbidden(
                    "Incorrect calling account type".to_string(),
                ));
            };

            if !cognito_user.is_app(&account_id) && !cognito_user.is_hardware(&account_id) {
                event!(
                    Level::ERROR,
                    "The provided access token is for the incorrect domain."
                );
                return Err(ApiError::GenericForbidden(
                    "The provided access token is for the incorrect domain.".to_string(),
                ));
            }

            let hardware_type = full_account
                .active_hardware_type()
                .map_err(|e| ApiError::GenericInternalApplicationError(e.to_string()))?;

            // Fetch the relationship to get the contact name and roles for proof binding.
            // Reissue uses the same add action as create — it's logically re-inviting.
            let relationship = recovery_relationship_service
                .get_recovery_relationship(&recovery_relationship_id)
                .await?;
            let common = relationship.common_fields();
            let action = add_action_for_roles(&common.trusted_contact_info.roles);
            let contact_name = alias_for_proof(&common.trusted_contact_info.alias);

            let result = AuthorizationRequirements::new(action, hardware_type)
                .value(contact_name)
                .entity_id(recovery_relationship_id.to_string())
                .execute(&auth, &anti_replay_repository, |_ctx| async move {
                    let result = recovery_relationship_service
                        .reissue_recovery_relationship_invitation(
                            ReissueRecoveryRelationshipInvitationInput {
                                customer_account: &full_account,
                                recovery_relationship_id: &recovery_relationship_id,
                            },
                        )
                        .await?;

                    let invitation: OutboundInvitation = result.try_into()?;
                    Ok::<_, ApiError>(invitation)
                })
                .await?;

            Ok(Json(UpdateRecoveryRelationshipResponse::Reissue {
                invitation: result,
            }))
        }
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct EndorseRecoveryRelationshipsRequest {
    pub endorsements: Vec<RecoveryRelationshipEndorsement>,
}

#[derive(Debug, Serialize, Deserialize, ToSchema, PartialEq)]
pub struct EndorseRecoveryRelationshipsResponse {
    pub unendorsed_trusted_contacts: Vec<UnendorsedTrustedContact>,
    pub endorsed_trusted_contacts: Vec<EndorsedTrustedContact>,
}

///
/// This route is used by Full Accounts to endorse recovery relationships
/// that are accepted by the Trusted Contact
///
#[instrument(
    err,
    skip(account_service, recovery_relationship_service, _feature_flags_service)
)]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/recovery/relationships",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = EndorseRecoveryRelationshipsRequest,
    responses(
        (status = 200, description = "Recovery relationships endorsed", body=EndorseRecoveryRelationshipsResponse),
    ),
)]
pub async fn endorse_recovery_relationships(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    _auth: Authorization,
    Json(request): Json<EndorseRecoveryRelationshipsRequest>,
) -> Result<Json<EndorseRecoveryRelationshipsResponse>, ApiError> {
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

    recovery_relationship_service
        .endorse_recovery_relationships(EndorseRecoveryRelationshipsInput {
            customer_account_id: &account_id,
            endorsements: request.endorsements,
        })
        .await?;
    let result = recovery_relationship_service
        .get_recovery_relationships(GetRecoveryRelationshipsInput {
            account_id: &account_id,
            trusted_contact_role_filter: None,
        })
        .await?;

    Ok(Json(EndorseRecoveryRelationshipsResponse {
        unendorsed_trusted_contacts: result
            .unendorsed_trusted_contacts
            .into_iter()
            .map(|i| i.try_into())
            .collect::<Result<Vec<_>, _>>()?,
        endorsed_trusted_contacts: result
            .endorsed_trusted_contacts
            .into_iter()
            .map(|i| i.try_into())
            .collect::<Result<Vec<_>, _>>()?,
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct GetRecoveryRelationshipInvitationForCodeRequest {
    pub expected_role: Option<TrustedContactRole>,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetRecoveryRelationshipInvitationForCodeResponse {
    pub invitation: InboundInvitation,
}

///
/// This route is used by either FullAccounts or LiteAccounts to retrieve
/// the details of a pending inbound invitation.
///
#[instrument(err, skip(recovery_relationship_service, _feature_flags_service))]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/recovery/relationship-invitations/{code}",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("code" = String, Path, description = "Code"),
        ("expected_role" = Option<TrustedContactRole>, Query, description = "Expected role for the invite code"),
    ),
    responses(
        (status = 200, description = "Recovery relationship invitation", body=GetRecoveryRelationshipInvitationForCodeResponse),
    ),
)]
pub async fn get_recovery_relationship_invitation_for_code(
    Path((account_id, code)): Path<(AccountId, String)>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    request: Query<GetRecoveryRelationshipInvitationForCodeRequest>,
) -> Result<Json<GetRecoveryRelationshipInvitationForCodeResponse>, ApiError> {
    let result = recovery_relationship_service
        .get_recovery_relationship_invitation_for_code(
            GetRecoveryRelationshipInvitationForCodeInput {
                code: &code,
                expected_role: request.expected_role,
            },
        )
        .await?;

    Ok(Json(GetRecoveryRelationshipInvitationForCodeResponse {
        invitation: result.try_into()?,
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct UploadRecoveryBackupRequest {
    pub recovery_backup_material: String,
}
#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct UploadRecoveryBackupResponse {}
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/recovery/backup",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = UploadRecoveryBackupRequest,
    responses(
        (status = 200, description = "Upload successful", body=UploadRecoveryBackupResponse),
    ),
)]
pub async fn upload_recovery_backup(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    Json(request): Json<UploadRecoveryBackupRequest>,
) -> Result<Json<UploadRecoveryBackupResponse>, ApiError> {
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
    recovery_relationship_service
        .upload_recovery_backup(account_id, request.recovery_backup_material)
        .await?;
    Ok(Json(UploadRecoveryBackupResponse {}))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct GetRecoveryBackupResponse {
    pub recovery_backup_material: String,
}
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/recovery/backup",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "Found recovery backup", body=GetRecoveryBackupResponse),
    ),
)]
pub async fn get_recovery_backup(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
) -> Result<Json<GetRecoveryBackupResponse>, ApiError> {
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
    let backup = recovery_relationship_service
        .get_recovery_backup(account_id)
        .await?;

    Ok(Json(GetRecoveryBackupResponse {
        recovery_backup_material: backup.material,
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct GetPromotionCodeForInviteCodeResponse {
    pub code: Option<String>,
}

#[instrument(skip(recovery_relationship_service, promotion_code_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/recovery/relationship-invitations/{invite_code}/promotion-code",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("invite_code" = String, Path, description = "Invite code"),
    ),
    responses(
        (status = 200, description = "Returns the promotion code for the account if it exists", body=GetPromotionCodeForInviteCodeResponse),
        (status = 404, description = "Invitation not found for the invite code"),
        (status = 500, description = "Internal server error when retrieving promotion code")
    ),
)]
async fn get_promotion_code_for_invite_code(
    Path((account_id, invite_code)): Path<(AccountId, String)>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(promotion_code_service): State<PromotionCodeService>,
) -> Result<Json<GetPromotionCodeForInviteCodeResponse>, ApiError> {
    let recovery_relationship = recovery_relationship_service
        .get_recovery_relationship_invitation_for_code(
            GetRecoveryRelationshipInvitationForCodeInput {
                code: &invite_code,
                expected_role: None,
            },
        )
        .await?;

    let benefactor_account_id = recovery_relationship
        .common_fields()
        .customer_account_id
        .to_owned();
    let code_key = if account_id == benefactor_account_id {
        CodeKey::inheritance_benefactor(benefactor_account_id)
    } else {
        CodeKey::inheritance_beneficiary(benefactor_account_id)
    };
    let code = promotion_code_service
        .get(&code_key)
        .await?
        .filter(|c| !c.is_redeemed)
        .map(|c| c.code);

    Ok(Json(GetPromotionCodeForInviteCodeResponse { code }))
}
