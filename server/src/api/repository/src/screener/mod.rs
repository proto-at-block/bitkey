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

#[derive(Clone)]
pub struct ScreenerRepository {
    base: BaseRepository,
}

#[async_trait]
impl Repository for ScreenerRepository {
    fn new(connection: Connection) -> Self {
        Self {
            base: BaseRepository::new(connection, DatabaseObject::SanctionsScreener),
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

        let gsis: Vec<GlobalSecondaryIndexDef> = vec![];

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
