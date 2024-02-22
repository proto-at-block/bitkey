pub mod migrations;

use time::Date;
use tracing::{event, instrument};

use database::ddb::DatabaseError;
use errors::ApiError;
use types::account::identifiers::AccountId;

use crate::daily_spend_record::entities::DailySpendingRecord;
use crate::daily_spend_record::repository::Repository;

#[derive(Clone)]
pub struct Service {
    repo: Repository,
}

impl Service {
    pub fn new(repo: Repository) -> Self {
        Self { repo }
    }

    #[instrument(skip(self))]
    pub async fn fetch_or_create_daily_spending_record(
        &self,
        account_id: &AccountId,
        date: Date,
    ) -> Result<DailySpendingRecord, ApiError> {
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
    ) -> Result<(), ApiError> {
        Ok(self.repo.persist(record).await?)
    }
}
