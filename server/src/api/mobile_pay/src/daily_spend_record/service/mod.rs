pub mod migrations;

use time::Date;
use tracing::{event, instrument};

use bdk_utils::bdk::bitcoin::Txid;
use database::ddb::DatabaseError;
use types::account::identifiers::AccountId;

use crate::daily_spend_record::entities::DailySpendingRecord;
use crate::daily_spend_record::repository::DailySpendRecordRepository;
use crate::error::SigningError;

#[derive(Clone)]
pub struct Service {
    repo: DailySpendRecordRepository,
}

impl Service {
    pub fn new(repo: DailySpendRecordRepository) -> Self {
        Self { repo }
    }

    #[instrument(skip(self))]
    pub async fn fetch_or_create_daily_spending_record(
        &self,
        account_id: &AccountId,
        date: Date,
    ) -> Result<DailySpendingRecord, SigningError> {
        match self.repo.fetch(account_id, date).await {
            Ok(record) => Ok(record),
            Err(err) => match err {
                DatabaseError::ObjectNotFound(_) => {
                    event!(
                        tracing::Level::INFO,
                        "daily spending record not found, creating one"
                    );
                    let record = DailySpendingRecord::try_new(account_id, date)?;
                    Ok(self.repo.persist_and_return(record.clone()).await?)
                }
                _ => Err(err.into()),
            },
        }
    }

    #[instrument(err, skip(self))]
    pub async fn save_daily_spending_record(
        &self,
        record: DailySpendingRecord,
    ) -> Result<(), SigningError> {
        Ok(self.repo.persist(record).await?)
    }

    /// Remove a spending entry by txid, with retry on version conflicts.
    #[instrument(err, skip(self))]
    pub async fn remove_spending_entry(
        &self,
        account_id: &AccountId,
        date: Date,
        txid: &Txid,
    ) -> Result<(), SigningError> {
        Ok(self
            .repo
            .remove_spending_entry(account_id, date, txid)
            .await?)
    }
}
