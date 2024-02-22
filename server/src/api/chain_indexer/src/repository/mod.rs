use async_trait::async_trait;
use database::{
    aws_sdk_dynamodb::{
        error::ProvideErrorMetadata,
        types::{
            AttributeDefinition, BillingMode, GlobalSecondaryIndex, KeySchemaElement, KeyType,
            Projection, ProjectionType::All, ScalarAttributeType,
        },
    },
    ddb::{Connection, DDBService, DatabaseError, DatabaseObject},
};
use tracing::{event, Level};

mod fetch;
mod persist;

pub(crate) const PARTITION_KEY: &str = "block_hash";
pub(crate) const NETWORK_HEIGHT_INDEX: &str = "network_height_index";
pub(crate) const NETWORK_HEIGHT_PARTITION_KEY: &str = "network";
pub(crate) const NETWORK_HEIGHT_SORT_KEY: &str = "height";

#[derive(Clone)]
pub struct Repository {
    pub connection: Connection,
}

#[async_trait]
impl DDBService for Repository {
    fn new(connection: Connection) -> Self {
        Self { connection }
    }

    fn get_database_object(&self) -> DatabaseObject {
        DatabaseObject::ChainIndexer
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
        let network_height_pk_attribute_definition = AttributeDefinition::builder()
            .attribute_name(NETWORK_HEIGHT_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let network_height_pk_key_schema = KeySchemaElement::builder()
            .attribute_name(NETWORK_HEIGHT_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;
        let network_height_sk_attribute_definition = AttributeDefinition::builder()
            .attribute_name(NETWORK_HEIGHT_SORT_KEY)
            .attribute_type(ScalarAttributeType::N)
            .build()?;
        let network_height_sk_key_schema = KeySchemaElement::builder()
            .attribute_name(NETWORK_HEIGHT_SORT_KEY)
            .key_type(KeyType::Range)
            .build()?;

        self.connection
            .client
            .create_table()
            .table_name(table_name.clone())
            .billing_mode(BillingMode::PayPerRequest)
            .attribute_definitions(pk_attribute_definition)
            .key_schema(pk_key_schema)
            .attribute_definitions(network_height_pk_attribute_definition)
            .attribute_definitions(network_height_sk_attribute_definition)
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(NETWORK_HEIGHT_INDEX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(network_height_pk_key_schema)
                    .key_schema(network_height_sk_key_schema)
                    .build()?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not update ChainIndexer table: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::CreateTableError(self.get_database_object())
            })?;

        Ok(())
    }
}
