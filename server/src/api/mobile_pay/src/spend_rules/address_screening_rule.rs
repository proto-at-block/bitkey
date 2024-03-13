use std::sync::Arc;

use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction;
use bdk_utils::bdk::bitcoin::{Address, Network};

use screener::service::SanctionsScreener;

use crate::spend_rules::Rule;

pub(crate) struct AddressScreeningRule {
    screener_service: Arc<dyn SanctionsScreener>,
    network: Network,
}

impl Rule for AddressScreeningRule {
    fn check_transaction(&self, psbt: &PartiallySignedTransaction) -> Result<(), String> {
        let tx = psbt.clone().unsigned_tx;
        let destination_addresses = tx
            .output
            .iter()
            .map(|output| {
                Address::from_script(&output.script_pubkey, self.network)
                    .map(|address| address.to_string())
                    .map_err(|_| {
                        "One or more script pub keys are invalid. Cannot check transaction"
                            .to_string()
                    })
            })
            .collect::<Result<Vec<String>, String>>()?;

        if self
            .screener_service
            .should_block_transaction(&destination_addresses)
        {
            Err("One or more outputs belong to sanctioned individuals.".to_string())
        } else {
            Ok(())
        }
    }
}

impl AddressScreeningRule {
    pub fn new(network: Network, screener_service: Arc<dyn SanctionsScreener>) -> Self {
        AddressScreeningRule {
            network,
            screener_service,
        }
    }
}

#[cfg(test)]
mod tests {
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

    #[test]
    fn invalid_psbt_for_sending_to_blocked_address() {
        // Setup
        let alice_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
        let bob_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/1/*)").0;
        let bob_address = bob_wallet.get_address(AddressIndex::New).unwrap();

        // Creates test sanctions service with bob's address in the blocklist
        let screener_service = Arc::new(TestSanctionsService {
            should_block_transaction: true,
        });

        // Build transaction
        let mut builder = bob_wallet.build_tx();
        builder
            .add_recipient(bob_address.script_pubkey(), 1_000)
            .fee_rate(FeeRate::from_sat_per_vb(5.0));
        let (psbt, _) = builder.finish().unwrap();
        let rule = AddressScreeningRule::new(alice_wallet.network(), screener_service);

        assert!(rule.check_transaction(&psbt).is_err())
    }

    #[test]
    fn valid_psbt_for_sending_to_unblocked_address() {
        // Setup
        let screener_service = Arc::new(TestSanctionsService {
            should_block_transaction: false,
        });
        let alice_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
        let bob_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/1/*)").0;
        let bob_address = bob_wallet.get_address(AddressIndex::New).unwrap();

        let mut builder = bob_wallet.build_tx();
        builder
            .add_recipient(bob_address.script_pubkey(), 1_000)
            .fee_rate(FeeRate::from_sat_per_vb(5.0));
        let (psbt, _) = builder.finish().unwrap();
        let rule = AddressScreeningRule::new(alice_wallet.network(), screener_service);

        assert!(rule.check_transaction(&psbt).is_ok())
    }

    #[test]
    fn invalid_psbt_for_invalid_output_spk() {
        // Setup
        let screener_service = Arc::new(TestSanctionsService {
            should_block_transaction: true,
        });
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
        let rule = AddressScreeningRule::new(alice_wallet.network(), screener_service);

        assert!(rule.check_transaction(&psbt).is_err())
    }
}
