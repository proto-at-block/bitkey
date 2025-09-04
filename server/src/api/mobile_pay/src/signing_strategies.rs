use crate::daily_spend_record::entities::DailySpendingRecord;
use crate::daily_spend_record::service::Service as DailySpendRecordService;
use crate::entities::{Features, Settings, TransactionVerificationFeatures};
use crate::error::SigningError;
use crate::routes::Config;
use crate::signed_psbt_cache::service::Service as SignedPsbtCacheService;
use crate::signing_processor::state::{Initialized, Validated};
use crate::signing_processor::{Broadcaster, Signer, SigningProcessor, SigningValidator};
use crate::spend_rules::SpendRuleSet;
use crate::{
    get_mobile_pay_spending_record, sats_for_limit, sats_for_threshold, MobilePaySpendingRecord,
};
use async_trait::async_trait;
use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::{DescriptorKeyset, ElectrumRpcUris};
use exchange_rate::service::Service as ExchangeRateService;
use feature_flags::flag::ContextKey;
use feature_flags::service::Service as FeatureFlagsService;
use screener::service::Service as ScreenerService;
use std::sync::Arc;
use types::account::entities::{FullAccount, TransactionVerificationPolicy};
use types::account::identifiers::KeysetId;
use types::transaction_verification::router::TransactionVerificationGrantView;

#[async_trait]
pub trait SigningStrategy: Sync + Send {
    async fn execute(&self) -> Result<Psbt, SigningError>;
}

pub struct TransferWithoutHardwareSigningStrategy {
    rpc_uris: ElectrumRpcUris,
    source_descriptor: DescriptorKeyset,
    keyset_id: KeysetId,
    signer: SigningProcessor<Validated>,
    today_spending_record: DailySpendingRecord,
    signed_psbt_cache_service: SignedPsbtCacheService,
    daily_spend_record_service: DailySpendRecordService,
}

impl TransferWithoutHardwareSigningStrategy {
    pub fn new(
        signing_validator: SigningProcessor<Initialized>,
        unsigned_psbt: Psbt,
        source_descriptor: DescriptorKeyset,
        keyset_id: KeysetId,
        rpc_uris: &ElectrumRpcUris,
        features: &Features,
        mobile_pay_spending_record: MobilePaySpendingRecord,
        screener_service: Arc<ScreenerService>,
        signed_psbt_cache_service: SignedPsbtCacheService,
        daily_spend_record_service: DailySpendRecordService,
        feature_flags_service: FeatureFlagsService,
        transaction_verification_features: Option<TransactionVerificationFeatures>,
        context_key: Option<ContextKey>,
    ) -> Result<Self, SigningError> {
        let unsynced_source_wallet = source_descriptor.generate_wallet(false, rpc_uris)?;

        // bundle up yesterday and today's spending records for spend rule checking
        let spending_entries = mobile_pay_spending_record.spending_entries();

        let signer = signing_validator.validate(
            &unsigned_psbt,
            SpendRuleSet::mobile_pay(
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
        let mut today_spending_record = mobile_pay_spending_record.today.clone();
        today_spending_record.update_with_psbt(&unsynced_source_wallet, &unsigned_psbt);

        Ok(Self {
            rpc_uris: rpc_uris.clone(),
            source_descriptor,
            keyset_id,
            signer,
            today_spending_record,
            signed_psbt_cache_service,
            daily_spend_record_service,
        })
    }
}

#[async_trait]
impl SigningStrategy for TransferWithoutHardwareSigningStrategy {
    async fn execute(&self) -> Result<Psbt, SigningError> {
        let mut broadcaster = self
            .signer
            .sign_transaction(&self.rpc_uris, &self.source_descriptor, &self.keyset_id)
            .await?;

        let signed_psbt = broadcaster.finalized_psbt();

        if self
            .signed_psbt_cache_service
            .get(signed_psbt.unsigned_tx.txid())
            .await?
            .is_none()
        {
            // Save the PSBT to cache so if the client retries the request,
            // we can avoid double-counting Mobile Pay spend limits.
            self.signed_psbt_cache_service
                .put(signed_psbt.clone())
                .await?;

            self.daily_spend_record_service
                .save_daily_spending_record(self.today_spending_record.clone())
                .await?;
        }

        broadcaster.broadcast_transaction(&self.rpc_uris, &self.source_descriptor)?;

        Ok(signed_psbt)
    }
}

pub struct RecoverySweepSigningStrategy {
    rpc_uris: ElectrumRpcUris,
    signer: SigningProcessor<Validated>,
    source_descriptor: DescriptorKeyset,
    keyset_id: KeysetId,
}

impl RecoverySweepSigningStrategy {
    pub fn new(
        signing_validator: SigningProcessor<Initialized>,
        unsigned_psbt: &Psbt,
        source_descriptor: DescriptorKeyset,
        active_descriptor: DescriptorKeyset,
        keyset_id: KeysetId,
        rpc_uris: &ElectrumRpcUris,
        screener_service: Arc<ScreenerService>,
        feature_flags_service: FeatureFlagsService,
        context_key: Option<ContextKey>,
    ) -> Result<Self, SigningError> {
        let unsynced_source_wallet = source_descriptor.generate_wallet(false, rpc_uris)?;

        // W-9888: A full sync is required here, because we don't have derivation path information in
        // the PSBT for sweep outputs so we need to generate addresses and check one-by-one.
        let active_wallet = active_descriptor.generate_wallet(true, rpc_uris)?;

        let signer = signing_validator.validate(
            unsigned_psbt,
            SpendRuleSet::sweep(
                &unsynced_source_wallet,
                &active_wallet,
                screener_service,
                feature_flags_service,
                context_key,
            ),
        )?;
        Ok(Self {
            rpc_uris: rpc_uris.clone(),
            signer,
            source_descriptor,
            keyset_id,
        })
    }
}

#[async_trait]
impl SigningStrategy for RecoverySweepSigningStrategy {
    async fn execute(&self) -> Result<Psbt, SigningError> {
        let mut broadcaster = self
            .signer
            .sign_transaction(&self.rpc_uris, &self.source_descriptor, &self.keyset_id)
            .await?;

        broadcaster.broadcast_transaction(&self.rpc_uris, &self.source_descriptor)?;

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
    ) -> Result<Arc<dyn SigningStrategy>, SigningError> {
        let source_descriptor: DescriptorKeyset = full_account
            .spending_keysets
            .get(signing_keyset_id)
            .ok_or_else(|| SigningError::NoSpendKeyset(signing_keyset_id.to_owned()))?
            .legacy_multi_sig_or(SigningError::InvalidKeysetType(
                signing_keyset_id.to_owned(),
            ))?
            .to_owned()
            .into();

        let is_for_transfer_without_hw = full_account.active_keyset_id == *signing_keyset_id;

        let signing_strategy: Arc<dyn SigningStrategy> = if is_for_transfer_without_hw {
            Self::create_transfer_without_hardware_signing_strategy(
                full_account,
                &config,
                self.signing_processor.clone(),
                unsigned_psbt,
                source_descriptor,
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
                rpc_uris,
                source_descriptor,
                signing_keyset_id,
                &self.screener_service,
                &self.feature_flags_service,
                context_key,
            )?
        };

        Ok(signing_strategy)
    }

    async fn create_transfer_without_hardware_signing_strategy(
        full_account: &FullAccount,
        config: &Config,
        signing_processor: SigningProcessor<Initialized>,
        unsigned_psbt: Psbt,
        source_descriptor: DescriptorKeyset,
        keyset_id: &KeysetId,
        grant: Option<TransactionVerificationGrantView>,
        rpc_uris: &ElectrumRpcUris,
        screener_service: &Arc<ScreenerService>,
        exchange_rate_service: &ExchangeRateService,
        daily_spend_record_service: &DailySpendRecordService,
        signed_psbt_cache_service: &SignedPsbtCacheService,
        feature_flags_service: &FeatureFlagsService,
        context_key: Option<ContextKey>,
    ) -> Result<Arc<TransferWithoutHardwareSigningStrategy>, SigningError> {
        let limit = full_account
            .spending_limit
            .clone()
            .ok_or(SigningError::MissingMobilePaySettings)?;

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

        Ok(Arc::new(TransferWithoutHardwareSigningStrategy::new(
            signing_processor,
            unsigned_psbt,
            source_descriptor,
            keyset_id.to_owned(),
            rpc_uris,
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

    fn create_recovery_sweep_signing_strategy(
        full_account: &FullAccount,
        signing_processor: SigningProcessor<Initialized>,
        unsigned_psbt: &Psbt,
        rpc_uris: &ElectrumRpcUris,
        source_descriptor: DescriptorKeyset,
        keyset_id: &KeysetId,
        screener_service: &Arc<ScreenerService>,
        feature_flags_service: &FeatureFlagsService,
        context_key: Option<ContextKey>,
    ) -> Result<Arc<RecoverySweepSigningStrategy>, SigningError> {
        let active_descriptor: DescriptorKeyset = full_account
            .active_spending_keyset()
            .ok_or(SigningError::NoActiveSpendKeyset)?
            .legacy_multi_sig_or(SigningError::ConflictingKeysetType)?
            .to_owned()
            .into();

        Ok(Arc::new(RecoverySweepSigningStrategy::new(
            signing_processor,
            unsigned_psbt,
            source_descriptor,
            active_descriptor,
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
                    policy: TransactionVerificationPolicy::Threshold(amount.clone()),
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
