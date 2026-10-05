use crate::daily_spend_record::entities::DailySpendingRecord;
use crate::daily_spend_record::service::Service as DailySpendRecordService;
use crate::entities::{Features, Settings, TransactionVerificationFeatures};
use crate::error::SigningError;
use crate::metrics::{
    APP_ID_KEY, COSIGN_SUCCESS, KEYSET_TYPE_KEY, LEGACY_VALUE, MOBILE_PAY_VALUE, PRIVATE_VALUE,
    SIGNING_STRATEGY_KEY, SWEEP_VALUE,
};
use crate::routes::Config;
use crate::signed_psbt_cache::service::Service as SignedPsbtCacheService;
use crate::signing_processor::state::{Initialized, Validated};
use crate::signing_processor::{
    Broadcaster, Signer, SigningMethod, SigningProcessor, SigningValidator,
};
use crate::spend_rules::SpendRuleSet;
use crate::{
    get_mobile_pay_spending_record, sats_for_limit, sats_for_threshold, MobilePaySpendingRecord,
    DEFAULT_CHANGE_CAP_USD, MOBILE_PAY_CHANGE_CAP_ENABLED, MOBILE_PAY_CHANGE_CAP_USD,
};
use async_trait::async_trait;
use bdk_utils::bdk::bitcoin::{psbt::Psbt, secp256k1::PublicKey, Network};
use bdk_utils::{ChaincodeDelegationCollaboratorWallet, ElectrumRpcUris};
use exchange_rate::service::Service as ExchangeRateService;
use feature_flags::flag::{evaluate_flag_value, ContextKey};
use feature_flags::service::Service as FeatureFlagsService;
use instrumentation::metrics::KeyValue;
use instrumentation::middleware::CLIENT_REQUEST_CONTEXT;
use screener::service::Service as ScreenerService;
use std::sync::Arc;
use tracing::error;
use transaction_verification::gating::{account_context_key, transaction_verification_enabled};
use types::account::entities::{Account, FullAccount, TransactionVerificationPolicy};
use types::account::identifiers::{AccountId, KeysetId};
use types::account::money::Money;
use types::account::spending::SpendingKeyset;
use types::currencies::CurrencyCode;
use types::transaction_verification::router::TransactionVerificationGrantView;

#[async_trait]
pub trait SigningStrategy: Sync + Send {
    async fn execute(self: Box<Self>) -> Result<Psbt, SigningError>;
}

pub struct MobilePaySigningStrategy {
    rpc_uris: ElectrumRpcUris,
    network: Network,
    signing_method: SigningMethod,
    keyset_id: KeysetId,
    signer: SigningProcessor<Validated>,
    today_spending_record: DailySpendingRecord,
    spend_already_recorded: bool,
    signed_psbt_cache_service: SignedPsbtCacheService,
    daily_spend_record_service: DailySpendRecordService,
}

impl MobilePaySigningStrategy {
    pub fn new(
        account: &Account,
        signing_validator: SigningProcessor<Initialized>,
        unsigned_psbt: Psbt,
        signing_method: SigningMethod,
        keyset_id: KeysetId,
        rpc_uris: &ElectrumRpcUris,
        network: Network,
        features: &Features,
        mobile_pay_spending_record: MobilePaySpendingRecord,
        screener_service: Arc<ScreenerService>,
        signed_psbt_cache_service: SignedPsbtCacheService,
        daily_spend_record_service: DailySpendRecordService,
        feature_flags_service: FeatureFlagsService,
        transaction_verification_features: Option<TransactionVerificationFeatures>,
        context_key: Option<ContextKey>,
    ) -> Result<Self, SigningError> {
        let mut today_spending_record = mobile_pay_spending_record.today.clone();
        // bundle up yesterday and today's spending records for spend rule checking
        let spending_entries = mobile_pay_spending_record.spending_entries();
        let txid = unsigned_psbt.unsigned_tx.compute_txid();
        let spend_already_recorded = spending_entries.iter().any(|entry| entry.txid == txid);

        let signer = match &signing_method {
            SigningMethod::LegacyMobilePay { source_descriptor } => {
                let unsynced_source_wallet = source_descriptor.generate_wallet(false, rpc_uris)?;

                let processor = signing_validator.validate(
                    &unsigned_psbt,
                    SpendRuleSet::mobile_pay(
                        account,
                        &unsynced_source_wallet,
                        features,
                        &spending_entries,
                        screener_service,
                        transaction_verification_features,
                        feature_flags_service,
                        context_key,
                    ),
                )?;
                // Update the daily spending record object with the PSBT. Will be persisted after signing.
                today_spending_record.update_with_psbt(&unsynced_source_wallet, &unsigned_psbt);

                processor
            }
            SigningMethod::PrivateMobilePay { source_keyset } => {
                let collaborator_wallet: ChaincodeDelegationCollaboratorWallet =
                    source_keyset.clone().into();

                let processor = signing_validator.validate(
                    &unsigned_psbt,
                    SpendRuleSet::mobile_pay_v2(
                        account,
                        source_keyset,
                        features,
                        &spending_entries,
                        screener_service,
                        transaction_verification_features,
                        feature_flags_service,
                        context_key,
                    ),
                )?;

                today_spending_record.update_with_psbt(&collaborator_wallet, &unsigned_psbt);

                processor
            }
            _ => return Err(SigningError::InvalidSigningMethodForStrategy),
        };

        Ok(Self {
            rpc_uris: rpc_uris.clone(),
            network,
            signing_method,
            keyset_id,
            signer,
            today_spending_record,
            spend_already_recorded,
            signed_psbt_cache_service,
            daily_spend_record_service,
        })
    }

    fn emit_success_metric(&self) {
        let keyset_type = match self.signing_method {
            SigningMethod::LegacyMobilePay { .. }
            | SigningMethod::LegacySweep { .. }
            | SigningMethod::MigrationSweep { .. } => LEGACY_VALUE,
            SigningMethod::PrivateMobilePay { .. }
            | SigningMethod::PrivateSweep { .. }
            | SigningMethod::InheritanceDowngradeSweep { .. } => PRIVATE_VALUE,
        };

        let mut attributes = vec![
            KeyValue::new(SIGNING_STRATEGY_KEY, MOBILE_PAY_VALUE),
            KeyValue::new(KEYSET_TYPE_KEY, keyset_type),
        ];

        if let Ok(Some(app_id)) = CLIENT_REQUEST_CONTEXT.try_with(|c| c.app_id.clone()) {
            attributes.push(KeyValue::new(APP_ID_KEY, app_id));
        }

        COSIGN_SUCCESS.add(1, &attributes);
    }
}

#[async_trait]
impl SigningStrategy for MobilePaySigningStrategy {
    async fn execute(self: Box<MobilePaySigningStrategy>) -> Result<Psbt, SigningError> {
        let mut broadcaster = self
            .signer
            .sign_transaction(&self.rpc_uris, &self.signing_method, &self.keyset_id)
            .await?;

        let signed_psbt = broadcaster.finalized_psbt();
        let txid = signed_psbt.unsigned_tx.compute_txid();

        let cache_hit = self.signed_psbt_cache_service.get(txid).await?.is_some();
        if !self.spend_already_recorded {
            // The spending record is authoritative. A cache entry can exist without a matching
            // record if an earlier request lost an optimistic-lock race, so charge the spend
            // independently of the cache state and before writing a new cache entry.
            self.daily_spend_record_service
                .save_daily_spending_record(self.today_spending_record.clone())
                .await?;
        }

        if !cache_hit {
            self.signed_psbt_cache_service
                .put(signed_psbt.clone())
                .await?;
        }

        // Keep the charge and cache entry if broadcast fails. The broadcast result is ambiguous,
        // and another request may have successfully broadcast the same signed transaction. A
        // later retry deduplicates the charge by txid.
        broadcaster.broadcast_transaction(&self.rpc_uris, self.network)?;

        self.emit_success_metric();

        Ok(signed_psbt)
    }
}

pub struct RecoverySweepSigningStrategy<T> {
    rpc_uris: ElectrumRpcUris,
    signer: T,
    signing_method: SigningMethod,
    network: Network,
    keyset_id: KeysetId,
}

impl<T> RecoverySweepSigningStrategy<T>
where
    T: Signer,
{
    pub fn new<U>(
        account: &Account,
        signing_validator: U,
        unsigned_psbt: &Psbt,
        signing_method: SigningMethod,
        network: Network,
        keyset_id: KeysetId,
        rpc_uris: &ElectrumRpcUris,
        screener_service: Arc<ScreenerService>,
        feature_flags_service: FeatureFlagsService,
        context_key: Option<ContextKey>,
    ) -> Result<Self, SigningError>
    where
        U: SigningValidator<SigningProcessor = T>,
    {
        let signer = match &signing_method {
            SigningMethod::LegacySweep {
                source_descriptor,
                active_descriptor,
            } => {
                let unsynced_source_wallet = source_descriptor.generate_wallet(false, rpc_uris)?;
                // W-9888: A full sync is required here, because we don't have derivation path information in
                // the PSBT for sweep outputs so we need to generate addresses and check one-by-one.
                let active_wallet = active_descriptor.generate_wallet(true, rpc_uris)?;
                signing_validator.validate(
                    unsigned_psbt,
                    SpendRuleSet::legacy_sweep(
                        account,
                        &unsynced_source_wallet,
                        &active_wallet,
                        screener_service,
                        feature_flags_service,
                        context_key,
                    ),
                )?
            }
            SigningMethod::PrivateSweep {
                source_keyset,
                active_keyset,
            } => signing_validator.validate(
                unsigned_psbt,
                SpendRuleSet::private_sweep(
                    account,
                    source_keyset,
                    active_keyset,
                    screener_service,
                    feature_flags_service,
                    context_key,
                ),
            )?,
            SigningMethod::MigrationSweep {
                source_descriptor,
                active_keyset,
            } => {
                let unsynced_source_wallet = source_descriptor.generate_wallet(false, rpc_uris)?;
                signing_validator.validate(
                    unsigned_psbt,
                    SpendRuleSet::migration_sweep(
                        account,
                        &unsynced_source_wallet,
                        active_keyset,
                        screener_service,
                        feature_flags_service,
                        context_key,
                    ),
                )?
            }
            SigningMethod::InheritanceDowngradeSweep {
                source_keyset,
                active_descriptor,
            } => {
                // W-9888: A full sync is required here, because we don't have derivation path information in
                // the PSBT for sweep outputs so we need to generate addresses and check one-by-one.
                let active_wallet = active_descriptor.generate_wallet(true, rpc_uris)?;
                signing_validator.validate(
                    unsigned_psbt,
                    SpendRuleSet::inheritance_downgrade_sweep(
                        account,
                        source_keyset,
                        &active_wallet,
                        screener_service,
                        feature_flags_service,
                        context_key,
                    ),
                )?
            }
            _ => return Err(SigningError::InvalidSigningMethodForStrategy),
        };

        Ok(Self {
            rpc_uris: rpc_uris.clone(),
            signer,
            signing_method,
            network,
            keyset_id,
        })
    }

    fn emit_success_metric(&self) {
        let keyset_type = match self.signing_method {
            SigningMethod::LegacyMobilePay { .. }
            | SigningMethod::LegacySweep { .. }
            | SigningMethod::MigrationSweep { .. } => LEGACY_VALUE,
            SigningMethod::PrivateMobilePay { .. }
            | SigningMethod::PrivateSweep { .. }
            | SigningMethod::InheritanceDowngradeSweep { .. } => PRIVATE_VALUE,
        };

        let mut attributes = vec![
            KeyValue::new(SIGNING_STRATEGY_KEY, SWEEP_VALUE),
            KeyValue::new(KEYSET_TYPE_KEY, keyset_type),
        ];

        if let Ok(Some(app_id)) = CLIENT_REQUEST_CONTEXT.try_with(|c| c.app_id.clone()) {
            attributes.push(KeyValue::new(APP_ID_KEY, app_id));
        }

        COSIGN_SUCCESS.add(1, &attributes);
    }
}

#[async_trait]
impl<T> SigningStrategy for RecoverySweepSigningStrategy<T>
where
    T: Signer + Send + Sync,
{
    async fn execute(self: Box<Self>) -> Result<Psbt, SigningError> {
        let mut broadcaster = self
            .signer
            .sign_transaction(&self.rpc_uris, &self.signing_method, &self.keyset_id)
            .await?;

        self.emit_success_metric();

        broadcaster.broadcast_transaction(&self.rpc_uris, self.network)?;

        Ok(broadcaster.finalized_psbt())
    }
}

/// Resolve which [`SigningMethod`] applies for signing `signing_keyset_id`
/// on `full_account`. A request to sign the active keyset is mobile-pay;
/// any other keyset is a sweep into the active (destination) keyset.
/// Pure over already-fetched account state (no PSBT needed), so callers
/// can use it to classify the request before signing.
pub fn determine_signing_method(
    full_account: &FullAccount,
    signing_keyset_id: &KeysetId,
) -> Result<SigningMethod, SigningError> {
    let source_keyset = full_account
        .spending_keysets
        .get(signing_keyset_id)
        .ok_or_else(|| SigningError::NoSpendKeyset(signing_keyset_id.to_owned()))?;

    let is_mobile_pay = full_account.active_keyset_id == *signing_keyset_id;

    Ok(match source_keyset {
        SpendingKeyset::LegacyMultiSig(legacy_source) => {
            if is_mobile_pay {
                SigningMethod::LegacyMobilePay {
                    source_descriptor: legacy_source.clone().into(),
                }
            } else {
                let active_keyset = full_account
                    .active_spending_keyset()
                    .ok_or(SigningError::NoActiveSpendKeyset)?;

                match active_keyset {
                    SpendingKeyset::LegacyMultiSig(legacy_dest) => SigningMethod::LegacySweep {
                        source_descriptor: legacy_source.clone().into(),
                        active_descriptor: legacy_dest.clone().into(),
                    },
                    SpendingKeyset::PrivateMultiSig(private_dest) => {
                        SigningMethod::MigrationSweep {
                            source_descriptor: legacy_source.clone().into(),
                            active_keyset: private_dest.clone(),
                        }
                    }
                }
            }
        }
        SpendingKeyset::PrivateMultiSig(source_keyset) => {
            if is_mobile_pay {
                SigningMethod::PrivateMobilePay {
                    source_keyset: source_keyset.clone(),
                }
            } else {
                let active_keyset = full_account
                    .active_spending_keyset()
                    .ok_or(SigningError::NoActiveSpendKeyset)?
                    .private_multi_sig_or(SigningError::ConflictingKeysetType)?;

                SigningMethod::PrivateSweep {
                    source_keyset: source_keyset.clone(),
                    active_keyset: active_keyset.clone(),
                }
            }
        }
    })
}

pub struct SigningStrategyFactory {
    signing_processor: SigningProcessor<Initialized>,
    screener_service: Arc<ScreenerService>,
    exchange_rate_service: ExchangeRateService,
    daily_spend_record_service: DailySpendRecordService,
    signed_psbt_cache_service: SignedPsbtCacheService,
    feature_flags_service: FeatureFlagsService,
}

impl SigningStrategyFactory {
    pub fn new(
        signing_processor: SigningProcessor<Initialized>,
        screener_service: Arc<ScreenerService>,
        exchange_rate_service: ExchangeRateService,
        daily_spend_record_service: DailySpendRecordService,
        signed_psbt_cache_service: SignedPsbtCacheService,
        feature_flags_service: FeatureFlagsService,
    ) -> Self {
        Self {
            signing_processor,
            screener_service,
            exchange_rate_service,
            daily_spend_record_service,
            signed_psbt_cache_service,
            feature_flags_service,
        }
    }

    pub async fn construct_strategy(
        &self,
        full_account: &FullAccount,
        config: Config,
        signing_keyset_id: &KeysetId,
        unsigned_psbt: Psbt,
        grant: Option<TransactionVerificationGrantView>,
        rpc_uris: &ElectrumRpcUris,
        context_key: Option<ContextKey>,
    ) -> Result<Box<dyn SigningStrategy>, SigningError> {
        let is_mobile_pay = full_account.active_keyset_id == *signing_keyset_id;

        let signing_method = determine_signing_method(full_account, signing_keyset_id)?;

        let signing_strategy: Box<dyn SigningStrategy> = if is_mobile_pay {
            Self::create_mobile_pay_signing_strategy(
                full_account,
                &config,
                self.signing_processor.clone(),
                unsigned_psbt,
                signing_method,
                signing_keyset_id,
                grant,
                rpc_uris,
                &self.screener_service,
                &self.exchange_rate_service,
                &self.daily_spend_record_service,
                &self.signed_psbt_cache_service,
                &self.feature_flags_service,
                context_key,
            )
            .await?
        } else {
            Self::create_recovery_sweep_signing_strategy(
                full_account,
                self.signing_processor.clone(),
                &unsigned_psbt,
                signing_method,
                rpc_uris,
                signing_keyset_id,
                &self.screener_service,
                &self.feature_flags_service,
                context_key,
            )?
        };

        Ok(signing_strategy)
    }

    async fn create_mobile_pay_signing_strategy(
        full_account: &FullAccount,
        config: &Config,
        signing_processor: SigningProcessor<Initialized>,
        unsigned_psbt: Psbt,
        signing_method: SigningMethod,
        keyset_id: &KeysetId,
        grant: Option<TransactionVerificationGrantView>,
        rpc_uris: &ElectrumRpcUris,
        screener_service: &Arc<ScreenerService>,
        exchange_rate_service: &ExchangeRateService,
        daily_spend_record_service: &DailySpendRecordService,
        signed_psbt_cache_service: &SignedPsbtCacheService,
        feature_flags_service: &FeatureFlagsService,
        context_key: Option<ContextKey>,
    ) -> Result<Box<MobilePaySigningStrategy>, SigningError> {
        let limit = full_account
            .spending_limit
            .clone()
            .ok_or(SigningError::MissingMobilePaySettings)?;

        let network = full_account
            .active_spending_keyset()
            .ok_or(SigningError::NoActiveSpendKeyset)?
            .network();

        let daily_limit_sats = sats_for_limit(&limit, config, exchange_rate_service).await?;

        // The change cap applies to private keysets only.
        let change_cap_sats = match &signing_method {
            SigningMethod::PrivateMobilePay { .. } => {
                Self::create_change_cap_sats(
                    &full_account.id,
                    config,
                    exchange_rate_service,
                    feature_flags_service,
                )
                .await?
            }
            _ => None,
        };

        let features = Features {
            settings: Settings { limit },
            daily_limit_sats,
            change_cap_sats,
        };

        let transaction_verification_features = Self::create_transaction_verification_features(
            &full_account.id,
            &full_account.transaction_verification_policy,
            grant,
            full_account.hardware_auth_pubkey,
            config,
            exchange_rate_service,
            feature_flags_service,
        )
        .await?;
        let mobile_pay_spending_record =
            get_mobile_pay_spending_record(&full_account.id, daily_spend_record_service).await?;

        Ok(Box::new(MobilePaySigningStrategy::new(
            &full_account.clone().into(),
            signing_processor,
            unsigned_psbt,
            signing_method,
            keyset_id.to_owned(),
            rpc_uris,
            network.into(),
            &features,
            mobile_pay_spending_record,
            screener_service.clone(),
            signed_psbt_cache_service.clone(),
            daily_spend_record_service.clone(),
            feature_flags_service.clone(),
            transaction_verification_features,
            context_key,
        )?))
    }

    fn create_recovery_sweep_signing_strategy<T, U>(
        full_account: &FullAccount,
        signing_processor: U,
        unsigned_psbt: &Psbt,
        signing_method: SigningMethod,
        rpc_uris: &ElectrumRpcUris,
        keyset_id: &KeysetId,
        screener_service: &Arc<ScreenerService>,
        feature_flags_service: &FeatureFlagsService,
        context_key: Option<ContextKey>,
    ) -> Result<Box<RecoverySweepSigningStrategy<T>>, SigningError>
    where
        T: Signer,
        U: SigningValidator<SigningProcessor = T>,
    {
        let active_spending_keyset = full_account
            .active_spending_keyset()
            .ok_or(SigningError::NoActiveSpendKeyset)?;

        let network = active_spending_keyset.network();

        Ok(Box::new(RecoverySweepSigningStrategy::new(
            &full_account.clone().into(),
            signing_processor,
            unsigned_psbt,
            signing_method,
            network.into(),
            keyset_id.to_owned(),
            rpc_uris,
            screener_service.clone(),
            feature_flags_service.clone(),
            context_key,
        )?))
    }

    /// Resolve the daily change cap for a private (chaincode-delegation)
    /// keyset. `None` disables the rule.
    async fn create_change_cap_sats(
        account_id: &AccountId,
        config: &Config,
        exchange_rate_service: &ExchangeRateService,
        feature_flags_service: &FeatureFlagsService,
    ) -> Result<Option<u64>, SigningError> {
        let Some(cap_usd) = Self::resolve_change_cap_usd(feature_flags_service, account_id) else {
            return Ok(None);
        };

        // `as` saturates, so an absurd flag value clamps rather than wraps.
        let cap = Money {
            amount: (cap_usd * 100.0).round() as u64, // cents
            currency_code: CurrencyCode::USD,
        };
        Ok(Some(
            sats_for_threshold(&cap, config, exchange_rate_service).await?,
        ))
    }

    /// Daily change cap in whole USD, or `None` when
    /// [`MOBILE_PAY_CHANGE_CAP_ENABLED`] is explicitly `false`. Any evaluation
    /// failure on either flag falls back to enforcing [`DEFAULT_CHANGE_CAP_USD`].
    fn resolve_change_cap_usd(
        feature_flags_service: &FeatureFlagsService,
        account_id: &AccountId,
    ) -> Option<f64> {
        let context_key = account_context_key(account_id);

        let enabled = evaluate_flag_value::<bool>(
            feature_flags_service,
            MOBILE_PAY_CHANGE_CAP_ENABLED.key,
            &context_key,
        )
        .unwrap_or_else(|e| {
            error!(
                "Failed to evaluate {}: {e}; enforcing default change cap",
                MOBILE_PAY_CHANGE_CAP_ENABLED.key
            );
            true
        });
        if !enabled {
            return None;
        }

        let cap_usd = match evaluate_flag_value::<f64>(
            feature_flags_service,
            MOBILE_PAY_CHANGE_CAP_USD.key,
            &context_key,
        ) {
            Ok(usd) if usd.is_finite() && usd > 0.0 => usd,
            Ok(_) => {
                error!(
                    "{} is not a positive finite number; using default",
                    MOBILE_PAY_CHANGE_CAP_USD.key
                );
                DEFAULT_CHANGE_CAP_USD
            }
            Err(e) => {
                error!(
                    "Failed to evaluate {}: {e}; using default",
                    MOBILE_PAY_CHANGE_CAP_USD.key
                );
                DEFAULT_CHANGE_CAP_USD
            }
        };
        Some(cap_usd)
    }

    /// `None` means "no verification is required of this spend", which is what
    /// the rule below treats as a pass.
    ///
    /// The kill switch is checked first: with the feature off, the routes that
    /// would let a customer satisfy a policy are closed, so continuing to
    /// enforce a stored policy would strand the account behind an approval
    /// path it cannot complete. Resolved against the same bare account-keyed
    /// context the routes use, so both halves agree per account.
    async fn create_transaction_verification_features(
        account_id: &AccountId,
        policy: &Option<TransactionVerificationPolicy>,
        grant: Option<TransactionVerificationGrantView>,
        expected_hw_auth_public_key: PublicKey,
        config: &Config,
        exchange_rate_service: &ExchangeRateService,
        feature_flags_service: &FeatureFlagsService,
    ) -> Result<Option<TransactionVerificationFeatures>, SigningError> {
        if !transaction_verification_enabled(
            feature_flags_service,
            &account_context_key(account_id),
        ) {
            return Ok(None);
        }

        match policy {
            Some(TransactionVerificationPolicy::Threshold(amount)) => {
                Ok(Some(TransactionVerificationFeatures {
                    policy: TransactionVerificationPolicy::Threshold(*amount),
                    verification_sats: sats_for_threshold(amount, config, exchange_rate_service)
                        .await?,
                    grant,
                    wik_pub_key: config.wik_pub_key,
                    expected_hw_auth_public_key,
                }))
            }
            Some(TransactionVerificationPolicy::Always) => {
                Ok(Some(TransactionVerificationFeatures {
                    policy: TransactionVerificationPolicy::Always,
                    verification_sats: 0,
                    grant,
                    wik_pub_key: config.wik_pub_key,
                    expected_hw_auth_public_key,
                }))
            }
            Some(TransactionVerificationPolicy::Never) | None => Ok(None),
        }
    }
}

#[cfg(test)]
mod tests {
    use std::collections::HashMap;

    use feature_flags::config::Config as FeatureFlagsConfig;
    use rstest::rstest;
    use types::account::identifiers::AccountId;

    use super::SigningStrategyFactory;
    use crate::{DEFAULT_CHANGE_CAP_USD, MOBILE_PAY_CHANGE_CAP_ENABLED, MOBILE_PAY_CHANGE_CAP_USD};

    async fn resolve_with_flags(flags: &[(&str, &str)]) -> Option<f64> {
        let overrides = flags
            .iter()
            .map(|(k, v)| (k.to_string(), v.to_string()))
            .collect::<HashMap<_, _>>();
        let feature_flags_service = FeatureFlagsConfig::new_with_overrides(overrides)
            .to_service()
            .await
            .unwrap();
        SigningStrategyFactory::resolve_change_cap_usd(
            &feature_flags_service,
            &AccountId::gen().unwrap(),
        )
    }

    #[tokio::test]
    async fn explicit_false_disables_the_cap() {
        assert_eq!(
            resolve_with_flags(&[(MOBILE_PAY_CHANGE_CAP_ENABLED.key, "false")]).await,
            None
        );
    }

    #[tokio::test]
    async fn missing_flags_enforce_the_default_cap() {
        assert_eq!(resolve_with_flags(&[]).await, Some(DEFAULT_CHANGE_CAP_USD));
    }

    #[tokio::test]
    async fn configured_usd_value_is_used() {
        assert_eq!(
            resolve_with_flags(&[
                (MOBILE_PAY_CHANGE_CAP_ENABLED.key, "true"),
                (MOBILE_PAY_CHANGE_CAP_USD.key, "250000"),
            ])
            .await,
            Some(250_000.0)
        );
    }

    #[rstest]
    #[case::missing(None)]
    #[case::zero(Some("0"))]
    #[case::negative(Some("-1"))]
    #[case::nan(Some("NaN"))]
    #[case::infinite(Some("inf"))]
    #[case::non_numeric(Some("abc"))]
    #[tokio::test]
    async fn unusable_usd_values_enforce_the_default_cap(#[case] usd: Option<&str>) {
        let mut flags = vec![(MOBILE_PAY_CHANGE_CAP_ENABLED.key, "true")];
        if let Some(usd) = usd {
            flags.push((MOBILE_PAY_CHANGE_CAP_USD.key, usd));
        }
        assert_eq!(
            resolve_with_flags(&flags).await,
            Some(DEFAULT_CHANGE_CAP_USD)
        );
    }

    #[tokio::test]
    async fn unparseable_enabled_flag_enforces_the_default_cap() {
        assert_eq!(
            resolve_with_flags(&[(MOBILE_PAY_CHANGE_CAP_ENABLED.key, "abc")]).await,
            Some(DEFAULT_CHANGE_CAP_USD)
        );
    }
}
