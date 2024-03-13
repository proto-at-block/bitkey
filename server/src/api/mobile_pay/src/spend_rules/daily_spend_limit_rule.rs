use time::OffsetDateTime;

use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::Wallet;

use crate::daily_spend_record::entities::SpendingEntry;
use crate::entities::Features;
use crate::metrics;
use crate::util::{get_total_outflow_for_psbt, total_sats_spent_today};

use super::Rule;

pub(crate) struct DailySpendingLimitRule<'a> {
    wallet: &'a Wallet<AnyDatabase>,
    features: &'a Features,
    spending_history: &'a Vec<&'a SpendingEntry>,
    now_utc: OffsetDateTime,
}

impl<'a> DailySpendingLimitRule<'a> {
    pub fn new(
        wallet: &'a Wallet<AnyDatabase>,
        features: &'a Features,
        spending_history: &'a Vec<&'a SpendingEntry>,
        now_utc: OffsetDateTime,
    ) -> Self {
        DailySpendingLimitRule {
            wallet,
            features,
            spending_history,
            now_utc,
        }
    }
}

impl<'a> Rule for DailySpendingLimitRule<'a> {
    /// Ensure that the total outflows for this PSBT plus the outflows so far today do not exceed
    /// the set spending limit
    fn check_transaction(&self, psbt: &PartiallySignedTransaction) -> Result<(), String> {
        let total_spent = total_sats_spent_today(
            self.spending_history,
            &self.features.settings.limit,
            self.now_utc,
        )?;

        let total_spend_for_unsigned_transaction_sats =
            get_total_outflow_for_psbt(self.wallet, psbt);
        if self.features.daily_limit_sats >= total_spend_for_unsigned_transaction_sats + total_spent
        {
            Ok(())
        } else {
            metrics::MOBILE_PAY_COSIGN_OVERFLOW.add(1, &[]);
            Err(format!(
                "Transaction spend total of {total_spend_for_unsigned_transaction_sats} with existing spend of {total_spent} for the day exceeds limit."
            ))
        }
    }
}

#[cfg(test)]
mod tests {
    use time::{macros::datetime, Duration, OffsetDateTime, UtcOffset};

    use account::spend_limit::{Money, SpendingLimit};
    use bdk_utils::bdk::bitcoin::consensus::deserialize;
    use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction;
    use bdk_utils::bdk::bitcoin::ScriptBuf;
    use bdk_utils::bdk::database::AnyDatabase;
    use bdk_utils::bdk::wallet::{get_funded_wallet, AddressIndex, AddressInfo};
    use bdk_utils::bdk::{BlockTime, FeeRate, TransactionDetails, Wallet};
    use exchange_rate::currency_conversion::sats_for;
    use exchange_rate::service::Service as ExchangeRateService;
    use types::currencies::CurrencyCode::USD;
    use types::exchange_rate::local_rate_provider::LocalRateProvider;

    use crate::daily_spend_record::entities::SpendingEntry;
    use crate::entities::{Features, Settings};
    use crate::spend_rules::daily_spend_limit_rule::DailySpendingLimitRule;
    use crate::spend_rules::Rule;

    fn generate_test_wallets_and_address() -> (Wallet<AnyDatabase>, AddressInfo) {
        let source_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
        let destination_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/1/*)").0;
        let destination_address = destination_wallet.get_address(AddressIndex::New).unwrap();
        (source_wallet, destination_address)
    }

    async fn generate_psbt(
        source_wallet: &Wallet<AnyDatabase>,
        recipient: ScriptBuf,
        m: &Money,
    ) -> PartiallySignedTransaction {
        let spend_sat = sats_for(&ExchangeRateService::new(), LocalRateProvider::new(), m)
            .await
            .unwrap();
        let mut builder = source_wallet.build_tx();
        builder
            .add_recipient(recipient, spend_sat)
            .fee_rate(FeeRate::from_sat_per_vb(5.0));
        let (psbt, _) = builder.finish().unwrap();
        psbt
    }

    async fn generate_fake_transaction_details(
        sent_money: &Money,
        timestamp: OffsetDateTime,
    ) -> TransactionDetails {
        let sent = sats_for(
            &ExchangeRateService::new(),
            LocalRateProvider::new(),
            sent_money,
        )
        .await
        .unwrap();
        let fake_id = [0_u8; 32];
        TransactionDetails {
            txid: deserialize(&fake_id).unwrap(),
            transaction: None,
            received: 0,
            sent,
            fee: None,
            confirmation_time: Some(BlockTime {
                height: 10,
                timestamp: timestamp.unix_timestamp() as u64,
            }),
        }
    }

    /// Given a vector of transactions, generate a vector of Spending Entries
    fn generate_daily_spend_entries_for_tx_history(
        transactions: Vec<TransactionDetails>,
    ) -> Vec<SpendingEntry> {
        transactions
            .iter()
            .map(|tx| SpendingEntry {
                txid: tx.txid,
                timestamp: OffsetDateTime::from_unix_timestamp(
                    tx.confirmation_time.clone().unwrap().timestamp as i64,
                )
                .unwrap(),
                outflow_amount: tx.sent,
            })
            .collect()
    }

    #[tokio::test]
    async fn tx_history_helper_function_works() {
        let spend_history = generate_daily_spend_entries_for_tx_history(vec![
            generate_fake_transaction_details(
                &Money {
                    amount: 2_00,
                    currency_code: USD,
                },
                datetime!(2022-12-30 00:00:00 -5),
            )
            .await,
            generate_fake_transaction_details(
                &Money {
                    amount: 3_00,
                    currency_code: USD,
                },
                datetime!(2022-12-31 00:00:00 -5),
            )
            .await,
        ]);

        let expected_sats = sats_for(
            &ExchangeRateService::new(),
            LocalRateProvider::new(),
            &Money {
                amount: 3_00,
                currency_code: USD,
            },
        )
        .await
        .unwrap();

        assert_eq!(spend_history.len(), 2);
        let latest_date = spend_history
            .iter()
            .max_by_key(|entry| entry.timestamp)
            .unwrap();
        assert_eq!(latest_date.outflow_amount, expected_sats);
    }

    #[tokio::test]
    async fn daily_spend_limit_rule_allows_conformant_tx() {
        let (alice_wallet, bob_address) = generate_test_wallets_and_address();
        let psbt = generate_psbt(
            &alice_wallet,
            bob_address.address.script_pubkey(),
            &Money {
                amount: 2_00,
                currency_code: USD,
            },
        )
        .await;
        let settings = Settings {
            limit: SpendingLimit {
                amount: Money {
                    amount: 50_00, // More than what's spent
                    currency_code: USD,
                },
                ..Default::default()
            },
            ..Default::default()
        };

        let daily_limit_sats = sats_for(
            &ExchangeRateService::new(),
            LocalRateProvider::new(),
            &settings.limit.amount,
        )
        .await
        .unwrap();

        let features = Features {
            settings,
            daily_limit_sats,
            ..Default::default()
        };

        let spending_entries = Vec::new();
        let rule = DailySpendingLimitRule::new(
            &alice_wallet,
            &features,
            &spending_entries,
            OffsetDateTime::now_utc(),
        );
        assert!(rule.check_transaction(&psbt).is_ok());
    }

    #[tokio::test]
    async fn daily_spend_limit_rule_doesnt_allow_nonconforming_tx() {
        let (alice_wallet, bob_address) = generate_test_wallets_and_address();
        let psbt = generate_psbt(
            &alice_wallet,
            bob_address.address.script_pubkey(),
            &Money {
                amount: 2_00,
                currency_code: USD,
            },
        )
        .await;
        let settings = Settings {
            limit: SpendingLimit {
                amount: Money {
                    amount: 1_00, // Less than what's spent
                    currency_code: USD,
                },
                ..Default::default()
            },
            ..Default::default()
        };

        let daily_limit_sats = sats_for(
            &ExchangeRateService::new(),
            LocalRateProvider::new(),
            &settings.limit.amount,
        )
        .await
        .unwrap();

        let features = Features {
            settings,
            daily_limit_sats,
            ..Default::default()
        };

        let spending_entries = Vec::new();
        let rule = DailySpendingLimitRule::new(
            &alice_wallet,
            &features,
            &spending_entries,
            OffsetDateTime::now_utc(),
        );
        assert!(rule.check_transaction(&psbt).is_err());
    }

    #[tokio::test]
    async fn daily_spend_limit_rule_doesnt_allow_with_current_daily_spend() {
        let (alice_wallet, bob_address) = generate_test_wallets_and_address();
        let midnight_utc = datetime!(2023-03-01 00:00:00 UTC); // Midnight EST
        let psbt = generate_psbt(
            &alice_wallet,
            bob_address.address.script_pubkey(),
            &Money {
                amount: 2_00,
                currency_code: USD,
            },
        )
        .await;
        let settings = Settings {
            limit: SpendingLimit {
                amount: Money {
                    amount: 5_00, // More than what's spent
                    currency_code: USD,
                },
                ..Default::default()
            },
            ..Default::default()
        };

        let daily_limit_sats = sats_for(
            &ExchangeRateService::new(),
            LocalRateProvider::new(),
            &settings.limit.amount,
        )
        .await
        .unwrap();

        let transaction_history = generate_daily_spend_entries_for_tx_history(vec![
            generate_fake_transaction_details(
                &Money {
                    amount: 2_00, // (4 + 2) > 5
                    currency_code: USD,
                },
                midnight_utc,
            )
            .await,
            generate_fake_transaction_details(
                &Money {
                    amount: 2_00, // (4 + 2) > 5
                    currency_code: USD,
                },
                midnight_utc - Duration::hours(12),
            )
            .await,
        ]);

        let features = Features {
            settings,
            daily_limit_sats,
            ..Default::default()
        };

        let spending_entries = transaction_history.iter().collect();

        let rule =
            DailySpendingLimitRule::new(&alice_wallet, &features, &spending_entries, midnight_utc);
        assert!(rule.check_transaction(&psbt).is_err());
    }

    #[tokio::test]
    async fn daily_spend_limit_rule_for_timezone() {
        let (alice_wallet, bob_address) = generate_test_wallets_and_address();
        let midnight_est_in_utc = datetime!(2023-01-01 05:00:00 UTC); // Midnight EST
        let psbt = generate_psbt(
            &alice_wallet,
            bob_address.address.script_pubkey(),
            &Money {
                amount: 2_00,
                currency_code: USD,
            },
        )
        .await;
        let settings = Settings {
            limit: SpendingLimit {
                amount: Money {
                    amount: 5_00,
                    currency_code: USD,
                },
                time_zone_offset: UtcOffset::from_hms(-5, 0, 0).unwrap(),
                ..Default::default()
            },
            ..Default::default()
        };

        let daily_limit_sats = sats_for(
            &ExchangeRateService::new(),
            LocalRateProvider::new(),
            &settings.limit.amount,
        )
        .await
        .unwrap();

        // These transactions don't count towards the limit since it's it's more than 25 hours old
        let transaction_history = generate_daily_spend_entries_for_tx_history(vec![
            generate_fake_transaction_details(
                &Money {
                    amount: 2_00,
                    currency_code: USD,
                },
                datetime!(2022-12-31 01:00:00 -5),
            )
            .await,
            generate_fake_transaction_details(
                &Money {
                    amount: 2_00,
                    currency_code: USD,
                },
                datetime!(2022-12-31 02:00:00 -5),
            )
            .await,
        ]);
        let features = Features {
            settings,
            daily_limit_sats,
            ..Default::default()
        };

        let spending_entries = transaction_history.iter().collect();
        let rule = DailySpendingLimitRule::new(
            &alice_wallet,
            &features,
            &spending_entries,
            midnight_est_in_utc,
        );
        assert!(rule.check_transaction(&psbt).is_ok());

        // These transactions fall into the window starting at 3 am so they'll count towards the limit
        let transaction_history = generate_daily_spend_entries_for_tx_history(vec![
            generate_fake_transaction_details(
                &Money {
                    amount: 2_00,
                    currency_code: USD,
                },
                datetime!(2022-12-31 03:01:00 -5),
            )
            .await,
            generate_fake_transaction_details(
                &Money {
                    amount: 2_00,
                    currency_code: USD,
                },
                datetime!(2022-12-31 03:01:00 -5),
            )
            .await,
        ]);

        let spending_entries = transaction_history.iter().collect();
        let rule = DailySpendingLimitRule::new(
            &alice_wallet,
            &features,
            &spending_entries,
            midnight_est_in_utc,
        );
        assert!(rule.check_transaction(&psbt).is_err());

        // These transactions fall into the window starting at 3 am so they'll count towards the limit
        let transaction_history = generate_daily_spend_entries_for_tx_history(vec![
            generate_fake_transaction_details(
                &Money {
                    amount: 2_00,
                    currency_code: USD,
                },
                datetime!(2022-12-31 02:59:00 -5),
            )
            .await,
            generate_fake_transaction_details(
                &Money {
                    amount: 3_00,
                    currency_code: USD,
                },
                datetime!(2022-12-31 03:01:00 -5),
            )
            .await,
        ]);

        let spending_entries = transaction_history.iter().collect();
        let rule = DailySpendingLimitRule::new(
            &alice_wallet,
            &features,
            &spending_entries,
            midnight_est_in_utc,
        );
        assert!(rule.check_transaction(&psbt).is_ok());
    }
}
