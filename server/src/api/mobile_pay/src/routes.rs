use std::str::FromStr;
use std::sync::Arc;

use axum::routing::delete;
use axum::{
    extract::{Path, State},
    routing::{get, post, put},
    Json, Router,
};
use serde::{Deserialize, Serialize};
use time::{Duration, OffsetDateTime};
use tracing::{error, event, instrument, Level};
use utoipa::{OpenApi, ToSchema};

use account::service::FetchAndUpdateSpendingLimitInput;
use account::service::{FetchAccountInput, Service as AccountService};
use account::spend_limit::{Money, SpendingLimit};
use authn_authz::key_claims::KeyClaims;
use authn_authz::userpool::UserPoolService;
use bdk_utils::bdk::SignOptions;
use bdk_utils::generate_electrum_rpc_uris;
use bdk_utils::{
    bdk::bitcoin::psbt::PartiallySignedTransaction as Psbt, is_psbt_addressed_to_wallet,
};
use bdk_utils::{AttributableWallet, DescriptorKeyset, TransactionBroadcasterTrait};
use errors::ErrorCode::NoSpendingLimitExists;
use errors::{ApiError, RouteError};
use exchange_rate::currency_conversion::sats_for;
use exchange_rate::flags::FLAG_USE_CASH_EXCHANGE_RATE_PROVIDER;
use exchange_rate::service::Service as ExchangeRateService;
use feature_flags::service::Service as FeatureFlagsService;
use http_server::swagger::{SwaggerEndpoint, Url};
use metrics::KeyValue;
use types::account::identifiers::{AccountId, KeysetId};
use types::currencies::CurrencyCode;
use types::currencies::CurrencyCode::BTC;
use types::exchange_rate::bitstamp::BitstampRateProvider;
use types::exchange_rate::cash::CashAppRateProvider;
use types::exchange_rate::local_rate_provider::LocalRateProvider;
use wsm_rust_client::{SigningService, WsmClient};

use crate::daily_spend_record::entities::{DailySpendingRecord, SpendingEntry};
use crate::daily_spend_record::service::Service as DailySpendRecordService;
use crate::entities::{Features, Settings};
use crate::metrics as mobile_pay_metrics;
use crate::signed_psbt_cache::service::Service as SignedPsbtCacheService;
use crate::spend_rules::SpendRuleSet;
use crate::util::total_sats_spent_today;

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
);

impl From<RouteState> for Router {
    fn from(value: RouteState) -> Self {
        Router::new()
            .route(
                "/api/accounts/:account_id/sign-transaction",
                post(sign_transaction_with_active_keyset),
            )
            .route(
                "/api/accounts/:account_id/keysets/:keyset_id/sign-transaction",
                post(sign_transaction_with_keyset),
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
            .with_state(value)
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
        sign_transaction_with_active_keyset,
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

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct SignTransactionData {
    pub psbt: String,
}

#[derive(Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct SignTransactionResponse {
    pub tx: String,
}

#[instrument(
    skip(
        account_service,
        wsm_client,
        config,
        daily_spend_record_service,
        signed_psbt_cache_service,
        exchange_rate_service,
        feature_flags_service
    ),
    fields(keyset_id, active_keyset_id)
)]
async fn sign_transaction_maybe_broadcast_impl(
    account_keyset: (AccountId, Option<KeysetId>),
    account_service: AccountService,
    wsm_client: WsmClient,
    config: Config,
    request: SignTransactionData,
    transaction_broadcaster: Arc<dyn TransactionBroadcasterTrait>,
    daily_spend_record_service: DailySpendRecordService,
    exchange_rate_service: ExchangeRateService,
    signed_psbt_cache_service: SignedPsbtCacheService,
    feature_flags_service: FeatureFlagsService,
) -> Result<SignTransactionResponse, ApiError> {
    let (account_id, keyset_id) = account_keyset;

    let signing_start_time = OffsetDateTime::now_utc();

    let psbt = Psbt::from_str(&request.psbt)
        .map_err(|err| RouteError::InvalidPsbt(err.to_string(), request.psbt.clone()))?;

    // if we've already signed the psbt, return it from the cache
    // do not update account state or spend time checking it against rules
    // idempotency is important here
    if let Some(signed_psbt) = signed_psbt_cache_service
        .get(psbt.unsigned_tx.txid())
        .await?
    {
        return Ok(SignTransactionResponse {
            tx: signed_psbt.psbt.to_string(),
        });
    }

    let full_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    tracing::Span::current().record(
        "active_keyset_id",
        &full_account.active_keyset_id.to_string(),
    );

    let active_descriptor: DescriptorKeyset = full_account
        .active_spending_keyset()
        .ok_or(RouteError::NoActiveSpendKeyset)?
        .to_owned()
        .into();

    let (is_not_active_keyset, keyset_id) = match keyset_id {
        Some(k_id) => (k_id != full_account.active_keyset_id.clone(), k_id),
        None => (false, full_account.active_keyset_id.clone()),
    };
    tracing::Span::current().record("keyset_id", &keyset_id.to_string());

    let requested_descriptor: DescriptorKeyset = full_account
        .spending_keysets
        .get(&keyset_id)
        .ok_or_else(|| RouteError::NoSpendKeysetError(keyset_id.to_string()))?
        .to_owned()
        .into();

    let rpc_uris = generate_electrum_rpc_uris(&feature_flags_service)?;
    // don't need to sync the wallet since we keep track of outflows in the spending records
    let wallet = requested_descriptor.generate_wallet(false, &rpc_uris)?;

    // Enforce spending conditions on any transaction not moving into the active keyset (in other words, it's not a self-spend)
    let bypass_mobile_spend_limit = if is_not_active_keyset {
        let active_wallet = active_descriptor.generate_wallet(true, &rpc_uris)?;
        is_psbt_addressed_to_wallet(&active_wallet, &psbt)
    } else {
        let is_self_spend = active_descriptor
            .generate_wallet(false, &rpc_uris)?
            .is_addressed_to_self(&psbt)
            .unwrap_or(false); // If there are malformed or invalid data in the PSBT, dont bypass checks
        Ok(is_self_spend)
    }?;

    let updated_spending_record = if !bypass_mobile_spend_limit {
        // TODO [W-4400]: Move limit enforcement here to its own rule for SpendRuleSet, and clean-up
        // duplicated access to spending limit.
        if !full_account.is_spending_limit_active() {
            let msg = "Attempted to sign with Mobile Pay when user has Mobile Pay turned off.";
            error!("{msg}");
            return Err(ApiError::GenericForbidden(msg.to_string()));
        }
        let limit = full_account
            .spending_limit
            .ok_or(RouteError::MissingMobilePaySettings)?;

        let daily_limit_sats = sats_for_limit(
            &limit,
            &config,
            &exchange_rate_service,
            &feature_flags_service,
        )
        .await?;

        let mobile_pay_spending_record =
            get_mobile_pay_spending_record(&account_id, &daily_spend_record_service).await?;

        // bundle up yesterday and today's spending records for spend rule checking
        let spending_entries = mobile_pay_spending_record.spending_entries();

        let features = Features {
            settings: Settings { limit },
            daily_limit_sats,
        };

        SpendRuleSet::default()
            .check_spend_rules(&wallet, &psbt, &features, &spending_entries)
            .map_err(|reasons| {
                let error_message = format!(
                    "Transaction failed to pass spend rules: {}",
                    reasons.first().expect("should be at least one error")
                );
                event!(Level::INFO, error_message);
                ApiError::GenericBadRequest(error_message)
            })?;

        let mut today_spending_record = mobile_pay_spending_record.today;
        today_spending_record.update_with_psbt(&wallet, &psbt);

        Some(today_spending_record)
    } else {
        // If the transaction is spending to the active keyset (it's a self-spend), we don't do rules checking on it
        None
    };

    // currently, wsm constructs a BDK wallet to do its signing, so we need to construct external and internal descriptors for it
    let receiving = requested_descriptor
        .receiving()
        .into_multisig_descriptor()?;
    let change = requested_descriptor.change().into_multisig_descriptor()?;

    let result = wsm_client
        .sign_psbt(
            &keyset_id.to_string(),
            &receiving.to_string(),
            &change.to_string(),
            &request.psbt,
        )
        .await
        .map_err(|err| {
            event!(
                Level::INFO,
                "Could not sign PSBT with WSM due to error: {}",
                err.to_string()
            );
            ApiError::GenericInternalApplicationError("WSM could not sign PSBT".to_string())
        })?;

    let mut signed_psbt = Psbt::from_str(&result.psbt)
        .map_err(|err| RouteError::InvalidPsbt(err.to_string(), result.psbt.clone()))?;

    mobile_pay_metrics::MOBILE_PAY_TIME_TO_COSIGN.record(
        (OffsetDateTime::now_utc() - signing_start_time).whole_milliseconds() as u64,
        &[KeyValue::new(
            mobile_pay_metrics::IS_MOBILE_PAY,
            !bypass_mobile_spend_limit,
        )],
    );

    let psbt_fully_signed = wallet
        .finalize_psbt(&mut signed_psbt, SignOptions::default())
        .map_err(|err| RouteError::InvalidPsbt(err.to_string(), result.psbt.clone()))?;

    // Once the PSBT has been signed by WSM, update the daily spending record if it needs updating
    if let Some(today_spending_record) = updated_spending_record {
        daily_spend_record_service
            .save_daily_spending_record(today_spending_record)
            .await?;
    }

    // Note: If there's a failure between updating the daily spending record and updating the cache,
    // the customer will consume some of their spending budget without actually being able to make the spend
    // TODO: [W-3292] make the cache update and the spending record update transactional.

    // save the psbt to the cache so that if the client
    // retries the request, we can return the same signed psbt
    signed_psbt_cache_service.put(signed_psbt.clone()).await?;

    if psbt_fully_signed {
        let broadcast_start_time = OffsetDateTime::now_utc();
        transaction_broadcaster.broadcast(wallet, &mut signed_psbt, &rpc_uris)?;
        mobile_pay_metrics::MOBILE_PAY_F8E_TIME_TO_BROADCAST.record(
            (OffsetDateTime::now_utc() - broadcast_start_time).whole_milliseconds() as u64,
            &[KeyValue::new(
                mobile_pay_metrics::IS_MOBILE_PAY,
                !bypass_mobile_spend_limit,
            )],
        );
    }

    Ok(SignTransactionResponse { tx: result.psbt })
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
        feature_flags_service
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
    Json(request): Json<SignTransactionData>,
) -> Result<Json<SignTransactionResponse>, ApiError> {
    let response = sign_transaction_maybe_broadcast_impl(
        (account_id, Some(keyset_id)),
        account_service,
        wsm_client,
        config,
        request,
        transaction_broadcaster,
        daily_spend_record_service,
        exchange_rate_service,
        signed_psbt_cache_service,
        feature_flags_service,
    )
    .await?;
    Ok(Json(response))
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
        feature_flags_service
    )
)]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/sign-transaction",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = SignTransactionData,
    responses(
        (status = 200, description = "Transaction was validated and signed with the server key for the active keyset", body=SignTransactionResponse),
        (status = 400, description = "Transaction didn't pass spend rules"),
        (status = 404, description = "Account could not be found")
    ),
)]
#[deprecated(note = "Use /api/accounts/{account_id}/keysets/{keyset_id}/sign-transaction instead")]
async fn sign_transaction_with_active_keyset(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(wsm_client): State<WsmClient>,
    State(config): State<Config>,
    State(transaction_broadcaster): State<Arc<dyn TransactionBroadcasterTrait>>,
    State(daily_spend_record_service): State<DailySpendRecordService>,
    State(exchange_rate_service): State<ExchangeRateService>,
    State(signed_psbt_cache_service): State<SignedPsbtCacheService>,
    State(feature_flags_service): State<FeatureFlagsService>,
    Json(request): Json<SignTransactionData>,
) -> Result<Json<SignTransactionResponse>, ApiError> {
    let response = sign_transaction_maybe_broadcast_impl(
        (account_id, None),
        account_service,
        wsm_client,
        config,
        request,
        transaction_broadcaster,
        daily_spend_record_service,
        exchange_rate_service,
        signed_psbt_cache_service,
        feature_flags_service,
    )
    .await?;
    Ok(Json(response))
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
    if account_id.to_string() != key_proof.account_id {
        event!(
            Level::ERROR,
            "Account id in path does not match account id in access token"
        );

        return Err(ApiError::GenericBadRequest(
            "Account id in path does not match account id in access token".to_string(),
        ));
    }

    if !(key_proof.hw_signed && key_proof.app_signed) {
        event!(
            Level::WARN,
            "valid signature over access token required by both app and hw auth keys"
        );
        return Err(ApiError::GenericBadRequest(
            "valid signature over access token required by both app and hw auth keys".to_string(),
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
pub struct MobilePayResponse {
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
        feature_flags_service
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
    State(feature_flags_service): State<FeatureFlagsService>,
) -> Result<Json<MobilePayResponse>, ApiError> {
    let full_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let limit = full_account.spending_limit.clone().ok_or_else(|| {
        ApiError::GenericNotFound("Account does not have mobile pay set up.".into())
    })?;
    let limit_in_sats = match limit.amount.currency_code {
        BTC => limit.clone(),
        _ => SpendingLimit {
            active: limit.active,
            amount: Money {
                amount: sats_for_limit(
                    &limit,
                    &config,
                    &exchange_rate_service,
                    &feature_flags_service,
                )
                .await?,
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

    let response = MobilePayResponse {
        spent: Money {
            amount: total_spent,
            currency_code: BTC,
        },
        available: Money {
            amount: limit_in_sats.amount.amount.saturating_sub(total_spent),
            currency_code: BTC,
        },
        limit,
    };
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
/// Data structure used to represent [`DailySpendingRecord`]s that are relevant to Mobile Pay.
///
/// Currently, 3AM is the start of each Mobile Pay window, so "yesterday's" spending record may
/// still be relevant. See [`get_mobile_pay_spending_record`] for more information.
struct MobilePaySpendingRecord {
    yesterday: DailySpendingRecord,
    today: DailySpendingRecord,
}

impl MobilePaySpendingRecord {
    /// Returns a flattened list of [`SpendingEntry`] from yesterday and today.
    fn spending_entries(&self) -> Vec<&SpendingEntry> {
        vec![
            self.yesterday.get_spending_entries(),
            self.today.get_spending_entries(),
        ]
        .into_iter()
        .flatten()
        .collect()
    }
}

enum RateProvider {
    Local(LocalRateProvider),
    CashApp(CashAppRateProvider),
    Bitstamp(BitstampRateProvider),
}

fn select_exchange_rate_provider(config: &Config, use_cash_app_rate: bool) -> RateProvider {
    if config.use_local_currency_exchange {
        RateProvider::Local(LocalRateProvider::new())
    } else if use_cash_app_rate {
        RateProvider::CashApp(CashAppRateProvider::new())
    } else {
        RateProvider::Bitstamp(BitstampRateProvider::new())
    }
}

async fn sats_for_limit(
    limit: &SpendingLimit,
    config: &Config,
    exchange_rate_service: &ExchangeRateService,
    feature_flags_service: &FeatureFlagsService,
) -> Result<u64, ApiError> {
    let use_cash_app_rate = FLAG_USE_CASH_EXCHANGE_RATE_PROVIDER
        .resolver(feature_flags_service)
        .resolve();

    match select_exchange_rate_provider(config, use_cash_app_rate) {
        RateProvider::Local(provider) => {
            sats_for(exchange_rate_service, provider, &limit.amount).await
        }
        RateProvider::CashApp(provider) => {
            sats_for(exchange_rate_service, provider, &limit.amount).await
        }
        RateProvider::Bitstamp(provider) => {
            sats_for(exchange_rate_service, provider, &limit.amount).await
        }
    }
    .map_err(|_| {
        ApiError::GenericInternalApplicationError("Could not convert limit to sats".to_string())
    })
}

async fn get_mobile_pay_spending_record(
    account_id: &AccountId,
    daily_spend_record_service: &DailySpendRecordService,
) -> Result<MobilePaySpendingRecord, ApiError> {
    // If a spend is before the daily roll-over, we'll need to check yesterday's spending record as well
    let yesterday_spending_record = daily_spend_record_service
        .fetch_or_create_daily_spending_record(
            account_id,
            OffsetDateTime::now_utc()
                .checked_sub(Duration::days(1))
                .ok_or(ApiError::GenericInternalApplicationError(
                    "arithmetic error subtracting date".to_string(),
                ))?
                .date(),
        )
        .await?;
    let today_spending_record = daily_spend_record_service
        .fetch_or_create_daily_spending_record(account_id, OffsetDateTime::now_utc().date())
        .await?;

    Ok(MobilePaySpendingRecord {
        yesterday: yesterday_spending_record,
        today: today_spending_record,
    })
}

#[cfg(test)]
mod tests {
    use crate::routes::{select_exchange_rate_provider, Config, RateProvider};

    #[test]
    fn test_select_exchange_rate_provider() {
        // Return LocalRateProvider
        match select_exchange_rate_provider(
            &Config {
                use_local_currency_exchange: true,
            },
            true,
        ) {
            RateProvider::Local(_provider) => {}
            _ => assert!(false, "Unexpected exchange rate provider returned"),
        }
        // Return BitstampRateProvider
        match select_exchange_rate_provider(
            &Config {
                use_local_currency_exchange: false,
            },
            false,
        ) {
            RateProvider::Bitstamp(_provider) => {}
            _ => assert!(false, "Unexpected exchange rate provider returned"),
        }
        // Return CashAppRateProvider
        match select_exchange_rate_provider(
            &Config {
                use_local_currency_exchange: false,
            },
            true,
        ) {
            RateProvider::CashApp(_provider) => {}
            _ => assert!(false, "Unexpected exchange rate provider returned"),
        }
    }
}
