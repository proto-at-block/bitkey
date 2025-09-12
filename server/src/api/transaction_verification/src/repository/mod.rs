use async_trait::async_trait;
use database::ddb::create_dynamodb_table;
use database::{
    aws_sdk_dynamodb::types::{KeyType, ScalarAttributeType},
    ddb::{
        Connection, DatabaseError, DatabaseObject, GlobalSecondaryIndexDef, Repository, TableKey,
    },
};

mod fetch;
mod persist;

pub(crate) const PARTITION_KEY: &str = "partition_key";
pub(crate) const WEB_AUTH_TOKEN_IDX: &str = "web_auth_token_idx";
pub(crate) const WEB_AUTH_TOKEN_IDX_PARTITION_KEY: &str = "web_auth_token";
pub(crate) const TXID_IDX: &str = "txid_idx";
pub(crate) const TXID_IDX_PARTITION_KEY: &str = "txid";

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
        let database_object = self.get_database_object();

        let partition_key = TableKey {
            name: PARTITION_KEY.to_string(),
            key_type: KeyType::Hash,
            attribute_type: ScalarAttributeType::S,
        };

        let gsis = vec![
            // Web Auth Token GSI
            GlobalSecondaryIndexDef {
                name: WEB_AUTH_TOKEN_IDX.to_string(),
                pk: TableKey {
                    name: WEB_AUTH_TOKEN_IDX_PARTITION_KEY.to_string(),
                    key_type: KeyType::Hash,
                    attribute_type: ScalarAttributeType::S,
                },
                sk: None,
            },
            // Txid GSI
            GlobalSecondaryIndexDef {
                name: TXID_IDX.to_string(),
                pk: TableKey {
                    name: TXID_IDX_PARTITION_KEY.to_string(),
                    key_type: KeyType::Hash,
                    attribute_type: ScalarAttributeType::S,
                },
                sk: None,
            },
        ];

        create_dynamodb_table(
            &self.get_connection().client,
            table_name,
            database_object,
            partition_key,
            None,
            gsis,
        )
        .await
    }
}
