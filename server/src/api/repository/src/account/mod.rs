use async_trait::async_trait;
use database::{
    aws_sdk_dynamodb::types::{KeyType, ScalarAttributeType},
    ddb::{
        create_dynamodb_table, BaseRepository, Connection, DatabaseError, DatabaseObject,
        GlobalSecondaryIndexDef, Repository, TableKey,
    },
};

pub mod delete;
pub mod fetch;
pub mod persist;

pub(crate) const PARTITION_KEY: &str = "partition_key";
pub(crate) const APPLICATION_TO_ACCOUNT_IDX: &str = "application_pubkey_to_account";

pub(crate) const HW_TO_ACCOUNT_IDX: &str = "hw_pubkey_to_account";
pub(crate) const RECOVERY_AUTHKEY_TO_ACCOUNT_IDX: &str = "recovery_pubkey_to_account";
pub(crate) const APPLICATION_IDX_PARTITION_KEY: &str = "application_auth_pubkey";

pub(crate) const HW_IDX_PARTITION_KEY: &str = "hardware_auth_pubkey";
pub(crate) const RECOVERY_AUTHKEY_IDX_PARTITION_KEY: &str = "recovery_auth_pubkey";

#[derive(Clone)]
pub struct AccountRepository {
    base: BaseRepository,
}

#[async_trait]
impl Repository for AccountRepository {
    fn new(connection: Connection) -> Self {
        Self {
            base: BaseRepository::new(connection, DatabaseObject::Account),
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
            key_type: KeyType::Hash,
            attribute_type: ScalarAttributeType::S,
        };

        let gsis = vec![
            // Hardware auth pubkey GSI
            GlobalSecondaryIndexDef {
                name: HW_TO_ACCOUNT_IDX.to_string(),
                pk: TableKey {
                    name: HW_IDX_PARTITION_KEY.to_string(),
                    key_type: KeyType::Hash,
                    attribute_type: ScalarAttributeType::S,
                },
                sk: Some(TableKey {
                    name: PARTITION_KEY.to_string(),
                    key_type: KeyType::Range,
                    attribute_type: ScalarAttributeType::S,
                }),
            },
            // Application auth pubkey GSI
            GlobalSecondaryIndexDef {
                name: APPLICATION_TO_ACCOUNT_IDX.to_string(),
                pk: TableKey {
                    name: APPLICATION_IDX_PARTITION_KEY.to_string(),
                    key_type: KeyType::Hash,
                    attribute_type: ScalarAttributeType::S,
                },
                sk: Some(TableKey {
                    name: PARTITION_KEY.to_string(),
                    key_type: KeyType::Range,
                    attribute_type: ScalarAttributeType::S,
                }),
            },
            // Recovery auth pubkey GSI
            GlobalSecondaryIndexDef {
                name: RECOVERY_AUTHKEY_TO_ACCOUNT_IDX.to_string(),
                pk: TableKey {
                    name: RECOVERY_AUTHKEY_IDX_PARTITION_KEY.to_string(),
                    key_type: KeyType::Hash,
                    attribute_type: ScalarAttributeType::S,
                },
                sk: Some(TableKey {
                    name: PARTITION_KEY.to_string(),
                    key_type: KeyType::Range,
                    attribute_type: ScalarAttributeType::S,
                }),
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
