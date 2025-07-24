use database::{
    aws_sdk_dynamodb::{error::ProvideErrorMetadata, types::AttributeValue},
    ddb::{try_from_item, DatabaseError, Repository},
};
use tracing::{event, instrument, Level};
use types::encrypted_attachment::{identifiers::EncryptedAttachmentId, EncryptedAttachment};

use super::{EncryptedAttachmentRepository, PARTITION_KEY};

impl EncryptedAttachmentRepository {
    #[instrument(skip(self))]
    pub async fn fetch_by_id(
        &self,
        id: &EncryptedAttachmentId,
    ) -> Result<EncryptedAttachment, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let item_output = self
            .get_connection()
            .client
            .get_item()
            .consistent_read(true)
            .table_name(table_name)
            .key(
                PARTITION_KEY,
                AttributeValue::S(id.to_string()),
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not fetch encrypted attachment for id: {id} with err: {service_err:?} and message: {:?}",
                    service_err.message(),
                );
                DatabaseError::FetchError(database_object)
            })?;

        let item = item_output.item.ok_or_else(|| {
            event!(
                Level::WARN,
                "Encrypted attachment {id} not found in the database"
            );
            DatabaseError::ObjectNotFound(database_object)
        })?;

        try_from_item(item, database_object)
    }
}
