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

pub mod fetch;
pub mod persist;

const PARTITION_KEY: &str = "partition_key";

const ACCOUNT_IDX: &str = "account_id_to_created_at";
const ACCOUNT_IDX_PARTITION_KEY: &str = "account_id";
const ACCOUNT_IDX_SORT_KEY: &str = "created_at";

const CANCELLATION_TOKEN_IDX: &str = "by_cancellation_token";
const CANCELLATION_TOKEN_IDX_PARTITION_KEY: &str = "cancellation_token";

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
        DatabaseObject::PrivilegedAction
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
        let database_object = self.get_database_object();

        let pk = AttributeDefinition::builder()
            .attribute_name(PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let pk_ks = KeySchemaElement::builder()
            .attribute_name(PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;

        let account_index_pk = AttributeDefinition::builder()
            .attribute_name(ACCOUNT_IDX_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let account_index_pk_ks = KeySchemaElement::builder()
            .attribute_name(ACCOUNT_IDX_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;
        let account_index_sk = AttributeDefinition::builder()
            .attribute_name(ACCOUNT_IDX_SORT_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let account_index_sk_ks = KeySchemaElement::builder()
            .attribute_name(ACCOUNT_IDX_SORT_KEY)
            .key_type(KeyType::Range)
            .build()?;

        let cancellation_token_index_pk = AttributeDefinition::builder()
            .attribute_name(CANCELLATION_TOKEN_IDX_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let cancellation_token_index_pk_ks = KeySchemaElement::builder()
            .attribute_name(CANCELLATION_TOKEN_IDX_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;

        self.connection
            .client
            .create_table()
            .table_name(table_name)
            .key_schema(pk_ks)
            .attribute_definitions(pk)
            .attribute_definitions(account_index_pk)
            .attribute_definitions(account_index_sk)
            .attribute_definitions(cancellation_token_index_pk)
            .billing_mode(BillingMode::PayPerRequest)
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(ACCOUNT_IDX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(account_index_pk_ks)
                    .key_schema(account_index_sk_ks)
                    .build()?,
            )
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(CANCELLATION_TOKEN_IDX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(cancellation_token_index_pk_ks)
                    .build()?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not create PrivilegedAction table: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::CreateTableError(database_object)
            })?;
        Ok(())
    }
}
