use serde::{Deserialize, Serialize};
use time::{Date, Duration, OffsetDateTime};
use tracing::event;

use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::bitcoin::Txid;
use bdk_utils::AttributableWallet;
use types::account::identifiers::AccountId;
use types::serde::{deserialize_ts, serialize_ts};

use crate::util::{get_total_outflow_for_psbt, MobilepayDatetimeError};

const RETENTION_DAYS: i64 = 30;

/// Uniquely defines a transaction in `DailySpendingRecord` so we can avoid updating a spending list with a transaction that is already accounted for
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct SpendingEntry {
    /// The transaction ID of the spend, used to deduplicate transactions in the database
    pub txid: Txid,
    /// The timestamp of the spend in UTC
    #[serde(serialize_with = "serialize_ts", deserialize_with = "deserialize_ts")]
    pub timestamp: OffsetDateTime,
    /// The total amount of money leaving the customers wallet, this should be the sum of outputs minus change
    pub outflow_amount: u64,
}

/// A record of the total amount spent (or at least cosigned by f8e) by an account on a given day.
/// This is used to enforce daily spending limits.
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct DailySpendingRecord {
    /// The account ID of the customer who owns these spends.
    /// Used as the partition key in the database
    pub account_id: AccountId,
    /// Version of the record. Used for optimistic locking. Not for schema versioning.
    pub(crate) version: u64,
    /// The UTC date for this record of spends.
    /// Used as the range key in the database
    pub date: Date,
    /// The unix epoch time in seconds at which this record will be deleted from the database
    #[serde(serialize_with = "serialize_ts", deserialize_with = "deserialize_ts")]
    pub expiring_at: OffsetDateTime,
    /// The transactions that were spent on this day
    spending_entries: Vec<SpendingEntry>,
}

impl DailySpendingRecord {
    pub fn try_new(account_id: &AccountId, date: Date) -> Result<Self, MobilepayDatetimeError> {
        Ok(Self {
            account_id: account_id.clone(),
            version: 0,
            date,
            expiring_at: date
                .checked_add(Duration::days(RETENTION_DAYS))
                .ok_or_else(|| {
                    MobilepayDatetimeError::DateMathError(format!("{date} + {RETENTION_DAYS}"))
                })?
                .midnight() // make all dates be at midnight for consistency in comparing them
                .assume_utc(), // expiring_at is used by the database to determine when to delete the record. we can ignore timezones here.
            spending_entries: vec![],
        })
    }

    pub fn update_with_psbt(&mut self, wallet: &dyn AttributableWallet, psbt: &Psbt) {
        let tx = &psbt.unsigned_tx;
        if !self
            .spending_entries
            .iter()
            .any(|entry| entry.txid == tx.txid())
        {
            self.spending_entries.push(SpendingEntry {
                txid: tx.txid(),
                timestamp: OffsetDateTime::now_utc(),
                outflow_amount: get_total_outflow_for_psbt(wallet, psbt),
            });
            if self.spending_entries.len() > 2000 {
                // NB: DDB max item size is 400kb
                // With the current schema, each [SpendingEntry] is 121 bytes. That means we can have up to
                // DailySpendRecord takes 138 bytes of data with no SpendingEntries. Each SpendingEntry takes
                // 121 bytes. That means that we can have ~3300 SpendingEntries in a DailySpendingRecord
                // We expect power users will have 10's of transactions a day, but to be sure we'll emit
                // an event if we see anyone at more than 2000 (~66% utilization) which would mean maybe
                // we need to look at a new scheme or make product changes
                event!(
                    tracing::Level::WARN,
                    "{}",
                    format!("DailySpendRecord for account {} and date {} has more than 2000 SpendingEntries", self.account_id.to_string(), self.date)
                )
            }
        } else {
            // we shouldn't really ever have a txid collision because there's a PSBT cache in front
            // of the service, so emitting an event if it happens incase we need to debug a failing cache
            event!(
                tracing::Level::WARN,
                "{}",
                format!(
                    "Transaction ID {} already in DailySpendingRecord, not putting it in again",
                    tx.txid()
                )
            );
        }
    }

    pub fn get_spending_entries(&self) -> &Vec<SpendingEntry> {
        &self.spending_entries
    }

    /// return a copy of self with an updated internal version number (for optimistic locking)
    pub fn clone_and_bump_version(&self) -> DailySpendingRecord {
        DailySpendingRecord {
            version: self.version + 1,
            ..self.clone()
        }
    }
}

#[cfg(test)]
mod tests {
    use std::ops::Sub;
    use std::str::FromStr;

    use time::{Date, Duration, OffsetDateTime};

    use bdk_utils::bdk::bitcoin::absolute::LockTime;
    use bdk_utils::bdk::bitcoin::psbt::Psbt;
    use bdk_utils::bdk::bitcoin::{Address, ScriptBuf, Transaction, TxOut};
    use bdk_utils::error::BdkUtilError;
    use bdk_utils::SpkWithDerivationPaths;
    use types::account::identifiers::AccountId;

    use crate::daily_spend_record::entities::{
        AttributableWallet, DailySpendingRecord, RETENTION_DAYS,
    };
    use crate::util::MobilepayDatetimeError;

    #[allow(dead_code)]
    struct DummyWallet {
        is_mine_scripts: Vec<ScriptBuf>,
    }

    impl DummyWallet {
        fn new(is_mine_scripts: Vec<ScriptBuf>) -> Self {
            Self { is_mine_scripts }
        }
    }

    impl AttributableWallet for DummyWallet {
        fn is_addressed_to_self(&self, _psbt: &Psbt) -> Result<bool, BdkUtilError> {
            Ok(true)
        }

        fn all_inputs_are_from_self(&self, _psbt: &Psbt) -> Result<bool, BdkUtilError> {
            Ok(true)
        }

        fn is_my_psbt_address(&self, _spk: &SpkWithDerivationPaths) -> Result<bool, BdkUtilError> {
            Ok(true)
        }
    }

    #[test]
    fn update_spending_record() {
        let dummy_transaction = Transaction {
            version: 0,
            lock_time: LockTime::ZERO,
            input: Vec::new(),
            output: Vec::new(),
        };
        let psbt = Psbt::from_unsigned_tx(dummy_transaction).unwrap();
        let dummy_wallet = DummyWallet::new(vec![]);

        let account_id = AccountId::gen().unwrap();
        let mut spending_record =
            DailySpendingRecord::try_new(&account_id, OffsetDateTime::now_utc().date()).unwrap();

        spending_record.update_with_psbt(&dummy_wallet, &psbt);

        assert_eq!(spending_record.spending_entries.len(), 1)
    }

    #[test]
    fn reject_record_with_date_past_retention() {
        let date_past_retention = Date::MAX.sub(Duration::days(RETENTION_DAYS - 1));
        let account_id = AccountId::gen().unwrap();
        let result = DailySpendingRecord::try_new(&account_id, date_past_retention);

        match result {
            Err(MobilepayDatetimeError::DateMathError(_)) => (),
            _ => panic!("DailySpendRecord should not be created."),
        }
    }

    #[test]
    fn do_not_update_spending_entries_with_duplicate_txid() {
        let output_script = Address::from_str("bc1qvh30c5k24q4z2h6e88tvsv7x3xyj7m4g37e498")
            .unwrap()
            .assume_checked()
            .script_pubkey();
        let transaction = Transaction {
            version: 0,
            lock_time: LockTime::ZERO,
            input: Vec::new(),
            output: vec![TxOut {
                value: 860000,
                script_pubkey: output_script.clone(),
            }],
        };
        let psbt = Psbt::from_unsigned_tx(transaction).unwrap();
        let dummy_wallet = DummyWallet::new(vec![output_script]);

        let account_id = AccountId::gen().unwrap();
        let mut spending_record =
            DailySpendingRecord::try_new(&account_id, OffsetDateTime::now_utc().date()).unwrap();

        spending_record.update_with_psbt(&dummy_wallet, &psbt);

        // We should not be pushing this to the spending_entries array again since there will be a
        // txid clash.
        spending_record.update_with_psbt(&dummy_wallet, &psbt);

        assert_eq!(spending_record.spending_entries.len(), 1)
    }
}
