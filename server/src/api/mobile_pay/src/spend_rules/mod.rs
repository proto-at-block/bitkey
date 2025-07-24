use feature_flags::flag::ContextKey;
use std::sync::Arc;
use strum_macros::Display;
use time::OffsetDateTime;
use tracing::instrument;

use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::Wallet;

use feature_flags::service::Service as FeatureFlagsService;
use screener::service::Service as ScreenerService;

use self::address_screening_rule::AddressScreeningRule;
use self::all_psbt_inputs_belong_to_wallet_rule::AllPsbtInputsBelongToWalletRule;
use self::all_psbt_outputs_belong_to_wallet_rule::AllPsbtOutputsBelongToWalletRule;
use self::daily_spend_limit_rule::DailySpendingLimitRule;
use self::no_psbt_outputs_belong_to_wallet_rule::NoPsbtOutputsBelongToWalletRule;
use crate::daily_spend_record::entities::SpendingEntry;
use crate::entities::{Features, TransactionVerificationFeatures};
use crate::spend_rules::errors::SpendRuleCheckError;
use crate::spend_rules::transaction_verification_rule::TransactionVerificationRule;

mod address_screening_rule;
mod all_psbt_inputs_belong_to_wallet_rule;
mod daily_spend_limit_rule;
mod transaction_verification_rule;

mod no_psbt_outputs_belong_to_wallet_rule;

mod all_psbt_outputs_belong_to_wallet_rule;
pub mod errors;

pub trait Rule {
    fn check_transaction(
        &self,
        psbt: &PartiallySignedTransaction,
    ) -> Result<(), SpendRuleCheckError>;
}

#[derive(Display)]
pub enum SpendRuleSet<'a> {
    MobilePay {
        rules: Vec<Box<dyn Rule + 'a>>,
    },
    Sweep {
        rules: Vec<Box<dyn Rule + 'a>>,
    },
    #[cfg(test)]
    Test {
        rules: Vec<Box<dyn Rule + 'a>>,
    },
}

impl<'a> SpendRuleSet<'a> {
    pub fn mobile_pay(
        source_wallet: &'a Wallet<AnyDatabase>,
        features: &'a Features,
        spending_history: &'a Vec<&'a SpendingEntry>,
        screener_service: Arc<ScreenerService>,
        transaction_verification_features: Option<TransactionVerificationFeatures>,
        feature_flags_service: FeatureFlagsService,
        context_key: Option<ContextKey>,
    ) -> Self {
        SpendRuleSet::MobilePay {
            rules: vec![
                Box::new(AddressScreeningRule::new(
                    source_wallet.network(),
                    screener_service,
                    feature_flags_service,
                    context_key,
                )),
                Box::new(DailySpendingLimitRule::new(
                    source_wallet,
                    features,
                    spending_history,
                    OffsetDateTime::now_utc(),
                )),
                Box::new(AllPsbtInputsBelongToWalletRule::new(source_wallet)),
                Box::new(NoPsbtOutputsBelongToWalletRule::new(source_wallet)),
                Box::new(TransactionVerificationRule::new(
                    source_wallet,
                    transaction_verification_features,
                )),
            ],
        }
    }

    pub fn sweep(
        source_wallet: &'a Wallet<AnyDatabase>,
        destination_wallet: &'a Wallet<AnyDatabase>,
        screener_service: Arc<ScreenerService>,
        feature_flags_service: FeatureFlagsService,
        context_key: Option<ContextKey>,
    ) -> Self {
        SpendRuleSet::Sweep {
            rules: vec![
                Box::new(AddressScreeningRule::new(
                    source_wallet.network(),
                    screener_service,
                    feature_flags_service,
                    context_key,
                )),
                Box::new(AllPsbtInputsBelongToWalletRule::new(source_wallet)),
                Box::new(AllPsbtOutputsBelongToWalletRule::new(destination_wallet)),
            ],
        }
    }

    #[instrument(skip(self, psbt))]
    pub fn check_spend_rules(
        &self,
        psbt: &PartiallySignedTransaction,
    ) -> Result<(), Vec<SpendRuleCheckError>> {
        let rules = match self {
            SpendRuleSet::MobilePay { rules } | SpendRuleSet::Sweep { rules } => rules,
            #[cfg(test)]
            SpendRuleSet::Test { rules } => rules,
        };
        let errors: Vec<SpendRuleCheckError> = rules
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

#[cfg(test)]
pub mod test {
    use crate::spend_rules::errors::SpendRuleCheckError;
    use crate::spend_rules::{Rule, SpendRuleSet};
    use bdk_utils::bdk::bitcoin::psbt::Psbt;
    use rstest::rstest;
    use std::str::FromStr;

    pub struct TestRule {
        pub fail_with_error: Option<SpendRuleCheckError>,
    }

    impl Rule for TestRule {
        fn check_transaction(&self, _psbt: &Psbt) -> Result<(), SpendRuleCheckError> {
            if let Some(ref error) = self.fail_with_error {
                Err(error.clone())
            } else {
                Ok(())
            }
        }
    }

    impl SpendRuleSet<'_> {
        pub fn test(rules: Vec<TestRule>) -> Self {
            SpendRuleSet::Test {
                rules: rules
                    .into_iter()
                    .map(|r| Box::new(r) as Box<dyn Rule>)
                    .collect(),
            }
        }
    }

    #[rstest(
        rules,
        expected_errors,
        case(
            vec![
                TestRule { fail_with_error: None },
                TestRule { fail_with_error: None },
            ],
            None
        ),
        case(
            vec![
                TestRule { fail_with_error: Some(SpendRuleCheckError::OutputsDontBelongToDestinationWallet) },
            ],
            Some(vec![SpendRuleCheckError::OutputsDontBelongToDestinationWallet])
        ),
        case(
            vec![
                TestRule { fail_with_error: Some(SpendRuleCheckError::OutputsDontBelongToDestinationWallet) },
                TestRule { fail_with_error: None },
                TestRule { fail_with_error: Some(SpendRuleCheckError::OutputsBelongToSanctionedIndividuals) },
            ],
            Some(vec![SpendRuleCheckError::OutputsDontBelongToDestinationWallet, SpendRuleCheckError::OutputsBelongToSanctionedIndividuals])
        )
    )]
    fn test_check_spend_rules(
        rules: Vec<TestRule>,
        expected_errors: Option<Vec<SpendRuleCheckError>>,
    ) {
        // Arrange
        let spend_rule_set = SpendRuleSet::test(rules);
        let psbt = Psbt::from_str("cHNidP8BAHUCAAAAASaBcTce3/KF6Tet7qSze3gADAVmy7OtZGQXE8pCFxv2AAAAAAD+////AtPf9QUAAAAAGXapFNDFmQPFusKGh2DpD9UhpGZap2UgiKwA4fUFAAAAABepFDVF5uM7gyxHBQ8k0+65PJwDlIvHh7MuEwAAAQD9pQEBAAAAAAECiaPHHqtNIOA3G7ukzGmPopXJRjr6Ljl/hTPMti+VZ+UBAAAAFxYAFL4Y0VKpsBIDna89p95PUzSe7LmF/////4b4qkOnHf8USIk6UwpyN+9rRgi7st0tAXHmOuxqSJC0AQAAABcWABT+Pp7xp0XpdNkCxDVZQ6vLNL1TU/////8CAMLrCwAAAAAZdqkUhc/xCX/Z4Ai7NK9wnGIZeziXikiIrHL++E4sAAAAF6kUM5cluiHv1irHU6m80GfWx6ajnQWHAkcwRAIgJxK+IuAnDzlPVoMR3HyppolwuAJf3TskAinwf4pfOiQCIAGLONfc0xTnNMkna9b7QPZzMlvEuqFEyADS8vAtsnZcASED0uFWdJQbrUqZY3LLh+GFbTZSYG2YVi/jnF6efkE/IQUCSDBFAiEA0SuFLYXc2WHS9fSrZgZU327tzHlMDDPOXMMJ/7X85Y0CIGczio4OFyXBl/saiK9Z9R5E5CVbIBZ8hoQDHAXR8lkqASECI7cr7vCWXRC+B3jv7NYfysb3mk6haTkzgHNEZPhPKrMAAAAAAAAA").expect("Failed to parse PSBT");

        // Act
        let result = spend_rule_set.check_spend_rules(&psbt);

        // Assert
        match expected_errors {
            None => {
                assert!(result.is_ok());
            }
            Some(expected) => {
                assert!(result.is_err());
                let errors = result.unwrap_err();
                assert_eq!(errors.len(), expected.len());
                for expected_error in expected {
                    assert!(errors.contains(&expected_error));
                }
            }
        }
    }
}
