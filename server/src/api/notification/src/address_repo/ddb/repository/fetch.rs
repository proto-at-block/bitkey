use std::collections::HashMap;

use crate::address_repo::ddb::entities::WatchedAddress;
use crate::address_repo::ddb::repository::{AddressRepository, HASH_KEY};
use bdk_utils::bdk::bitcoin::address::NetworkUnchecked;
use bdk_utils::bdk::bitcoin::Address;
use database::aws_sdk_dynamodb::error::ProvideErrorMetadata;
use database::aws_sdk_dynamodb::types::{AttributeValue, KeysAndAttributes};
use database::aws_sdk_dynamodb::Client;
use database::ddb::{
    try_from_item, try_to_attribute_val, DatabaseError, DatabaseObject, Repository,
};
use futures::future::try_join_all;
use tracing::{event, instrument, Level};

const DDB_BATCH_READ_SIZE_MAX: usize = 100;

impl AddressRepository {
    #[instrument(skip(self))]
    pub(crate) async fn fetch_batch(
        &self,
        addresses: &[Address<NetworkUnchecked>],
    ) -> Result<Vec<WatchedAddress>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let reads = addresses
            .iter()
            .map(|v| {
                Ok((
                    HASH_KEY.to_string(),
                    try_to_attribute_val(v, database_object)?,
                ))
            })
            .collect::<Result<Vec<(String, AttributeValue)>, DatabaseError>>()?;

        let batches = reads.chunks(DDB_BATCH_READ_SIZE_MAX).map(|chunk| {
            fetch_batch_chunk(
                &self.get_connection().client,
                self.get_database_object(),
                &table_name,
                chunk,
            )
        });

        Ok(try_join_all(batches)
            .await?
            .into_iter()
            .flatten()
            .collect::<Vec<WatchedAddress>>())
    }
}

#[instrument]
async fn fetch_batch_chunk(
    client: &Client,
    database_object: DatabaseObject,
    table_name: &str,
    keys_and_attributes_slice: &[(String, AttributeValue)],
) -> Result<Vec<WatchedAddress>, DatabaseError> {
    let ka: Vec<HashMap<String, AttributeValue>> = keys_and_attributes_slice
        .iter()
        .map(|(k, v)| HashMap::from([(k.clone(), v.clone())]))
        .collect();

    let mut addresses: Vec<WatchedAddress> = Vec::new();
    let keys_and_attributes = KeysAndAttributes::builder().set_keys(Some(ka)).build()?;

    let mut unprocessed_opt = Some(HashMap::from([(
        table_name.to_string(),
        keys_and_attributes,
    )]));

    // On completion, unprocessed_keys is Some({}). Use a filter to break out of the loop.
    while let Some(unprocessed) = unprocessed_opt.filter(|m| !m.is_empty()) {
        let result = client
            .batch_get_item()
            .set_request_items(Some(unprocessed))
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

        let response_addresses = result
            .responses()
            .and_then(|tables| {
                tables.get(table_name).map(|rows| {
                    rows.iter()
                        .map(|item| try_from_item(item.clone(), database_object))
                })
            })
            .into_iter()
            .flatten()
            .collect::<Result<Vec<WatchedAddress>, DatabaseError>>()?;

        addresses.extend(response_addresses);
        unprocessed_opt = result.unprocessed_keys().cloned();
    }

    Ok(addresses)
}
