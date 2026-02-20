use super::Rule;
use crate::daily_spend_record::entities::SpendingEntry;
use crate::entities::Features;
use crate::metrics;
use crate::spend_rules::errors::SpendRuleCheckError;
use crate::util::total_sats_spent_today;
use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::Wallet;
use bdk_utils::{
    get_total_outflow_for_psbt, ChaincodeDelegationCollaboratorWallet, ChaincodeDelegationPsbt,
};
use time::OffsetDateTime;
use types::account::spending::PrivateMultiSigSpendingKeyset;

pub(crate) struct DailySpendingLimitRule<'a> {
    wallet: &'a Wallet,
    features: &'a Features,
    spending_history: &'a Vec<&'a SpendingEntry>,
    now_utc: OffsetDateTime,
}

impl<'a> DailySpendingLimitRule<'a> {
    pub fn new(
        wallet: &'a Wallet,
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

impl Rule for DailySpendingLimitRule<'_> {
    /// Ensure that the total outflows for this PSBT plus the outflows so far today do not exceed
    /// the set spending limit
    fn check_transaction(&self, psbt: &Psbt) -> Result<(), SpendRuleCheckError> {
        if !self.features.settings.limit.active {
            return Err(SpendRuleCheckError::SpendLimitInactive);
        }

        let total_spent = total_sats_spent_today(
            self.spending_history,
            &self.features.settings.limit,
            self.now_utc,
        )
        .map_err(|err| SpendRuleCheckError::CouldNotFetchSpendAmount(err.to_string()))?;

        let total_spend_for_unsigned_transaction_sats =
            get_total_outflow_for_psbt(self.wallet, psbt);
        if self.features.daily_limit_sats >= total_spend_for_unsigned_transaction_sats + total_spent
        {
            Ok(())
        } else {
            metrics::MOBILE_PAY_COSIGN_OVERFLOW.add(1, &[]);
            Err(SpendRuleCheckError::SpendLimitExceeded(
                total_spend_for_unsigned_transaction_sats,
                total_spent,
                self.features.daily_limit_sats,
            ))
        }
    }
}

pub(crate) struct DailySpendingLimitRuleV2<'a> {
    features: &'a Features,
    private_keyset: &'a PrivateMultiSigSpendingKeyset,
    spending_history: &'a Vec<&'a SpendingEntry>,
    now_utc: OffsetDateTime,
}

impl Rule for DailySpendingLimitRuleV2<'_> {
    fn check_transaction(&self, psbt: &Psbt) -> Result<(), SpendRuleCheckError> {
        if !self.features.settings.limit.active {
            return Err(SpendRuleCheckError::SpendLimitInactive);
        }

        let total_spent = total_sats_spent_today(
            self.spending_history,
            &self.features.settings.limit,
            self.now_utc,
        )
        .map_err(|err| SpendRuleCheckError::CouldNotFetchSpendAmount(err.to_string()))?;

        let chaincode_delegation_psbt = ChaincodeDelegationPsbt::new(
            psbt,
            vec![
                self.private_keyset.server_pub,
                self.private_keyset.app_pub,
                self.private_keyset.hardware_pub,
            ],
        )
        .map_err(|err| SpendRuleCheckError::InvalidChaincodeDelegationPsbt(err.to_string()))?;

        let delegator_wallet = ChaincodeDelegationCollaboratorWallet::new(
            self.private_keyset.server_pub,
            self.private_keyset.app_pub,
            self.private_keyset.hardware_pub,
        );

        let total_spend_for_unsigned_transaction_sats = delegator_wallet
            .get_outflow_for_psbt(&chaincode_delegation_psbt)
            .map_err(|err| SpendRuleCheckError::CouldNotFetchSpendAmount(err.to_string()))?;

        if self.features.daily_limit_sats >= total_spend_for_unsigned_transaction_sats + total_spent
        {
            Ok(())
        } else {
            metrics::MOBILE_PAY_COSIGN_OVERFLOW.add(1, &[]);
            Err(SpendRuleCheckError::SpendLimitExceeded(
                total_spend_for_unsigned_transaction_sats,
                total_spent,
                self.features.daily_limit_sats,
            ))
        }
    }
}

impl<'a> DailySpendingLimitRuleV2<'a> {
    pub fn new(
        features: &'a Features,
        private_keyset: &'a PrivateMultiSigSpendingKeyset,
        spending_history: &'a Vec<&'a SpendingEntry>,
        now_utc: OffsetDateTime,
    ) -> Self {
        DailySpendingLimitRuleV2 {
            features,
            private_keyset,
            spending_history,
            now_utc,
        }
    }
}

#[cfg(test)]
mod tests {
    use bdk_utils::bdk::bitcoin::consensus::deserialize;
    use bdk_utils::bdk::bitcoin::psbt::Psbt;
    use bdk_utils::bdk::bitcoin::{Amount, FeeRate, ScriptBuf, Txid};
    use bdk_utils::bdk::{AddressInfo, KeychainKind, Wallet};
    use exchange_rate::currency_conversion::sats_for;
    use exchange_rate::service::Service as ExchangeRateService;
    use time::macros::datetime;
    use time::{Duration, OffsetDateTime, UtcOffset};
    use types::account::money::Money;
    use types::account::spend_limit::SpendingLimit;
    use types::currencies::CurrencyCode::USD;
    use types::exchange_rate::local_rate_provider::LocalRateProvider;

    use crate::daily_spend_record::entities::SpendingEntry;
    use crate::entities::{Features, Settings};
    use crate::spend_rules::daily_spend_limit_rule::DailySpendingLimitRule;
    use crate::spend_rules::test::get_funded_wallet;
    use crate::spend_rules::Rule;

    struct TransactionDetails {
        txid: Txid,
        confirmation_time: u64,
        sent: u64,
        received: u64,
    }

    fn generate_test_wallets_and_address() -> (Wallet, AddressInfo) {
        let source_wallet = get_funded_wallet(0);
        let mut destination_wallet = get_funded_wallet(1);
        let destination_address = destination_wallet.next_unused_address(KeychainKind::External);
        (source_wallet, destination_address)
    }

    async fn generate_psbt(source_wallet: &mut Wallet, recipient: ScriptBuf, m: &Money) -> Psbt {
        let spend_sat = sats_for(&ExchangeRateService::new(), LocalRateProvider::new(), m)
            .await
            .unwrap();
        let mut builder = source_wallet.build_tx();
        builder
            .add_recipient(recipient, Amount::from_sat(spend_sat))
            .fee_rate(FeeRate::from_sat_per_vb_unchecked(5));
        builder.finish().unwrap()
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
            received: 0,
            sent,
            confirmation_time: timestamp.unix_timestamp() as u64,
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
                timestamp: OffsetDateTime::from_unix_timestamp(tx.confirmation_time as i64)
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
        let (mut alice_wallet, bob_address) = generate_test_wallets_and_address();
        let psbt = generate_psbt(
            &mut alice_wallet,
            bob_address.address.script_pubkey(),
            &Money {
                amount: 2_00,
                currency_code: USD,
            },
        )
        .await;
        let settings = Settings {
            limit: SpendingLimit {
                active: true,
                amount: Money {
                    amount: 50_00, // More than what's spent
                    currency_code: USD,
                },
                ..Default::default()
            },
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
        let (mut alice_wallet, bob_address) = generate_test_wallets_and_address();
        let psbt = generate_psbt(
            &mut alice_wallet,
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
        let (mut alice_wallet, bob_address) = generate_test_wallets_and_address();
        let midnight_utc = datetime!(2023-03-01 00:00:00 UTC); // Midnight EST
        let psbt = generate_psbt(
            &mut alice_wallet,
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
        };

        let spending_entries = transaction_history.iter().collect();

        let rule =
            DailySpendingLimitRule::new(&alice_wallet, &features, &spending_entries, midnight_utc);
        assert!(rule.check_transaction(&psbt).is_err());
    }

    #[tokio::test]
    async fn daily_spend_limit_rule_for_timezone() {
        let (mut alice_wallet, bob_address) = generate_test_wallets_and_address();
        let midnight_est_in_utc = datetime!(2023-01-01 05:00:00 UTC); // Midnight EST
        let psbt = generate_psbt(
            &mut alice_wallet,
            bob_address.address.script_pubkey(),
            &Money {
                amount: 2_00,
                currency_code: USD,
            },
        )
        .await;
        let settings = Settings {
            limit: SpendingLimit {
                active: true,
                amount: Money {
                    amount: 5_00,
                    currency_code: USD,
                },
                time_zone_offset: UtcOffset::from_hms(-5, 0, 0).unwrap(),
            },
        };

        let daily_limit_sats = sats_for(
            &ExchangeRateService::new(),
            LocalRateProvider::new(),
            &settings.limit.amount,
        )
        .await
        .unwrap();

        // These transactions don't count towards the limit since it's more than 25 hours old
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
