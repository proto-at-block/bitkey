use database::{
    aws_sdk_dynamodb::types::{PutRequest, WriteRequest},
    ddb::{try_to_attribute_val, try_to_item, DatabaseError, PersistBatchTrait, Repository},
};
use time::format_description::well_known::Rfc3339;
use time::OffsetDateTime;
use tracing::{event, Level};
use types::account::identifiers::AccountId;

use super::{KeyType, PublicKeyRecord, PublicKeyRepository};

impl PublicKeyRepository {
    /// Persist a public key record with conditional uniqueness enforcement.
    ///
    /// Uses a compound condition expression:
    ///   `attribute_not_exists(public_key) OR account_id = :account_id`
    /// This succeeds if the key is new (insert) or already belongs to the same account (idempotent).
    /// It fails only if the key exists for a different account.
    ///
    /// Returns `Ok(true)` if the entry was inserted or already exists for the same account.
    /// Returns `Ok(false)` if the key already exists for a different account (conflict).
    pub async fn persist_public_key(
        &self,
        public_key: &str,
        account_id: &AccountId,
        key_type: KeyType,
    ) -> Result<bool, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let created_at = OffsetDateTime::now_utc().format(&Rfc3339).map_err(|err| {
            event!(Level::ERROR, "Could not format created_at: {:?}", err);
            DatabaseError::PersistenceError(database_object)
        })?;

        let record = PublicKeyRecord {
            public_key: public_key.to_string(),
            account_id: account_id.clone(),
            key_type,
            created_at,
        };

        let item = try_to_item(record, database_object)?;

        let result = self
            .get_connection()
            .client
            .put_item()
            .table_name(table_name)
            .set_item(Some(item))
            .condition_expression("attribute_not_exists(public_key) OR account_id = :account_id")
            .expression_attribute_values(
                ":account_id",
                try_to_attribute_val(account_id, database_object)?,
            )
            .send()
            .await;

        match result {
            Ok(_) => Ok(true),
            Err(err) => {
                let service_err = err.into_service_error();
                if service_err.is_conditional_check_failed_exception() {
                    // Key exists for a different account
                    Ok(false)
                } else {
                    event!(
                        Level::ERROR,
                        "Failed to persist public key entry: {service_err:?}"
                    );
                    Err(DatabaseError::PersistenceError(database_object))
                }
            }
        }
    }

    /// Batch persist public key records without conditional expressions.
    ///
    /// Used for backfill migrations where we want unconditional puts
    /// (first writer wins for duplicates). Handles chunking into batches
    /// of 25 and retries unprocessed items.
    pub async fn batch_persist_public_keys(
        &self,
        records: &[PublicKeyRecord],
    ) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let ops: Vec<WriteRequest> = records
            .iter()
            .map(|record| {
                Ok(WriteRequest::builder()
                    .set_put_request(Some(
                        PutRequest::builder()
                            .set_item(Some(try_to_item(record, database_object)?))
                            .build()?,
                    ))
                    .build())
            })
            .collect::<Result<Vec<WriteRequest>, DatabaseError>>()?;

        ops.persist(&self.get_connection().client, &table_name, database_object)
            .await
    }
}
