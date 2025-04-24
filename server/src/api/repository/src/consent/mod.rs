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
const SORT_KEY: &str = "sort_key";

const EMAIL_IDX: &str = "email_address_to_created_at";
const EMAIL_IDX_PARTITION_KEY: &str = "email_address";
const EMAIL_IDX_SORT_KEY: &str = "created_at";

#[derive(Clone)]
pub struct ConsentRepository {
    base: BaseRepository,
}

#[async_trait]
impl Repository for ConsentRepository {
    fn new(connection: Connection) -> Self {
        Self {
            base: BaseRepository::new(connection, DatabaseObject::Consent),
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

        let sort_key = TableKey {
            name: SORT_KEY.to_string(),
            key_type: KeyType::Range,
            attribute_type: ScalarAttributeType::S,
        };

        let gsis: Vec<GlobalSecondaryIndexDef> = vec![
            // Email GSI
            GlobalSecondaryIndexDef {
                name: EMAIL_IDX.to_string(),
                pk: TableKey {
                    name: EMAIL_IDX_PARTITION_KEY.to_string(),
                    key_type: KeyType::Hash,
                    attribute_type: ScalarAttributeType::S,
                },
                sk: Some(TableKey {
                    name: EMAIL_IDX_SORT_KEY.to_string(),
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
            Some(sort_key),
            gsis,
        )
        .await
    }
}
