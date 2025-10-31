use database::{
    aws_sdk_dynamodb::error::ProvideErrorMetadata,
    ddb::{try_to_item, DatabaseError, Repository},
};
use tracing::{event, instrument, Level};
use types::screener::ScreenerRow;

use super::ScreenerRepository;

impl ScreenerRepository {
    #[instrument(skip(self, row))]
    pub async fn persist(&self, row: &ScreenerRow) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let item = try_to_item(row, database_object)?;

        self.get_connection()
            .client
            .put_item()
            .table_name(table_name)
            .set_item(Some(item))
            .condition_expression("attribute_not_exists(created_at)")
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not persist screener row: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::PersistenceError(self.get_database_object())
            })?;
        Ok(())
    }
}
