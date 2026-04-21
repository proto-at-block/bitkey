use async_trait::async_trait;
use database::{
    aws_sdk_dynamodb::types::{KeyType as DdbKeyType, ScalarAttributeType},
    ddb::{
        create_dynamodb_table, BaseRepository, Connection, DatabaseError, DatabaseObject,
        Repository, TableKey,
    },
};
use serde::{Deserialize, Serialize};
use types::account::identifiers::AccountId;

pub mod fetch;
pub mod persist;

const PARTITION_KEY: &str = "public_key";

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
pub enum KeyType {
    HardwareAuth,
    AppAuth,
    RecoveryAuth,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct PublicKeyRecord {
    pub public_key: String,
    pub account_id: AccountId,
    pub key_type: KeyType,
    pub created_at: String,
}

#[derive(Clone)]
pub struct PublicKeyRepository {
    base: BaseRepository,
}

#[async_trait]
impl Repository for PublicKeyRepository {
    fn new(connection: Connection) -> Self {
        Self {
            base: BaseRepository::new(connection, DatabaseObject::PublicKey),
        }
    }

    fn get_database_object(&self) -> DatabaseObject {
        self.base.get_database_object()
    }

    fn get_connection(&self) -> &Connection {
        self.base.get_connection()
    }

    async fn create_table(&self) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let partition_key = TableKey {
            name: PARTITION_KEY.to_string(),
            key_type: DdbKeyType::Hash,
            attribute_type: ScalarAttributeType::S,
        };

        create_dynamodb_table(
            &self.get_connection().client,
            table_name,
            database_object,
            partition_key,
            None,
            vec![],
        )
        .await?;

        Ok(())
    }
}
