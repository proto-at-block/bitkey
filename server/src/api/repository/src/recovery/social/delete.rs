use database::{
    aws_sdk_dynamodb::error::ProvideErrorMetadata,
    ddb::{try_to_attribute_val, DDBService, DatabaseError},
};
use time::format_description::well_known::Rfc3339;
use tracing::{event, instrument, Level};
use types::recovery::social::relationship::RecoveryRelationship;

use super::{Repository, PARTITION_KEY};

impl Repository {
    #[instrument(skip(self, relationship))]
    pub async fn delete_recovery_relationship(
        &self,
        relationship: &RecoveryRelationship,
    ) -> Result<(), DatabaseError> {
        let database_object = self.get_database_object();

        let condition_value = relationship
            .common_fields()
            .updated_at
            .format(&Rfc3339)
            .map_err(|err| {
                event!(Level::ERROR, "Could not format updated_at: {:?}", err);
                DatabaseError::PersistenceError(database_object)
            })?;

        self.connection
            .client
            .delete_item()
            .table_name(self.get_table_name().await?)
            .key(
                PARTITION_KEY,
                try_to_attribute_val(relationship.common_fields().id.clone(), database_object)?,
            )
            .condition_expression("updated_at = :updated_at")
            .expression_attribute_values(
                ":updated_at",
                try_to_attribute_val(condition_value, database_object)?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not delete recovery relationship: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::PersistenceError(database_object)
            })?;
        Ok(())
    }
}
