use time::OffsetDateTime;
use tracing::instrument;

use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::Wallet;

use crate::daily_spend_record::entities::SpendingEntry;
use crate::entities::Features;

use self::daily_spend_limit_rule::DailySpendingLimitRule;
use self::valid_psbt_for_wallet_rule::ValidPsbtForWalletRule;

mod daily_spend_limit_rule;
mod valid_psbt_for_wallet_rule;

trait Rule {
    fn check_transaction(
        &self,
        wallet: &Wallet<AnyDatabase>,
        psbt: &PartiallySignedTransaction,
        features: &Features,
        spending_history: &Vec<&SpendingEntry>,
        now_utc: OffsetDateTime,
    ) -> Result<(), String>;
}

pub struct SpendRuleSet {
    rules: Vec<Box<dyn Rule>>,
}

impl Default for SpendRuleSet {
    fn default() -> Self {
        SpendRuleSet {
            rules: vec![
                Box::new(DailySpendingLimitRule {}),
                Box::new(ValidPsbtForWalletRule {}),
            ],
        }
    }
}

impl SpendRuleSet {
    #[instrument(skip(self, wallet, psbt))]
    pub fn check_spend_rules(
        &self,
        wallet: &Wallet<AnyDatabase>,
        psbt: &PartiallySignedTransaction,
        features: &Features,
        spending_history: &Vec<&SpendingEntry>,
    ) -> Result<(), Vec<String>> {
        let errors: Vec<String> = self
            .rules
            .iter()
            .map(|p| {
                p.check_transaction(
                    wallet,
                    psbt,
                    features,
                    spending_history,
                    OffsetDateTime::now_utc(),
                )
            })
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
