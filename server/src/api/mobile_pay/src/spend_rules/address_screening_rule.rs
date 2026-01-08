use std::sync::Arc;

use feature_flags::flag::ContextKey;
use feature_flags::service::Service as FeatureFlagsService;

use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction;
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
    fn check_transaction(
        &self,
        psbt: &PartiallySignedTransaction,
    ) -> Result<(), SpendRuleCheckError> {
        self.screener_service.screen_psbt_outputs_for_sanctions(
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

    use super::*;
    use bdk_utils::bdk::bitcoin::ScriptBuf;
    use bdk_utils::bdk::wallet::{get_funded_wallet, AddressIndex};
    use bdk_utils::bdk::FeeRate;
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
        let alice_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
        let bob_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/1/*)").0;
        let bob_address = bob_wallet.get_address(AddressIndex::New).unwrap();

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
            .add_recipient(bob_address.script_pubkey(), 1_000)
            .fee_rate(FeeRate::from_sat_per_vb(5.0));
        let (psbt, _) = builder.finish().unwrap();
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
        let alice_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
        let bob_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/1/*)").0;
        let bob_address = bob_wallet.get_address(AddressIndex::New).unwrap();

        let mut builder = bob_wallet.build_tx();
        builder
            .add_recipient(bob_address.script_pubkey(), 1_000)
            .fee_rate(FeeRate::from_sat_per_vb(5.0));
        let (psbt, _) = builder.finish().unwrap();
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
}
