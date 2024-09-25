use database::{
    aws_sdk_dynamodb::error::ProvideErrorMetadata,
    ddb::{try_to_item, DatabaseError, Repository},
};
use tracing::{event, instrument, Level};

use super::ChainIndexerRepository;
use crate::entities::Block;

impl ChainIndexerRepository {
    #[instrument(skip(self))]
    pub async fn persist(&self, block: &Block) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let item = try_to_item(block, database_object)?;

        self.connection
            .client
            .put_item()
            .table_name(table_name)
            .set_item(Some(item))
            .condition_expression("attribute_not_exists(block_hash)")
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not persist account: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::PersistenceError(database_object)
            })?;

        Ok(())
    }
}
