use tracing::{event, instrument, Level};

use bdk_utils::bdk::bitcoin::Txid;
use database::aws_sdk_dynamodb::error::ProvideErrorMetadata;
use database::ddb::{try_from_item, try_to_attribute_val, DDBService, DatabaseError};

use crate::signed_psbt_cache::entities::{CachedPsbt, NonBase64CachedPsbt};
use crate::signed_psbt_cache::repository::PARTITION_KEY;

use super::Repository;

impl Repository {
    #[instrument(skip(self))]
    pub(crate) async fn fetch(&self, txid: Txid) -> Result<CachedPsbt, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let item_output = self
            .connection
            .client
            .get_item()
            .table_name(table_name)
            .key(PARTITION_KEY, try_to_attribute_val(txid, database_object)?)
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
                "cached psbt for txid {txid} not found in the database"
            );
            DatabaseError::ObjectNotFound(database_object)
        })?;
        match try_from_item(item.clone(), database_object) {
            Ok(cached_psbt) => Ok(cached_psbt),
            Err(DatabaseError::DeserializationError(_, _)) => {
                // Attempt to deserialize as non base64 encoded cached psbt
                let non_base64_cached_psbt: Result<NonBase64CachedPsbt, DatabaseError> =
                    try_from_item(item, database_object);
                non_base64_cached_psbt.map(Into::into)
            }
            Err(err) => Err(err),
        }
    }
}
