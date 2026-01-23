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
};
use async_trait::async_trait;
use bdk_utils::bdk::bitcoin::{psbt::Psbt, Network};
use bdk_utils::{ChaincodeDelegationCollaboratorWallet, ElectrumRpcUris};
use exchange_rate::service::Service as ExchangeRateService;
use feature_flags::flag::ContextKey;
use feature_flags::service::Service as FeatureFlagsService;
use instrumentation::metrics::KeyValue;
use instrumentation::middleware::CLIENT_REQUEST_CONTEXT;
use screener::service::Service as ScreenerService;
use std::sync::Arc;
use tracing::warn;
use types::account::entities::{Account, FullAccount, TransactionVerificationPolicy};
use types::account::identifiers::KeysetId;
use types::account::spending::SpendingKeyset;
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
        let txid = signed_psbt.unsigned_tx.txid();

        let cache_hit = self.signed_psbt_cache_service.get(txid).await?.is_some();

        if !cache_hit {
            // Persist state before broadcasting; if broadcast fails, we'll roll back.
            self.signed_psbt_cache_service
                .put(signed_psbt.clone())
                .await?;

            self.daily_spend_record_service
                .save_daily_spending_record(self.today_spending_record.clone())
                .await?;
        }

        if let Err(error) = broadcaster.broadcast_transaction(&self.rpc_uris, self.network) {
            if !cache_hit {
                // Best-effort rollback so failed broadcasts don't count against limits.
                if let Err(rollback_err) = self.signed_psbt_cache_service.delete(txid).await {
                    warn!(
                        ?txid,
                        ?rollback_err,
                        "failed to roll back PSBT cache after broadcast error"
                    );
                }
                if let Err(rollback_err) = self
                    .daily_spend_record_service
                    .remove_spending_entry(
                        &self.today_spending_record.account_id,
                        self.today_spending_record.date,
                        &txid,
                    )
                    .await
                {
                    warn!(
                        ?txid,
                        ?rollback_err,
                        "failed to roll back daily spend record after broadcast error"
                    );
                }
            }
            return Err(error);
        }

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
        let source_keyset = full_account
            .spending_keysets
            .get(signing_keyset_id)
            .ok_or_else(|| SigningError::NoSpendKeyset(signing_keyset_id.to_owned()))?;

        let is_mobile_pay = full_account.active_keyset_id == *signing_keyset_id;

        let signing_method = match source_keyset {
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
        };

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

        let features = Features {
            settings: Settings { limit },
            daily_limit_sats,
        };

        let transaction_verification_features = Self::create_transaction_verification_features(
            &full_account.transaction_verification_policy,
            grant,
            config,
            exchange_rate_service,
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

    async fn create_transaction_verification_features(
        policy: &Option<TransactionVerificationPolicy>,
        grant: Option<TransactionVerificationGrantView>,
        config: &Config,
        exchange_rate_service: &ExchangeRateService,
    ) -> Result<Option<TransactionVerificationFeatures>, SigningError> {
        match policy {
            Some(TransactionVerificationPolicy::Threshold(amount)) => {
                Ok(Some(TransactionVerificationFeatures {
                    policy: TransactionVerificationPolicy::Threshold(*amount),
                    verification_sats: sats_for_threshold(amount, config, exchange_rate_service)
                        .await?,
                    grant,
                    wik_pub_key: config.wik_pub_key,
                }))
            }
            Some(TransactionVerificationPolicy::Always) => {
                Ok(Some(TransactionVerificationFeatures {
                    policy: TransactionVerificationPolicy::Always,
                    verification_sats: 0,
                    grant,
                    wik_pub_key: config.wik_pub_key,
                }))
            }
            Some(TransactionVerificationPolicy::Never) | None => Ok(None),
        }
    }
}
