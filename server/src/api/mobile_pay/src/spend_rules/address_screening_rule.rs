use std::sync::Arc;

use feature_flags::flag::ContextKey;
use feature_flags::service::Service as FeatureFlagsService;

use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::bitcoin::Network;
use types::account::entities::Account;

use crate::spend_rules::errors::SpendRuleCheckError;
use crate::spend_rules::Rule;
use screener::screening::SanctionsScreener;

pub(crate) struct AddressScreeningRule<'a> {
    screener_service: Arc<dyn SanctionsScreener + Send + Sync>,
    feature_flags_service: FeatureFlagsService,
    account: &'a Account,
    network: Network,
    context_key: Option<ContextKey>,
}

impl Rule for AddressScreeningRule<'_> {
    fn check_transaction(&self, psbt: &Psbt) -> Result<(), SpendRuleCheckError> {
        self.screener_service.screen_psbt_for_sanctions(
            psbt,
            self.network,
            self.account,
            &self.feature_flags_service,
            self.context_key.clone(),
        )?;
        Ok(())
    }
}

impl<'a> AddressScreeningRule<'a> {
    pub fn new(
        screener_service: Arc<dyn SanctionsScreener + Send + Sync>,
        feature_flags_service: FeatureFlagsService,
        account: &'a Account,
        network: Network,
        context_key: Option<ContextKey>,
    ) -> Self {
        AddressScreeningRule {
            screener_service,
            feature_flags_service,
            account,
            network,
            context_key,
        }
    }
}

#[cfg(test)]
mod tests {
    use std::collections::HashMap;
    use std::sync::Mutex;

    use super::*;
    use crate::spend_rules::test::get_funded_wallet;
    use bdk_utils::bdk::bitcoin::{Address, Amount, FeeRate, ScriptBuf};
    use bdk_utils::bdk::KeychainKind;
    use screener::screening::{SanctionsScreenerError, SANCTION_TEST_FLAG_KEY};
    use secp256k1::rand::rngs::OsRng;
    use secp256k1::{Secp256k1, SecretKey};
    use time::OffsetDateTime;
    use types::account::entities::{AccountProperties, CommonAccountFields, FullAccount};
    use types::account::identifiers::{AccountId, AuthKeysId, KeysetId};

    struct TestSanctionsService {
        should_block_transaction: bool,
    }

    impl SanctionsScreener for TestSanctionsService {
        fn should_block_transaction(
            &self,
            _account: &Account,
            _addresses: &[String],
        ) -> Result<bool, SanctionsScreenerError> {
            Ok(self.should_block_transaction)
        }
    }

    /// A sanctions service that captures the addresses passed to should_block_transaction.
    struct CapturingSanctionsService {
        captured_addresses: Mutex<Vec<String>>,
    }

    impl SanctionsScreener for CapturingSanctionsService {
        fn should_block_transaction(
            &self,
            _account: &Account,
            addresses: &[String],
        ) -> Result<bool, SanctionsScreenerError> {
            *self.captured_addresses.lock().unwrap() = addresses.to_vec();
            Ok(false)
        }
    }

    fn test_account() -> Account {
        let secp = Secp256k1::new();
        Account::Full(FullAccount {
            id: AccountId::gen().unwrap(),
            active_keyset_id: KeysetId::gen().unwrap(),
            spending_keysets: Default::default(),
            descriptor_backups_set: None,
            spending_limit: None,
            transaction_verification_policy: None,
            application_auth_pubkey: None,
            hardware_auth_pubkey: SecretKey::new(&mut OsRng).public_key(&secp),
            auth_keys: Default::default(),
            common_fields: CommonAccountFields {
                active_auth_keys_id: AuthKeysId::gen().unwrap(),
                touchpoints: Default::default(),
                created_at: OffsetDateTime::now_utc(),
                updated_at: OffsetDateTime::now_utc(),
                properties: AccountProperties {
                    is_test_account: true,
                },
                onboarding_complete: true,
                recovery_auth_pubkey: None,
                notifications_preferences_state: Default::default(),
                configured_privileged_action_delay_durations: Default::default(),
                comms_verification_claims: Default::default(),
                notifications_triggers: Default::default(),
            },
        })
    }

    #[tokio::test]
    async fn invalid_psbt_for_sending_to_blocked_address() {
        // Setup
        let alice_wallet = get_funded_wallet(0);
        let mut bob_wallet = get_funded_wallet(1);
        let bob_address = bob_wallet.next_unused_address(KeychainKind::External);

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
            .add_recipient(bob_address.script_pubkey(), Amount::from_sat(1_000))
            .fee_rate(FeeRate::from_sat_per_vb(5).expect("Invalid fee rate"));
        let psbt = builder.finish().expect("Failed to build transaction");
        let account = test_account();
        let rule = AddressScreeningRule::new(
            screener_service,
            feature_flags_service,
            &account,
            alice_wallet.network(),
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
        let alice_wallet = get_funded_wallet(0);
        let mut bob_wallet = get_funded_wallet(1);
        let bob_address = bob_wallet.next_unused_address(KeychainKind::External);

        let screener_service = Arc::new(TestSanctionsService {
            should_block_transaction: false,
        });

        // Sets the flag to test sanctions
        let feature_flags_service = feature_flags::config::Config::new_with_overrides(
            HashMap::from([(SANCTION_TEST_FLAG_KEY.to_string(), true.to_string())]),
        )
        .to_service()
        .await
        .unwrap();

        // Build transaction
        let mut builder = bob_wallet.build_tx();
        builder
            .add_recipient(bob_address.address.script_pubkey(), Amount::from_sat(1_000))
            .fee_rate(FeeRate::from_sat_per_vb_unchecked(5));
        let psbt = builder.finish().unwrap();
        let account = test_account();
        let rule = AddressScreeningRule::new(
            screener_service,
            feature_flags_service,
            &account,
            alice_wallet.network(),
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
        let alice_wallet = get_funded_wallet(0);
        let mut bob_wallet = get_funded_wallet(1);
        let bob_address = bob_wallet.next_unused_address(KeychainKind::External);

        let mut builder = bob_wallet.build_tx();
        builder
            .add_recipient(bob_address.address.script_pubkey(), Amount::from_sat(1_000))
            .fee_rate(FeeRate::from_sat_per_vb_unchecked(5));
        let psbt = builder.finish().unwrap();
        let account = test_account();
        let rule = AddressScreeningRule::new(
            screener_service,
            feature_flags_service,
            &account,
            alice_wallet.network(),
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
        let alice_wallet = get_funded_wallet(0);
        let mut bob_wallet = get_funded_wallet(1);

        // Create a Psbt with an output containing an invalid script pub key
        let invalid_segwitv0_script =
            ScriptBuf::from_hex("001161458e330389cd0437ee9fe3641d70cc18").unwrap();
        let mut builder = bob_wallet.build_tx();
        builder
            .add_recipient(invalid_segwitv0_script, Amount::from_sat(1_000))
            .fee_rate(FeeRate::from_sat_per_vb_unchecked(5));
        let psbt = builder.finish().unwrap();
        let account = test_account();
        let rule = AddressScreeningRule::new(
            screener_service,
            feature_flags_service,
            &account,
            alice_wallet.network(),
            Some(ContextKey::Account(
                "accountid".to_string(),
                Default::default(),
            )),
        );

        assert!(rule.check_transaction(&psbt).is_err())
    }

    #[tokio::test]
    async fn screening_includes_input_and_output_addresses() {
        let mut source_wallet = get_funded_wallet(0);
        let mut dest_wallet = get_funded_wallet(1);
        let dest_address = dest_wallet.next_unused_address(KeychainKind::External);
        let network = source_wallet.network();

        let mut builder = source_wallet.build_tx();
        builder
            .add_recipient(
                dest_address.address.script_pubkey(),
                Amount::from_sat(1_000),
            )
            .fee_rate(FeeRate::from_sat_per_vb_unchecked(5));
        let psbt = builder.finish().unwrap();

        // Collect expected input addresses from the PSBT's witness_utxo fields
        let expected_input_addresses: Vec<String> = psbt
            .inputs
            .iter()
            .filter_map(|input| input.witness_utxo.as_ref())
            .filter_map(|utxo| Address::from_script(&utxo.script_pubkey, network).ok())
            .map(|addr| addr.to_string())
            .collect();
        assert!(
            !expected_input_addresses.is_empty(),
            "PSBT should have input addresses"
        );

        // Collect expected output addresses
        let expected_output_addresses: Vec<String> = psbt
            .unsigned_tx
            .output
            .iter()
            .filter_map(|output| Address::from_script(&output.script_pubkey, network).ok())
            .map(|addr| addr.to_string())
            .collect();

        let screener_service = Arc::new(CapturingSanctionsService {
            captured_addresses: Mutex::new(Vec::new()),
        });

        let feature_flags_service =
            feature_flags::config::Config::new_with_overrides(Default::default())
                .to_service()
                .await
                .unwrap();

        let account = test_account();
        let rule = AddressScreeningRule::new(
            screener_service.clone(),
            feature_flags_service,
            &account,
            network,
            None,
        );

        assert!(rule.check_transaction(&psbt).is_ok());

        let captured = screener_service.captured_addresses.lock().unwrap();
        for addr in &expected_input_addresses {
            assert!(
                captured.contains(addr),
                "Input address {addr} should be screened"
            );
        }
        for addr in &expected_output_addresses {
            assert!(
                captured.contains(addr),
                "Output address {addr} should be screened"
            );
        }
    }
}
