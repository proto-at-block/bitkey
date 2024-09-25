use time::Date;
use tracing::{event, instrument, Level};

use crate::daily_spend_record::entities::DailySpendingRecord;
use crate::daily_spend_record::repository::{PARTITION_KEY, SORT_KEY};
use database::aws_sdk_dynamodb::error::ProvideErrorMetadata;
use database::ddb::{try_from_item, try_to_attribute_val, DatabaseError, Repository};
use types::account::identifiers::AccountId;

use super::DailySpendRecordRepository;

impl DailySpendRecordRepository {
    #[instrument(skip(self))]
    pub(crate) async fn fetch(
        &self,
        id: &AccountId,
        date: Date,
    ) -> Result<DailySpendingRecord, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let item_output = self
            .connection
            .client
            .get_item()
            .table_name(table_name)
            .key(PARTITION_KEY, try_to_attribute_val(id, database_object)?)
            .key(
                SORT_KEY,
                try_to_attribute_val(date.to_string(), database_object)?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not query database: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::FetchError(database_object)
            })?;

        let item = item_output.item.ok_or_else(|| {
            event!(
                Level::WARN,
                "daily spending record for account {id} and date {date} not found in the database"
            );
            DatabaseError::ObjectNotFound(database_object)
        })?;
        try_from_item(item, database_object)
    }
}
