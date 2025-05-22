use async_trait::async_trait;
use database::{
    aws_sdk_dynamodb::types::{KeyType, ScalarAttributeType},
    ddb::{
        create_dynamodb_table, BaseRepository, Connection, DatabaseError, DatabaseObject,
        GlobalSecondaryIndexDef, Repository, TableKey, Upsertable,
    },
};
use serde::{Deserialize, Serialize};
use types::recovery::{
    backup::Backup,
    social::{challenge::SocialChallenge, relationship::RecoveryRelationship},
};

pub mod delete;
pub mod fetch;
pub mod persist;

pub(super) const PARTITION_KEY: &str = "partition_key";

pub(super) const CUSTOMER_IDX: &str = "customer_account_id_to_created_at";
pub(super) const CUSTOMER_IDX_PARTITION_KEY: &str = "customer_account_id";
const CUSTOMER_IDX_SORT_KEY: &str = "created_at";

pub(super) const TRUSTED_CONTACT_IDX: &str = "trusted_contact_account_id_to_customer_account_id";
pub(super) const TRUSTED_CONTACT_IDX_PARTITION_KEY: &str = "trusted_contact_account_id";
const TRUSTED_CONTACT_IDX_SORT_KEY: &str = "customer_account_id";

pub(super) const CODE_IDX: &str = "by_code";
pub(super) const CODE_IDX_PARTITION_KEY: &str = "code";

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(tag = "_SocialRecoveryRow_type")]
enum SocialRecoveryRow {
    Relationship(RecoveryRelationship),
    Challenge(SocialChallenge),
    Backup(Backup),
}

impl Upsertable for SocialRecoveryRow {
    const KEY_PROPERTIES: &'static [&'static str] = &["partition_key"];
    const IF_NOT_EXISTS_PROPERTIES: &'static [&'static str] = &["created_at"];
}

#[derive(Clone)]
pub struct SocialRecoveryRepository {
    base: BaseRepository,
}

#[async_trait]
impl Repository for SocialRecoveryRepository {
    fn new(connection: Connection) -> Self {
        Self {
            base: BaseRepository::new(connection, DatabaseObject::SocialRecovery),
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
            // Customer Index GSI
            GlobalSecondaryIndexDef {
                name: CUSTOMER_IDX.to_string(),
                pk: TableKey {
                    name: CUSTOMER_IDX_PARTITION_KEY.to_string(),
                    key_type: KeyType::Hash,
                    attribute_type: ScalarAttributeType::S,
                },
                sk: Some(TableKey {
                    name: CUSTOMER_IDX_SORT_KEY.to_string(),
                    key_type: KeyType::Range,
                    attribute_type: ScalarAttributeType::S,
                }),
            },
            // Recovery Contact Index GSI
            GlobalSecondaryIndexDef {
                name: TRUSTED_CONTACT_IDX.to_string(),
                pk: TableKey {
                    name: TRUSTED_CONTACT_IDX_PARTITION_KEY.to_string(),
                    key_type: KeyType::Hash,
                    attribute_type: ScalarAttributeType::S,
                },
                sk: Some(TableKey {
                    name: TRUSTED_CONTACT_IDX_SORT_KEY.to_string(),
                    key_type: KeyType::Range,
                    attribute_type: ScalarAttributeType::S,
                }),
            },
            // Code Index GSI
            GlobalSecondaryIndexDef {
                name: CODE_IDX.to_string(),
                pk: TableKey {
                    name: CODE_IDX_PARTITION_KEY.to_string(),
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
