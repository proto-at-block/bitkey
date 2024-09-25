use database::{
    aws_sdk_dynamodb::types::{PutRequest, WriteRequest},
    ddb::{try_to_item, DatabaseError, PersistBatchTrait, Repository},
};
use tracing::instrument;

use crate::entities::TransactionRecord;

use super::MempoolIndexerRepository;

impl MempoolIndexerRepository {
    #[instrument(skip(self))]
    pub(crate) async fn update_expiry(
        &self,
        records: Vec<TransactionRecord>,
    ) -> Result<Vec<TransactionRecord>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let updated_records = records
            .into_iter()
            .map(|v| v.update_expiry())
            .collect::<Vec<TransactionRecord>>();
        let ops = updated_records
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

        ops.persist(&self.connection.client, &table_name, database_object)
            .await?;
        Ok(updated_records)
    }
}
