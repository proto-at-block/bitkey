use crate::error::TransactionVerificationError;
use crate::service::Service as TransactionVerificationService;
use crate::static_handler::{get_template, static_handler};
use account::service::{
    tests::default_electrum_rpc_uris, FetchAccountInput, Service as AccountService,
};
use axum::{
    extract::{Path, Query, State},
    http::{header, StatusCode},
    response::{Html, IntoResponse},
    routing::{get, post, put},
    Json, Router,
};
use bdk_utils::{
    bdk::bitcoin::psbt::PartiallySignedTransaction as Psbt, get_outflow_addresses_for_psbt,
    get_total_outflow_for_psbt,
};
use bdk_utils::{generate_electrum_rpc_uris, DescriptorKeyset};
use errors::ApiError;
use experimentation::claims::ExperimentationClaims;
use feature_flags::service::Service as FeatureFlagsService;
use http_server::{
    router::RouterBuilder,
    swagger::{SwaggerEndpoint, Url},
};
use regex;
use serde::{Deserialize, Serialize};
use serde_json;
use std::str::FromStr;
use tracing::instrument;
use types::account::entities::TransactionVerificationPolicy;
use types::account::identifiers::{AccountId, KeysetId};
use types::currencies::CurrencyCode;
use types::transaction_verification::entities::BitcoinDisplayUnit;
use types::transaction_verification::router::{
    InitiateTransactionVerificationView, TransactionVerificationView,
};
use types::transaction_verification::service::DescriptorKeysetWalletProvider;
use types::transaction_verification::TransactionVerificationId;
use userpool::userpool::UserPoolService;
use utoipa::{OpenApi, ToSchema};

// Helper function to create HTML error responses
// TODO: Move this to a more general purpose location
fn html_error<E: std::fmt::Display>(
    status: StatusCode,
    error: E,
) -> (
    StatusCode,
    [(header::HeaderName, &'static str); 1],
    Html<String>,
) {
    (
        status,
        [(header::CONTENT_TYPE, "text/html; charset=utf-8")],
        Html(format!(
            "<html><body><h1>Error</h1><p>{}</p></body></html>",
            error
        )),
    )
}

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub AccountService,
    pub FeatureFlagsService,
    pub UserPoolService,
    pub TransactionVerificationService,
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
                post(check_transaction_verification),
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
            .route("/static/*file", get(static_handler))
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
        put_transaction_verification_policy,
        check_transaction_verification,
        initiate_transaction_verification,
    ),
    components(
        schemas(
            PutTransactionVerificationPolicyRequest,
            PutTransactionVerificationPolicyResponse,
            CheckTransactionVerificationRequest,
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
pub struct PutTransactionVerificationPolicyRequest {
    #[serde(flatten)]
    pub policy: TransactionVerificationPolicy,
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
#[instrument(skip(account_service))]
async fn put_transaction_verification_policy(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    Json(request): Json<PutTransactionVerificationPolicyRequest>,
) -> Result<Json<PutTransactionVerificationPolicyResponse>, ApiError> {
    account_service
        .put_transaction_verification_policy(&account_id, request.policy)
        .await?;
    Ok(Json(PutTransactionVerificationPolicyResponse {}))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct CheckTransactionVerificationRequest {
    pub psbt: String,
    pub hw_grant: String,
    pub should_prompt_user: bool,
}

#[instrument(skip(transaction_verification_service), fields(account_id = %account_id))]
#[utoipa::path(
    post,
    path = "/api/accounts/:account_id/tx-verify/requests/:verification_id",
    request_body = CheckTransactionVerificationRequest,
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
    Json(request): Json<CheckTransactionVerificationRequest>,
) -> Result<Json<TransactionVerificationView>, ApiError> {
    let tx_verification = transaction_verification_service
        .fetch(&account_id, &verification_id)
        .await?;
    Ok(Json(tx_verification.into()))
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
    fields(account_id = %account_id))
]
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

    let amount_sats = get_total_outflow_for_psbt(&wallet, psbt);
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
        "recipient": recipient,
        "confirmToken": confirm_token,
        "cancelToken": cancel_token
    });

    let params_json =
        serde_json::to_string(&verification_params).unwrap_or_else(|_| "{}".to_string());

    // Create a regex pattern to find and replace the mock verification-params script
    let mock_script_pattern =
        r#"<script type="application/json" id="verification-params">(\s*\{[\s\S]*?\})\s*</script>"#;
    let replacement = format!(
        r#"<script type="application/json" id="verification-params">{}</script>"#,
        params_json
    );

    // Replace the mock data with real data using regex
    let html_regex = regex::Regex::new(mock_script_pattern)
        .map_err(|e| html_error(StatusCode::INTERNAL_SERVER_ERROR, e))?;
    let html = html_regex
        .replace(&html_template, replacement.as_str())
        .to_string();

    Ok((
        StatusCode::OK,
        [(header::CONTENT_TYPE, "text/html; charset=utf-8")],
        Html(html),
    ))
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
