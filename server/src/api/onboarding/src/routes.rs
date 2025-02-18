use std::collections::HashSet;
use std::env;
use std::str::FromStr;

use account::service::CreateSoftwareAccountInput;
use account::service::{
    ActivateTouchpointForAccountInput, AddPushTouchpointToAccountInput, CompleteOnboardingInput,
    CreateAccountAndKeysetsInput, CreateInactiveSpendingKeysetInput, CreateLiteAccountInput,
    DeleteAccountInput, FetchAccountInput, FetchOrCreateEmailTouchpointInput,
    FetchOrCreatePhoneTouchpointInput, FetchTouchpointByIdInput,
    PutInactiveSpendingDistributedKeyInput, RotateToSpendingKeyDefinitionInput,
    RotateToSpendingKeysetInput, Service as AccountService, UpgradeLiteAccountToFullAccountInput,
};
use argon2::{
    password_hash::{PasswordHash, PasswordVerifier},
    Argon2,
};
use authn_authz::key_claims::KeyClaims;
use axum::routing::{delete, get, post, put};
use axum::Router;
use axum::{
    extract::{Path, State},
    Json,
};
use axum_extra::{
    headers::{authorization::Bearer, Authorization},
    TypedHeader,
};
use bdk_utils::generate_electrum_rpc_uris;
use bdk_utils::{bdk::miniscript::ToPublicKey, error::BdkUtilError};
use bdk_utils::{
    bdk::{
        bitcoin::{secp256k1::PublicKey, Network},
        miniscript::DescriptorPublicKey,
    },
    get_electrum_server, parse_electrum_server, ElectrumServerConfig,
};
use comms_verification::{
    ConsumeVerificationForScopeInput, InitiateVerificationForScopeInput,
    Service as CommsVerificationService, VerifyForScopeInput,
};
use errors::{ApiError, ErrorCode, RouteError};
use experimentation::claims::ExperimentationClaims;
use external_identifier::ExternalIdentifier;
use feature_flags::service::Service as FeatureFlagsService;
use http_server::swagger::{SwaggerEndpoint, Url};
use http_server::{middlewares::identifier_generator::IdentifierGenerator, router::RouterBuilder};
use isocountry::CountryCode;
use notification::clients::iterable::{IterableClient, IterableMode};
use notification::clients::twilio::{TwilioClient, TwilioMode};
use notification::entities::NotificationTouchpoint;
use once_cell::sync::Lazy;
use privileged_action::service::authorize_privileged_action::{
    AuthorizePrivilegedActionInput, AuthorizePrivilegedActionOutput,
    PrivilegedActionRequestValidatorBuilder,
};
use privileged_action::service::Service as PrivilegedActionService;
use recovery::repository::RecoveryRepository;
use regex::Regex;
use serde::{Deserialize, Serialize};

use serde_with::{base64::Base64, serde_as};
use time::Duration;
use tracing::{error, event, instrument, Level};
use types::account::entities::{
    Account, CommsVerificationScope, FullAccountAuthKeysPayload as FullAccountAuthKeysRequest,
    Keyset, LiteAccount, LiteAccountAuthKeysPayload as LiteAccountAuthKeysRequest,
    SoftwareAccountAuthKeysPayload as SoftwareAccountAuthKeysRequest, SpendingKeysetRequest,
    Touchpoint, TouchpointPlatform, UpgradeLiteAccountAuthKeysPayload,
};
use types::account::spending::SpendingKeyDefinition;
use types::{
    account::{
        identifiers::{AccountId, AuthKeysId, KeyDefinitionId, KeysetId, TouchpointId},
        keys::{FullAccountAuthKeys, LiteAccountAuthKeys, SoftwareAccountAuthKeys},
        spending::{SpendingDistributedKey, SpendingKeyset},
    },
    privileged_action::{
        router::generic::{
            AuthorizationStrategyInput, AuthorizationStrategyOutput,
            ContinuePrivilegedActionRequest, DelayAndNotifyInput, DelayAndNotifyOutput,
            PendingPrivilegedActionResponse, PrivilegedActionInstanceInput,
            PrivilegedActionInstanceOutput, PrivilegedActionRequest, PrivilegedActionResponse,
        },
        shared::PrivilegedActionType,
    },
};
use userpool::userpool::UserPoolService;
use utoipa::{OpenApi, ToSchema};
use wsm_rust_client::{SigningService, WsmClient};

use crate::account_validation::{AccountValidation, AccountValidationRequest};
use crate::{create_touchpoint_iterable_user, metrics, upsert_account_iterable_user};

static EMAIL_REGEX: Lazy<Regex> = Lazy::new(|| {
    Regex::new(
        // https://www.w3.org/TR/2012/WD-html-markup-20120329/input.email.html#input.email.attrs.value.single
        r"^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$",
    )
    .unwrap()
});

#[derive(Clone, Deserialize)]
pub struct Config {
    use_local_sns: bool,
    pub(crate) allow_test_accounts_with_mainnet_keysets: bool,
    pub iterable: IterableMode,
    pub twilio: TwilioMode,
}

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub UserPoolService,
    pub Config,
    pub IdentifierGenerator,
    pub AccountService,
    pub RecoveryRepository,
    pub WsmClient,
    pub CommsVerificationService,
    pub IterableClient,
    pub TwilioClient,
    pub FeatureFlagsService,
    pub PrivilegedActionService,
);

impl RouterBuilder for RouteState {
    fn unauthed_router(&self) -> Router {
        Router::new()
            .route("/api/accounts", post(create_account))
            .route("/api/bdk-configuration", get(get_bdk_config))
            .route("/api/demo/initiate", post(initiate_demo_mode))
            .route_layer(metrics::FACTORY.route_layer("onboarding".to_owned()))
            .with_state(self.to_owned())
    }

    fn account_authed_router(&self) -> Router {
        Router::new()
            .route("/api/accounts/:account_id", get(account_status))
            .route("/api/accounts/:account_id", delete(delete_account))
            .route("/api/accounts/:account_id/keysets", post(create_keyset))
            .route(
                "/api/accounts/:account_id/touchpoints",
                post(add_touchpoint_to_account),
            )
            .route(
                "/api/accounts/:account_id/touchpoints/:touchpoint_id/verify",
                post(verify_touchpoint_for_account),
            )
            .route(
                "/api/accounts/:account_id/touchpoints/:touchpoint_id/activate",
                post(activate_touchpoint_for_account),
            )
            .route(
                "/api/accounts/:account_id/touchpoints",
                get(get_touchpoints_for_account),
            )
            .route("/api/accounts/:account_id/keysets", get(account_keysets))
            .route(
                "/api/accounts/:account_id/keysets/:keyset_id",
                put(rotate_spending_keyset),
            )
            .route(
                "/api/accounts/:account_id/complete-onboarding",
                post(complete_onboarding),
            )
            .route(
                "/api/accounts/:account_id/distributed-keygen",
                post(initiate_distributed_keygen),
            )
            .route(
                "/api/accounts/:account_id/distributed-keygen/:key_definition_id",
                put(continue_distributed_keygen),
            )
            .route(
                "/api/accounts/:account_id/spending-key-definition",
                put(activate_spending_key_definition),
            )
            .route_layer(metrics::FACTORY.route_layer("onboarding".to_owned()))
            .with_state(self.to_owned())
    }

    fn recovery_authed_router(&self) -> Router {
        Router::new()
            .route("/api/accounts/:account_id/upgrade", post(upgrade_account))
            .route_layer(metrics::FACTORY.route_layer("onboarding".to_owned()))
            .with_state(self.to_owned())
    }

    fn account_or_recovery_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/device-token",
                post(add_device_token_to_account),
            )
            .route_layer(metrics::FACTORY.route_layer("onboarding".to_owned()))
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Onboarding", "/docs/onboarding/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        account_keysets,
        account_status,
        activate_touchpoint_for_account,
        add_device_token_to_account,
        add_touchpoint_to_account,
        complete_onboarding,
        create_account,
        create_keyset,
        delete_account,
        get_bdk_config,
        get_touchpoints_for_account,
        initiate_demo_mode,
        rotate_spending_keyset,
        upgrade_account,
        verify_touchpoint_for_account,
        initiate_distributed_keygen,
        continue_distributed_keygen,
        activate_spending_key_definition,
    ),
    components(
        schemas(
            DelayAndNotifyInput,
            AuthorizationStrategyInput,
            PrivilegedActionInstanceInput,
            ContinuePrivilegedActionRequest,
            PrivilegedActionType,
            DelayAndNotifyOutput,
            AuthorizationStrategyOutput,
            PrivilegedActionInstanceOutput,
            PendingPrivilegedActionResponse,
            AccountActivateTouchpointRequest,
            PrivilegedActionRequest<AccountActivateTouchpointRequest>,
            AccountActivateTouchpointResponse,
            PrivilegedActionResponse<AccountActivateTouchpointResponse>,
            AccountActivateTouchpointResponse,
            AccountAddDeviceTokenRequest,
            AccountAddDeviceTokenResponse,
            AccountAddTouchpointRequest,
            AccountAddTouchpointResponse,
            AccountGetTouchpointsResponse,
            AccountKeyset,
            AccountStatusResponse,
            AccountVerifyTouchpointRequest,
            AccountVerifyTouchpointResponse,
            BdkConfigResponse,
            CompleteOnboardingRequest,
            CompleteOnboardingResponse,
            CreateAccountRequest,
            CreateAccountResponse,
            CreateKeysetRequest,
            CreateKeysetResponse,
            ElectrumServer,
            ElectrumServers,
            FullAccountAuthKeysRequest,
            GetAccountKeysetsResponse,
            GetAccountStatusResponse,
            InitiateDemoModeRequest,
            InitiateDemoModeResponse,
            LiteAccountAuthKeysRequest,
            RotateSpendingKeysetRequest,
            RotateSpendingKeysetResponse,
            SpendingKeysetRequest,
            TouchpointPlatform,
            UpgradeAccountRequest,
            UpgradeLiteAccountAuthKeysPayload,
            SoftwareAccountAuthKeysRequest,
            InititateDistributedKeygenRequest,
            InititateDistributedKeygenResponse,
            ContinueDistributedKeygenRequest,
            ContinueDistributedKeygenResponse,
            ActivateSpendingKeyDefinitionRequest,
            ActivateSpendingKeyDefinitionResponse,
        ),
    ),
    tags(
        (name = "Onboarding", description = "Wallet Creation and Touchpoint Setup")
    )
)]
struct ApiDoc;

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct AccountAddDeviceTokenRequest {
    pub device_token: String,
    pub platform: TouchpointPlatform,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct AccountAddDeviceTokenResponse {}

#[instrument(fields(account_id), skip(account_service, config, request))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/device-token",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = AccountAddDeviceTokenRequest,
    responses(
        (status = 200, description = "Device token was added", body=AccountAddDeviceTokenResponse),
        (status = 400, description = "Input validation failed")
    ),
)]
async fn add_device_token_to_account(
    State(account_service): State<AccountService>,
    State(config): State<Config>,
    TypedHeader(bearer): TypedHeader<Authorization<Bearer>>,
    Path(account_id): Path<AccountId>,
    Json(request): Json<AccountAddDeviceTokenRequest>,
) -> Result<Json<AccountAddDeviceTokenResponse>, ApiError> {
    account_service
        .add_push_touchpoint_for_account(AddPushTouchpointToAccountInput {
            account_id: &account_id,
            use_local_sns: config.use_local_sns,
            platform: request.platform,
            device_token: request.device_token,
            access_token: bearer.token().to_string(),
        })
        .await?;

    Ok(Json(AccountAddDeviceTokenResponse {}))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(tag = "type")]
pub enum AccountAddTouchpointRequest {
    Phone { phone_number: String },
    Email { email_address: String },
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct AccountAddTouchpointResponse {
    pub touchpoint_id: TouchpointId,
}

#[instrument(
    fields(account_id),
    skip(
        account_service,
        comms_verification_service,
        iterable_client,
        twilio_client,
        request
    )
)]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/touchpoints",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = AccountAddTouchpointRequest,
    responses(
        (status = 200, description = "Touchpoint was added pending verification", body=AccountAddTouchpointResponse),
    ),
)]
async fn add_touchpoint_to_account(
    State(account_service): State<AccountService>,
    State(comms_verification_service): State<CommsVerificationService>,
    State(iterable_client): State<IterableClient>,
    State(twilio_client): State<TwilioClient>,
    Path(account_id): Path<AccountId>,
    Json(request): Json<AccountAddTouchpointRequest>,
) -> Result<Json<AccountAddTouchpointResponse>, ApiError> {
    let account = account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    match request {
        AccountAddTouchpointRequest::Phone { phone_number } => {
            let lookup_response = twilio_client.lookup(phone_number.clone()).await?;
            if !lookup_response.valid {
                return Err(ApiError::Specific {
                    code: ErrorCode::InvalidPhoneNumber,
                    detail: Some("Invalid phone number".to_string()),
                    field: None,
                });
            }

            let country_code = match lookup_response.country_code {
                Some(country_code) => CountryCode::for_alpha2(country_code.as_str()).ok(),
                None => None,
            }
            .ok_or_else(|| {
                let msg = "Unable to determine country code";
                error!("{msg}");
                ApiError::GenericInternalApplicationError(msg.to_string())
            })?;

            if !twilio_client.is_supported_sms_country_code(country_code) {
                let msg = "Unsupported country code";
                error!("{msg}");
                return Err(ApiError::Specific {
                    code: ErrorCode::UnsupportedCountryCode,
                    detail: Some(msg.to_owned()),
                    field: None,
                });
            }

            let touchpoint = account_service
                .fetch_or_create_phone_touchpoint(FetchOrCreatePhoneTouchpointInput {
                    account_id: &account_id,
                    phone_number: lookup_response.phone_number,
                    country_code,
                })
                .await?;

            let Touchpoint::Phone {
                id: touchpoint_id,
                active,
                ..
            } = touchpoint.clone()
            else {
                let msg = "Unexpected error adding touchpoint";
                error!("{msg}");
                return Err(ApiError::GenericInternalApplicationError(msg.to_string()));
            };

            if active {
                let msg = "Touchpoint already active";
                error!("{msg}");
                return Err(ApiError::Specific {
                    code: ErrorCode::TouchpointAlreadyActive,
                    detail: Some(msg.to_string()),
                    field: None,
                });
            }

            comms_verification_service
                .initiate_verification_for_scope(InitiateVerificationForScopeInput {
                    account_id: &account_id,
                    account_properties: &account.get_common_fields().properties,
                    scope: CommsVerificationScope::AddTouchpointId(touchpoint_id.clone()),
                    only_touchpoints: Some(HashSet::from([NotificationTouchpoint::from(
                        touchpoint,
                    )])),
                })
                .await?;

            Ok(Json(AccountAddTouchpointResponse { touchpoint_id }))
        }
        AccountAddTouchpointRequest::Email { email_address } => {
            if !EMAIL_REGEX.is_match(&email_address) {
                return Err(ApiError::Specific {
                    code: ErrorCode::InvalidEmailAddress,
                    detail: Some("Invalid email address".to_string()),
                    field: None,
                });
            }

            let touchpoint = account_service
                .fetch_or_create_email_touchpoint(FetchOrCreateEmailTouchpointInput {
                    account_id: &account_id,
                    email_address,
                })
                .await?;

            let Touchpoint::Email {
                id: touchpoint_id,
                email_address,
                active,
            } = touchpoint.clone()
            else {
                let msg = "Unexpected error adding touchpoint";
                error!("{msg}");
                return Err(ApiError::GenericInternalApplicationError(msg.to_string()));
            };

            if active {
                let msg = "Touchpoint already active";
                error!("{msg}");
                return Err(ApiError::Specific {
                    code: ErrorCode::TouchpointAlreadyActive,
                    detail: Some(msg.to_string()),
                    field: None,
                });
            }

            // Ensure Iterable touchpoint "user" is created and subscribed to account security notifications
            // before sending verification email
            create_touchpoint_iterable_user(
                &iterable_client,
                &account_id,
                &touchpoint_id,
                email_address,
            )
            .await?;

            comms_verification_service
                .initiate_verification_for_scope(InitiateVerificationForScopeInput {
                    account_id: &account_id,
                    account_properties: &account.get_common_fields().properties,
                    scope: CommsVerificationScope::AddTouchpointId(touchpoint_id.clone()),
                    only_touchpoints: Some(HashSet::from([NotificationTouchpoint::from(
                        touchpoint,
                    )])),
                })
                .await?;

            Ok(Json(AccountAddTouchpointResponse { touchpoint_id }))
        }
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct AccountVerifyTouchpointRequest {
    pub verification_code: String,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct AccountVerifyTouchpointResponse {}

#[instrument(fields(account_id), skip(account_service, comms_verification_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/touchpoints/{touchpoint_id}/verify",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("touchpoint_id" = TouchpointId, description = "TouchpointId"),
    ),
    request_body = AccountVerifyTouchpointRequest,
    responses(
        (status = 200, description = "Touchpoint was verified", body=AccountVerifyTouchpointResponse),
    ),
)]
async fn verify_touchpoint_for_account(
    State(account_service): State<AccountService>,
    State(comms_verification_service): State<CommsVerificationService>,
    Path((account_id, touchpoint_id)): Path<(AccountId, TouchpointId)>,
    Json(request): Json<AccountVerifyTouchpointRequest>,
) -> Result<Json<AccountVerifyTouchpointResponse>, ApiError> {
    let touchpoint = account_service
        .fetch_touchpoint_by_id(FetchTouchpointByIdInput {
            account_id: &account_id,
            touchpoint_id: touchpoint_id.clone(),
        })
        .await?;

    if match touchpoint {
        Touchpoint::Email { active, .. } | Touchpoint::Phone { active, .. } => active,
        _ => false,
    } {
        let msg = "Touchpoint already active";
        error!("{msg}");
        return Err(ApiError::Specific {
            code: ErrorCode::TouchpointAlreadyActive,
            detail: Some(msg.to_string()),
            field: None,
        });
    }

    let scope = CommsVerificationScope::AddTouchpointId(touchpoint_id.clone());
    comms_verification_service
        .verify_for_scope(VerifyForScopeInput {
            account_id: &account_id,
            scope,
            code: request.verification_code,
            duration: Duration::minutes(10),
        })
        .await?;

    Ok(Json(AccountVerifyTouchpointResponse {}))
}

#[derive(Clone, Debug, Serialize, Deserialize, ToSchema)]
pub struct AccountActivateTouchpointRequest {}

#[derive(Clone, Debug, Serialize, Deserialize, ToSchema)]
pub struct AccountActivateTouchpointResponse {}

#[instrument(
    fields(account_id),
    skip(
        account_service,
        comms_verification_service,
        iterable_client,
        privileged_action_service
    )
)]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/touchpoints/{touchpoint_id}/activate",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("touchpoint_id" = TouchpointId, description = "TouchpointId"),
    ),
    request_body = PrivilegedActionRequest<AccountActivateTouchpointRequest>,
    responses(
        (status = 200, description = "Touchpoint was activated", body=PrivilegedActionResponse<AccountActivateTouchpointResponse>),
    ),
)]
async fn activate_touchpoint_for_account(
    State(account_service): State<AccountService>,
    State(comms_verification_service): State<CommsVerificationService>,
    State(iterable_client): State<IterableClient>,
    State(privileged_action_service): State<PrivilegedActionService>,
    key_proof: KeyClaims,
    Path((account_id, touchpoint_id)): Path<(AccountId, TouchpointId)>,
    Json(privileged_action_request): Json<
        PrivilegedActionRequest<AccountActivateTouchpointRequest>,
    >,
) -> Result<Json<PrivilegedActionResponse<AccountActivateTouchpointResponse>>, ApiError> {
    let scope = CommsVerificationScope::AddTouchpointId(touchpoint_id.clone());

    let cloned_account_id_1 = account_id.clone();
    let cloned_account_id_2 = account_id.clone();
    let cloned_touchpoint_id = touchpoint_id.clone();
    let cloned_scope_1 = scope.clone();
    let cloned_scope_2 = scope.clone();
    let cloned_comms_verification_service_1 = comms_verification_service.clone();
    let cloned_comms_verification_service_2 = comms_verification_service.clone();
    let cloned_account_service = account_service.clone();

    let authorize_result = privileged_action_service
        .authorize_privileged_action(AuthorizePrivilegedActionInput {
            account_id: &account_id,
            privileged_action_definition: &PrivilegedActionType::ActivateTouchpoint.into(),
            key_proof: &key_proof,
            privileged_action_request: &privileged_action_request,
            request_validator: PrivilegedActionRequestValidatorBuilder::default()
                .on_initiate_delay_and_notify(Box::new(|_| {
                    Box::pin(async move {
                        cloned_comms_verification_service_1
                            .consume_verification_for_scope(ConsumeVerificationForScopeInput {
                                account_id: &cloned_account_id_1,
                                scope: cloned_scope_1,
                            })
                            .await?;

                        cloned_account_service
                            .activate_touchpoint_for_account(ActivateTouchpointForAccountInput {
                                account_id: &cloned_account_id_1,
                                touchpoint_id: cloned_touchpoint_id,
                                dry_run: true,
                            })
                            .await?;

                        Ok::<(), ApiError>(())
                    })
                }))
                .on_initiate_hardware_proof_of_possession(Box::new(|_| {
                    Box::pin(async move {
                        cloned_comms_verification_service_2
                            .consume_verification_for_scope(ConsumeVerificationForScopeInput {
                                account_id: &cloned_account_id_2,
                                scope: cloned_scope_2,
                            })
                            .await?;

                        Ok::<(), ApiError>(())
                    })
                }))
                .build()?,
        })
        .await?;

    if let AuthorizePrivilegedActionOutput::Pending(response) = authorize_result {
        return Ok(Json(response));
    }

    let touchpoint = account_service
        .activate_touchpoint_for_account(ActivateTouchpointForAccountInput {
            account_id: &account_id,
            touchpoint_id,
            dry_run: false,
        })
        .await?;

    if let Touchpoint::Email {
        id: touchpoint_id,
        email_address,
        ..
    } = &touchpoint
    {
        // Ensure Iterable account "user" is updated
        upsert_account_iterable_user(
            &iterable_client,
            &account_id,
            Some(touchpoint_id),
            Some(email_address.to_owned()),
        )
        .await?;
    }

    Ok(Json(PrivilegedActionResponse::Completed(
        AccountActivateTouchpointResponse {},
    )))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct AccountGetTouchpointsResponse {
    pub touchpoints: Vec<Touchpoint>,
}

#[instrument(fields(account_id), skip(account_service))]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/touchpoints",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "Touchpoints", body=AccountGetTouchpointsResponse),
    ),
)]
async fn get_touchpoints_for_account(
    State(account_service): State<AccountService>,
    Path(account_id): Path<AccountId>,
) -> Result<Json<AccountGetTouchpointsResponse>, ApiError> {
    let account = account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let touchpoints = account
        .get_common_fields()
        .to_owned()
        .touchpoints
        .into_iter()
        .filter(|t| match t {
            Touchpoint::Email { active, .. } | Touchpoint::Phone { active, .. } => *active,
            _ => false,
        })
        .collect();

    Ok(Json(AccountGetTouchpointsResponse { touchpoints }))
}

#[derive(Serialize, Deserialize, ToSchema)]
pub struct GetAccountStatusResponse {
    pub keyset_id: KeysetId,
    pub spending: SpendingKeyset,
}

#[derive(Debug, Serialize, ToSchema)]
pub struct AccountStatusResponse {}
#[instrument(skip(account_service,))]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "Retrieved status for Account", body=AccountStatusResponse),
        (status = 404, description = "Account not found")
    ),
)]
async fn account_status(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
) -> Result<Json<GetAccountStatusResponse>, ApiError> {
    //TODO: Update this when we introduce Trusted Contacts
    let account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    Ok(Json(GetAccountStatusResponse {
        keyset_id: account.clone().active_keyset_id,
        spending: account
            .active_spending_keyset()
            .ok_or(RouteError::NoActiveSpendKeyset)?
            .to_owned(),
    }))
}

pub const MAINNET_DERIVATION_PATH: &str = "m/84'/0'/0'";
pub const TESTNET_DERIVATION_PATH: &str = "m/84'/1'/0'";

#[derive(Deserialize, Serialize, PartialEq, Debug, ToSchema)]
#[serde(untagged)]
pub enum CreateAccountRequest {
    Full {
        auth: FullAccountAuthKeysRequest, // TODO: [W-774] Update visibility of struct after migration
        spending: SpendingKeysetRequest, // TODO: [W-774] Update visibility of struct after migration
        #[serde(default)]
        is_test_account: bool,
    },
    Software {
        auth: SoftwareAccountAuthKeysRequest, // TODO: [W-774] Update visibility of struct after migration
        #[serde(default)]
        is_test_account: bool,
    },
    Lite {
        auth: LiteAccountAuthKeysRequest, // TODO: [W-774] Update visibility of struct after migration
        #[serde(default)]
        is_test_account: bool,
    },
}

impl CreateAccountRequest {
    pub fn recovery_auth_key(&self) -> Option<PublicKey> {
        match self {
            CreateAccountRequest::Full { auth, .. } => auth.recovery,
            CreateAccountRequest::Lite { auth, .. } => Some(auth.recovery),
            CreateAccountRequest::Software { auth, .. } => Some(auth.recovery),
        }
    }

    pub fn auth_keys(&self) -> (Option<PublicKey>, Option<PublicKey>, Option<PublicKey>) {
        match self {
            CreateAccountRequest::Full { auth, .. } => {
                (Some(auth.app), Some(auth.hardware), auth.recovery)
            }
            CreateAccountRequest::Lite { auth, .. } => (None, None, Some(auth.recovery)),
            CreateAccountRequest::Software { auth, .. } => {
                (Some(auth.app), None, Some(auth.recovery))
            }
        }
    }
}

impl From<&CreateAccountRequest> for AccountValidationRequest {
    fn from(value: &CreateAccountRequest) -> Self {
        match value {
            CreateAccountRequest::Full {
                auth,
                spending,
                is_test_account,
            } => AccountValidationRequest::CreateFullAccount {
                auth: auth.to_owned(),
                spending: spending.to_owned(),
                is_test_account: *is_test_account,
                spending_network: spending.network.into(),
            },
            CreateAccountRequest::Lite {
                auth,
                is_test_account: _,
            } => AccountValidationRequest::CreateLiteAccount {
                auth: auth.to_owned(),
            },
            CreateAccountRequest::Software { auth, .. } => {
                AccountValidationRequest::CreateSoftwareAccount {
                    auth: auth.to_owned(),
                }
            }
        }
    }
}

#[derive(Deserialize, Serialize, PartialEq, Debug, ToSchema)]
pub struct CreateAccountResponse {
    pub account_id: AccountId,
    #[serde(flatten)]
    pub keyset: Option<CreateKeysetResponse>,
}

impl TryFrom<&Account> for CreateAccountResponse {
    type Error = ApiError;

    fn try_from(value: &Account) -> Result<Self, Self::Error> {
        let (account_id, keyset) = match value {
            Account::Full(full_account) => {
                let spending_keyset = full_account
                    .active_spending_keyset()
                    .ok_or(RouteError::NoActiveSpendKeyset)?
                    .to_owned();
                (
                    full_account.id.clone(),
                    Some(CreateKeysetResponse {
                        keyset_id: full_account.active_keyset_id.clone(),
                        spending: spending_keyset.server_dpub,
                        spending_sig: None,
                    }),
                )
            }
            Account::Lite(lite_account) => (lite_account.id.clone(), None),
            Account::Software(software_account) => (software_account.id.clone(), None),
        };

        Ok(CreateAccountResponse { account_id, keyset })
    }
}

#[instrument(
    fields(account_id),
    skip(
        account_service,
        recovery_repository,
        id_generator,
        user_pool_service,
        config,
        iterable_client,
    )
)]
#[utoipa::path(
    post,
    path = "/api/accounts",
    request_body = CreateAccountRequest,
    responses(
        (status = 200, description = "Account was created", body=CreateAccountResponse),
        (status = 400, description = "Input validation failed")
    ),
)]
// TODO: [W-1218] Abstract root key generation/public key derivation logic shared with
// `create_keyset` to common function
pub async fn create_account(
    State(account_service): State<AccountService>,
    State(recovery_repository): State<RecoveryRepository>,
    State(wsm_client): State<WsmClient>,
    State(id_generator): State<IdentifierGenerator>,
    State(user_pool_service): State<UserPoolService>,
    State(config): State<Config>,
    State(iterable_client): State<IterableClient>,
    Json(request): Json<CreateAccountRequest>,
) -> Result<Json<CreateAccountResponse>, ApiError> {
    if let Some(v) = AccountValidation::default()
        .validate(
            AccountValidationRequest::from(&request),
            &config,
            &account_service,
            &recovery_repository,
        )
        .await?
    {
        let mut create_account_response = CreateAccountResponse::try_from(&v.existing_account)?;
        match &v.existing_account {
            Account::Full(full_account) => {
                let spending_sig = maybe_get_wsm_integrity_sig(
                    &wsm_client,
                    &full_account.active_keyset_id.to_string(),
                )
                .await;
                if let Some(keyset) = &mut create_account_response.keyset {
                    keyset.spending_sig = spending_sig;
                }
            }
            Account::Lite(_) | Account::Software(_) => {}
        }
        return Ok(Json(create_account_response));
    }

    let account_id = AccountId::new(id_generator.gen_account_id()).map_err(|e| {
        let msg = "Failed to generate account id";
        error!("{msg}: {e}");
        ApiError::GenericInternalApplicationError(msg.to_string())
    })?;

    // provide the generated account ID once we have it
    tracing::Span::current().record("account_id", account_id.to_string());

    // Create Cognito users
    let (app_auth_pubkey, hardware_auth_pubkey, recovery_auth_pubkey) = request.auth_keys();
    user_pool_service
        .create_or_update_account_users_if_necessary(
            &account_id,
            app_auth_pubkey,
            hardware_auth_pubkey,
            recovery_auth_pubkey,
        )
        .await
        .map_err(|e| {
            let msg = "Failed to create new accounts in Cognito";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;

    match request {
        CreateAccountRequest::Full {
            spending,
            is_test_account,
            ..
        } => {
            // Generate a server key in WSM
            let keyset_id = KeysetId::new(id_generator.gen_spending_keyset_id())
                .map_err(RouteError::InvalidIdentifier)?;
            // TODO: use a correctly derived spending dpub from WSM, rather than the root xpub [W-5622]
            let key = wsm_client
                .create_root_key(&keyset_id.to_string(), spending.network)
                .await
                .map_err(|e| {
                    let msg = "Failed to create new key in WSM";
                    error!("{msg}: {e}");
                    ApiError::GenericInternalApplicationError(msg.to_string())
                })?;

            // Attempt to parse the DescriptorPublicKey from the xpub string
            let spending_server_dpub = DescriptorPublicKey::from_str(&key.xpub).map_err(|e| {
                let msg = "Failed to parse spending dpub from WSM";
                error!("{msg}: {e}");
                ApiError::GenericInternalApplicationError(msg.to_string())
            })?;

            let auth_key_id = AuthKeysId::new(id_generator.gen_spending_keyset_id())
                .map_err(RouteError::InvalidIdentifier)?;

            let input = CreateAccountAndKeysetsInput {
                account_id,
                network: spending.network.into(),
                keyset_id,
                auth_key_id: auth_key_id.clone(),
                keyset: Keyset {
                    auth: FullAccountAuthKeys {
                        app_pubkey: app_auth_pubkey.unwrap(),
                        hardware_pubkey: hardware_auth_pubkey.unwrap(),
                        recovery_pubkey: recovery_auth_pubkey,
                    },
                    spending: SpendingKeyset {
                        network: spending.network.into(),
                        app_dpub: spending.app,
                        hardware_dpub: spending.hardware,
                        server_dpub: spending_server_dpub.clone(),
                    },
                },
                is_test_account,
            };
            let account = account_service.create_account_and_keysets(input).await?;

            // Attempt to create account Iterable user early, but don't fail the account creation if
            // this fails. We upsert the users later when they're needed anyway; this is an optimization
            // to avoid added latency or errors waiting for Iterable user database consistency on first use.
            upsert_account_iterable_user(&iterable_client, &account.id, None, None)
                .await
                .map_or_else(
                    |e| {
                        error!("Failed to create account Iterable user: {e}");
                    },
                    |_| (),
                );

            Ok(Json(CreateAccountResponse {
                account_id: account.id,
                keyset: Some(CreateKeysetResponse {
                    keyset_id: account.active_keyset_id,
                    spending: spending_server_dpub,
                    spending_sig: Some(key.xpub_sig),
                }),
            }))
        }
        CreateAccountRequest::Lite {
            auth,
            is_test_account,
        } => {
            let auth_key_id = AuthKeysId::new(id_generator.gen_spending_keyset_id())
                .map_err(RouteError::InvalidIdentifier)?;
            let input = CreateLiteAccountInput {
                account_id: &account_id,
                auth_key_id,
                auth: LiteAccountAuthKeys {
                    recovery_pubkey: auth.recovery,
                },
                is_test_account,
            };
            let account = account_service.create_lite_account(input).await?;
            Ok(Json(CreateAccountResponse {
                account_id: account.id,
                keyset: None,
            }))
        }
        CreateAccountRequest::Software {
            auth,
            is_test_account,
        } => {
            let auth_key_id = AuthKeysId::new(id_generator.gen_spending_keyset_id())
                .map_err(RouteError::InvalidIdentifier)?;
            let input = CreateSoftwareAccountInput {
                account_id: &account_id,
                auth_key_id,
                auth: SoftwareAccountAuthKeys {
                    app_pubkey: auth.app,
                    recovery_pubkey: auth.recovery,
                },
                is_test_account,
            };
            let account = account_service.create_software_account(input).await?;
            Ok(Json(CreateAccountResponse {
                account_id: account.id,
                keyset: None,
            }))
        }
    }
}

#[derive(Deserialize, Serialize, PartialEq, Debug, ToSchema)]
pub struct UpgradeAccountRequest {
    pub auth: UpgradeLiteAccountAuthKeysPayload, // TODO: [W-774] Update visibility of struct after migration
    pub spending: SpendingKeysetRequest, // TODO: [W-774] Update visibility of struct after migration
}

impl From<(&LiteAccount, &UpgradeAccountRequest)> for AccountValidationRequest {
    fn from(value: (&LiteAccount, &UpgradeAccountRequest)) -> Self {
        AccountValidationRequest::UpgradeAccount {
            auth: value.1.auth.to_owned(),
            is_test_account: value.0.common_fields.properties.is_test_account,
            spending_network: value.1.spending.network.into(),
        }
    }
}

#[instrument(
    fields(account_id),
    skip(
        account_service,
        recovery_repository,
        id_generator,
        user_pool_service,
        config
    )
)]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/upgrade",
    request_body = UpgradeAccountRequest,
    responses(
        (status = 200, description = "Account was upgraded to a full account", body=CreateAccountResponse),
        (status = 400, description = "Input validation failed")
    ),
)]
pub async fn upgrade_account(
    State(account_service): State<AccountService>,
    State(recovery_repository): State<RecoveryRepository>,
    State(wsm_client): State<WsmClient>,
    State(id_generator): State<IdentifierGenerator>,
    State(user_pool_service): State<UserPoolService>,
    State(config): State<Config>,
    Path(account_id): Path<AccountId>,
    Json(request): Json<UpgradeAccountRequest>,
) -> Result<Json<CreateAccountResponse>, ApiError> {
    let existing_account = &account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let lite_account = match existing_account {
        Account::Lite(lite_account) => lite_account,
        Account::Full(full_account) => {
            let Some(active_auth_keys) = full_account.active_auth_keys() else {
                return Err(RouteError::NoActiveAuthKeys)?;
            };

            let Some(active_spending_keyset) = full_account.active_spending_keyset() else {
                return Err(RouteError::NoActiveSpendKeyset)?;
            };

            if active_auth_keys.app_pubkey == request.auth.app
                && active_auth_keys.hardware_pubkey == request.auth.hardware
                && active_spending_keyset.app_dpub == request.spending.app
                && active_spending_keyset.hardware_dpub == request.spending.hardware
            {
                let mut account = CreateAccountResponse::try_from(existing_account)?;
                if let Some(keyset) = &mut account.keyset {
                    let spending_sig =
                        maybe_get_wsm_integrity_sig(&wsm_client, &keyset.keyset_id.to_string())
                            .await;
                    keyset.spending_sig = spending_sig;
                }
                return Ok(Json(account));
            } else {
                return Err(ApiError::GenericConflict(
                    "Account is already a full account".to_string(),
                ));
            }
        }
        Account::Software(_) => {
            return Err(ApiError::GenericInternalApplicationError(
                "Unimplemented".to_string(),
            ));
        }
    };

    AccountValidation::default()
        .validate(
            AccountValidationRequest::from((lite_account, &request)),
            &config,
            &account_service,
            &recovery_repository,
        )
        .await?;

    // Create Cognito users
    user_pool_service
        .create_or_update_account_users_if_necessary(
            &account_id,
            Some(request.auth.app),
            Some(request.auth.hardware),
            None,
        )
        .await
        .map_err(|e| {
            let msg = "Failed to create new accounts in Cognito";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;

    // Generate a server key in WSM
    let keyset_id = KeysetId::new(id_generator.gen_spending_keyset_id())
        .map_err(RouteError::InvalidIdentifier)?;
    // TODO: use a correctly derived spending dpub from WSM, rather than the root xpub [W-5622]
    let create_key_result = wsm_client
        .create_root_key(&keyset_id.to_string(), request.spending.network)
        .await;

    let (xpub, xpub_sig) = match create_key_result {
        Ok(k) => (k.xpub, k.xpub_sig),
        Err(e) => {
            let msg = "Failed to create new key in WSM";
            error!("{msg}: {e}");
            return Err(ApiError::GenericInternalApplicationError(msg.to_string()));
        }
    };

    // Attempt to parse the DescriptorPublicKey
    let spending_server_dpub = DescriptorPublicKey::from_str(&xpub).map_err(|e| {
        let msg = "Failed to parse spending dpub from WSM";
        error!("{msg}: {e}");
        ApiError::GenericInternalApplicationError(msg.to_string())
    })?;

    let auth_key_id = AuthKeysId::new(id_generator.gen_spending_keyset_id())
        .map_err(RouteError::InvalidIdentifier)?;

    let input = UpgradeLiteAccountToFullAccountInput {
        lite_account,
        keyset_id,
        spending_keyset: SpendingKeyset {
            network: request.spending.network.into(),
            app_dpub: request.spending.app,
            hardware_dpub: request.spending.hardware,
            server_dpub: spending_server_dpub.clone(),
        },
        auth_key_id: auth_key_id.clone(),
        auth_keys: FullAccountAuthKeys {
            app_pubkey: request.auth.app,
            hardware_pubkey: request.auth.hardware,
            recovery_pubkey: Some(
                lite_account
                    .active_auth_keys()
                    .ok_or(RouteError::NoActiveAuthKeys)?
                    .recovery_pubkey,
            ),
        },
    };
    let full_account = account_service
        .upgrade_lite_account_to_full_account(input)
        .await?;
    Ok(Json(CreateAccountResponse {
        account_id: full_account.id,
        keyset: Some(CreateKeysetResponse {
            keyset_id: full_account.active_keyset_id,
            spending: spending_server_dpub,
            spending_sig: Some(xpub_sig),
        }),
    }))
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CreateKeysetRequest {
    pub spending: SpendingKeysetRequest,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CreateKeysetResponse {
    pub keyset_id: KeysetId,
    #[serde(with = "bdk_utils::serde::descriptor_key")]
    pub spending: DescriptorPublicKey,
    #[serde(default)]
    pub spending_sig: Option<String>,
}

#[instrument(skip(account_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/keysets",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = CreateKeysetRequest,
    responses(
        (status = 200, description = "New keyset was created for account", body=CreateKeysetResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn create_keyset(
    Path(account_id): Path<AccountId>,
    key_proof: KeyClaims,
    State(account_service): State<AccountService>,
    State(wsm_client): State<WsmClient>,
    Json(request): Json<CreateKeysetRequest>,
) -> Result<Json<CreateKeysetResponse>, ApiError> {
    if !(key_proof.hw_signed && key_proof.app_signed) {
        event!(
            Level::WARN,
            "valid signature over access token required by both app and hw auth keys"
        );
        return Err(ApiError::GenericForbidden(
            "valid signature over access token required by both app and hw auth keys".to_string(),
        ));
    }

    let account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    if let Some((keyset_id, keyset)) =
        account
            .spending_keysets
            .iter()
            .find(|(_, spending_keyset)| {
                spending_keyset.app_dpub == request.spending.app
                    && spending_keyset.hardware_dpub == request.spending.hardware
            })
    {
        let spending_sig = maybe_get_wsm_integrity_sig(&wsm_client, &keyset_id.to_string()).await;

        return Ok(Json(CreateKeysetResponse {
            keyset_id: keyset_id.to_owned(),
            spending: keyset.server_dpub.to_owned(),
            spending_sig,
        }));
    }

    // Don't allow account to hop networks
    account.active_spending_keyset().map_or(
        Err(RouteError::NoActiveSpendKeyset),
        |active_keyset| {
            if active_keyset.network != request.spending.network.into() {
                return Err(RouteError::InvalidNetworkForNewKeyset);
            }
            Ok(())
        },
    )?;

    let spending_keyset_id = KeysetId::gen().map_err(RouteError::InvalidIdentifier)?;
    let key = wsm_client
        .create_root_key(&spending_keyset_id.to_string(), request.spending.network)
        .await
        .map_err(|e| {
            let msg = "Failed to create new key in WSM";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;

    let spending_server_dpub = DescriptorPublicKey::from_str(&key.xpub).map_err(|e| {
        let msg = "Failed to parse spending dpub from WSM";
        error!("{msg}: {e}");
        ApiError::GenericInternalApplicationError(msg.to_string())
    })?;

    let (inactive_spend_keyset_id, inactive_spend_keyset) = account_service
        .create_inactive_spending_keyset(CreateInactiveSpendingKeysetInput {
            account_id,
            spending_keyset_id,
            spending: SpendingKeyset {
                network: request.spending.network.into(),
                app_dpub: request.spending.app,
                hardware_dpub: request.spending.hardware,
                server_dpub: spending_server_dpub,
            },
        })
        .await?;

    Ok(Json(CreateKeysetResponse {
        keyset_id: inactive_spend_keyset_id,
        spending: inactive_spend_keyset.server_dpub,
        spending_sig: Some(key.xpub_sig),
    }))
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
pub struct AccountKeyset {
    pub keyset_id: KeysetId,
    pub network: bdk_utils::bdk::bitcoin::Network,

    // Public Keys
    #[serde(with = "bdk_utils::serde::descriptor_key")]
    pub app_dpub: DescriptorPublicKey,
    #[serde(with = "bdk_utils::serde::descriptor_key")]
    pub hardware_dpub: DescriptorPublicKey,
    #[serde(with = "bdk_utils::serde::descriptor_key")]
    pub server_dpub: DescriptorPublicKey,
}

#[derive(Serialize, Deserialize, ToSchema)]
pub struct GetAccountKeysetsResponse {
    pub keysets: Vec<AccountKeyset>,
}

#[instrument(skip(account_service))]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/keysets",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "Retrieved the keysets for Account", body=AccountStatusResponse),
        (status = 403, description = "Invalid access token"),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn account_keysets(
    Path(account_id): Path<AccountId>,
    key_proof: KeyClaims,
    State(account_service): State<AccountService>,
) -> Result<Json<GetAccountKeysetsResponse>, ApiError> {
    let account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let mut keysets = account
        .spending_keysets
        .into_iter()
        .map(|(keyset_id, spend_keyset)| AccountKeyset {
            keyset_id,
            network: spend_keyset.network.into(),
            app_dpub: spend_keyset.app_dpub,
            hardware_dpub: spend_keyset.hardware_dpub,
            server_dpub: spend_keyset.server_dpub,
        })
        .collect::<Vec<AccountKeyset>>();
    keysets.sort_by_key(|k| k.keyset_id.to_string());

    Ok(Json(GetAccountKeysetsResponse { keysets }))
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct RotateSpendingKeysetRequest {}

#[derive(Serialize, Deserialize, ToSchema)]
pub struct RotateSpendingKeysetResponse {}

#[instrument(skip(account_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/keysets/{keyset_id}",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("keyset_id" = KeysetId, Path, description = "KeysetId"),
    ),
    responses(
        (status = 200, description = "Updated the active spending keyset", body=AccountStatusResponse),
        (status = 403, description = "Invalid access token"),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn rotate_spending_keyset(
    Path((account_id, keyset_id)): Path<(AccountId, KeysetId)>,
    key_proof: KeyClaims,
    State(account_service): State<AccountService>,
    Json(request): Json<RotateSpendingKeysetRequest>,
) -> Result<Json<RotateSpendingKeysetResponse>, ApiError> {
    if !(key_proof.hw_signed && key_proof.app_signed) {
        event!(
            Level::WARN,
            "valid signature over access token required by both app and hw auth keys"
        );
        return Err(ApiError::GenericForbidden(
            "valid signature over access token required by both app and hw auth keys".to_string(),
        ));
    }

    account_service
        .rotate_to_spending_keyset(RotateToSpendingKeysetInput {
            account_id: &account_id,
            keyset_id: &keyset_id,
        })
        .await?;
    Ok(Json(RotateSpendingKeysetResponse {}))
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct InititateDistributedKeygenRequest {
    pub network: bdk_utils::bdk::bitcoin::Network,
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    #[serde_as(as = "Base64")]
    pub noise_session: Vec<u8>,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct InititateDistributedKeygenResponse {
    pub key_definition_id: KeyDefinitionId,
    #[serde_as(as = "Base64")]
    pub sealed_response: Vec<u8>,
}

#[instrument(skip(account_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/distributed-keygen",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = InititateDistributedKeygenRequest,
    responses(
        (status = 200, description = "Initiated distributed keygen", body=InititateDistributedKeygenResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn initiate_distributed_keygen(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(wsm_client): State<WsmClient>,
    Json(request): Json<InititateDistributedKeygenRequest>,
) -> Result<Json<InititateDistributedKeygenResponse>, ApiError> {
    // TODO: Are there ever sweeps? Or should this only ever be called once in the lifetime of a software account?
    // If there are sweeps, does this need to be time-delayed, or does activation?
    let account = account_service
        .fetch_software_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    // TODO: idempotency?

    // Don't allow account to hop networks
    if account
        .spending_key_definitions
        .iter()
        .any(|(_, d)| d.network() != request.network.into())
    {
        return Err(RouteError::InvalidNetworkForNewKeyDefinition)?;
    }

    let key_definition_id = KeyDefinitionId::gen().map_err(RouteError::InvalidIdentifier)?;

    let wsm_response = wsm_client
        .initiate_distributed_keygen(
            &key_definition_id.to_string(),
            request.network,
            request.sealed_request,
            request.noise_session,
        )
        .await
        .map_err(|e| {
            let msg = "Failed to initiate distributed keygen in WSM";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;

    account_service
        .put_inactive_spending_distributed_key(PutInactiveSpendingDistributedKeyInput {
            account_id: &account_id,
            spending_key_definition_id: &key_definition_id,
            spending: SpendingDistributedKey::new(
                request.network.into(),
                wsm_response.aggregate_public_key.to_public_key(),
                false,
            ),
        })
        .await?;

    Ok(Json(InititateDistributedKeygenResponse {
        key_definition_id,
        sealed_response: wsm_response.sealed_response,
    }))
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct ContinueDistributedKeygenRequest {
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    #[serde_as(as = "Base64")]
    pub noise_session: Vec<u8>,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct ContinueDistributedKeygenResponse {}

#[instrument(skip(account_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/distributed-keygen/{key_definition_id}",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("key_definition_id" = KeyDefinitionId, Path, description = "KeyDefinitionId"),
    ),
    request_body = ContinueDistributedKeygenRequest,
    responses(
        (status = 200, description = "Continued distributed keygen", body=ContinueDistributedKeygenResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn continue_distributed_keygen(
    Path((account_id, key_definition_id)): Path<(AccountId, KeyDefinitionId)>,
    State(account_service): State<AccountService>,
    State(wsm_client): State<WsmClient>,
    Json(request): Json<ContinueDistributedKeygenRequest>,
) -> Result<Json<ContinueDistributedKeygenResponse>, ApiError> {
    let account = account_service
        .fetch_software_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

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

    if distributed_key.dkg_complete {
        return Err(ApiError::GenericConflict(
            "Keygen is already complete for key definition".to_string(),
        ));
    }

    wsm_client
        .continue_distributed_keygen(
            &key_definition_id.to_string(),
            distributed_key.network.into(),
            request.sealed_request,
            request.noise_session,
        )
        .await
        .map_err(|e| {
            let msg = "Failed to continue distributed keygen in WSM";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;

    account_service
        .put_inactive_spending_distributed_key(PutInactiveSpendingDistributedKeyInput {
            account_id: &account_id,
            spending_key_definition_id: &key_definition_id,
            spending: SpendingDistributedKey::new(
                distributed_key.network,
                distributed_key.public_key,
                true,
            ),
        })
        .await?;

    Ok(Json(ContinueDistributedKeygenResponse {}))
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct ActivateSpendingKeyDefinitionRequest {
    pub key_definition_id: KeyDefinitionId,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct ActivateSpendingKeyDefinitionResponse {}

#[instrument(skip(account_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/spending-key-definition",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = ActivateSpendingKeyDefinitionRequest,
    responses(
        (status = 200, description = "Activated spending key definition", body=ActivateSpendingKeyDefinitionResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn activate_spending_key_definition(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(wsm_client): State<WsmClient>,
    Json(request): Json<ActivateSpendingKeyDefinitionRequest>,
) -> Result<Json<ActivateSpendingKeyDefinitionResponse>, ApiError> {
    // Ensure endpoint only called by software accounts
    account_service
        .fetch_software_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    // TODO: Delay and notify?

    account_service
        .rotate_to_spending_key_definition(RotateToSpendingKeyDefinitionInput {
            account_id: &account_id,
            key_definition_id: &request.key_definition_id,
        })
        .await?;

    return Ok(Json(ActivateSpendingKeyDefinitionResponse {}));
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CompleteOnboardingRequest {}

#[derive(Serialize, Deserialize, ToSchema)]
pub struct CompleteOnboardingResponse {}

#[instrument(skip(account_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/complete-onboarding",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "Marked onboarding complete", body=CompleteOnboardingResponse),
    ),
)]
pub async fn complete_onboarding(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    Json(request): Json<CompleteOnboardingRequest>,
) -> Result<Json<CompleteOnboardingResponse>, ApiError> {
    account_service
        .complete_onboarding(CompleteOnboardingInput {
            account_id: &account_id,
        })
        .await?;

    Ok(Json(CompleteOnboardingResponse {}))
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
pub struct BdkConfigResponse {
    pub electrum_servers: ElectrumServers,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
pub struct ElectrumServers {
    pub mainnet: ElectrumServer,
    pub testnet: ElectrumServer,
    pub signet: ElectrumServer,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub regtest: Option<ElectrumServer>,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
pub struct ElectrumServer {
    pub scheme: String,
    pub host: String,
    pub port: u16,
}

impl From<ElectrumServerConfig> for ElectrumServer {
    fn from(server: ElectrumServerConfig) -> Self {
        Self {
            scheme: server.scheme,
            host: server.host,
            port: server.port,
        }
    }
}

#[instrument(skip(feature_flags_service))]
#[utoipa::path(
    get,
    path = "/api/bdk-configuration",
    responses(
        (status = 200, description = "Retrieved BDK configuration", body=ConfigResponse),
    ),
)]
pub async fn get_bdk_config(
    feature_flags_service: State<FeatureFlagsService>,
    experimentation_claims: ExperimentationClaims,
) -> Result<Json<BdkConfigResponse>, ApiError> {
    let context_key = experimentation_claims.app_installation_context_key().ok();
    let rpc_uris = generate_electrum_rpc_uris(&feature_flags_service, context_key);
    Ok(Json(BdkConfigResponse {
        electrum_servers: ElectrumServers {
            mainnet: get_electrum_server(Network::Bitcoin, &rpc_uris)?.into(),
            testnet: get_electrum_server(Network::Testnet, &rpc_uris)?.into(),
            signet: get_electrum_server(Network::Signet, &rpc_uris)?.into(),
            regtest: env::var("REGTEST_ELECTRUM_SERVER_EXTERNAL_URI")
                .map_err(|_| BdkUtilError::UnsupportedBitcoinNetwork(Network::Regtest.to_string()))
                .and_then(|s| parse_electrum_server(&s))
                .or_else(|_err| get_electrum_server(Network::Regtest, &rpc_uris))
                .map_or_else(
                    |err| {
                        // Regtest isn't configured, just return None
                        if matches!(err, BdkUtilError::UnsupportedBitcoinNetwork(_)) {
                            return Ok(None);
                        }

                        // There's an error with the regtest configuration, so propagate it
                        Err(err)
                    },
                    |config| Ok(Some(config.into())),
                )?,
        },
    }))
}

#[instrument(skip(account_service))]
#[utoipa::path(
    delete,
    path = "/api/accounts/{account_id}",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "Account deleted"),
        (status = 404, description = "Account not found"),
    ),
)]
pub async fn delete_account(
    State(account_service): State<AccountService>,
    key_proof: KeyClaims,
    Path(account_id): Path<AccountId>,
) -> Result<(), ApiError> {
    if !(key_proof.app_signed && key_proof.hw_signed) {
        let msg = "valid signature over access token required by both app and hw auth keys";
        error!("{msg}");
        return Err(ApiError::GenericForbidden(msg.to_string()));
    }

    account_service
        .delete_account(DeleteAccountInput {
            account_id: &account_id,
        })
        .await?;

    Ok(())
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct InitiateDemoModeRequest {
    pub code: String,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct InitiateDemoModeResponse {}

#[instrument]
#[utoipa::path(
    post,
    path = "/api/demo/initiate",
    responses(
        (status = 200, description = "Code is valid"),
        (status = 400, description = "Code is not valid"),
    ),
)]
pub async fn initiate_demo_mode(
    Json(request): Json<InitiateDemoModeRequest>,
) -> Result<Json<InitiateDemoModeResponse>, ApiError> {
    let code_hash = std::env::var("ONBOARDING_DEMO_MODE_CODE_HASH").map_err(|e| {
        let msg = "Failed to retrieve code hash from environment";
        error!("{msg}: {e}");
        ApiError::GenericInternalApplicationError(msg.to_string())
    })?;

    let parsed_hash = PasswordHash::new(&code_hash).map_err(|e| {
        let msg = "Failed to parse code hash from environment";
        error!("{msg}: {e}");
        ApiError::GenericInternalApplicationError(msg.to_string())
    })?;

    if Argon2::default()
        .verify_password(request.code.as_bytes(), &parsed_hash)
        .is_ok()
    {
        Ok(Json(InitiateDemoModeResponse {}))
    } else {
        Err(ApiError::GenericBadRequest("Invalid code".to_string()))
    }
}

async fn maybe_get_wsm_integrity_sig(wsm_client: &WsmClient, keyset_id: &String) -> Option<String> {
    wsm_client
        .get_key_integrity_sig(keyset_id)
        .await
        .map_or_else(
            |e| {
                let msg = "Failed to get key integrity signature";
                error!("{msg}: {e}");
                None
            },
            |response| Some(response.signature),
        )
}

#[cfg(test)]
mod tests {
    use std::str::FromStr;

    use bdk_utils::bdk::{
        bitcoin::{key::Secp256k1, secp256k1::rand::thread_rng, Network},
        keys::DescriptorPublicKey,
    };
    use types::account::entities::{
        FullAccountAuthKeysPayload, LiteAccountAuthKeysPayload, SoftwareAccountAuthKeysPayload,
        SpendingKeysetRequest,
    };

    use super::CreateAccountRequest;

    #[test]
    // This may seem trivial, but because the CreateAccountRequest enum is untagged, and because the payloads
    // are essentially telescoping (Full contains all the fields and more of Software, which
    // in turn contains all the fields and more of Lite), and because we don't deny unknown fields
    // during serde deserialization, this test failed when the variants are defined in their original order
    // (Full, Lite, Software). This means that you could create a Software request, serialize it, and it
    // would deserialize as a Lite request.
    fn test_untagged_create_account_request_deserialization() {
        let secp = Secp256k1::new();
        let public_key = secp.generate_keypair(&mut thread_rng()).1;

        let descriptor_public_key = DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap();

        let requests = [
            CreateAccountRequest::Full {
                auth: FullAccountAuthKeysPayload {
                    app: public_key,
                    hardware: public_key,
                    recovery: Some(public_key),
                },
                spending: SpendingKeysetRequest {
                    network: Network::Bitcoin,
                    app: descriptor_public_key.clone(),
                    hardware: descriptor_public_key,
                },
                is_test_account: true,
            },
            CreateAccountRequest::Software {
                auth: SoftwareAccountAuthKeysPayload {
                    app: public_key,
                    recovery: public_key,
                },
                is_test_account: true,
            },
            CreateAccountRequest::Lite {
                auth: LiteAccountAuthKeysPayload {
                    recovery: public_key,
                },
                is_test_account: true,
            },
        ];

        for request in requests.iter() {
            let serialized = serde_json::to_string(request).unwrap();
            let deserialized: CreateAccountRequest = serde_json::from_str(&serialized).unwrap();
            assert_eq!(request, &deserialized);
        }
    }
}
