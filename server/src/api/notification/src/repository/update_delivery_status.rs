use database::{
    aws_sdk_dynamodb::{error::ProvideErrorMetadata, types::AttributeValue},
    ddb::{try_to_attribute_val, DatabaseError, Repository},
};
use tracing::{event, instrument, Level};

use crate::{
    entities::NotificationCompositeKey,
    repository::{PARTITION_KEY, SORT_KEY},
    DeliveryStatus,
};

use super::NotificationRepository;

impl NotificationRepository {
    /// Update the delivery status for a given notification
    ///
    /// ### Arguments
    ///
    /// * `composite_key` - The composite key containing both (AccountId, NotificationId) that uniquely identifies the notification
    /// * `delivery_status` - The delivery status to be updated on the notification
    ///
    #[instrument(skip(self))]
    pub async fn update_delivery_status(
        &self,
        composite_key: NotificationCompositeKey,
        delivery_status: DeliveryStatus,
    ) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let (partition_key, sort_key) = composite_key;
        let partition_key_val: AttributeValue =
            try_to_attribute_val(partition_key, database_object)?;
        let sort_key_val: AttributeValue = try_to_attribute_val(sort_key, database_object)?;
        self.connection
            .client
            .update_item()
            .table_name(table_name)
            .key(
                PARTITION_KEY,
                partition_key_val.clone(),
            )
            .key(
                SORT_KEY,
                sort_key_val.clone(),
            )
            .condition_expression("partition_key = :partition_key AND sort_key = :sort_key")
            .expression_attribute_values(
                ":partition_key",
                partition_key_val,
            )
            .expression_attribute_values(
                ":sort_key",
                sort_key_val,
            )
            .update_expression("SET delivery_status = :l")
            .expression_attribute_values(":l", AttributeValue::S(delivery_status.to_string()))     
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not update delivery status for notification: {service_err:?} with message: {:?}",
                    service_err.message(),
                );
                DatabaseError::UpdateError(database_object)
            })?;
        Ok(())
    }
}
