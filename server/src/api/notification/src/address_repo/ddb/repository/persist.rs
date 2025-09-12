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
        types::{PutRequest, WriteRequest},
    },
    ddb::{try_to_item, DatabaseError, Repository},
};

use super::AddressRepository;

const DDB_CHUNK_SIZE_MAX: usize = 25;

impl AddressRepository {
    #[instrument(skip(self))]
    pub(crate) async fn persist_batch(
        &self,
        watched_addresses: Vec<WatchedAddress>,
    ) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        // Generate write requests for each watched address
        let ops: Vec<WriteRequest> = watched_addresses
            .iter()
            .map(|v| {
                Ok(WriteRequest::builder()
                    .set_put_request(Some(
                        PutRequest::builder()
                            .set_item(Some(try_to_item(v, database_object)?))
                            .build()?,
                    ))
                    .build())
            })
            .collect::<Result<Vec<WriteRequest>, DatabaseError>>()?;

        // Split the requests into chunks < DDB limitation
        let batches = ops
            .chunks(DDB_CHUNK_SIZE_MAX)
            .map(|chunk| persist_batch_chunk(&self.get_connection().client, &table_name, chunk));

        // Join on the futures propagating errors
        if let Err(err) = try_join_all(batches).await {
            let service_err = err.into_service_error();
            event!(
                Level::ERROR,
                "Could not persist WatchedAddress: {service_err:?} with message: {:?}",
                service_err.message()
            );
            return Err(DatabaseError::PersistenceError(self.get_database_object()));
        }

        Ok(())
    }
}

#[instrument]
async fn persist_batch_chunk(
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
