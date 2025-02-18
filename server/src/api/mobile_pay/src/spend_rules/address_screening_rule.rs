use std::sync::Arc;

use feature_flags::flag::{evaluate_flag_value, ContextKey};
use feature_flags::service::Service as FeatureFlagsService;

use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction;
use bdk_utils::bdk::bitcoin::{Address, Network};

use crate::spend_rules::errors::SpendRuleCheckError;
use crate::spend_rules::Rule;
use screener::service::SanctionsScreener;

const FLAG_KEY: &str = "f8e-sanction-test-account";

pub(crate) struct AddressScreeningRule {
    screener_service: Arc<dyn SanctionsScreener>,
    feature_flags_service: FeatureFlagsService,
    network: Network,
    context_key: Option<ContextKey>,
}

impl Rule for AddressScreeningRule {
    fn check_transaction(
        &self,
        psbt: &PartiallySignedTransaction,
    ) -> Result<(), SpendRuleCheckError> {
        let tx = psbt.clone().unsigned_tx;
        let destination_addresses = tx
            .output
            .iter()
            .map(|output| {
                Address::from_script(&output.script_pubkey, self.network)
                    .map(|address| address.to_string())
                    .map_err(|_| SpendRuleCheckError::InvalidScriptPubKeys)
            })
            .collect::<Result<Vec<String>, SpendRuleCheckError>>()?;

        let sanction_test_account = self
            .context_key
            .as_ref()
            .and_then(|ck| evaluate_flag_value(&self.feature_flags_service, FLAG_KEY, ck).ok())
            .unwrap_or(false);

        if sanction_test_account
            || self
                .screener_service
                .should_block_transaction(&destination_addresses)
        {
            Err(SpendRuleCheckError::OutputsBelongToSanctionedIndividuals)
        } else {
            Ok(())
        }
    }
}

impl AddressScreeningRule {
    pub fn new(
        network: Network,
        screener_service: Arc<dyn SanctionsScreener>,
        feature_flags_service: FeatureFlagsService,
        context_key: Option<ContextKey>,
    ) -> Self {
        AddressScreeningRule {
            network,
            screener_service,
            feature_flags_service,
            context_key,
        }
    }
}

#[cfg(test)]
mod tests {
    use std::collections::HashMap;

    use super::*;
    use bdk_utils::bdk::bitcoin::ScriptBuf;
    use bdk_utils::bdk::wallet::{get_funded_wallet, AddressIndex};
    use bdk_utils::bdk::FeeRate;

    struct TestSanctionsService {
        should_block_transaction: bool,
    }

    impl SanctionsScreener for TestSanctionsService {
        fn should_block_transaction(&self, _addresses: &[String]) -> bool {
            self.should_block_transaction
        }
    }

    #[tokio::test]
    async fn invalid_psbt_for_sending_to_blocked_address() {
        // Setup
        let alice_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
        let bob_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/1/*)").0;
        let bob_address = bob_wallet.get_address(AddressIndex::New).unwrap();

        // Creates test sanctions service with bob's address in the blocklist
        let screener_service = Arc::new(TestSanctionsService {
            should_block_transaction: true,
        });

        let feature_flags_service =
            feature_flags::config::Config::new_with_overrides(Default::default())
                .to_service()
                .await
                .unwrap();

        // Build transaction
        let mut builder = bob_wallet.build_tx();
        builder
            .add_recipient(bob_address.script_pubkey(), 1_000)
            .fee_rate(FeeRate::from_sat_per_vb(5.0));
        let (psbt, _) = builder.finish().unwrap();
        let rule = AddressScreeningRule::new(
            alice_wallet.network(),
            screener_service,
            feature_flags_service,
            Some(ContextKey::Account(
                "accountid".to_string(),
                Default::default(),
            )),
        );

        assert!(rule.check_transaction(&psbt).is_err())
    }

    #[tokio::test]
    async fn invalid_psbt_for_sending_to_blocked_address_by_flag() {
        // Setup
        let alice_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
        let bob_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/1/*)").0;
        let bob_address = bob_wallet.get_address(AddressIndex::New).unwrap();

        let screener_service = Arc::new(TestSanctionsService {
            should_block_transaction: false,
        });

        // Sets the flag to test sanctions
        let feature_flags_service = feature_flags::config::Config::new_with_overrides(
            HashMap::from([(FLAG_KEY.to_string(), true.to_string())]),
        )
        .to_service()
        .await
        .unwrap();

        // Build transaction
        let mut builder = bob_wallet.build_tx();
        builder
            .add_recipient(bob_address.script_pubkey(), 1_000)
            .fee_rate(FeeRate::from_sat_per_vb(5.0));
        let (psbt, _) = builder.finish().unwrap();
        let rule = AddressScreeningRule::new(
            alice_wallet.network(),
            screener_service,
            feature_flags_service,
            Some(ContextKey::Account(
                "accountid".to_string(),
                Default::default(),
            )),
        );

        assert!(rule.check_transaction(&psbt).is_err())
    }

    #[tokio::test]
    async fn valid_psbt_for_sending_to_unblocked_address() {
        // Setup
        let screener_service = Arc::new(TestSanctionsService {
            should_block_transaction: false,
        });
        let feature_flags_service =
            feature_flags::config::Config::new_with_overrides(Default::default())
                .to_service()
                .await
                .unwrap();
        let alice_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
        let bob_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/1/*)").0;
        let bob_address = bob_wallet.get_address(AddressIndex::New).unwrap();

        let mut builder = bob_wallet.build_tx();
        builder
            .add_recipient(bob_address.script_pubkey(), 1_000)
            .fee_rate(FeeRate::from_sat_per_vb(5.0));
        let (psbt, _) = builder.finish().unwrap();
        let rule = AddressScreeningRule::new(
            alice_wallet.network(),
            screener_service,
            feature_flags_service,
            Some(ContextKey::Account(
                "accountid".to_string(),
                Default::default(),
            )),
        );

        assert!(rule.check_transaction(&psbt).is_ok())
    }

    #[tokio::test]
    async fn invalid_psbt_for_invalid_output_spk() {
        // Setup
        let screener_service = Arc::new(TestSanctionsService {
            should_block_transaction: true,
        });
        let feature_flags_service =
            feature_flags::config::Config::new_with_overrides(Default::default())
                .to_service()
                .await
                .unwrap();
        let alice_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
        let bob_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/1/*)").0;

        // Create a Psbt with an output containing an invalid script pub key
        let invalid_segwitv0_script =
            ScriptBuf::from_hex("001161458e330389cd0437ee9fe3641d70cc18").unwrap();
        let mut builder = bob_wallet.build_tx();
        builder
            .add_recipient(invalid_segwitv0_script, 1_000)
            .fee_rate(FeeRate::from_sat_per_vb(5.0));
        let (psbt, _) = builder.finish().unwrap();
        let rule = AddressScreeningRule::new(
            alice_wallet.network(),
            screener_service,
            feature_flags_service,
            Some(ContextKey::Account(
                "accountid".to_string(),
                Default::default(),
            )),
        );

        assert!(rule.check_transaction(&psbt).is_err())
    }
}
