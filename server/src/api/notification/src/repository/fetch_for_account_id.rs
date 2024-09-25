use tracing::{event, instrument, Level};

use crate::{
    entities::{CustomerNotification, NotificationType, ScheduledNotification},
    NotificationPayloadType,
};
use database::{
    aws_sdk_dynamodb::{error::ProvideErrorMetadata, types::AttributeValue},
    ddb::{try_from_items, try_to_attribute_val, DatabaseError, Repository},
};
use types::account::identifiers::AccountId;

use super::NotificationRepository;

impl NotificationRepository {
    /// Returns all scheduled notification corresponding to the account id
    /// ** This should only be used in tests **
    ///
    /// ### Arguments
    ///
    /// * `account_id` - The account id for the given user
    #[instrument(skip(self))]
    pub async fn fetch_scheduled_for_account_id(
        &self,
        account_id: &AccountId,
    ) -> Result<Vec<ScheduledNotification>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let account_id_attr =
            try_to_attribute_val(account_id.to_string(), self.get_database_object())?;
        let sort_key_start_with = try_to_attribute_val(
            NotificationType::Scheduled.to_string(),
            self.get_database_object(),
        )?;
        let item_output = self.connection.client
            .query()
            .table_name(table_name)
            .key_condition_expression("partition_key=:partition_key and begins_with(sort_key, :sort_key_start_with)")
            .expression_attribute_values(":partition_key", account_id_attr)
            .expression_attribute_values(":sort_key_start_with", sort_key_start_with)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not fetch notifications for account id: {account_id} with err: {service_err:?} and message: {:?}",
                    service_err.message(),
                );
                DatabaseError::FetchError(database_object)
            })?;

        let items = item_output.items().to_owned();
        try_from_items(items, database_object)
    }

    /// Returns all customer notification corresponding to the account id and payload type
    ///
    /// ### Arguments
    ///
    /// * `account_id` - The account id for the given user
    /// * `payload_type` - The desired payload type
    #[instrument(skip(self))]
    pub async fn fetch_customer_for_account_id_and_payload_type(
        &self,
        account_id: &AccountId,
        payload_type: Option<NotificationPayloadType>,
    ) -> Result<Vec<CustomerNotification>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let account_id_attr: AttributeValue =
            try_to_attribute_val(account_id.to_string(), self.get_database_object())?;
        let sort_key_start_with: AttributeValue = try_to_attribute_val(
            NotificationType::Customer.to_string(),
            self.get_database_object(),
        )?;

        let mut exclusive_start_key = None;
        let mut results = Vec::new();

        loop {
            let mut query = self
                .connection
                .client
                .query()
                .table_name(table_name.clone())
                .set_exclusive_start_key(exclusive_start_key)
                .key_condition_expression(
                    "partition_key=:partition_key and begins_with(sort_key, :sort_key_start_with)",
                )
                .expression_attribute_values(":partition_key", account_id_attr.clone())
                .expression_attribute_values(":sort_key_start_with", sort_key_start_with.clone());

            if let Some(payload_type) = payload_type {
                query = query
                    .filter_expression("payload_type = :payload_type")
                    .expression_attribute_values(
                        ":payload_type",
                        try_to_attribute_val(payload_type, database_object)?,
                    );
            }

            let item_output = query
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not fetch customer notifications for account id: {} with err: {service_err:?} and message: {:?}",
                        account_id,
                        service_err.message(),
                    );
                    DatabaseError::FetchError(database_object)
                })?;

            results.append(&mut try_from_items(
                item_output.items().to_owned(),
                database_object,
            )?);

            if let Some(last_evaluated_key) = item_output.last_evaluated_key() {
                exclusive_start_key = Some(last_evaluated_key.to_owned());
            } else {
                break;
            }
        }

        Ok(results)
    }
}
