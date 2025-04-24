use async_trait::async_trait;
use database::{
    aws_sdk_dynamodb::types::{KeyType, ScalarAttributeType},
    ddb::{
        create_dynamodb_table, BaseRepository, Connection, DatabaseError, DatabaseObject,
        GlobalSecondaryIndexDef, Repository, TableKey, Upsertable,
    },
};
use serde::{Deserialize, Serialize};

use types::recovery::inheritance::{
    claim::InheritanceClaim, package::Package as InheritancePackage,
};

pub mod fetch;
pub mod persist;

pub(super) const PARTITION_KEY: &str = "partition_key";
pub(super) const IDX_SORT_KEY: &str = "created_at";

pub(super) const RECOVERY_RELATIONSHIP_ID_IDX: &str = "by_recovery_relationship_id";
pub(super) const RECOVERY_RELATIONSHIP_ID_IDX_PARTITION_KEY: &str = "recovery_relationship_id";

pub(super) const BENEFACTOR_ACCOUNT_ID_IDX: &str = "by_benefactor_account_id_to_created_at";
pub(super) const BENEFACTOR_ACCOUNT_ID_IDX_PARTITION_KEY: &str = "benefactor_account_id";

pub(super) const BENEFICIARY_ACCOUNT_ID_IDX: &str = "by_beneficiary_account_id_to_created_at";
pub(super) const BENEFICIARY_ACCOUNT_ID_IDX_PARTITION_KEY: &str = "beneficiary_account_id";

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
    base: BaseRepository,
}

#[async_trait]
impl Repository for InheritanceRepository {
    fn new(connection: Connection) -> Self {
        Self {
            base: BaseRepository::new(connection, DatabaseObject::Inheritance),
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
            // Recovery Relationship ID GSI
            GlobalSecondaryIndexDef {
                name: RECOVERY_RELATIONSHIP_ID_IDX.to_string(),
                pk: TableKey {
                    name: RECOVERY_RELATIONSHIP_ID_IDX_PARTITION_KEY.to_string(),
                    key_type: KeyType::Hash,
                    attribute_type: ScalarAttributeType::S,
                },
                sk: Some(TableKey {
                    name: IDX_SORT_KEY.to_string(),
                    key_type: KeyType::Range,
                    attribute_type: ScalarAttributeType::S,
                }),
            },
            // Benefactor Account ID GSI
            GlobalSecondaryIndexDef {
                name: BENEFACTOR_ACCOUNT_ID_IDX.to_string(),
                pk: TableKey {
                    name: BENEFACTOR_ACCOUNT_ID_IDX_PARTITION_KEY.to_string(),
                    key_type: KeyType::Hash,
                    attribute_type: ScalarAttributeType::S,
                },
                sk: Some(TableKey {
                    name: IDX_SORT_KEY.to_string(),
                    key_type: KeyType::Range,
                    attribute_type: ScalarAttributeType::S,
                }),
            },
            // Beneficiary Account ID GSI
            GlobalSecondaryIndexDef {
                name: BENEFICIARY_ACCOUNT_ID_IDX.to_string(),
                pk: TableKey {
                    name: BENEFICIARY_ACCOUNT_ID_IDX_PARTITION_KEY.to_string(),
                    key_type: KeyType::Hash,
                    attribute_type: ScalarAttributeType::S,
                },
                sk: Some(TableKey {
                    name: IDX_SORT_KEY.to_string(),
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
