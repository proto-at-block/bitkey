use super::Rule;
use crate::daily_spend_record::entities::SpendingEntry;
use crate::entities::Features;
use crate::metrics;
use crate::spend_rules::errors::SpendRuleCheckError;
use crate::util::{spending_history_excluding, total_change_sats_today};
use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::ChaincodeDelegationCollaboratorWallet;
use time::OffsetDateTime;
use types::account::spending::PrivateMultiSigSpendingKeyset;

/// Caps the total value a day's Mobile Pay transactions may classify as
/// change back to the wallet. Private (V2) keysets only; disabled when
/// `change_cap_sats` is `None`.
pub(crate) struct DailyChangeLimitRuleV2<'a> {
    features: &'a Features,
    private_keyset: &'a PrivateMultiSigSpendingKeyset,
    spending_history: &'a Vec<&'a SpendingEntry>,
    now_utc: OffsetDateTime,
}

impl<'a> DailyChangeLimitRuleV2<'a> {
    pub fn new(
        features: &'a Features,
        private_keyset: &'a PrivateMultiSigSpendingKeyset,
        spending_history: &'a Vec<&'a SpendingEntry>,
        now_utc: OffsetDateTime,
    ) -> Self {
        DailyChangeLimitRuleV2 {
            features,
            private_keyset,
            spending_history,
            now_utc,
        }
    }
}

impl Rule for DailyChangeLimitRuleV2<'_> {
    fn check_transaction(&self, psbt: &Psbt) -> Result<(), SpendRuleCheckError> {
        let Some(change_cap_sats) = self.features.change_cap_sats else {
            return Ok(());
        };
        metrics::MOBILE_PAY_CHANGE_CAP_CHECKED.add(1, &[]);

        let spending_history =
            spending_history_excluding(self.spending_history, psbt.unsigned_tx.compute_txid());
        let change_so_far = total_change_sats_today(
            &spending_history,
            &self.features.settings.limit,
            self.now_utc,
        )
        .map_err(|err| SpendRuleCheckError::CouldNotFetchSpendAmount(err.to_string()))?;

        let delegator_wallet = ChaincodeDelegationCollaboratorWallet::new(
            self.private_keyset.server_pub,
            self.private_keyset.app_pub,
            self.private_keyset.hardware_pub,
        );

        let chaincode_delegation_psbt = delegator_wallet
            .chaincode_delegation_psbt(psbt)
            .map_err(|err| SpendRuleCheckError::InvalidChaincodeDelegationPsbt(err.to_string()))?;

        let change_for_unsigned_transaction_sats = delegator_wallet
            .get_change_for_psbt(&chaincode_delegation_psbt)
            .map_err(|err| SpendRuleCheckError::CouldNotFetchSpendAmount(err.to_string()))?;

        // Overflowing the sum means the change total exceeds any cap.
        match change_for_unsigned_transaction_sats.checked_add(change_so_far) {
            Some(total_change) if change_cap_sats >= total_change => Ok(()),
            _ => {
                metrics::MOBILE_PAY_CHANGE_CAP_OVERFLOW.add(1, &[]);
                Err(SpendRuleCheckError::ChangeLimitExceeded)
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use std::str::FromStr;

    use bdk_utils::bdk::bitcoin::absolute::LockTime;
    use bdk_utils::bdk::bitcoin::consensus::deserialize;
    use bdk_utils::bdk::bitcoin::psbt::raw::ProprietaryKey;
    use bdk_utils::bdk::bitcoin::psbt::{Output, Psbt};
    use bdk_utils::bdk::bitcoin::secp256k1::{PublicKey, Scalar, Secp256k1, SecretKey};
    use bdk_utils::bdk::bitcoin::transaction::Version;
    use bdk_utils::bdk::bitcoin::{Address, Amount, Transaction, TxOut};
    use bdk_utils::bdk::miniscript::Descriptor;
    use bdk_utils::{PROPRIETARY_KEY_PREFIX, PROPRIETARY_KEY_SUBTYPE};
    use time::OffsetDateTime;
    use types::account::bitcoin::Network;
    use types::account::money::Money;
    use types::account::spend_limit::SpendingLimit;
    use types::account::spending::PrivateMultiSigSpendingKeyset;
    use types::currencies::CurrencyCode::USD;

    use crate::daily_spend_record::entities::SpendingEntry;
    use crate::entities::{Features, Settings};
    use crate::spend_rules::errors::SpendRuleCheckError;
    use crate::spend_rules::Rule;

    use super::DailyChangeLimitRuleV2;

    fn test_keyset() -> PrivateMultiSigSpendingKeyset {
        let secp = Secp256k1::new();
        let pk =
            |b: u8| PublicKey::from_secret_key(&secp, &SecretKey::from_slice(&[b; 32]).unwrap());
        PrivateMultiSigSpendingKeyset::new(
            Network::BitcoinSignet,
            pk(1), // app
            pk(2), // hardware
            pk(3), // server
            "unused-integrity-sig".to_string(),
            None,
        )
    }

    /// An output the collaborator wallet classifies as change: spk and witness
    /// script derive from the tweaked 2-of-3 sortedmulti, and the PSBT output
    /// carries the matching proprietary tweak entries.
    fn change_output(keyset: &PrivateMultiSigSpendingKeyset, value: u64) -> (TxOut, Output) {
        let secp = Secp256k1::new();
        let tweak_bytes = [7u8; 32];
        let tweak = Scalar::from_be_bytes(tweak_bytes).unwrap();

        let base_keys = [keyset.server_pub, keyset.app_pub, keyset.hardware_pub];
        let tweaked: Vec<PublicKey> = base_keys
            .iter()
            .map(|pk| pk.add_exp_tweak(&secp, &tweak).unwrap())
            .collect();
        let descriptor = Descriptor::new_wsh_sortedmulti(2, tweaked).unwrap();

        let mut output = Output {
            witness_script: Some(descriptor.script_code().unwrap()),
            ..Output::default()
        };
        for pk in base_keys {
            output.proprietary.insert(
                ProprietaryKey {
                    prefix: PROPRIETARY_KEY_PREFIX.to_vec(),
                    subtype: PROPRIETARY_KEY_SUBTYPE,
                    key: pk.serialize().to_vec(),
                },
                tweak_bytes.to_vec(),
            );
        }

        (
            TxOut {
                value: Amount::from_sat(value),
                script_pubkey: descriptor.script_pubkey(),
            },
            output,
        )
    }

    fn recipient_output(value: u64) -> (TxOut, Output) {
        let script_pubkey = Address::from_str("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")
            .unwrap()
            .assume_checked()
            .script_pubkey();
        (
            TxOut {
                value: Amount::from_sat(value),
                script_pubkey,
            },
            Output::default(),
        )
    }

    fn psbt_with_outputs(outputs: Vec<(TxOut, Output)>) -> Psbt {
        let (tx_outputs, psbt_outputs): (Vec<_>, Vec<_>) = outputs.into_iter().unzip();
        let tx = Transaction {
            version: Version::TWO,
            lock_time: LockTime::ZERO,
            // No inputs: the per-input tweak requirement passes vacuously, which
            // keeps the fixture focused on output classification.
            input: Vec::new(),
            output: tx_outputs,
        };
        let mut psbt = Psbt::from_unsigned_tx(tx).unwrap();
        psbt.outputs = psbt_outputs;
        psbt
    }

    fn features_with_cap(change_cap_sats: Option<u64>) -> Features {
        Features {
            settings: Settings {
                limit: SpendingLimit {
                    active: true,
                    amount: Money {
                        amount: 100_00,
                        currency_code: USD,
                    },
                    ..Default::default()
                },
            },
            daily_limit_sats: u64::MAX,
            change_cap_sats,
        }
    }

    fn recorded_change_entry(change_amount: u64, now: OffsetDateTime) -> SpendingEntry {
        SpendingEntry {
            txid: deserialize(&[0_u8; 32]).unwrap(),
            timestamp: now,
            outflow_amount: 0,
            change_amount,
        }
    }

    #[test]
    fn no_cap_means_no_check() {
        let keyset = test_keyset();
        let psbt = psbt_with_outputs(vec![change_output(&keyset, u64::MAX)]);
        let features = features_with_cap(None);
        let spending_entries = Vec::new();

        let rule = DailyChangeLimitRuleV2::new(
            &features,
            &keyset,
            &spending_entries,
            OffsetDateTime::now_utc(),
        );

        assert!(rule.check_transaction(&psbt).is_ok());
    }

    #[test]
    fn allows_change_under_cap_and_ignores_recipient_outputs() {
        let keyset = test_keyset();
        let psbt = psbt_with_outputs(vec![
            change_output(&keyset, 6_000),
            recipient_output(50_000), // outflow, not change; must not count against the cap
        ]);
        let features = features_with_cap(Some(10_000));
        let spending_entries = Vec::new();

        let rule = DailyChangeLimitRuleV2::new(
            &features,
            &keyset,
            &spending_entries,
            OffsetDateTime::now_utc(),
        );

        assert!(rule.check_transaction(&psbt).is_ok());
    }

    #[test]
    fn rejects_change_over_cap() {
        let keyset = test_keyset();
        let psbt = psbt_with_outputs(vec![change_output(&keyset, 6_000)]);
        let features = features_with_cap(Some(5_000));
        let spending_entries = Vec::new();

        let rule = DailyChangeLimitRuleV2::new(
            &features,
            &keyset,
            &spending_entries,
            OffsetDateTime::now_utc(),
        );

        assert_eq!(
            rule.check_transaction(&psbt),
            Err(SpendRuleCheckError::ChangeLimitExceeded)
        );
    }

    #[test]
    fn accumulates_change_recorded_earlier_in_the_window() {
        let keyset = test_keyset();
        let now = OffsetDateTime::now_utc();
        let psbt = psbt_with_outputs(vec![change_output(&keyset, 6_000)]);
        let features = features_with_cap(Some(10_000));
        let history = [recorded_change_entry(5_000, now)];
        let spending_entries = history.iter().collect();

        let rule = DailyChangeLimitRuleV2::new(&features, &keyset, &spending_entries, now);

        assert_eq!(
            rule.check_transaction(&psbt),
            Err(SpendRuleCheckError::ChangeLimitExceeded)
        );
    }

    #[test]
    fn retry_of_recorded_transaction_is_not_double_counted() {
        let keyset = test_keyset();
        let now = OffsetDateTime::now_utc();
        let psbt = psbt_with_outputs(vec![change_output(&keyset, 6_000)]);
        let features = features_with_cap(Some(6_000));
        let history = [SpendingEntry {
            txid: psbt.unsigned_tx.compute_txid(),
            timestamp: now,
            outflow_amount: 0,
            change_amount: 6_000,
        }];
        let spending_entries = history.iter().collect();

        let rule = DailyChangeLimitRuleV2::new(&features, &keyset, &spending_entries, now);

        assert!(rule.check_transaction(&psbt).is_ok());
    }

    #[test]
    fn output_with_mismatched_tweaks_is_not_counted_as_change() {
        let keyset = test_keyset();
        // Claim a different tweak than the one the spk/witness script were
        // built from: classification fails, so the output counts as outflow
        // (the daily spend limit's problem), not change.
        let (tx_out, mut psbt_out) = change_output(&keyset, 1_000_000);
        for value in psbt_out.proprietary.values_mut() {
            *value = [8u8; 32].to_vec();
        }
        let psbt = psbt_with_outputs(vec![(tx_out, psbt_out)]);
        let features = features_with_cap(Some(0));
        let spending_entries = Vec::new();

        let rule = DailyChangeLimitRuleV2::new(
            &features,
            &keyset,
            &spending_entries,
            OffsetDateTime::now_utc(),
        );

        assert!(rule.check_transaction(&psbt).is_ok());
    }
}
