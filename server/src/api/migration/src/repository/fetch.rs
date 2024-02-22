use tracing::{event, instrument, Level};

use crate::entities::MigrationRecord;
use crate::repository::PARTITION_KEY;
use database::aws_sdk_dynamodb::error::ProvideErrorMetadata;
use database::ddb::{try_from_item, try_to_attribute_val, DDBService, DatabaseError};

use super::Repository;

impl Repository {
    #[instrument(skip(self))]
    pub(crate) async fn fetch(
        &self,
        service_identifier: &str,
    ) -> Result<MigrationRecord, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let item_output = self
            .connection
            .client
            .get_item()
            .table_name(table_name)
            .key(
                PARTITION_KEY,
                try_to_attribute_val(service_identifier, database_object)?,
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
                Level::INFO,
                "migration record for service {service_identifier} not found in the database"
            );
            DatabaseError::ObjectNotFound(database_object)
        })?;
        try_from_item(item, database_object)
    }
}
