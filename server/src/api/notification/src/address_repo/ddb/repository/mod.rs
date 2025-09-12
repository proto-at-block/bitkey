use async_trait::async_trait;
use database::{
    aws_sdk_dynamodb::types::{KeyType, ScalarAttributeType},
    ddb::{
        create_dynamodb_table, BaseRepository, Connection, DatabaseError, DatabaseObject,
        GlobalSecondaryIndexDef, Repository, TableKey,
    },
};

mod delete;
mod fetch;
mod persist;

const HASH_KEY: &str = "address";

// GSI for looking up addresses by account_id
pub const ACCOUNT_ID_INDEX: &str = "by_account_id";
pub const ACCOUNT_ID_KEY: &str = "account_id";

#[derive(Clone)]
pub struct AddressRepository {
    base: BaseRepository,
}

#[async_trait]
impl Repository for AddressRepository {
    fn new(connection: Connection) -> Self {
        Self {
            base: BaseRepository::new(connection, DatabaseObject::AddressWatchlist),
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
            name: HASH_KEY.to_string(),
            key_type: KeyType::Hash,
            attribute_type: ScalarAttributeType::S,
        };

        let gsis = vec![GlobalSecondaryIndexDef {
            name: ACCOUNT_ID_INDEX.to_string(),
            pk: TableKey {
                name: ACCOUNT_ID_KEY.to_string(),
                key_type: KeyType::Hash,
                attribute_type: ScalarAttributeType::S,
            },
            sk: None,
        }];

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
