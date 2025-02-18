use async_trait::async_trait;
use database::{
    aws_sdk_dynamodb::{
        error::ProvideErrorMetadata,
        types::{
            AttributeDefinition, BillingMode, GlobalSecondaryIndex, KeySchemaElement, KeyType,
            Projection, ProjectionType::All, ScalarAttributeType,
        },
    },
    ddb::{Connection, DatabaseError, DatabaseObject, Repository},
};
use tracing::{event, Level};

mod fetch;
mod persist;

pub(crate) const PARTITION_KEY: &str = "partition_key";
pub(crate) const SORT_KEY: &str = "sort_key";

pub(crate) const CODE_IDX: &str = "by_code";
pub(crate) const CODE_IDX_PARTITION_KEY: &str = "code";

#[derive(Clone)]
pub struct PromotionCodeRepository {
    pub connection: Connection,
}

#[async_trait]
impl Repository for PromotionCodeRepository {
    fn new(connection: Connection) -> Self {
        Self { connection }
    }

    fn get_database_object(&self) -> DatabaseObject {
        DatabaseObject::PromotionCode
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
        let sort_attribute_definition = AttributeDefinition::builder()
            .attribute_name(SORT_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let pk_key_schema = KeySchemaElement::builder()
            .attribute_name(PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;
        let sort_key_schema = KeySchemaElement::builder()
            .attribute_name(SORT_KEY)
            .key_type(KeyType::Range)
            .build()?;

        let code_index_pk = AttributeDefinition::builder()
            .attribute_name(CODE_IDX_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let code_index_pk_ks = KeySchemaElement::builder()
            .attribute_name(CODE_IDX_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;

        self.connection
            .client
            .create_table()
            .table_name(table_name.clone())
            .billing_mode(BillingMode::PayPerRequest)
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(CODE_IDX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(code_index_pk_ks.clone())
                    .build()?,
            )
            .attribute_definitions(pk_attribute_definition)
            .attribute_definitions(sort_attribute_definition)
            .attribute_definitions(code_index_pk)
            .key_schema(pk_key_schema.clone())
            .key_schema(sort_key_schema.clone())
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not update PromotionCode table: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::CreateTableError(self.get_database_object())
            })?;

        Ok(())
    }
}
