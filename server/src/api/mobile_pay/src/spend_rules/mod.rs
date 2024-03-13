use std::sync::Arc;
use time::OffsetDateTime;
use tracing::instrument;

use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::Wallet;

use screener::service::Service as ScreenerService;

use crate::daily_spend_record::entities::SpendingEntry;
use crate::entities::Features;

use self::address_screening_rule::AddressScreeningRule;
use self::all_psbt_inputs_belong_to_wallet_rule::AllPsbtInputsBelongToWalletRule;
use self::all_psbt_outputs_belong_to_wallet_rule::AllPsbtOutputsBelongToWalletRule;
use self::daily_spend_limit_rule::DailySpendingLimitRule;
use self::no_psbt_outputs_belong_to_wallet_rule::NoPsbtOutputsBelongToWalletRule;

mod address_screening_rule;
mod all_psbt_inputs_belong_to_wallet_rule;
mod daily_spend_limit_rule;

mod no_psbt_outputs_belong_to_wallet_rule;

mod all_psbt_outputs_belong_to_wallet_rule;

trait Rule {
    fn check_transaction(&self, psbt: &PartiallySignedTransaction) -> Result<(), String>;
}

pub struct SpendRuleSet<'a> {
    rules: Vec<Box<dyn Rule + 'a>>,
}

impl<'a> SpendRuleSet<'a> {
    pub fn mobile_pay(
        source_wallet: &'a Wallet<AnyDatabase>,
        features: &'a Features,
        spending_history: &'a Vec<&'a SpendingEntry>,
        screener_service: Arc<ScreenerService>,
    ) -> Self {
        Self {
            rules: vec![
                Box::new(AddressScreeningRule::new(
                    source_wallet.network(),
                    screener_service,
                )),
                Box::new(DailySpendingLimitRule::new(
                    source_wallet,
                    features,
                    spending_history,
                    OffsetDateTime::now_utc(),
                )),
                Box::new(AllPsbtInputsBelongToWalletRule::new(source_wallet)),
                Box::new(NoPsbtOutputsBelongToWalletRule::new(source_wallet)),
            ],
        }
    }

    pub fn sweep(
        source_wallet: &'a Wallet<AnyDatabase>,
        destination_wallet: &'a Wallet<AnyDatabase>,
        screener_service: Arc<ScreenerService>,
    ) -> Self {
        Self {
            rules: vec![
                Box::new(AddressScreeningRule::new(
                    source_wallet.network(),
                    screener_service,
                )),
                Box::new(AllPsbtInputsBelongToWalletRule::new(source_wallet)),
                Box::new(AllPsbtOutputsBelongToWalletRule::new(destination_wallet)),
            ],
        }
    }

    #[instrument(skip(self, psbt))]
    pub fn check_spend_rules(&self, psbt: &PartiallySignedTransaction) -> Result<(), Vec<String>> {
        let errors: Vec<String> = self
            .rules
            .iter()
            .map(|p| p.check_transaction(psbt))
            .filter(|p| p.is_err())
            .map(|p| p.unwrap_err())
            .collect();
        if errors.is_empty() {
            Ok(())
        } else {
            Err(errors)
        }
    }
}
