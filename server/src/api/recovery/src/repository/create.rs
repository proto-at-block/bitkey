use database::{
    aws_sdk_dynamodb::error::ProvideErrorMetadata,
    ddb::{try_to_item, DDBService, DatabaseError},
};
use tracing::{event, Level};

use crate::entities::WalletRecovery;

use super::Repository;

impl Repository {
    pub async fn create(&self, recovery: &WalletRecovery) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let item = try_to_item(recovery, database_object)?;

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
                    "Could not persist recovery: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::PersistenceError(database_object)
            })?;
        event!(Level::INFO, "Persisted recovery");
        Ok(())
    }
}
