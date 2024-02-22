use database::{
    aws_sdk_dynamodb::{error::ProvideErrorMetadata, types::AttributeValue},
    ddb::{try_from_item, DDBService, DatabaseError},
};
use tracing::{event, instrument, Level};

use crate::{entities::CustomerNotification, DeliveryStatus, NotificationCompositeKey};

use super::Repository;

impl Repository {
    /// Returns a pending customer notification corresponding to the account id and unique_id
    ///
    /// ### Arguments
    ///
    /// * `account_id` - The account id for the given user
    /// * `unique_id` - The unique identifier for this customer notification (should start with `Customer`)
    #[instrument(skip(self))]
    pub async fn fetch_pending(
        &self,
        composite_key: NotificationCompositeKey,
    ) -> Result<Option<CustomerNotification>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let (partition_key, sort_key) = composite_key;

        let item_output = self
            .connection
            .client
            .query()
            .table_name(table_name)
            .key_condition_expression(
                "partition_key = :partition_key AND sort_key = :sort_key"
            )
            .filter_expression("delivery_status = :delivery_status")
            .expression_attribute_values(":partition_key", AttributeValue::S(partition_key.to_string()))
            .expression_attribute_values(":sort_key", AttributeValue::S(sort_key.to_string()))
            .expression_attribute_values(
                ":delivery_status",
                AttributeValue::S(DeliveryStatus::Enqueued.to_string()),
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not fetch pending customer notification: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::FetchError(database_object)
            })?;

        let items = item_output.items();
        if items.is_empty() {
            Ok(None)
        } else {
            let item = items
                .first()
                .ok_or_else(|| DatabaseError::FetchError(self.get_database_object()))?
                .clone();
            let notification: CustomerNotification =
                try_from_item(item, self.get_database_object())?;
            event!(
                Level::INFO,
                "Retrieved pending customer notification with account id {} and unique identifier {}",
                &notification.account_id,
                &notification.unique_id,
            );
            Ok(Some(notification))
        }
    }
}
