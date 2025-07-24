use std::str::FromStr;
use std::sync::Arc;

use axum::{
    extract::{Path, State},
    routing::{delete, get, post, put},
    Json, Router,
};
use serde::{Deserialize, Serialize};
use serde_with::{base64::Base64, serde_as};
use time::OffsetDateTime;
use tracing::{error, event, instrument, Level};
use utoipa::{OpenApi, ToSchema};

use account::service::{
    FetchAccountInput, FetchAndUpdateSpendingLimitInput, Service as AccountService,
};
use authn_authz::key_claims::KeyClaims;
use bdk_utils::{
    bdk::bitcoin::psbt::PartiallySignedTransaction as Psbt, generate_electrum_rpc_uris,
    TransactionBroadcasterTrait,
};
use errors::{ApiError, ErrorCode::NoSpendingLimitExists};
use exchange_rate::service::Service as ExchangeRateService;
use experimentation::claims::ExperimentationClaims;
use feature_flags::{flag::ContextKey, service::Service as FeatureFlagsService};
use http_server::{
    router::RouterBuilder,
    swagger::{SwaggerEndpoint, Url},
};
use screener::service::Service as ScreenerService;
use transaction_verification::service::Service as TransactionVerificationService;
use types::{
    account::{
        entities::FullAccount,
        identifiers::{AccountId, KeysetId},
        money::Money,
        spend_limit::SpendingLimit,
        spending::SpendingKeyDefinition,
    },
    currencies::{Currency, CurrencyCode, CurrencyCode::BTC},
    transaction_verification::{
        entities::BitcoinDisplayUnit, service::InitiateVerificationResult,
        TransactionVerificationId,
    },
};
use userpool::userpool::UserPoolService;
use wsm_rust_client::{SigningService, WsmClient};

use crate::{
    daily_spend_record::service::Service as DailySpendRecordService, entities::Settings,
    error::SigningError, get_mobile_pay_spending_record, metrics as mobile_pay_metrics,
    sats_for_limit, signed_psbt_cache::service::Service as SignedPsbtCacheService,
    signing_processor::SigningProcessor, signing_strategies::SigningStrategyFactory,
    util::total_sats_spent_today, SERVER_SIGNING_ENABLED,
};

#[derive(Clone, Deserialize)]
pub struct Config {
    pub use_local_currency_exchange: bool,
}

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub Config,
    pub WsmClient,
    pub AccountService,
    pub UserPoolService,
    pub Arc<dyn TransactionBroadcasterTrait>,
    pub DailySpendRecordService,
    pub ExchangeRateService,
    pub SignedPsbtCacheService,
    pub FeatureFlagsService,
    pub Arc<ScreenerService>,
    pub TransactionVerificationService,
);

impl RouterBuilder for RouteState {
    fn account_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/keysets/:keyset_id/sign-transaction",
                post(sign_transaction_with_keyset),
            )
            .route(
                "/api/accounts/:account_id/generate-partial-signatures",
                post(generate_partial_signatures_with_key_share),
            )
            .route(
                "/api/accounts/:account_id/mobile-pay",
                put(setup_mobile_pay_for_account),
            )
            .route(
                "/api/accounts/:account_id/mobile-pay",
                get(get_mobile_pay_for_account),
            )
            .route(
                "/api/accounts/:account_id/mobile-pay",
                delete(delete_mobile_pay_for_account),
            )
            .route_layer(
                mobile_pay_metrics::FACTORY
                    .route_layer(mobile_pay_metrics::FACTORY_NAME.to_owned()),
            )
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Mobile Pay", "/docs/mobilepay/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        sign_transaction_with_keyset,
        setup_mobile_pay_for_account,
        get_mobile_pay_for_account,
    ),
    components(
        schemas(CurrencyCode, SpendingLimit, Settings, Money, MobilePaySetupRequest, MobilePaySetupResponse, SignTransactionData, SignTransactionResponse)
    ),
    tags(
        (name = "Mobile Pay", description = "Spend Limits & Transaction Signing")
    )
)]
struct ApiDoc;

#[derive(Debug, Serialize, Deserialize, ToSchema, Default)]
#[serde(rename_all = "snake_case")]
pub struct SignTransactionData {
    pub psbt: String,
    #[serde(default)]
    pub should_prompt_user: bool,
    #[serde(default)]
    pub fiat_currency: Option<CurrencyCode>,
    #[serde(default)]
    pub bitcoin_display_unit: Option<BitcoinDisplayUnit>,
}

#[derive(Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE", tag = "status")]
pub enum SignTransactionResponse {
    Signed {
        tx: String,
    },
    VerificationRequired,
    VerificationRequested {
        verification_id: TransactionVerificationId,
        expiration: OffsetDateTime,
    },
}

impl From<InitiateVerificationResult> for SignTransactionResponse {
    fn from(result: InitiateVerificationResult) -> Self {
        match result {
            InitiateVerificationResult::VerificationRequired => Self::VerificationRequired,
            InitiateVerificationResult::VerificationRequested {
                verification_id,
                expiration,
            } => Self::VerificationRequested {
                verification_id,
                expiration,
            },
            _ => unreachable!("Expected VerificationRequired or VerificationRequested"),
        }
    }
}

async fn initiate_transaction_verification(
    transaction_verification_service: TransactionVerificationService,
    full_account: &FullAccount,
    psbt: Psbt,
    request: &SignTransactionData,
) -> Result<SignTransactionResponse, ApiError> {
    let initiate_result = transaction_verification_service
        .mobile_pay_initiate(
            &full_account.id,
            psbt,
            request.fiat_currency.unwrap_or(BTC),
            request
                .bitcoin_display_unit
                .clone()
                .unwrap_or(BitcoinDisplayUnit::Bitcoin),
            request.should_prompt_user,
        )
        .await?;
    Ok(initiate_result.into())
}

#[instrument(
    skip(
        wsm_client,
        config,
        daily_spend_record_service,
        signed_psbt_cache_service,
        exchange_rate_service,
        feature_flags_service,
        screener_service,
        transaction_broadcaster,
        transaction_verification_service,
        context_key
    ),
    fields(keyset_id, active_keyset_id)
)]
async fn sign_transaction_maybe_broadcast_impl(
    full_account: &FullAccount,
    keyset_id: &KeysetId,
    wsm_client: WsmClient,
    config: Config,
    request: SignTransactionData,
    transaction_broadcaster: Arc<dyn TransactionBroadcasterTrait>,
    daily_spend_record_service: DailySpendRecordService,
    exchange_rate_service: ExchangeRateService,
    signed_psbt_cache_service: SignedPsbtCacheService,
    feature_flags_service: FeatureFlagsService,
    screener_service: Arc<ScreenerService>,
    transaction_verification_service: TransactionVerificationService,
    context_key: Option<ContextKey>,
) -> Result<SignTransactionResponse, ApiError> {
    // At the earliest opportunity, we block the request if mobile pay is disabled by feature flag.
    if !SERVER_SIGNING_ENABLED
        .resolver(&feature_flags_service)
        .resolve()
    {
        return Err(SigningError::ServerSigningDisabled.into());
    }

    tracing::Span::current().record(
        "active_keyset_id",
        full_account.active_keyset_id.to_string(),
    );
    tracing::Span::current().record("keyset_id", keyset_id.to_string());

    let psbt = Psbt::from_str(&request.psbt).map_err(SigningError::from)?;

    let rpc_uris = generate_electrum_rpc_uris(&feature_flags_service, context_key.clone());

    let signing_processor = SigningProcessor::new(
        Arc::new(wsm_client),
        feature_flags_service.clone(),
        transaction_broadcaster,
    );

    let signing_strategy_factory = SigningStrategyFactory::new(
        signing_processor,
        screener_service,
        exchange_rate_service,
        daily_spend_record_service,
        signed_psbt_cache_service,
        feature_flags_service,
    );

    let signing_strategy = match signing_strategy_factory
        .construct_strategy(
            full_account,
            config,
            keyset_id,
            psbt.clone(),
            &rpc_uris,
            context_key,
        )
        .await
    {
        Ok(strategy) => strategy,
        Err(SigningError::TransactionVerificationRequired) => {
            return initiate_transaction_verification(
                transaction_verification_service,
                full_account,
                psbt,
                &request,
            )
            .await;
        }
        Err(e) => return Err(e.into()),
    };

    let signed_psbt = signing_strategy.execute().await?;
    Ok(SignTransactionResponse::Signed {
        tx: signed_psbt.to_string(),
    })
}

#[instrument(
    err,
    level = "INFO",
    skip(
        account_service,
        wsm_client,
        config,
        daily_spend_record_service,
        exchange_rate_service,
        signed_psbt_cache_service,
        request,
        feature_flags_service,
        screener_service,
        transaction_verification_service
    )
)]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/keysets/{keyset_id}/sign-transaction",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("keyset_id" = KeySetId, Path, description = "KeysetId"),
    ),
    request_body = SignTransactionData,
    responses(
        (status = 200, description = "Transaction was validated and signed with the server key in the specified keyset", body=SignTransactionResponse),
        (status = 400, description = "Transaction didn't pass spend rules"),
        (status = 404, description = "Account could not be found")
    ),
)]
async fn sign_transaction_with_keyset(
    Path((account_id, keyset_id)): Path<(AccountId, KeysetId)>,
    State(account_service): State<AccountService>,
    State(wsm_client): State<WsmClient>,
    State(config): State<Config>,
    State(transaction_broadcaster): State<Arc<dyn TransactionBroadcasterTrait>>,
    State(daily_spend_record_service): State<DailySpendRecordService>,
    State(exchange_rate_service): State<ExchangeRateService>,
    State(signed_psbt_cache_service): State<SignedPsbtCacheService>,
    State(feature_flags_service): State<FeatureFlagsService>,
    State(screener_service): State<Arc<ScreenerService>>,
    State(transaction_verification_service): State<TransactionVerificationService>,
    experimentation_claims: ExperimentationClaims,
    Json(request): Json<SignTransactionData>,
) -> Result<Json<SignTransactionResponse>, ApiError> {
    let full_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    let context_key = experimentation_claims.account_context_key().ok();

    let response = sign_transaction_maybe_broadcast_impl(
        &full_account,
        &keyset_id,
        wsm_client,
        config,
        request,
        transaction_broadcaster,
        daily_spend_record_service,
        exchange_rate_service,
        signed_psbt_cache_service,
        feature_flags_service,
        screener_service,
        transaction_verification_service,
        context_key,
    )
    .await?;

    Ok(Json(response))
}

#[serde_as]
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct GeneratePartialSignaturesRequest {
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    #[serde_as(as = "Base64")]
    pub noise_session: Vec<u8>,
}

#[serde_as]
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct GeneratePartialSignaturesResponse {
    #[serde_as(as = "Base64")]
    pub sealed_response: Vec<u8>,
}

/// Generates partial signatures for a Software Account.
///
/// This endpoint consumes a sealed request containing: (1) A base64-encoded PSBT, and (2) A list of
/// public signing commitments for each of the inputs in the PSBT. It returns a sealed response
/// containing the partial signatures, which the App would be responsible for aggregating, finalizing,
/// and broadcasting.
#[utoipa::path(
    post,
    path = "/api/accounts/:account_id/generate-partial-signatures",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = GeneratePartialSignaturesRequest,
    responses(
        (status = 200, description = "Partial signatures were generated for client", body=GeneratePartialSignaturesResponse),
        (status = 404, description = "An active key definition was not found for the account."),
        (status = 500, description = "Failed to generate partial signatures in WSM"),
    ),
)]
#[instrument(err, level = "INFO", skip(account_service, wsm_client, request,))]
async fn generate_partial_signatures_with_key_share(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(wsm_client): State<WsmClient>,
    Json(request): Json<GeneratePartialSignaturesRequest>,
) -> Result<Json<GeneratePartialSignaturesResponse>, ApiError> {
    let account = account_service
        .fetch_software_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let active_key_definition_id =
        account
            .active_key_definition_id
            .ok_or(ApiError::GenericNotFound(
                "Active key definition id not found. Was distributed key completed?".to_string(),
            ))?;

    let Some(key_definition) = account
        .spending_key_definitions
        .get(&active_key_definition_id)
    else {
        return Err(ApiError::GenericNotFound(
            "Key definition not found".to_string(),
        ));
    };

    let SpendingKeyDefinition::DistributedKey(distributed_key) = key_definition else {
        return Err(ApiError::GenericBadRequest(
            "Key definition is not a distributed key".to_string(),
        ));
    };

    let wsm_response = wsm_client
        .generate_partial_signatures(
            &active_key_definition_id.to_string(),
            distributed_key.network.into(),
            request.sealed_request,
            request.noise_session,
        )
        .await
        .map_err(|e| {
            let msg = "Failed to generate partial signatures in WSM";
            error!("{msg}: {e}");
            ApiError::GenericInternalApplicationError(msg.to_string())
        })?;

    Ok(Json(GeneratePartialSignaturesResponse {
        sealed_response: wsm_response.sealed_response,
    }))
}

#[derive(Clone, Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct MobilePaySetupRequest {
    pub limit: SpendingLimit,
}

#[derive(Debug, Serialize, Deserialize, ToSchema, PartialEq)]
#[serde(rename_all = "snake_case")]
pub struct MobilePaySetupResponse {}

#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/mobile-pay",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = MobilePaySetupRequest,
    responses(
        (status = 200, description = "Mobile Pay Spend Limit was successfully set", body=MobilePaySetupResponse),
        (status = 404, description = "Account could not be found")
    ),
)]
async fn setup_mobile_pay_for_account(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    key_proof: KeyClaims,
    Json(request): Json<MobilePaySetupRequest>,
) -> Result<Json<MobilePaySetupResponse>, ApiError> {
    setup_mobile_pay(account_id, account_service, key_proof, request).await
}

#[instrument(err, skip(account_service, request))]
async fn setup_mobile_pay(
    account_id: AccountId,
    account_service: AccountService,
    key_proof: KeyClaims,
    request: MobilePaySetupRequest,
) -> Result<Json<MobilePaySetupResponse>, ApiError> {
    if !(key_proof.hw_signed && key_proof.app_signed) {
        event!(
            Level::WARN,
            "valid signature over access token required by both app and hw auth keys"
        );
        return Err(ApiError::GenericForbidden(
            "valid signature over access token required by both app and hw auth keys".to_string(),
        ));
    }

    if !Currency::supported_currency_codes().contains(&request.limit.amount.currency_code) {
        return Err(ApiError::GenericForbidden(
            "valid supported currency required to setup mobile pay".to_string(),
        ));
    }

    account_service
        .fetch_and_update_spend_limit(FetchAndUpdateSpendingLimitInput {
            account_id: &account_id,
            new_spending_limit: Some(request.limit),
        })
        .await?;

    Ok(Json(MobilePaySetupResponse {}))
}

/// Response body representing the current state of the user's Mobile Pay setup.
#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetMobilePayResponse {
    // TODO [W-6166]: Remove extraneous fields here kept for EXT beta backward-compatibility.
    spent: Money,
    available: Money,
    limit: SpendingLimit,
    mobile_pay: Option<MobilePayConfiguration>,
}

impl GetMobilePayResponse {
    pub fn new(mobile_pay: Option<MobilePayConfiguration>) -> Self {
        match mobile_pay {
            None => Self::default(),
            Some(config) => Self {
                spent: config.clone().spent,
                available: config.clone().available,
                limit: config.clone().limit,
                mobile_pay: Some(config),
            },
        }
    }

    pub fn mobile_pay(&self) -> Option<&MobilePayConfiguration> {
        self.mobile_pay.as_ref()
    }
}

impl Default for GetMobilePayResponse {
    fn default() -> Self {
        Self {
            spent: Money {
                amount: 0,
                currency_code: BTC,
            },
            available: Money {
                amount: 0,
                currency_code: BTC,
            },
            limit: SpendingLimit {
                active: false,
                amount: Money {
                    amount: 0,
                    currency_code: BTC,
                },
                time_zone_offset: time::UtcOffset::UTC,
            },
            mobile_pay: None,
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct MobilePayConfiguration {
    /// Amount the user has spent so far.
    pub spent: Money,
    /// Amount that is available for the user to spend using Mobile Pay today.
    pub available: Money,
    /// The configured Mobile Pay limit the user has set.
    pub limit: SpendingLimit,
}

#[instrument(
    err,
    skip(
        account_service,
        config,
        daily_spend_record_service,
        exchange_rate_service,
    )
)]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/mobile-pay",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "The account's Mobile Pay settings are returned", body=MobilePaySetupResponse),
        (status = 404, description = "Account or mobile pay settings not found")
    ),
)]
async fn get_mobile_pay_for_account(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(config): State<Config>,
    State(daily_spend_record_service): State<DailySpendRecordService>,
    State(exchange_rate_service): State<ExchangeRateService>,
) -> Result<Json<GetMobilePayResponse>, ApiError> {
    let full_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    // If limit doesn't exist, return GetMobilePayResponse with None.
    let Some(limit) = full_account.spending_limit else {
        return Ok(Json(GetMobilePayResponse::default()));
    };

    let limit_in_sats = match limit.amount.currency_code {
        BTC => limit.clone(),
        _ => SpendingLimit {
            active: limit.active,
            amount: Money {
                amount: sats_for_limit(&limit, &config, &exchange_rate_service)
                    .await
                    .map_err(|_| {
                        ApiError::GenericInternalApplicationError(
                            "Could not convert limit to sats".to_string(),
                        )
                    })?,
                currency_code: BTC,
            },
            time_zone_offset: limit.time_zone_offset,
        },
    };

    let spending_history =
        get_mobile_pay_spending_record(&account_id, &daily_spend_record_service).await?;
    let total_spent = total_sats_spent_today(
        &spending_history.spending_entries(),
        &limit,
        OffsetDateTime::now_utc(),
    )
    .map_err(ApiError::GenericBadRequest)?;

    let response = GetMobilePayResponse::new(Some(MobilePayConfiguration {
        spent: Money {
            amount: total_spent,
            currency_code: BTC,
        },
        available: Money {
            amount: limit_in_sats.amount.amount.saturating_sub(total_spent),
            currency_code: BTC,
        },
        limit,
    }));

    Ok(Json(response))
}

#[instrument(err, skip(account_service))]
#[utoipa::path(
    delete,
    path = "/api/accounts/{account_id}/mobile-pay",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "The account's Mobile Pay is disabled"),
        (status = 404, description = "Account or mobile pay settings not found")
    ),
)]
async fn delete_mobile_pay_for_account(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
) -> Result<(), ApiError> {
    let full_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let spending_limit = full_account.spending_limit.clone().ok_or_else(|| {
        let msg = "Account does not have mobile pay set up.";
        ApiError::Specific {
            code: NoSpendingLimitExists,
            detail: Some(msg.to_owned()),
            field: None,
        }
    })?;

    account_service
        .fetch_and_update_spend_limit(FetchAndUpdateSpendingLimitInput {
            account_id: &account_id,
            new_spending_limit: Some(SpendingLimit {
                active: false,
                ..spending_limit
            }),
        })
        .await?;

    Ok(())
}
