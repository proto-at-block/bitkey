use async_trait::async_trait;
use database::{
    aws_sdk_dynamodb::{
        error::ProvideErrorMetadata,
        types::{
            AttributeDefinition, BillingMode, GlobalSecondaryIndex, KeySchemaElement, KeyType,
            Projection, ProjectionType, ScalarAttributeType,
        },
    },
    ddb::{Connection, DatabaseError, DatabaseObject, Repository},
};
use tracing::{event, Level};

mod fetch;
mod fetch_for_account_id;
mod fetch_in_execution_window;
mod persist;
mod update_delivery_status;

const PARTITION_KEY: &str = "partition_key";
const SORT_KEY: &str = "sort_key";
const WORKER_SECONDARY_INDEX_NAME: &str = "WorkerShardIndex";
const WORKER_SECONDARY_PARTITION_KEY: &str = "sharded_execution_date";
const WORKER_SECONDARY_SORT_KEY: &str = "execution_time";

#[derive(Clone)]
pub struct NotificationRepository {
    connection: Connection,
}

#[async_trait]
impl Repository for NotificationRepository {
    fn new(connection: Connection) -> Self {
        Self { connection }
    }

    fn get_database_object(&self) -> DatabaseObject {
        DatabaseObject::Notification
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
        let sk = AttributeDefinition::builder()
            .attribute_name(SORT_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let worker_secondary_pk = AttributeDefinition::builder()
            .attribute_name(WORKER_SECONDARY_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let worker_secondary_sk = AttributeDefinition::builder()
            .attribute_name(WORKER_SECONDARY_SORT_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let partition_ks = KeySchemaElement::builder()
            .attribute_name(PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;
        let sort_ks = KeySchemaElement::builder()
            .attribute_name(SORT_KEY)
            .key_type(KeyType::Range)
            .build()?;
        let worker_secondary_index = GlobalSecondaryIndex::builder()
            .index_name(WORKER_SECONDARY_INDEX_NAME)
            .key_schema(
                KeySchemaElement::builder()
                    .attribute_name(WORKER_SECONDARY_PARTITION_KEY)
                    .key_type(KeyType::Hash)
                    .build()?,
            )
            .key_schema(
                KeySchemaElement::builder()
                    .attribute_name(WORKER_SECONDARY_SORT_KEY)
                    .key_type(KeyType::Range)
                    .build()?,
            )
            .projection(
                Projection::builder()
                    .projection_type(ProjectionType::All)
                    .build(),
            )
            .build()?;
        self.connection
            .client
            .create_table()
            .table_name(table_name)
            .key_schema(partition_ks)
            .key_schema(sort_ks)
            .attribute_definitions(pk)
            .attribute_definitions(sk)
            .attribute_definitions(worker_secondary_pk)
            .attribute_definitions(worker_secondary_sk)
            .global_secondary_indexes(worker_secondary_index)
            .billing_mode(BillingMode::PayPerRequest)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not create Notification table: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::CreateTableError(database_object)
            })?;
        Ok(())
    }
}
