use tracing::{event, instrument, Level};

use database::{
    aws_sdk_dynamodb::error::ProvideErrorMetadata,
    ddb::{try_to_attribute_val, try_to_item, DatabaseError, Repository},
};
use time::format_description::well_known::Rfc3339;
use types::transaction_verification::entities::TransactionVerification;

use super::TransactionVerificationRepository;

impl TransactionVerificationRepository {
    #[instrument(skip(self))]
    pub async fn persist(
        &self,
        tx_verification: &TransactionVerification,
    ) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let item = try_to_item(tx_verification, database_object)?;
        let formatted_updated_at = tx_verification
            .common_fields()
            .updated_at
            .format(&Rfc3339)
            .map_err(|err| {
                event!(Level::ERROR, "Could not format updated_at: {:?}", err);
                DatabaseError::PersistenceError(self.get_database_object())
            })?;

        self.connection
            .client
            .put_item()
            .table_name(table_name)
            .set_item(Some(item))
            .condition_expression("attribute_not_exists(updated_at) OR updated_at = :updated_at")
            .expression_attribute_values(
                ":updated_at",
                try_to_attribute_val(formatted_updated_at, database_object)?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not persist transaction verification: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::PersistenceError(database_object)
            })?;

        Ok(())
    }
}
