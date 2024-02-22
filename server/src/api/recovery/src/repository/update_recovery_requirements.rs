use database::{
    aws_sdk_dynamodb::{error::ProvideErrorMetadata, types::AttributeValue},
    ddb::{try_to_attribute_val, DDBService, DatabaseError},
};
use time::format_description::well_known::Rfc3339;
use tracing::{event, Level};

use crate::{
    entities::{RecoveryRequirements, WalletRecoveryCompositeKey},
    repository::{PARTITION_KEY, SORT_KEY},
};

use super::Repository;

impl Repository {
    pub async fn update_recovery_requirements(
        &self,
        composite_key: WalletRecoveryCompositeKey,
        recovery_requirements: RecoveryRequirements,
    ) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let (account_id, initiation_time) = composite_key;
        let database_object = self.get_database_object();

        let account_id_attr = try_to_attribute_val(account_id.to_owned(), database_object)?;
        let recovery_requirements_attr =
            try_to_attribute_val(recovery_requirements, database_object)?;
        let updated_at_attr =
            try_to_attribute_val(self.cur_time().format(&Rfc3339).unwrap(), database_object)?;

        self.connection
            .client
            .update_item()
            .table_name(table_name)
            .key(PARTITION_KEY, account_id_attr)
            .key(
                SORT_KEY,
                AttributeValue::S(initiation_time.format(&Rfc3339).unwrap()),
            )
            .update_expression("SET requirements = :l, updated_at=:u")
            .expression_attribute_values(":l", recovery_requirements_attr)
            .expression_attribute_values(":u", updated_at_attr)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not update requirements for recovery: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::UpdateError(database_object)
            })?;
        event!(Level::INFO, "Updated recovery requirements");
        Ok(())
    }
}
