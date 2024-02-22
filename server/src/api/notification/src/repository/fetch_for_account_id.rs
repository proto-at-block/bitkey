use database::{
    aws_sdk_dynamodb::error::ProvideErrorMetadata,
    ddb::{try_from_items, try_to_attribute_val, DDBService, DatabaseError},
};
use tracing::{event, instrument, Level};
use types::account::identifiers::AccountId;

use crate::entities::{CustomerNotification, NotificationType, ScheduledNotification};

use super::Repository;

impl Repository {
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

    /// Returns all customer notification corresponding to the account id
    /// ** This should only be used in tests **
    ///
    /// ### Arguments
    ///
    /// * `account_id` - The account id for the given user
    #[instrument(skip(self))]
    pub async fn fetch_customer_for_account_id(
        &self,
        account_id: &AccountId,
    ) -> Result<Vec<CustomerNotification>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let account_id_attr =
            try_to_attribute_val(account_id.to_string(), self.get_database_object())?;
        let sort_key_start_with = try_to_attribute_val(
            NotificationType::Customer.to_string(),
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
                    "Could not fetch customer notifications for account id: {} with err: {service_err:?} and message: {:?}",
                    account_id,
                    service_err.message(),
                );
                DatabaseError::FetchError(database_object)
            })?;

        let items = item_output.items().to_owned();
        try_from_items(items, database_object)
    }
}
