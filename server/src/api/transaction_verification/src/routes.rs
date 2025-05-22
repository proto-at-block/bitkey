use crate::error::TransactionVerificationError;
use crate::service::Service as TransactionVerificationService;
use account::service::{FetchAccountInput, Service as AccountService};
use axum::{
    extract::{Path, State},
    routing::{get, post, put},
    Json, Router,
};
use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction as Psbt;
use bdk_utils::{generate_electrum_rpc_uris, DescriptorKeyset};
use errors::ApiError;
use experimentation::claims::ExperimentationClaims;
use feature_flags::service::Service as FeatureFlagsService;
use http_server::{
    router::RouterBuilder,
    swagger::{SwaggerEndpoint, Url},
};
use serde::{Deserialize, Serialize};
use std::str::FromStr;
use tracing::instrument;
use types::account::entities::TransactionVerificationPolicy;
use types::account::identifiers::{AccountId, KeysetId};
use types::transaction_verification::router::{
    InitiateTransactionVerificationView, TransactionVerificationView,
};
use types::transaction_verification::service::DescriptorKeysetWalletProvider;
use types::transaction_verification::TransactionVerificationId;
use userpool::userpool::UserPoolService;
use utoipa::{OpenApi, ToSchema};

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
    pub signing_keyset_id: KeysetId,
    pub hw_grant: String,
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
            wallet_provider,
            psbt,
            request.hw_grant,
            request.should_prompt_user,
        )
        .await?;
    Ok(Json(tx_verification.to_response()))
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
