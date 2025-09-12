use std::collections::HashMap;

use futures::future::try_join_all;
use tracing::{event, instrument, Level};

use crate::address_repo::ddb::entities::WatchedAddress;
use database::aws_sdk_dynamodb::error::SdkError;
use database::aws_sdk_dynamodb::operation::batch_write_item::BatchWriteItemError;
use database::aws_sdk_dynamodb::Client;
use database::{
    aws_sdk_dynamodb::{
        error::ProvideErrorMetadata,
        types::{AttributeValue, DeleteRequest, WriteRequest},
    },
    ddb::{try_from_item, try_to_attribute_val, DatabaseError, Repository},
};
use types::account::identifiers::AccountId;

use super::{AddressRepository, ACCOUNT_ID_INDEX, ACCOUNT_ID_KEY, HASH_KEY};

const DDB_CHUNK_SIZE_MAX: usize = 25;

impl AddressRepository {
    /// Delete all addresses belonging to a specific account
    #[instrument(skip(self))]
    pub(crate) async fn delete_all_addresses_for_account(
        &self,
        account_id: &AccountId,
    ) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let addresses = self.query_addresses_for_account(account_id).await?;

        if addresses.is_empty() {
            return Ok(());
        }

        // Create delete requests for all addresses
        let delete_requests: Vec<WriteRequest> = addresses
            .iter()
            .map(|watched_address| {
                Ok(WriteRequest::builder()
                    .set_delete_request(Some(
                        DeleteRequest::builder()
                            .key(
                                HASH_KEY,
                                try_to_attribute_val(&watched_address.address, database_object)?,
                            )
                            .build()?,
                    ))
                    .build())
            })
            .collect::<Result<Vec<WriteRequest>, DatabaseError>>()?;

        // Split into batches and process
        let batches = delete_requests
            .chunks(DDB_CHUNK_SIZE_MAX)
            .map(|chunk| delete_batch_chunk(&self.get_connection().client, &table_name, chunk));

        // Execute all deletion batches
        if let Err(err) = try_join_all(batches).await {
            let service_err = err.into_service_error();
            event!(
                Level::ERROR,
                "Could not delete WatchedAddresses: {service_err:?} with message: {:?} for account: {account_id}",
                service_err.message()
            );
            return Err(DatabaseError::DeleteItemsError(database_object));
        }

        Ok(())
    }

    /// Query for all addresses belonging to a specific account using GSI
    #[instrument(skip(self))]
    async fn query_addresses_for_account(
        &self,
        account_id: &AccountId,
    ) -> Result<Vec<WatchedAddress>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let mut addresses = Vec::new();
        let mut last_evaluated_key: Option<HashMap<String, AttributeValue>> = None;

        loop {
            let mut query_builder = self
                .get_connection()
                .client
                .query()
                .table_name(&table_name)
                .index_name(ACCOUNT_ID_INDEX)
                .key_condition_expression("#account_id = :account_id")
                .expression_attribute_names("#account_id", ACCOUNT_ID_KEY)
                .expression_attribute_values(
                    ":account_id",
                    try_to_attribute_val(account_id, database_object)?,
                );

            query_builder = query_builder.set_exclusive_start_key(last_evaluated_key.take());

            let result = query_builder.send().await.map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not query addresses for account: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::FetchError(database_object)
            })?;

            let items = result.items();
            for item in items {
                let watched_address: WatchedAddress = try_from_item(item.clone(), database_object)?;
                addresses.push(watched_address);
            }

            last_evaluated_key = result.last_evaluated_key().cloned();
            if last_evaluated_key.is_none() {
                break;
            }
        }

        Ok(addresses)
    }
}

#[instrument]
async fn delete_batch_chunk(
    client: &Client,
    table_name: &str,
    ops: &[WriteRequest],
) -> Result<(), SdkError<BatchWriteItemError>> {
    let mut unprocessed = Some(HashMap::from([(table_name.to_string(), ops.to_vec())]));
    while unprocessed_count(unprocessed.as_ref(), table_name) > 0 {
        unprocessed = client
            .batch_write_item()
            .set_request_items(unprocessed)
            .send()
            .await?
            .unprocessed_items;
    }

    Ok(())
}

fn unprocessed_count(
    unprocessed: Option<&HashMap<String, Vec<WriteRequest>>>,
    table_name: &str,
) -> usize {
    unprocessed
        .map(|m| m.get(table_name).map(|v| v.len()).unwrap_or_default())
        .unwrap_or_default()
}
