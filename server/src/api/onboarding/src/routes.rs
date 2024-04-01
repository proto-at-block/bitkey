use std::collections::HashSet;
use std::env;
use std::str::FromStr;

use argon2::{
    password_hash::{PasswordHash, PasswordVerifier},
    Argon2,
};
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
use bdk_utils::error::BdkUtilError;
use bdk_utils::generate_electrum_rpc_uris;
use isocountry::CountryCode;
use notification::entities::NotificationTouchpoint;
use regex::Regex;
use serde::{Deserialize, Serialize};
use time::Duration;
use tracing::{error, event, instrument, Level};
use userpool::userpool::{CreateRecoveryUserInput, CreateWalletUserInput, UserPoolService};
use utoipa::{OpenApi, ToSchema};

use account::entities::{
    Account, CommsVerificationScope, FullAccountAuthKeys,
    FullAccountAuthKeysPayload as FullAccountAuthKeysRequest, Keyset, LiteAccount,
    LiteAccountAuthKeys, LiteAccountAuthKeysPayload as LiteAccountAuthKeysRequest, SpendingKeyset,
    SpendingKeysetRequest, Touchpoint, TouchpointPlatform, UpgradeLiteAccountAuthKeysPayload,
};
use account::service::{
    ActivateTouchpointForAccountInput, AddPushTouchpointToAccountInput, CompleteOnboardingInput,
    CreateAccountAndKeysetsInput, CreateInactiveSpendingKeysetInput, CreateLiteAccountInput,
    DeleteAccountInput, FetchAccountInput, FetchOrCreateEmailTouchpointInput,
    FetchOrCreatePhoneTouchpointInput, FetchTouchpointByIdInput, RotateToSpendingKeysetInput,
    Service as AccountService, UpgradeLiteAccountToFullAccountInput,
};
use authn_authz::key_claims::KeyClaims;
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
use external_identifier::ExternalIdentifier;
use feature_flags::service::Service as FeatureFlagsService;
use http_server::middlewares::identifier_generator::IdentifierGenerator;
use http_server::swagger::{SwaggerEndpoint, Url};
use notification::clients::iterable::{IterableClient, IterableMode};
use notification::clients::twilio::{TwilioClient, TwilioMode};
use notification::service::Service as NotificationService;
use recovery::repository::Repository as RecoveryService;
use types::account::identifiers::{AccountId, AuthKeysId, KeysetId, TouchpointId};
use wsm_rust_client::{SigningService, WsmClient};

use crate::account_validation::{AccountValidation, AccountValidationRequest};
use crate::{create_touchpoint_iterable_user, metrics, upsert_account_iterable_user};
use once_cell::sync::Lazy;

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
    pub NotificationService,
    pub AccountService,
    pub RecoveryService,
    pub WsmClient,
    pub CommsVerificationService,
    pub IterableClient,
    pub TwilioClient,
    pub FeatureFlagsService,
);

impl RouteState {
    pub fn unauthed_router(&self) -> Router {
        Router::new()
            .route("/api/accounts", post(create_account))
            .route("/api/bdk-configuration", get(get_bdk_config))
            .route("/api/demo/initiate", post(initiate_demo_mode))
            .route_layer(metrics::FACTORY.route_layer("onboarding".to_owned()))
            .with_state(self.to_owned())
    }

    pub fn authed_router(&self) -> Router {
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
            .route_layer(metrics::FACTORY.route_layer("onboarding".to_owned()))
            .with_state(self.to_owned())
    }

    pub fn recovery_authed_router(&self) -> Router {
        Router::new()
            .route("/api/accounts/:account_id/upgrade", post(upgrade_account))
            .route_layer(metrics::FACTORY.route_layer("onboarding".to_owned()))
            .with_state(self.to_owned())
    }

    pub fn account_or_recovery_authed_router(&self) -> Router {
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
    ),
    components(
        schemas(
            AccountActivateTouchpointRequest,
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
            account_id: account_id.clone(),
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
                    account_id: account_id.clone(),
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
                    account_id: account_id.clone(),
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
            account_id: account_id.clone(),
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
            account_id: account_id.clone(),
            scope,
            code: request.verification_code,
            duration: Duration::minutes(10),
        })
        .await?;

    Ok(Json(AccountVerifyTouchpointResponse {}))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct AccountActivateTouchpointRequest {}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct AccountActivateTouchpointResponse {}

#[instrument(
    fields(account_id),
    skip(account_service, comms_verification_service, iterable_client)
)]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/touchpoints/{touchpoint_id}/activate",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("touchpoint_id" = TouchpointId, description = "TouchpointId"),
    ),
    request_body = AccountActivateTouchpointRequest,
    responses(
        (status = 200, description = "Touchpoint was activated", body=AccountActivateTouchpointResponse),
    ),
)]
async fn activate_touchpoint_for_account(
    State(account_service): State<AccountService>,
    State(comms_verification_service): State<CommsVerificationService>,
    State(iterable_client): State<IterableClient>,
    key_proof: KeyClaims,
    Path((account_id, touchpoint_id)): Path<(AccountId, TouchpointId)>,
    Json(_request): Json<AccountActivateTouchpointRequest>,
) -> Result<Json<AccountActivateTouchpointResponse>, ApiError> {
    let account = account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    if account.get_common_fields().onboarding_complete
        && !(key_proof.app_signed && key_proof.hw_signed)
    {
        let msg = "valid signature over access token required by both app and hw auth keys";
        error!("{msg}");
        return Err(ApiError::GenericForbidden(msg.to_string()));
    }

    let touchpoint = account_service
        .fetch_touchpoint_by_id(FetchTouchpointByIdInput {
            account_id: account_id.clone(),
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
        .consume_verification_for_scope(ConsumeVerificationForScopeInput {
            account_id: account_id.clone(),
            scope: scope.clone(),
        })
        .await?;

    account_service
        .activate_touchpoint_for_account(ActivateTouchpointForAccountInput {
            account_id: account_id.clone(),
            touchpoint_id,
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

    Ok(Json(AccountActivateTouchpointResponse {}))
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
        }
    }

    pub fn auth_keys(&self) -> (Option<PublicKey>, Option<PublicKey>, Option<PublicKey>) {
        match self {
            CreateAccountRequest::Full { auth, .. } => {
                (Some(auth.app), Some(auth.hardware), auth.recovery)
            }
            CreateAccountRequest::Lite { auth, .. } => (None, None, Some(auth.recovery)),
        }
    }

    pub fn cognito_wallet_input(&self) -> Option<CreateWalletUserInput> {
        match self {
            CreateAccountRequest::Full { auth, .. } => {
                Some(CreateWalletUserInput::new(auth.app, auth.hardware))
            }
            CreateAccountRequest::Lite { .. } => None,
        }
    }

    pub fn cognito_recovery_input(&self) -> Option<CreateRecoveryUserInput> {
        match self {
            CreateAccountRequest::Full { auth, .. } => {
                let Some(k) = auth.recovery else {
                    return None;
                };
                Some(CreateRecoveryUserInput::new(k))
            }
            CreateAccountRequest::Lite { auth, .. } => {
                Some(CreateRecoveryUserInput::new(auth.recovery))
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
        };

        Ok(CreateAccountResponse { account_id, keyset })
    }
}

#[instrument(
    fields(account_id),
    skip(
        account_service,
        recovery_service,
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
    State(recovery_service): State<RecoveryService>,
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
            &recovery_service,
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
            Account::Lite(_) => {}
        }
        return Ok(Json(create_account_response));
    }

    let account_id = AccountId::new(id_generator.gen_account_id()).map_err(|e| {
        let msg = "Failed to generate account id";
        error!("{msg}: {e}");
        ApiError::GenericInternalApplicationError(msg.to_string())
    })?;

    // provide the generated account ID once we have it
    tracing::Span::current().record("account_id", &account_id.to_string());

    // Create a Cognito account
    let (app_auth_pubkey, hardware_auth_pubkey, recovery_auth_pubkey) = request.auth_keys();
    let wallet_input = request.cognito_wallet_input();
    let recovery_input = request.cognito_recovery_input();
    user_pool_service
        .create_users(&account_id, wallet_input, recovery_input)
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
        recovery_service,
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
    State(recovery_service): State<RecoveryService>,
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
    };

    AccountValidation::default()
        .validate(
            AccountValidationRequest::from((lite_account, &request)),
            &config,
            &account_service,
            &recovery_service,
        )
        .await?;

    // Create a wallet Cognito user
    user_pool_service
        .create_users(
            &account_id,
            Some(CreateWalletUserInput::new(
                request.auth.app,
                request.auth.hardware,
            )),
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

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CompleteOnboardingRequest {}

#[derive(Serialize, Deserialize, ToSchema)]
pub struct CompleteOnboardingResponse {}

#[instrument(skip(account_service, notification_service))]
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
    State(notification_service): State<NotificationService>,
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
) -> Result<Json<BdkConfigResponse>, ApiError> {
    let rpc_uris = generate_electrum_rpc_uris(&feature_flags_service)?;
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
