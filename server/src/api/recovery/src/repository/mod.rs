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
use time::OffsetDateTime;
use tracing::{event, Level};

mod complete_recovery;
mod count;
mod create;
mod fetch;
mod update_recovery_action;
mod update_recovery_requirements;

pub(crate) const PARTITION_KEY: &str = "account_id";
pub(crate) const SORT_KEY: &str = "created_at";

pub(crate) const HW_TO_RECOVERY_IDX: &str = "hw_pubkey_to_recovery";
pub(crate) const HW_IDX_PARTITION_KEY: &str = "destination_hardware_auth_pubkey";

pub(crate) const APP_TO_RECOVERY_IDX: &str = "app_pubkey_to_recovery";
pub(crate) const APP_IDX_PARTITION_KEY: &str = "destination_app_auth_pubkey";

pub(crate) const RECOVERY_AUTHKEY_TO_RECOVERY_IDX: &str = "recovery_pubkey_to_recovery";
pub(crate) const RECOVERY_AUTHKEY_IDX_PARTITION_KEY: &str = "destination_recovery_auth_pubkey";

#[derive(Clone)]
pub struct RecoveryRepository {
    connection: Connection,
    pub override_cur_time: Option<OffsetDateTime>,
}

#[async_trait]
impl Repository for RecoveryRepository {
    fn new(_: Connection) -> Self {
        unreachable!();
    }

    fn get_database_object(&self) -> DatabaseObject {
        DatabaseObject::Recovery
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
        let database_object = self.get_database_object();
        let pk = AttributeDefinition::builder()
            .attribute_name(PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let sk = AttributeDefinition::builder()
            .attribute_name(SORT_KEY)
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

        let hw_index_pk = AttributeDefinition::builder()
            .attribute_name(HW_IDX_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let hw_index_pk_ks = KeySchemaElement::builder()
            .attribute_name(HW_IDX_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;

        let app_index_pk = AttributeDefinition::builder()
            .attribute_name(APP_IDX_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let app_index_pk_ks = KeySchemaElement::builder()
            .attribute_name(APP_IDX_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;

        let recovery_authkey_index_pk = AttributeDefinition::builder()
            .attribute_name(RECOVERY_AUTHKEY_IDX_PARTITION_KEY)
            .attribute_type(ScalarAttributeType::S)
            .build()?;
        let recovery_authkey_index_pk_ks = KeySchemaElement::builder()
            .attribute_name(RECOVERY_AUTHKEY_IDX_PARTITION_KEY)
            .key_type(KeyType::Hash)
            .build()?;

        self.connection
            .client
            .create_table()
            .table_name(table_name)
            .key_schema(partition_ks)
            .key_schema(sort_ks.clone())
            .attribute_definitions(pk)
            .attribute_definitions(sk)
            .attribute_definitions(hw_index_pk)
            .attribute_definitions(app_index_pk)
            .attribute_definitions(recovery_authkey_index_pk)
            .billing_mode(BillingMode::PayPerRequest)
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(HW_TO_RECOVERY_IDX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(hw_index_pk_ks)
                    .key_schema(sort_ks.clone())
                    .build()?,
            )
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(APP_TO_RECOVERY_IDX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(app_index_pk_ks)
                    .key_schema(sort_ks.clone())
                    .build()?,
            )
            .global_secondary_indexes(
                GlobalSecondaryIndex::builder()
                    .index_name(RECOVERY_AUTHKEY_TO_RECOVERY_IDX)
                    .projection(Projection::builder().projection_type(All).build())
                    .key_schema(recovery_authkey_index_pk_ks)
                    .key_schema(sort_ks)
                    .build()?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not create Recovery table: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::CreateTableError(database_object)
            })?;
        Ok(())
    }
}

impl RecoveryRepository {
    pub fn new_with_override(connection: Connection, override_cur_time: bool) -> Self {
        Self {
            connection,
            override_cur_time: if override_cur_time {
                Some(OffsetDateTime::now_utc())
            } else {
                None
            },
        }
    }

    pub fn cur_time(&self) -> OffsetDateTime {
        self.override_cur_time
            .unwrap_or_else(OffsetDateTime::now_utc)
    }
}
