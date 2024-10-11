use async_trait::async_trait;
use database::{
    aws_sdk_dynamodb::{
        error::ProvideErrorMetadata,
        types::{
            AttributeDefinition, BillingMode, GlobalSecondaryIndex, KeySchemaElement, KeyType,
            Projection, ProjectionType::All, ScalarAttributeType,
        },
    },
    ddb::{Connection, DatabaseError, DatabaseObject, Repository, Upsertable},
};
use serde::{Deserialize, Serialize};

use tracing::{event, Level};
use types::recovery::inheritance::{
    claim::InheritanceClaim, package::Package as InheritancePackage,
};

pub mod fetch;
pub mod persist;

pub(super) const PARTITION_KEY: &str = "partition_key";

pub(super) const RECOVERY_RELATIONSHIP_ID_IDX: &str = "by_recovery_relationship_id";
pub(super) const RECOVERY_RELATIONSHIP_ID_IDX_PARTITION_KEY: &str = "recovery_relationship_id";
const RECOVERY_RELATIONSHIP_ID_IDX_SORT_KEY: &str = "created_at";

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(tag = "_InheritanceRow_type")]
enum InheritanceRow {
    Claim(InheritanceClaim),
    Package(InheritancePackage),
}

impl Upsertable for InheritanceRow {
    const KEY_PROPERTIES: &'static [&'static str] = &["partition_key"];
    const IF_NOT_EXISTS_PROPERTIES: &'static [&'static str] = &["created_at"];
}

#[derive(Clone)]
pub struct InheritanceRepository {
    connection: Connection,
}

#[async_trait]
impl Repository for InheritanceRepository {
    fn new(connection: Connection) -> Self {
        Self { connection }
    }

    fn get_database_object(&self) -> DatabaseObject {
        DatabaseObject::Inheritance
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

        let recovery_relationship_id_idx_pk = AttributeDefinition::builder()
            .attribute_name(RECOVERY_RELATIONSHIP_ID_IDX_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let recovery_relationship_id_idx_pk_ks = KeySchemaElement::builder()
            .attribute_name(RECOVERY_RELATIONSHIP_ID_IDX_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;
        let recovery_relationship_id_idx_sk = AttributeDefinition::builder()
            .attribute_name(RECOVERY_RELATIONSHIP_ID_IDX_SORT_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let recovery_relationship_id_idx_sk_ks = KeySchemaElement::builder()
            .attribute_name(RECOVERY_RELATIONSHIP_ID_IDX_SORT_KEY)
            .key_type(KeyType::Range)
            .build()?;

        self.connection
            .client
            .create_table()
            .table_name(table_name)
            .key_schema(partition_ks)
            .attribute_definitions(pk)
            .attribute_definitions(recovery_relationship_id_idx_pk)
            .attribute_definitions(recovery_relationship_id_idx_sk)
            .billing_mode(BillingMode::PayPerRequest)
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(RECOVERY_RELATIONSHIP_ID_IDX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(recovery_relationship_id_idx_pk_ks)
                    .key_schema(recovery_relationship_id_idx_sk_ks)
                    .build()?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not create Inheritance table: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::CreateTableError(database_object)
            })?;
        Ok(())
    }
}
