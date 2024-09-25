use tracing::{event, instrument, Level};

use database::{
    aws_sdk_dynamodb::error::ProvideErrorMetadata,
    ddb::{try_to_attribute_val, try_to_item, DatabaseError, Repository},
};

use crate::daily_spend_record::entities::DailySpendingRecord;

use super::DailySpendRecordRepository;

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
}
