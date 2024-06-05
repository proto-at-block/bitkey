use async_trait::async_trait;
use database::{
    aws_sdk_dynamodb::{
        error::ProvideErrorMetadata,
        types::{
            AttributeDefinition, BillingMode, GlobalSecondaryIndex, KeySchemaElement, KeyType,
            Projection,
            ProjectionType::{All, KeysOnly},
            ScalarAttributeType, TimeToLiveSpecification,
        },
    },
    ddb::{Connection, DDBService, DatabaseError, DatabaseObject},
};
use tracing::{event, Level};

mod fetch;
mod persist;
mod update_expiry;

pub(crate) const PARTITION_KEY: &str = "tx_id";

pub(crate) const NETWORK_TX_IDS_IDX: &str = "network_tx_ids_index";
pub(crate) const NETWORK_TX_IDS_IDX_PARTITION_KEY: &str = "network";
pub(crate) const NETWORK_TX_IDS_IDX_SORT_KEY: &str = "tx_id";

pub(crate) const NETWORK_EXPIRING_IDX: &str = "network_expiring_index";
pub(crate) const NETWORK_EXPIRING_IDX_PARTITION_KEY: &str = "network";
pub(crate) const NETWORK_EXPIRING_IDX_SORT_KEY: &str = "expiring_at";

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
        DatabaseObject::MempoolIndexer
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
        let tx_id_attribute_definition = AttributeDefinition::builder()
            .attribute_name(PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let network_attribute_definition = AttributeDefinition::builder()
            .attribute_name(NETWORK_TX_IDS_IDX_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let expiring_at_attribute_definition: AttributeDefinition = AttributeDefinition::builder()
            .attribute_name(NETWORK_EXPIRING_IDX_SORT_KEY)
            .attribute_type(ScalarAttributeType::N)
            .build()?;
        let pk_key_schema = KeySchemaElement::builder()
            .attribute_name(PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;
        let secondary_tx_ids_pk_key_schema = KeySchemaElement::builder()
            .attribute_name(NETWORK_TX_IDS_IDX_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;
        let secondary_tx_ids_sk_key_schema = KeySchemaElement::builder()
            .attribute_name(NETWORK_TX_IDS_IDX_SORT_KEY)
            .key_type(KeyType::Range)
            .build()?;
        let secondary_expiring_pk_key_schema = KeySchemaElement::builder()
            .attribute_name(NETWORK_EXPIRING_IDX_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;
        let secondary_expiring_sk_key_schema = KeySchemaElement::builder()
            .attribute_name(NETWORK_EXPIRING_IDX_SORT_KEY)
            .key_type(KeyType::Range)
            .build()?;

        self.connection
            .client
            .create_table()
            .table_name(table_name.clone())
            .billing_mode(BillingMode::PayPerRequest)
            .attribute_definitions(tx_id_attribute_definition)
            .attribute_definitions(network_attribute_definition)
            .attribute_definitions(expiring_at_attribute_definition)
            .key_schema(pk_key_schema.clone())
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(NETWORK_TX_IDS_IDX)
                    .projection(Projection::builder().projection_type(KeysOnly).build())
                    .key_schema(secondary_tx_ids_pk_key_schema)
                    .key_schema(secondary_tx_ids_sk_key_schema)
                    .build()?,
            )
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(NETWORK_EXPIRING_IDX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(secondary_expiring_pk_key_schema)
                    .key_schema(secondary_expiring_sk_key_schema)
                    .build()?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not update MempoolIndexer table: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::CreateTableError(self.get_database_object())
            })?;

        self.connection
            .client
            .update_time_to_live()
            .set_time_to_live_specification(Some(
                TimeToLiveSpecification::builder()
                    .enabled(true)
                    .attribute_name("expiring_at")
                    .build()?,
            ))
            .table_name(table_name)
            .send()
            .await
            .map_err(|_| DatabaseError::TimeToLiveSpecification(self.get_database_object()))?;
        Ok(())
    }
}
