use std::str::FromStr;

use axum::{
    extract::{Path, Query, State},
    http::{header, StatusCode},
    response::{Html, IntoResponse},
    routing::{delete, get, post, put},
    Json, Router,
};
use serde::{Deserialize, Serialize};
use serde_json;
use tracing::{event, instrument, Level};
use utoipa::{OpenApi, ToSchema};

use crate::{
    error::TransactionVerificationError,
    service::Service as TransactionVerificationService,
    static_handler::{get_template, static_handler},
};

use account::service::{
    tests::default_electrum_rpc_uris, FetchAccountInput, Service as AccountService,
};
use authn_authz::key_claims::KeyClaims;
use bdk_utils::{
    bdk::bitcoin::psbt::PartiallySignedTransaction as Psbt, generate_electrum_rpc_uris,
    get_outflow_addresses_for_psbt, get_total_outflow_for_psbt, DescriptorKeyset,
};
use errors::ApiError;
use exchange_rate::{
    currency_conversion::money_for_sats, select_exchange_rate_provider,
    service::Service as ExchangeRateService, ExchangeRateConfig,
};
use experimentation::claims::ExperimentationClaims;
use feature_flags::service::Service as FeatureFlagsService;
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
use secure_site::static_handler::{html_error, inject_json_into_template};
use types::{
    account::{
        entities::TransactionVerificationPolicy,
        identifiers::{AccountId, KeysetId},
    },
    currencies::CurrencyCode,
    exchange_rate::RateProvider,
    privileged_action::{
        router::generic::{PrivilegedActionRequest, PrivilegedActionResponse},
        shared::PrivilegedActionType,
    },
    transaction_verification::{
        entities::BitcoinDisplayUnit,
        router::{
            InitiateTransactionVerificationView, PutTransactionVerificationPolicyRequest,
            TransactionVerificationView,
        },
        service::DescriptorKeysetWalletProvider,
        TransactionVerificationId,
    },
};
use userpool::userpool::UserPoolService;

#[derive(Clone, Deserialize)]
pub struct Config {
    pub(crate) use_local_currency_exchange: bool,
}

impl ExchangeRateConfig for Config {
    fn use_local_currency_exchange(&self) -> bool {
        self.use_local_currency_exchange
    }
}

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub Config,
    pub AccountService,
    pub FeatureFlagsService,
    pub UserPoolService,
    pub TransactionVerificationService,
    pub ExchangeRateService,
    pub PrivilegedActionService,
);

impl RouterBuilder for RouteState {
    fn account_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/tx-verify/policy",
                get(get_transaction_verification_policy),
            )
            .route(
                "/api/accounts/:account_id/tx-verify/policy",
                put(put_transaction_verification_policy),
            )
            .route(
                "/api/accounts/:account_id/tx-verify/requests/:verification_id",
                get(check_transaction_verification),
            )
            .route(
                "/api/accounts/:account_id/tx-verify/requests/:verification_id",
                delete(cancel_transaction_verification),
            )
            .route(
                "/api/accounts/:account_id/tx-verify/requests",
                post(initiate_transaction_verification),
            )
            .with_state(self.to_owned())
    }

    fn secure_site_router(&self) -> Router {
        Router::new()
            .route("/tx-verify", get(get_transaction_verification_interface))
            .route(
                "/api/tx-verify/:verification_id",
                put(process_transaction_verification_token),
            )
            .route("/tx-verify/static/*file", get(static_handler))
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new(
                "Transaction Verification",
                "/docs/transaction-verification/openapi.json",
            ),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        cancel_transaction_verification,
        get_transaction_verification_policy,
        put_transaction_verification_policy,
        check_transaction_verification,
        initiate_transaction_verification,
    ),
    components(
        schemas(
            CancelTransactionVerificationResponse,
            GetTransactionVerificationPolicyResponse,
            PutTransactionVerificationPolicyRequest,
            PutTransactionVerificationPolicyResponse,
            InitiateTransactionVerificationRequest,
            InitiateTransactionVerificationView,
            TransactionVerificationView,
        )
    ),
    tags(
        (name = "Transaction Verification", description = "Transaction verification endpoints")
    ),
)]
struct ApiDoc;

#[derive(Debug, Serialize, Deserialize, ToSchema, PartialEq)]
#[serde(rename_all = "snake_case")]
pub struct GetTransactionVerificationPolicyResponse {
    #[serde(flatten)]
    pub policy: TransactionVerificationPolicy,
}

#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/tx-verify/policy",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "Transaction verification policy was successfully retrieved", body=GetTransactionVerificationPolicyResponse),
        (status = 404, description = "Account could not be found")
    ),
)]
#[instrument(skip(account_service))]
async fn get_transaction_verification_policy(
    State(account_service): State<AccountService>,
    Path(account_id): Path<AccountId>,
) -> Result<Json<GetTransactionVerificationPolicyResponse>, ApiError> {
    let account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    let policy = account.transaction_verification_policy.unwrap_or_default();
    Ok(Json(GetTransactionVerificationPolicyResponse { policy }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema, PartialEq)]
#[serde(rename_all = "snake_case")]
pub struct PutTransactionVerificationPolicyResponse {}

#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/tx-verify/policy",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = PutTransactionVerificationPolicyRequest,
    responses(
        (status = 200, description = "Mobile Pay Spend Limit was successfully set", body=PutTransactionVerificationPolicyResponse),
        (status = 404, description = "Account could not be found")
    ),
)]
#[instrument(skip(account_service, privileged_action_service))]
async fn put_transaction_verification_policy(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(privileged_action_service): State<PrivilegedActionService>,
    key_proof: KeyClaims,
    Json(request): Json<PutTransactionVerificationPolicyRequest>,
) -> Result<Json<PrivilegedActionResponse<PutTransactionVerificationPolicyResponse>>, ApiError> {
    let account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let get_threshold_amount = |policy: &TransactionVerificationPolicy| match policy {
        TransactionVerificationPolicy::Always => 0,
        TransactionVerificationPolicy::Threshold(v) => v.amount,
        TransactionVerificationPolicy::Never => u64::MAX,
    };

    let current_threshold = account
        .transaction_verification_policy
        .as_ref()
        .map(get_threshold_amount)
        .unwrap_or(u64::MAX);
    let new_threshold = get_threshold_amount(&request.policy);

    // Requires out-of-band if we're making verification LESS restrictive (increasing threshold)
    if new_threshold > current_threshold {
        let is_app_signed = key_proof.app_signed;
        let is_hw_signed = key_proof.hw_signed;
        let authorize_result = privileged_action_service
            .authorize_privileged_action(AuthorizePrivilegedActionInput {
                account_id: &account_id,
                privileged_action_definition:
                    &PrivilegedActionType::LoosenTransactionVerificationPolicy.into(),
                authentication: AuthenticationContext::Standard,
                privileged_action_request: &PrivilegedActionRequest::Initiate(request),
                request_validator: PrivilegedActionRequestValidatorBuilder::default()
                .on_initiate_out_of_band(Box::new(
                    move |_| {
                        Box::pin(async move {
                            if !(is_hw_signed && is_app_signed) {
                                event!(
                                    Level::WARN,
                                    "valid signature over access token required by both app and hw auth keys"
                                );
                                return Err(ApiError::GenericForbidden(
                                    "valid signature over access token required by both app and hw auth keys".to_string(),
                                ));
                            }
                            Ok::<(), ApiError>(())
                        })
                    },
                ))
                .build()?,
            })
            .await?;

        // For policy loosening, we expect this to always return Pending (requiring out-of-band)
        if let AuthorizePrivilegedActionOutput::Pending(response) = authorize_result {
            return Ok(Json(response));
        }
        return Err(ApiError::GenericInternalApplicationError(
            "Policy loosening should require privileged action authorization".to_string(),
        ));
    }

    account_service
        .put_transaction_verification_policy(&account_id, request.policy)
        .await?;
    Ok(Json(PrivilegedActionResponse::Completed(
        PutTransactionVerificationPolicyResponse {},
    )))
}

#[instrument(skip(transaction_verification_service), fields(account_id = %account_id))]
#[utoipa::path(
    get,
    path = "/api/accounts/:account_id/tx-verify/requests/:verification_id",
    responses(
        (status = 200, description = "Transaction verification request processed successfully", body = CheckTransactionVerificationResponse),
        (status = 400, description = "Bad Request - missing or invalid request"),
        (status = 401, description = "Unauthorized - invalid API key"),
        (status = 500, description = "Internal server error processing transaction verification")
    ),
    tag = "Transaction Verification"
)]
async fn check_transaction_verification(
    Path((account_id, verification_id)): Path<(AccountId, TransactionVerificationId)>,
    State(transaction_verification_service): State<TransactionVerificationService>,
) -> Result<Json<TransactionVerificationView>, ApiError> {
    let tx_verification = transaction_verification_service
        .fetch(&account_id, &verification_id)
        .await?;
    Ok(Json(tx_verification.into()))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct CancelTransactionVerificationResponse {}

#[instrument(skip(transaction_verification_service), fields(account_id = %account_id))]
#[utoipa::path(
    delete,
    path = "/api/accounts/:account_id/tx-verify/requests/:verification_id",
    responses(
        (status = 200, description = "Transaction verification request cancelled successfully", body = CancelTransactionVerificationResponse),
        (status = 400, description = "Bad Request - missing or invalid request"),
        (status = 401, description = "Unauthorized - invalid API key"),
        (status = 500, description = "Internal server error processing transaction verification")
    ),
    tag = "Transaction Verification"
)]
async fn cancel_transaction_verification(
    Path((account_id, verification_id)): Path<(AccountId, TransactionVerificationId)>,
    State(transaction_verification_service): State<TransactionVerificationService>,
) -> Result<Json<TransactionVerificationView>, ApiError> {
    let updated_tx_verification = transaction_verification_service
        .cancel(&account_id, &verification_id)
        .await?;
    Ok(Json(updated_tx_verification.into()))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct InitiateTransactionVerificationRequest {
    pub psbt: String,
    pub fiat_currency: CurrencyCode,
    pub bitcoin_display_unit: BitcoinDisplayUnit,
    pub signing_keyset_id: KeysetId,
    pub should_prompt_user: bool,
}

#[instrument(
    skip(
        account_service,
        feature_flags_service,
        transaction_verification_service,
        experimentation_claims
    ),
    fields(account_id = %account_id)
)]
#[utoipa::path(
    post,
    path = "/api/accounts/:account_id/tx-verify/requests",
    request_body = InitiateTransactionVerificationRequest,
    responses(
        (status = 200, description = "Transaction verification request processed successfully", body = TxVerificationResponse),
        (status = 400, description = "Bad Request - missing or invalid request"),
        (status = 401, description = "Unauthorized - invalid API key"),
        (status = 500, description = "Internal server error processing transaction verification")
    ),
    tag = "Transaction Verification"
)]
async fn initiate_transaction_verification(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(feature_flags_service): State<FeatureFlagsService>,
    State(transaction_verification_service): State<TransactionVerificationService>,
    experimentation_claims: ExperimentationClaims,
    Json(request): Json<InitiateTransactionVerificationRequest>,
) -> Result<Json<InitiateTransactionVerificationView>, ApiError> {
    let context_key = experimentation_claims.account_context_key().ok();
    let rpc_uris = generate_electrum_rpc_uris(&feature_flags_service, context_key.clone());
    let psbt = Psbt::from_str(&request.psbt).map_err(TransactionVerificationError::from)?;
    let account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    let signing_keyset_id = request.signing_keyset_id;
    let source_descriptor: DescriptorKeyset = account
        .spending_keysets
        .get(&signing_keyset_id)
        .ok_or_else(|| TransactionVerificationError::NoSpendKeyset(signing_keyset_id))?
        .to_owned()
        .into();
    let wallet_provider = DescriptorKeysetWalletProvider::new(source_descriptor, rpc_uris);

    let tx_verification = transaction_verification_service
        .initiate(
            &account_id,
            account.hardware_auth_pubkey,
            wallet_provider,
            psbt,
            request.fiat_currency,
            request.bitcoin_display_unit,
            request.should_prompt_user,
        )
        .await?;
    Ok(Json(tx_verification.to_response()))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case", tag = "action")]
pub enum ProcessTransactionVerificationTokenRequest {
    Cancel { cancel_token: String },
    Confirm { confirm_token: String },
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ProcessTransactionVerificationTokenResponse {}

async fn process_transaction_verification_token(
    State(transaction_verification_service): State<TransactionVerificationService>,
    Path(verification_id): Path<TransactionVerificationId>,
    Json(request): Json<ProcessTransactionVerificationTokenRequest>,
) -> Result<Json<ProcessTransactionVerificationTokenResponse>, ApiError> {
    match request {
        ProcessTransactionVerificationTokenRequest::Confirm { confirm_token } => {
            transaction_verification_service
                .verify_with_confirmation_token(&verification_id, &confirm_token)
                .await?;
            Ok(Json(ProcessTransactionVerificationTokenResponse {}))
        }
        ProcessTransactionVerificationTokenRequest::Cancel { cancel_token } => {
            transaction_verification_service
                .verify_with_cancellation_token(&verification_id, &cancel_token)
                .await?;
            Ok(Json(ProcessTransactionVerificationTokenResponse {}))
        }
    }
}

#[derive(Deserialize)]
pub struct TransactionVerificationInterfaceParams {
    web_auth_token: String,
}

pub async fn get_transaction_verification_interface(
    State(transaction_verification_service): State<TransactionVerificationService>,
    State(account_service): State<AccountService>,
    State(config): State<Config>,
    State(exchange_rate_service): State<ExchangeRateService>,
    Query(params): Query<TransactionVerificationInterfaceParams>,
) -> Result<impl IntoResponse, impl IntoResponse> {
    let tx_verification = transaction_verification_service
        .fetch_pending_with_web_auth_token(params.web_auth_token)
        .await
        .map_err(|e| html_error(StatusCode::INTERNAL_SERVER_ERROR, e))?;

    let account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &tx_verification.common_fields.account_id,
        })
        .await
        .map_err(|e| html_error(StatusCode::INTERNAL_SERVER_ERROR, e))?;
    let electrum_rpc_uris = default_electrum_rpc_uris();
    let wallet = account
        .active_descriptor_keyset()
        .ok_or(html_error(StatusCode::BAD_REQUEST, "Invalid keyset state"))?
        .generate_wallet(false, &electrum_rpc_uris)
        .map_err(|e| html_error(StatusCode::INTERNAL_SERVER_ERROR, e))?;
    let psbt = &tx_verification.common_fields.psbt;

    let to_currency_code = tx_verification.fiat_currency;
    let amount_sats = get_total_outflow_for_psbt(&wallet, psbt);
    let amount_fiat = cents_for_sats(
        &config,
        &exchange_rate_service,
        amount_sats,
        &to_currency_code,
    )
    .await
    .map_err(|e| html_error(StatusCode::INTERNAL_SERVER_ERROR, e))?;
    let recipient = match get_outflow_addresses_for_psbt(&wallet, psbt, wallet.network())
        .map_err(|e| html_error(StatusCode::INTERNAL_SERVER_ERROR, e))?
    {
        addresses if addresses.is_empty() => {
            return Err(html_error(
                StatusCode::INTERNAL_SERVER_ERROR,
                "No recipient address found in PSBT",
            ))
        }
        addresses if addresses.len() > 1 => {
            return Err(html_error(
                StatusCode::INTERNAL_SERVER_ERROR,
                "Multiple recipient addresses found in PSBT",
            ))
        }
        addresses => addresses[0].clone(),
    };

    let confirm_token = &tx_verification.confirmation_token;
    let cancel_token = &tx_verification.cancellation_token;

    let html_template = get_template();
    let verification_params = serde_json::json!({
        "verificationId": tx_verification.common_fields.id,
        "amountSats": amount_sats,
        "amountFiat": amount_fiat,
        "amountCurrency": to_currency_code.to_string(),
        "recipient": recipient,
        "confirmToken": confirm_token,
        "cancelToken": cancel_token
    });

    let html =
        inject_json_into_template(&html_template, "verification-params", verification_params)
            .map_err(|e| html_error(StatusCode::INTERNAL_SERVER_ERROR, e))?;

    Ok((
        StatusCode::OK,
        [(header::CONTENT_TYPE, "text/html; charset=utf-8")],
        Html(html),
    ))
}

async fn cents_for_sats(
    config: &Config,
    exchange_rate_service: &ExchangeRateService,
    sats: u64,
    to_currency: &CurrencyCode,
) -> Result<u64, TransactionVerificationError> {
    let money = match select_exchange_rate_provider(config) {
        RateProvider::Local(provider) => {
            money_for_sats(exchange_rate_service, provider, sats, to_currency).await?
        }
        RateProvider::Coingecko(provider) => {
            money_for_sats(exchange_rate_service, provider, sats, to_currency).await?
        }
    };
    Ok(money.amount)
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    use types::account::money::Money;
    use types::currencies::CurrencyCode::USD;

    #[test]
    fn test_transaction_verification_policy_serialization() {
        // Test Never policy
        let policy = TransactionVerificationPolicy::Never;
        let serialized = serde_json::to_value(&policy).unwrap();
        let expected = json!({
            "state": "NEVER"
        });
        assert_eq!(serialized, expected);

        // Test Threshold policy
        let policy = TransactionVerificationPolicy::Threshold(Money {
            amount: 1000,
            currency_code: USD,
        });
        let serialized = serde_json::to_value(&policy).unwrap();
        let expected = json!({
            "state": "THRESHOLD",
            "threshold": {
                "amount": 1000,
                "currency_code": "USD"
            }
        });
        assert_eq!(serialized, expected);

        // Test Always policy
        let policy = TransactionVerificationPolicy::Always;
        let serialized = serde_json::to_value(&policy).unwrap();
        let expected = json!({
            "state": "ALWAYS"
        });
        assert_eq!(serialized, expected);
    }

    #[test]
    fn test_transaction_verification_policy_deserialization() {
        // Test Never policy
        let json = json!({
            "state": "NEVER"
        });
        let deserialized: TransactionVerificationPolicy = serde_json::from_value(json).unwrap();
        assert!(matches!(deserialized, TransactionVerificationPolicy::Never));

        // Test Threshold policy
        let json = json!({
            "state": "THRESHOLD",
            "threshold": {
                "amount": 1000,
                "currency_code": "USD"
            }
        });
        let deserialized: TransactionVerificationPolicy = serde_json::from_value(json).unwrap();
        match deserialized {
            TransactionVerificationPolicy::Threshold(money) => {
                assert_eq!(money.amount, 1000);
                assert_eq!(money.currency_code, USD);
            }
            _ => panic!("Expected Threshold policy"),
        }

        // Test Always policy
        let json = json!({
            "state": "ALWAYS"
        });
        let deserialized: TransactionVerificationPolicy = serde_json::from_value(json).unwrap();
        assert!(matches!(
            deserialized,
            TransactionVerificationPolicy::Always
        ));
    }
}
