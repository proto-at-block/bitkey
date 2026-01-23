use super::Service;
use crate::error::TransactionVerificationError;
use account::service::FetchAccountInput;
use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction as Psbt;
use bdk_utils::bdk::bitcoin::Network;
use exchange_rate::currency_conversion::sats_for;
use exchange_rate::error::ExchangeRateError;
use exchange_rate::service::Service as ExchangeRateService;
use feature_flags::flag::ContextKey;
use notification::payloads::transaction_verification::TransactionVerificationPayload;
use notification::service::SendNotificationInput;
use notification::{NotificationPayloadBuilder, NotificationPayloadType};
use tracing::instrument;
use types::account::entities::{
    Account, FullAccount,
    TransactionVerificationPolicy::{Always, Never, Threshold},
};
use types::account::money::Money;
use types::currencies::CurrencyCode;
use types::exchange_rate::coingecko::RateProvider as CoingeckoRateProvider;
use types::exchange_rate::local_rate_provider::LocalRateProvider;
use types::transaction_verification::entities::{
    BitcoinDisplayUnit, TransactionVerificationPending,
};
use types::transaction_verification::router::TransactionVerificationGrantView;
use types::transaction_verification::service::{
    TransactionVerificationWallet, VerificationWalletProvider,
};
use types::{
    account::identifiers::AccountId,
    transaction_verification::{
        entities::TransactionVerification, service::InitiateVerificationResult,
    },
};

impl Service {
    #[instrument(skip(self, wallet_provider, psbt))]
    pub async fn initiate(
        &self,
        account: &FullAccount,
        wallet_provider: VerificationWalletProvider,
        psbt: Psbt,
        network: Network,
        fiat_currency: CurrencyCode,
        bitcoin_display_unit: BitcoinDisplayUnit,
        should_prompt_user: bool,
        context_key: Option<ContextKey>,
        use_bip_177: bool,
    ) -> Result<InitiateVerificationResult, TransactionVerificationError> {
        let hardware_auth_pubkey = account.hardware_auth_pubkey;
        self.screener_service.screen_psbt_outputs_for_sanctions(
            &psbt,
            network,
            &Account::Full(account.clone()),
            &self.feature_flags_service,
            context_key,
        )?;
        let account_id = account.id.clone();

        let needs_verify = self
            .is_verification_required(account, wallet_provider, &psbt)
            .await?;

        let result = if !needs_verify {
            let grant = self
                .grant_service
                .approve_psbt(&psbt.to_string(), hardware_auth_pubkey)
                .await
                .map_err(TransactionVerificationError::from)?;
            InitiateVerificationResult::SignedWithoutVerification {
                psbt,
                hw_grant: TransactionVerificationGrantView {
                    version: grant.version,
                    hw_auth_public_key: grant.hw_auth_public_key,
                    commitment: grant.commitment,
                    signature: grant.signature,
                    reverse_hash_chain: grant.reverse_hash_chain,
                },
            }
        } else if !should_prompt_user {
            InitiateVerificationResult::VerificationRequired
        } else {
            if let Some(tx_verification) = self
                .repo
                .fetch_pending_by_txid(&psbt.unsigned_tx.txid())
                .await?
            {
                return Ok(InitiateVerificationResult::VerificationRequested {
                    verification_id: tx_verification.common_fields().id.clone(),
                    expiration: tx_verification.common_fields().expires_at,
                });
            }
            let tx_verification = TransactionVerification::new_pending(
                &account_id,
                psbt,
                fiat_currency,
                bitcoin_display_unit,
                use_bip_177,
            );
            self.repo.persist(&tx_verification).await?;
            let pending = match tx_verification.clone() {
                TransactionVerification::Pending(p) => &p.clone(),
                _ => unreachable!("Expected TransactionVerification::Pending"),
            };
            self.send_verification_notification(&account_id, pending)
                .await?;
            InitiateVerificationResult::VerificationRequested {
                verification_id: tx_verification.common_fields().id.clone(),
                expiration: tx_verification.common_fields().expires_at,
            }
        };

        Ok(result)
    }

    pub async fn mobile_pay_initiate(
        &self,
        account_id: &AccountId,
        psbt: Psbt,
        network: Network,
        fiat_currency: CurrencyCode,
        bitcoin_display_unit: BitcoinDisplayUnit,
        should_prompt_user: bool,
        context_key: Option<ContextKey>,
        use_bip_177: bool,
    ) -> Result<InitiateVerificationResult, TransactionVerificationError> {
        let account = self
            .account_service
            .fetch_full_account(FetchAccountInput { account_id })
            .await?;

        self.screener_service.screen_psbt_outputs_for_sanctions(
            &psbt,
            network,
            &Account::Full(account),
            &self.feature_flags_service,
            context_key,
        )?;

        let result = if !should_prompt_user {
            InitiateVerificationResult::VerificationRequired
        } else {
            let tx_verification = TransactionVerification::new_pending(
                account_id,
                psbt,
                fiat_currency,
                bitcoin_display_unit,
                use_bip_177,
            );
            self.repo.persist(&tx_verification).await?;
            let pending = match &tx_verification {
                TransactionVerification::Pending(p) => p,
                _ => unreachable!("Expected TransactionVerification::Pending"),
            };
            self.send_verification_notification(account_id, pending)
                .await?;
            InitiateVerificationResult::VerificationRequested {
                verification_id: tx_verification.common_fields().id.clone(),
                expiration: tx_verification.common_fields().expires_at,
            }
        };

        Ok(result)
    }

    async fn is_verification_required(
        &self,
        account: &FullAccount,
        wallet_provider: VerificationWalletProvider,
        psbt: &Psbt,
    ) -> Result<bool, TransactionVerificationError> {
        let policy_opt = account.transaction_verification_policy.clone();

        let result = match policy_opt {
            None => false,
            Some(policy) => match policy {
                Never => false,
                Always => true,
                Threshold(threshold) => {
                    self.is_above_threshold(wallet_provider, psbt, threshold)
                        .await?
                }
            },
        };
        Ok(result)
    }

    async fn is_above_threshold(
        &self,
        wallet_provider: VerificationWalletProvider,
        psbt: &Psbt,
        threshold: Money,
    ) -> Result<bool, TransactionVerificationError> {
        let wallet: TransactionVerificationWallet = wallet_provider
            .into_wallet()
            .map_err(TransactionVerificationError::BdkUtils)?;
        let total_spend_sats = wallet
            .total_outflow_sats(psbt)
            .map_err(TransactionVerificationError::BdkUtils)?;
        let threshold_sats = sats_for_amount(
            threshold,
            self.config.use_local_currency_exchange,
            &self.exchange_rate_service,
        )
        .await
        .map_err(TransactionVerificationError::ExchangeRateError)?;
        Ok(total_spend_sats >= threshold_sats)
    }

    async fn send_verification_notification(
        &self,
        account_id: &AccountId,
        tx_verification: &TransactionVerificationPending,
    ) -> Result<(), TransactionVerificationError> {
        let payload = NotificationPayloadBuilder::default()
            .transaction_verification_payload(Some(TransactionVerificationPayload {
                base_verification_url: self.config.ext_secure_site_base_url.clone(),
                web_auth_token: tx_verification.web_auth_token.clone(),
            }))
            .build()?;
        self.notification_service
            .send_notification(SendNotificationInput {
                account_id,
                payload_type: NotificationPayloadType::TransactionVerification,
                payload: &payload,
                only_touchpoints: None,
            })
            .await
            .map_err(TransactionVerificationError::from)
    }
}

async fn sats_for_amount(
    amount: Money,
    use_local_currency_exchange: bool,
    exchange_rate_service: &ExchangeRateService,
) -> Result<u64, ExchangeRateError> {
    if use_local_currency_exchange {
        sats_for(exchange_rate_service, LocalRateProvider::new(), &amount).await
    } else {
        sats_for(exchange_rate_service, CoingeckoRateProvider::new(), &amount).await
    }
}
