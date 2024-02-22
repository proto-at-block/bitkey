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
use serde::{Deserialize, Serialize};
use tracing::{event, Level};
use types::recovery::social::{challenge::SocialChallenge, relationship::RecoveryRelationship};

pub mod delete;
pub mod fetch;
pub mod persist;

pub(super) const PARTITION_KEY: &str = "partition_key";

pub(super) const CUSTOMER_IDX: &str = "customer_account_id_to_created_at";
pub(super) const CUSTOMER_IDX_PARTITION_KEY: &str = "customer_account_id";
const CUSTOMER_IDX_SORT_KEY: &str = "created_at";

pub(super) const TRUSTED_CONTACT_IDX: &str = "trusted_contact_account_id_to_customer_account_id";
pub(super) const TRUSTED_CONTACT_IDX_PARTITION_KEY: &str = "trusted_contact_account_id";
const TRUSTED_CONTACT_IDX_SORT_KEY: &str = "customer_account_id";

pub(super) const CODE_IDX: &str = "by_code";
pub(super) const CODE_IDX_PARTITION_KEY: &str = "code";

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(tag = "_SocialRecoveryRow_type")]
enum SocialRecoveryRow {
    Relationship(RecoveryRelationship),
    Challenge(SocialChallenge),
}

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
        DatabaseObject::SocialRecovery
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
        let partition_ks = KeySchemaElement::builder()
            .attribute_name(PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;

        let customer_index_pk = AttributeDefinition::builder()
            .attribute_name(CUSTOMER_IDX_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let customer_index_pk_ks = KeySchemaElement::builder()
            .attribute_name(CUSTOMER_IDX_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;
        let customer_index_sk = AttributeDefinition::builder()
            .attribute_name(CUSTOMER_IDX_SORT_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let customer_index_sk_ks = KeySchemaElement::builder()
            .attribute_name(CUSTOMER_IDX_SORT_KEY)
            .key_type(KeyType::Range)
            .build()?;

        let trusted_contact_index_pk = AttributeDefinition::builder()
            .attribute_name(TRUSTED_CONTACT_IDX_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let trusted_contact_index_pk_ks = KeySchemaElement::builder()
            .attribute_name(TRUSTED_CONTACT_IDX_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;
        let trusted_contact_index_sk_ks = KeySchemaElement::builder()
            .attribute_name(TRUSTED_CONTACT_IDX_SORT_KEY)
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
            .table_name(table_name)
            .key_schema(partition_ks)
            .attribute_definitions(pk)
            .attribute_definitions(customer_index_pk)
            .attribute_definitions(customer_index_sk)
            .attribute_definitions(trusted_contact_index_pk)
            .attribute_definitions(code_index_pk)
            .billing_mode(BillingMode::PayPerRequest)
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(CUSTOMER_IDX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(customer_index_pk_ks)
                    .key_schema(customer_index_sk_ks)
                    .build()?,
            )
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(TRUSTED_CONTACT_IDX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(trusted_contact_index_pk_ks)
                    .key_schema(trusted_contact_index_sk_ks)
                    .build()?,
            )
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(CODE_IDX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(code_index_pk_ks)
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
