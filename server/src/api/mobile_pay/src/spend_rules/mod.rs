use std::sync::Arc;
use strum_macros::Display;
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

pub trait Rule {
    fn check_transaction(&self, psbt: &PartiallySignedTransaction) -> Result<(), String>;
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
    ) -> Self {
        SpendRuleSet::MobilePay {
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
        SpendRuleSet::Sweep {
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
        let rules = match self {
            SpendRuleSet::MobilePay { rules } | SpendRuleSet::Sweep { rules } => rules,
            #[cfg(test)]
            SpendRuleSet::Test { rules } => rules,
        };
        let errors: Vec<String> = rules
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
    use crate::spend_rules::{Rule, SpendRuleSet};
    use bdk_utils::bdk::bitcoin::psbt::Psbt;
    use rstest::rstest;
    use std::str::FromStr;

    pub struct TestRule {
        pub fail_with_error: Option<String>,
    }

    impl Rule for TestRule {
        fn check_transaction(&self, _psbt: &Psbt) -> Result<(), String> {
            if let Some(ref error) = self.fail_with_error {
                Err(error.clone())
            } else {
                Ok(())
            }
        }
    }

    impl<'a> SpendRuleSet<'a> {
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
                TestRule { fail_with_error: Some("TestRule failed".to_string()) },
            ],
            Some(vec!["TestRule failed".to_string()])
        ),
        case(
            vec![
                TestRule { fail_with_error: Some("TestRule 1 failed".to_string()) },
                TestRule { fail_with_error: None },
                TestRule { fail_with_error: Some("TestRule 2 failed".to_string()) },
            ],
            Some(vec!["TestRule 1 failed".to_string(), "TestRule 2 failed".to_string()])
        )
    )]
    fn test_check_spend_rules(rules: Vec<TestRule>, expected_errors: Option<Vec<String>>) {
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
                for error_message in expected {
                    assert!(errors.contains(&error_message));
                }
            }
        }
    }
}
