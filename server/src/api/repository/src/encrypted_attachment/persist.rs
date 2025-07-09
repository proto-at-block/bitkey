use database::{
    aws_sdk_dynamodb::error::ProvideErrorMetadata,
    ddb::{try_to_attribute_val, try_to_item, DatabaseError, Repository},
};
use time::{format_description::well_known::Rfc3339, OffsetDateTime};
use tracing::{event, instrument, Level};
use types::encrypted_attachment::EncryptedAttachment;

use super::EncryptedAttachmentRepository;

impl EncryptedAttachmentRepository {
    #[instrument(skip(self, attachment))]
    pub async fn persist(&self, attachment: &EncryptedAttachment) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let mut updated_attachment = attachment.clone();
        updated_attachment.updated_at = OffsetDateTime::now_utc();

        let item = try_to_item(&updated_attachment, database_object)?;

        let formatted_original_updated_at =
            attachment.updated_at.format(&Rfc3339).map_err(|err| {
                event!(Level::ERROR, "Could not format updated_at: {:?}", err);
                DatabaseError::PersistenceError(self.get_database_object())
            })?;

        self.get_connection()
            .client
            .put_item()
            .table_name(table_name)
            .set_item(Some(item))
            .condition_expression("attribute_not_exists(updated_at) OR updated_at = :updated_at")
            .expression_attribute_values(
                ":updated_at",
                try_to_attribute_val(formatted_original_updated_at, database_object)?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not persist encrypted attachment: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::PersistenceError(self.get_database_object())
            })?;
        Ok(())
    }
}
