use tracing::{event, instrument, Level};

use crate::{entities::ScheduledNotification, DeliveryStatus};
use database::{
    aws_sdk_dynamodb::{error::ProvideErrorMetadata, types::AttributeValue},
    ddb::{try_from_items, DatabaseError, Repository},
};

use super::{NotificationRepository, WORKER_SECONDARY_INDEX_NAME};

impl NotificationRepository {
    /// Returns all scheduled notifications in a given execution window for a worker
    ///
    /// This is currently used by our workers.
    /// The partition key is a combination of the worker identifier as well as the execution date
    ///
    /// ### Arguments
    ///
    /// * `sharded_execution_date` - This contains the date that we're fetching for as well as the worker id
    /// * `execution_time_lower` - The beginning of the execution window for which we're fetching notifications
    /// * `execution_time_upper` - The end of the execution window for which we're fetching notifications
    /// * `delivery_status` - The status of the notifications we are fetching
    ///
    #[instrument(skip(self))]
    pub async fn fetch_scheduled_in_execution_window(
        &self,
        sharded_execution_date: String,
        execution_time_lower: String,
        execution_time_upper: String,
        delivery_status: DeliveryStatus,
    ) -> Result<Vec<ScheduledNotification>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let item_output = self.connection.client
                .query()
                .table_name(table_name)
                .index_name(WORKER_SECONDARY_INDEX_NAME)
                .key_condition_expression(
                    "sharded_execution_date = :sharded_execution_date AND execution_time BETWEEN :execution_time_lower AND :execution_time_upper",
                )
                .filter_expression("#delivery_status = :delivery_status")
                .expression_attribute_values(
                    ":sharded_execution_date",
                    AttributeValue::S(sharded_execution_date.clone()),
                )
                .expression_attribute_values(
                    ":execution_time_lower",
                    AttributeValue::S(execution_time_lower.clone()),
                )
                .expression_attribute_values(
                    ":execution_time_upper",
                    AttributeValue::S(execution_time_upper.clone()),
                )
                .expression_attribute_names("#delivery_status", "delivery_status")
                .expression_attribute_values(
                    ":delivery_status",
                    AttributeValue::S(delivery_status.to_string()),
                )
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not fetch notification: {service_err:?} with message: {:?}", 
                        service_err.message()
                    );
                    DatabaseError::FetchError(self.get_database_object())
                })?;

        let items = item_output
            .items
            .ok_or(DatabaseError::ObjectNotFound(database_object))?;

        try_from_items(items, database_object)
    }
}
