use time::Date;
use tracing::{event, instrument, Level};

use bdk_utils::bdk::bitcoin::Txid;
use database::{
    aws_sdk_dynamodb::error::ProvideErrorMetadata,
    ddb::{try_to_attribute_val, try_to_item, DatabaseError, Repository},
};
use types::account::identifiers::AccountId;

use crate::daily_spend_record::entities::DailySpendingRecord;

use super::DailySpendRecordRepository;

const MAX_ROLLBACK_RETRIES: usize = 3;

impl DailySpendRecordRepository {
    /// Persist the DailySpendingRecord to the repository. Consumes the record and returns () if OK.
    #[instrument(skip(self, spending_record))]
    pub(crate) async fn persist(
        &self,
        spending_record: DailySpendingRecord,
    ) -> Result<(), DatabaseError> {
        self.persist_and_return(spending_record).await?;
        Ok(())
    }

    /// Persist the DailySpendingRecord to the repository. Consumes the record and returns the record with
    /// updated internal state. Useful when doing a create-and-insert operation.
    pub(crate) async fn persist_and_return(
        &self,
        spending_record: DailySpendingRecord,
    ) -> Result<DailySpendingRecord, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let curr_version = spending_record.version;
        let updated_spending_record = spending_record.clone_and_bump_version();
        let item = try_to_item(updated_spending_record.clone(), database_object)?;

        self.connection
            .client
            .put_item()
            .table_name(table_name)
            .set_item(Some(item))
            .condition_expression("attribute_not_exists(version) or version = :curr_version")
            .expression_attribute_values(
                ":curr_version",
                try_to_attribute_val(curr_version, database_object)?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not persist DailySpendingRecord: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::PersistenceError(self.get_database_object())
            })?;
        Ok(updated_spending_record)
    }

    /// Remove a spending entry by txid with retry on version conflict.
    /// Fetches the current record, removes the entry, and persists.
    #[instrument(skip(self))]
    pub(crate) async fn remove_spending_entry(
        &self,
        account_id: &AccountId,
        date: Date,
        txid: &Txid,
    ) -> Result<(), DatabaseError> {
        for attempt in 0..MAX_ROLLBACK_RETRIES {
            let mut record = self.fetch(account_id, date).await?;

            if !record.remove_spending_entry(txid) {
                // Entry not found, nothing to rollback
                return Ok(());
            }

            match self.persist(record).await {
                Ok(()) => return Ok(()),
                Err(DatabaseError::PersistenceError(_)) if attempt < MAX_ROLLBACK_RETRIES - 1 => {
                    event!(
                        Level::WARN,
                        ?txid,
                        attempt,
                        "version conflict during spending entry rollback, retrying"
                    );
                    continue;
                }
                Err(e) => return Err(e),
            }
        }
        Err(DatabaseError::PersistenceError(self.get_database_object()))
    }
}
