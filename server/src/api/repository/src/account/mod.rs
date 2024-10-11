use async_trait::async_trait;
use database::aws_sdk_dynamodb::error::ProvideErrorMetadata;
use database::aws_sdk_dynamodb::types::ProjectionType::All;
use database::aws_sdk_dynamodb::types::{GlobalSecondaryIndex, Projection};
use database::{
    aws_sdk_dynamodb::types::{
        AttributeDefinition, BillingMode, KeySchemaElement, KeyType, ScalarAttributeType,
    },
    ddb::{Connection, DatabaseError, DatabaseObject, Repository},
};
use tracing::{event, Level};

pub mod delete;
pub mod fetch;
pub mod persist;

pub(crate) const PARTITION_KEY: &str = "partition_key";
pub(crate) const APPLICATION_TO_ACCOUNT_IDX: &str = "application_pubkey_to_account";

pub(crate) const HW_TO_ACCOUNT_IDX: &str = "hw_pubkey_to_account";
pub(crate) const RECOVERY_AUTHKEY_TO_ACCOUNT_IDX: &str = "recovery_pubkey_to_account";
pub(crate) const APPLICATION_IDX_PARTITION_KEY: &str = "application_auth_pubkey";

pub(crate) const HW_IDX_PARTITION_KEY: &str = "hardware_auth_pubkey";
pub(crate) const RECOVERY_AUTHKEY_IDX_PARTITION_KEY: &str = "recovery_auth_pubkey";

#[derive(Clone)]
pub struct AccountRepository {
    connection: Connection,
}

#[async_trait]
impl Repository for AccountRepository {
    fn new(connection: Connection) -> Self {
        Self { connection }
    }

    fn get_database_object(&self) -> DatabaseObject {
        DatabaseObject::Account
    }

    async fn get_table_name(&self) -> Result<String, DatabaseError> {
        self.connection.get_table_name(self.get_database_object())
    }

    fn get_connection(&self) -> &Connection {
        &self.connection
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

        let application_index_attribute_definition = AttributeDefinition::builder()
            .attribute_name(APPLICATION_IDX_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let application_index_pk_key_schema = KeySchemaElement::builder()
            .attribute_name(APPLICATION_IDX_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;
        let hw_index_attribute_definition = AttributeDefinition::builder()
            .attribute_name(HW_IDX_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let hw_index_pk_key_schema = KeySchemaElement::builder()
            .attribute_name(HW_IDX_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;
        let index_rk_key_schema = KeySchemaElement::builder()
            .attribute_name(PARTITION_KEY)
            .key_type(KeyType::Range)
            .build()?;
        let recovery_authkey_index_attribute_definition = AttributeDefinition::builder()
            .attribute_name(RECOVERY_AUTHKEY_IDX_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let recovery_authkey_index_pk_key_schema = KeySchemaElement::builder()
            .attribute_name(RECOVERY_AUTHKEY_IDX_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;

        self.connection
            .client
            .create_table()
            .table_name(table_name.clone())
            .billing_mode(BillingMode::PayPerRequest)
            .attribute_definitions(pk_attribute_definition)
            .key_schema(pk_key_schema)
            .attribute_definitions(hw_index_attribute_definition)
            .attribute_definitions(recovery_authkey_index_attribute_definition)
            .attribute_definitions(application_index_attribute_definition)
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(HW_TO_ACCOUNT_IDX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(hw_index_pk_key_schema)
                    .key_schema(index_rk_key_schema.clone())
                    .build()?,
            )
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(APPLICATION_TO_ACCOUNT_IDX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(application_index_pk_key_schema)
                    .key_schema(index_rk_key_schema.clone())
                    .build()?,
            )
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(RECOVERY_AUTHKEY_TO_ACCOUNT_IDX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(recovery_authkey_index_pk_key_schema)
                    .key_schema(index_rk_key_schema.clone())
                    .build()?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not update Accounts table: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::CreateTableError(self.get_database_object())
            })?;
        Ok(())
    }
}
