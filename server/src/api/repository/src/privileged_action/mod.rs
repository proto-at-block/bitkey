use async_trait::async_trait;
use database::{
    aws_sdk_dynamodb::types::{KeyType, ScalarAttributeType},
    ddb::{
        create_dynamodb_table, BaseRepository, Connection, DatabaseError, DatabaseObject,
        GlobalSecondaryIndexDef, Repository, TableKey,
    },
};

pub mod fetch;
pub mod persist;

const PARTITION_KEY: &str = "partition_key";

const ACCOUNT_IDX: &str = "account_id_to_created_at";
const ACCOUNT_IDX_PARTITION_KEY: &str = "account_id";
const ACCOUNT_IDX_SORT_KEY: &str = "created_at";

const CANCELLATION_TOKEN_IDX: &str = "by_cancellation_token";
const CANCELLATION_TOKEN_IDX_PARTITION_KEY: &str = "cancellation_token";

const WEB_AUTH_TOKEN_IDX: &str = "by_web_auth_token";
const WEB_AUTH_TOKEN_IDX_PARTITION_KEY: &str = "web_auth_token";

#[derive(Clone)]
pub struct PrivilegedActionRepository {
    base: BaseRepository,
}

#[async_trait]
impl Repository for PrivilegedActionRepository {
    fn new(connection: Connection) -> Self {
        Self {
            base: BaseRepository::new(connection, DatabaseObject::PrivilegedAction),
        }
    }

    fn get_database_object(&self) -> DatabaseObject {
        self.base.get_database_object()
    }

    fn get_connection(&self) -> &Connection {
        self.base.get_connection()
    }

    async fn table_exists(&self) -> Result<bool, DatabaseError> {
        let table_name = self.get_table_name().await?;
        Ok(self
            .get_connection()
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
            // Account ID GSI
            GlobalSecondaryIndexDef {
                name: ACCOUNT_IDX.to_string(),
                pk: TableKey {
                    name: ACCOUNT_IDX_PARTITION_KEY.to_string(),
                    key_type: KeyType::Hash,
                    attribute_type: ScalarAttributeType::S,
                },
                sk: Some(TableKey {
                    name: ACCOUNT_IDX_SORT_KEY.to_string(),
                    key_type: KeyType::Range,
                    attribute_type: ScalarAttributeType::S,
                }),
            },
            // Cancellation Token GSI
            GlobalSecondaryIndexDef {
                name: CANCELLATION_TOKEN_IDX.to_string(),
                pk: TableKey {
                    name: CANCELLATION_TOKEN_IDX_PARTITION_KEY.to_string(),
                    key_type: KeyType::Hash,
                    attribute_type: ScalarAttributeType::S,
                },
                sk: None, // No sort key for this GSI
            },
            // Web Auth Token GSI
            GlobalSecondaryIndexDef {
                name: WEB_AUTH_TOKEN_IDX.to_string(),
                pk: TableKey {
                    name: WEB_AUTH_TOKEN_IDX_PARTITION_KEY.to_string(),
                    key_type: KeyType::Hash,
                    attribute_type: ScalarAttributeType::S,
                },
                sk: None, // No sort key for this GSI
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
