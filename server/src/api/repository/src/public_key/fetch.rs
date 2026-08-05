use database::aws_sdk_dynamodb::types::AttributeValue;
use database::ddb::{try_from_item, DatabaseError, Repository};
use tracing::{event, Level};

use super::{PublicKeyRecord, PublicKeyRepository};

impl PublicKeyRepository {
    /// Fetch a public key record by its key value.
    ///
    /// Returns `Ok(Some(record))` if the key exists, `Ok(None)` if not found.
    pub async fn fetch_by_public_key(
        &self,
        public_key: &str,
    ) -> Result<Option<PublicKeyRecord>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let result = self
            .get_connection()
            .client
            .get_item()
            .table_name(table_name)
            .consistent_read(true)
            .key("public_key", AttributeValue::S(public_key.to_string()))
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Failed to fetch public key record: {service_err:?}"
                );
                DatabaseError::FetchError(database_object)
            })?;

        match result.item() {
            Some(item) => {
                let record: PublicKeyRecord =
                    try_from_item(item.clone(), database_object)?;
                Ok(Some(record))
            }
            None => Ok(None),
        }
    }
}
