use tracing::{event, instrument, Level};

use crate::entities::MigrationRecord;
use database::{
    aws_sdk_dynamodb::error::ProvideErrorMetadata,
    ddb::{try_to_item, DDBService, DatabaseError},
};

use super::Repository;

impl Repository {
    #[instrument(skip(self))]
    pub async fn persist(&self, migration_record: &MigrationRecord) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let item = try_to_item(migration_record, database_object)?;

        self.connection
            .client
            .put_item()
            .table_name(table_name)
            .set_item(Some(item))
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not persist MigrationRecord: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::PersistenceError(self.get_database_object())
            })?;
        Ok(())
    }
}
