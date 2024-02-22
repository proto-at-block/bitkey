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
const SORT_KEY: &str = "sort_key";

const EMAIL_IDX: &str = "email_address_to_created_at";
const EMAIL_IDX_PARTITION_KEY: &str = "email_address";
const EMAIL_IDX_SORT_KEY: &str = "created_at";

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
        DatabaseObject::Consent
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
        let sk = AttributeDefinition::builder()
            .attribute_name(SORT_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let sk_ks = KeySchemaElement::builder()
            .attribute_name(SORT_KEY)
            .key_type(KeyType::Range)
            .build()?;

        let email_index_pk = AttributeDefinition::builder()
            .attribute_name(EMAIL_IDX_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let email_index_pk_ks = KeySchemaElement::builder()
            .attribute_name(EMAIL_IDX_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;
        let email_index_sk = AttributeDefinition::builder()
            .attribute_name(EMAIL_IDX_SORT_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let email_index_sk_ks = KeySchemaElement::builder()
            .attribute_name(EMAIL_IDX_SORT_KEY)
            .key_type(KeyType::Range)
            .build()?;

        self.connection
            .client
            .create_table()
            .table_name(table_name)
            .key_schema(pk_ks)
            .key_schema(sk_ks)
            .attribute_definitions(pk)
            .attribute_definitions(sk)
            .attribute_definitions(email_index_pk)
            .attribute_definitions(email_index_sk)
            .billing_mode(BillingMode::PayPerRequest)
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(EMAIL_IDX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(email_index_pk_ks)
                    .key_schema(email_index_sk_ks)
                    .build()?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not create SocialRecovery table: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::CreateTableError(database_object)
            })?;
        Ok(())
    }
}
