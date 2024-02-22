use database::{
    aws_sdk_dynamodb::error::ProvideErrorMetadata,
    ddb::{try_to_attribute_val, try_to_item, DDBService, DatabaseError},
};
use time::{format_description::well_known::Rfc3339, OffsetDateTime};
use tracing::{event, instrument, Level};

use crate::entities::{Account, CommonAccountFields};

use super::Repository;

impl Repository {
    #[instrument(skip(self, account))]
    pub async fn persist(&self, account: &Account) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let common_fields = account.get_common_fields();
        let updated_account = account
            .update(CommonAccountFields {
                updated_at: OffsetDateTime::now_utc(),
                ..common_fields.to_owned()
            })
            .map_err(|_| DatabaseError::UpdateError(database_object))?;
        let item = try_to_item(updated_account, database_object)?;

        let formatted_updated_at = common_fields.updated_at.format(&Rfc3339).map_err(|err| {
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
                    "Could not persist account: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::PersistenceError(self.get_database_object())
            })?;
        Ok(())
    }
}
