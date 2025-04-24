use database::{
    aws_sdk_dynamodb::{error::ProvideErrorMetadata, types::AttributeValue},
    ddb::{try_from_item, try_from_items, try_to_attribute_val, DatabaseError, Repository},
};
use serde::de::DeserializeOwned;
use tracing::{event, instrument, Level};
use types::{
    account::identifiers::AccountId,
    privileged_action::{
        repository::{DelayAndNotifyStatus, PrivilegedActionInstanceRecord},
        shared::{PrivilegedActionInstanceId, PrivilegedActionType},
    },
};

use super::{
    PrivilegedActionRepository, ACCOUNT_IDX, ACCOUNT_IDX_PARTITION_KEY, CANCELLATION_TOKEN_IDX,
    CANCELLATION_TOKEN_IDX_PARTITION_KEY, PARTITION_KEY,
};

const AUTHORIZATION_STRATEGY_TYPE_EXPRESSION: &str =
    "authorization_strategy_type = :authorization_strategy_type";
const PRIVILEGED_ACTION_TYPE_EXPRESSION: &str = "privileged_action_type = :privileged_action_type";
const STATUS_EXPRESSION: &str = "#status = :status";

impl PrivilegedActionRepository {
    #[instrument(skip(self))]
    pub async fn fetch_by_id<T>(
        &self,
        privileged_action_instance_id: &PrivilegedActionInstanceId,
    ) -> Result<PrivilegedActionInstanceRecord<T>, DatabaseError>
    where
        T: DeserializeOwned,
    {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let item_output = self
            .get_connection()
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

    #[instrument(skip(self, cancellation_token))]
    pub async fn fetch_by_cancellation_token<T>(
        &self,
        cancellation_token: String,
    ) -> Result<PrivilegedActionInstanceRecord<T>, DatabaseError>
    where
        T: DeserializeOwned,
    {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let item_output = self
            .get_connection()
            .client
            .query()
            .table_name(table_name)
            .index_name(CANCELLATION_TOKEN_IDX)
            .key_condition_expression(format!(
                "{} = :{}",
                CANCELLATION_TOKEN_IDX_PARTITION_KEY, CANCELLATION_TOKEN_IDX_PARTITION_KEY
            ))
            .expression_attribute_values(
                format!(":{}", CANCELLATION_TOKEN_IDX_PARTITION_KEY),
                try_to_attribute_val(cancellation_token, database_object)?,
            )
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

        let items = item_output.items();
        match items.len() {
            1 => try_from_item(items[0].to_owned(), database_object),
            0 => Err(DatabaseError::ObjectNotFound(database_object)),
            _ => Err(DatabaseError::ObjectNotUnique(database_object)),
        }
    }

    #[instrument(skip(self))]
    pub async fn fetch_delay_notify_for_account_id<T>(
        &self,
        account_id: &AccountId,
        privileged_action_type: Option<PrivilegedActionType>,
        status: Option<DelayAndNotifyStatus>,
    ) -> Result<Vec<PrivilegedActionInstanceRecord<T>>, DatabaseError>
    where
        T: DeserializeOwned,
    {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let account_id_attr: AttributeValue =
            try_to_attribute_val(account_id.to_string(), self.get_database_object())?;
        let authorization_strategy_type_attr: AttributeValue =
            try_to_attribute_val("DELAY_AND_NOTIFY", self.get_database_object())?;
        let privileged_action_type_attr: Option<AttributeValue> = privileged_action_type
            .map(|p| try_to_attribute_val(p, self.get_database_object()))
            .transpose()?;
        let status_attr: Option<AttributeValue> = status
            .map(|status| try_to_attribute_val(status, self.get_database_object()))
            .transpose()?;

        let mut exclusive_start_key = None;
        let mut result = Vec::new();

        loop {
            let mut filter_expression = AUTHORIZATION_STRATEGY_TYPE_EXPRESSION.to_owned();
            let mut query = self
                .get_connection()
                .client
                .query()
                .table_name(table_name.clone())
                .index_name(ACCOUNT_IDX)
                .key_condition_expression(format!(
                    "{} = :{}",
                    ACCOUNT_IDX_PARTITION_KEY, ACCOUNT_IDX_PARTITION_KEY
                ))
                .expression_attribute_values(
                    format!(":{}", ACCOUNT_IDX_PARTITION_KEY),
                    account_id_attr.clone(),
                )
                .expression_attribute_values(
                    ":authorization_strategy_type",
                    authorization_strategy_type_attr.clone(),
                );

            if let Some(privileged_action_type_attr) = &privileged_action_type_attr {
                filter_expression = format!(
                    "{} AND {}",
                    filter_expression, PRIVILEGED_ACTION_TYPE_EXPRESSION
                );
                query = query.expression_attribute_values(
                    ":privileged_action_type",
                    privileged_action_type_attr.to_owned(),
                );
            }

            if let Some(status_attr) = &status_attr {
                filter_expression = format!("{} AND {}", filter_expression, STATUS_EXPRESSION);
                query = query
                    .expression_attribute_names("#status", "status")
                    .expression_attribute_values(":status", status_attr.to_owned());
            }

            query = query.filter_expression(filter_expression.clone());

            let item_output = query
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
