use bdk_utils::bdk::bitcoin::{BlockHash, Network};
use database::ddb::{try_from_item, try_to_attribute_val, DDBService, DatabaseError};
use tracing::{event, instrument, Level};

use super::Repository;
use crate::{
    entities::Block,
    repository::{NETWORK_HEIGHT_INDEX, NETWORK_HEIGHT_PARTITION_KEY, PARTITION_KEY},
};

impl Repository {
    #[instrument(skip(self))]
    pub(crate) async fn fetch(&self, hash: BlockHash) -> Result<Option<Block>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        self.connection
            .client
            .get_item()
            .table_name(table_name)
            .key(PARTITION_KEY, try_to_attribute_val(hash, database_object)?)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(Level::ERROR, "Could not fetch block: {service_err:?}",);
                DatabaseError::FetchError(database_object)
            })?
            .item
            .map(|block| try_from_item(block, database_object))
            .transpose()
    }

    #[instrument(skip(self))]
    pub(crate) async fn fetch_init_block(
        &self,
        network: Network,
    ) -> Result<Option<Block>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        self.connection
            .client
            .query()
            .table_name(table_name)
            .index_name(NETWORK_HEIGHT_INDEX)
            .key_condition_expression(format!("{NETWORK_HEIGHT_PARTITION_KEY} = :val"))
            .expression_attribute_values(":val", try_to_attribute_val(network, database_object)?)
            .limit(1)
            .scan_index_forward(true)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not fetch latest block: {service_err:?}",
                );
                DatabaseError::FetchError(database_object)
            })?
            .items
            .and_then(|blocks| blocks.into_iter().next())
            .map(|block| try_from_item(block, database_object))
            .transpose()
    }
}
