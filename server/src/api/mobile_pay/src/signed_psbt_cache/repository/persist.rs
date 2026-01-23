use std::collections::HashMap;

use tracing::{event, instrument, Level};

use database::{
    aws_sdk_dynamodb::error::ProvideErrorMetadata,
    ddb::{try_to_attribute_val, try_to_item, DatabaseError, Repository},
};

use crate::signed_psbt_cache::entities::CachedPsbtTxid;
use crate::signed_psbt_cache::repository::PARTITION_KEY;

use super::PsbtTxidCacheRepository;

impl PsbtTxidCacheRepository {
    #[instrument(skip(self, cached_psbt))]
    pub async fn persist(&self, cached_psbt: &CachedPsbtTxid) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let item = try_to_item(cached_psbt, database_object)?;

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
                    "Could not persist CachedPsbt: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::PersistenceError(self.get_database_object())
            })?;
        Ok(())
    }

    #[instrument(skip(self))]
    pub async fn delete(&self, txid: bdk_utils::bdk::bitcoin::Txid) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let key = HashMap::from([(
            PARTITION_KEY.to_string(),
            try_to_attribute_val(txid, database_object)?,
        )]);

        self.connection
            .client
            .delete_item()
            .table_name(table_name)
            .set_key(Some(key))
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not delete CachedPsbtTxid: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::PersistenceError(self.get_database_object())
            })?;
        Ok(())
    }
}
