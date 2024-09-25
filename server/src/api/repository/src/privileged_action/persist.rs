use database::{
    aws_sdk_dynamodb::error::ProvideErrorMetadata,
    ddb::{try_to_attribute_val, try_to_item, DatabaseError, Repository},
};
use serde::Serialize;
use time::{format_description::well_known::Rfc3339, OffsetDateTime};
use tracing::{event, instrument, Level};
use types::privileged_action::repository::PrivilegedActionInstanceRecord;

use super::PrivilegedActionRepository;

impl PrivilegedActionRepository {
    #[instrument(skip(self, instance))]
    pub async fn persist<T>(
        &self,
        instance: &PrivilegedActionInstanceRecord<T>,
    ) -> Result<(), DatabaseError>
    where
        T: Serialize + Clone,
    {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let updated_instance = PrivilegedActionInstanceRecord {
            updated_at: OffsetDateTime::now_utc(),
            ..instance.to_owned()
        };
        let item = try_to_item(updated_instance, database_object)?;

        let formatted_updated_at = instance.updated_at.format(&Rfc3339).map_err(|err| {
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
                    "Could not persist privileged action instance: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::PersistenceError(self.get_database_object())
            })?;
        Ok(())
    }
}
