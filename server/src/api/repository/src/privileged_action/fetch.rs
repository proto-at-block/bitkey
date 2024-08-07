use database::{
    aws_sdk_dynamodb::{error::ProvideErrorMetadata, types::AttributeValue},
    ddb::{try_from_item, try_from_items, try_to_attribute_val, DDBService, DatabaseError},
};
use serde::de::DeserializeOwned;
use tracing::{event, instrument, Level};
use types::{
    account::identifiers::AccountId,
    privileged_action::{
        repository::PrivilegedActionInstanceRecord, shared::PrivilegedActionInstanceId,
    },
};

use super::{Repository, ACCOUNT_IDX, ACCOUNT_IDX_PARTITION_KEY, PARTITION_KEY};

impl Repository {
    #[instrument(skip(self))]
    pub async fn fetch<T>(
        &self,
        privileged_action_instance_id: &PrivilegedActionInstanceId,
    ) -> Result<PrivilegedActionInstanceRecord<T>, DatabaseError>
    where
        T: DeserializeOwned,
    {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let item_output = self
            .connection
            .client
            .get_item()
            .table_name(table_name)
            .key(
                PARTITION_KEY,
                try_to_attribute_val(privileged_action_instance_id, database_object)?,
            )
            .consistent_read(true)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not query database: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::FetchError(database_object)
            })?;

        let item = item_output.item.ok_or_else(|| {
            event!(Level::WARN, "privileged action instance {privileged_action_instance_id} not found in the database");
            DatabaseError::ObjectNotFound(database_object)
        })?;
        try_from_item(item, database_object)
    }

    #[instrument(skip(self))]
    pub async fn fetch_for_account_id<T>(
        &self,
        account_id: &AccountId,
    ) -> Result<Vec<PrivilegedActionInstanceRecord<T>>, DatabaseError>
    where
        T: DeserializeOwned,
    {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let account_id_attr: AttributeValue =
            try_to_attribute_val(account_id.to_string(), self.get_database_object())?;

        let mut exclusive_start_key = None;
        let mut result = Vec::new();

        loop {
            let item_output = self.connection.client
                .scan()
                .table_name(table_name.clone())
                .index_name(ACCOUNT_IDX)
                .filter_expression(format!("{} = :account_id", ACCOUNT_IDX_PARTITION_KEY))
                .expression_attribute_values(":account_id", account_id_attr.clone())
                .set_exclusive_start_key(exclusive_start_key.clone())
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not fetch privileged action instances for account id: {account_id} with err: {service_err:?} and message: {:?}",
                        service_err.message(),
                    );
                    DatabaseError::FetchError(database_object)
                })?;

            let items = item_output.items();
            let mut instances: Vec<PrivilegedActionInstanceRecord<T>> =
                try_from_items(items.to_owned(), database_object)?;
            result.append(&mut instances);

            if let Some(last_evaluated_key) = item_output.last_evaluated_key() {
                exclusive_start_key = Some(last_evaluated_key.to_owned());
            } else {
                break;
            }
        }

        Ok(result)
    }
}
