use async_trait::async_trait;
use tracing::{event, Level};

use database::aws_sdk_dynamodb::error::ProvideErrorMetadata;
use database::{
    aws_sdk_dynamodb::types::{
        AttributeDefinition, BillingMode, KeySchemaElement, KeyType, ScalarAttributeType,
    },
    ddb::{Connection, DDBService, DatabaseError, DatabaseObject},
};

mod fetch;
mod persist;

pub(crate) const PARTITION_KEY: &str = "account_id";
pub(crate) const SORT_KEY: &str = "date";

#[derive(Clone)]
pub struct Repository {
    connection: Connection,
}

#[async_trait]
impl DDBService for Repository {
    fn new(connection: Connection) -> Self {
        Self { connection }
    }

    fn get_database_object(&self) -> DatabaseObject {
        DatabaseObject::DailySpendingRecord
    }

    fn get_connection(&self) -> &Connection {
        &self.connection
    }

    async fn get_table_name(&self) -> Result<String, DatabaseError> {
        self.connection.get_table_name(self.get_database_object())
    }

    async fn table_exists(&self) -> Result<bool, DatabaseError> {
        let table_name = self.get_table_name().await?;
        Ok(self
            .connection
            .client
            .describe_table()
            .table_name(table_name)
            .send()
            .await
            .is_ok())
    }

    async fn create_table(&self) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let pk_attribute_definition = AttributeDefinition::builder()
            .attribute_name(PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let pk_key_schema = KeySchemaElement::builder()
            .attribute_name(PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;

        let date_attribute_definition = AttributeDefinition::builder()
            .attribute_name(SORT_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let date_attribute_schema = KeySchemaElement::builder()
            .attribute_name(SORT_KEY)
            .key_type(KeyType::Range)
            .build()?;

        self.connection
            .client
            .create_table()
            .table_name(table_name.clone())
            .billing_mode(BillingMode::PayPerRequest)
            .attribute_definitions(pk_attribute_definition)
            .attribute_definitions(date_attribute_definition)
            .key_schema(pk_key_schema)
            .key_schema(date_attribute_schema)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not update Daily Spending Total table: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::CreateTableError(self.get_database_object())
            })?;
        Ok(())
    }
}
