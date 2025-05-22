use async_trait::async_trait;
use database::aws_sdk_dynamodb::types::ProjectionType::All;
use database::aws_sdk_dynamodb::types::{GlobalSecondaryIndex, Projection};
use database::{
    aws_sdk_dynamodb::{
        error::ProvideErrorMetadata,
        types::{AttributeDefinition, BillingMode, KeySchemaElement, KeyType, ScalarAttributeType},
    },
    ddb::{Connection, DatabaseError, DatabaseObject, Repository},
};
use tracing::{event, Level};

mod fetch;
mod persist;

pub(crate) const PARTITION_KEY: &str = "partition_key";
pub(crate) const WEB_AUTH_TOKEN_IDX: &str = "web_auth_token_idx";
pub(crate) const WEB_AUTH_TOKEN_IDX_PARTITION_KEY: &str = "web_auth_token";

#[derive(Clone)]
pub struct TransactionVerificationRepository {
    pub connection: Connection,
}

#[async_trait]
impl Repository for TransactionVerificationRepository {
    fn new(connection: Connection) -> Self {
        Self { connection }
    }

    fn get_database_object(&self) -> DatabaseObject {
        DatabaseObject::TransactionVerification
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
        let web_auth_token_key = AttributeDefinition::builder()
            .attribute_name(WEB_AUTH_TOKEN_IDX_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let web_auth_token_key_schema = KeySchemaElement::builder()
            .attribute_name(WEB_AUTH_TOKEN_IDX_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;

        self.connection
            .client
            .create_table()
            .table_name(table_name.clone())
            .billing_mode(BillingMode::PayPerRequest)
            .attribute_definitions(pk_attribute_definition)
            .attribute_definitions(web_auth_token_key)
            .key_schema(pk_key_schema.clone())
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(WEB_AUTH_TOKEN_IDX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(web_auth_token_key_schema)
                    .build()?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not update TransactionVerification table: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::CreateTableError(self.get_database_object())
            })?;

        Ok(())
    }
}
