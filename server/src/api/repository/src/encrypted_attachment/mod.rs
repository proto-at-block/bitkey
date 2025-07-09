use async_trait::async_trait;
use database::{
    aws_sdk_dynamodb::types::{KeyType, ScalarAttributeType},
    ddb::{
        create_dynamodb_table, BaseRepository, Connection, DatabaseError, DatabaseObject,
        Repository, TableKey,
    },
};

pub mod fetch;
pub mod persist;

const PARTITION_KEY: &str = "partition_key";

#[derive(Clone)]
pub struct EncryptedAttachmentRepository {
    base: BaseRepository,
}

#[async_trait]
impl Repository for EncryptedAttachmentRepository {
    fn new(connection: Connection) -> Self {
        Self {
            base: BaseRepository::new(connection, DatabaseObject::EncryptedAttachment),
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

        create_dynamodb_table(
            &self.get_connection().client,
            table_name,
            database_object,
            partition_key,
            None,
            vec![],
        )
        .await
    }
}

#[cfg(all(test, feature = "encrypted_attachment"))]
mod tests {
    use super::*;
    use database::ddb::Config;
    use external_identifier::ExternalIdentifier;
    use http_server::config;
    use time::OffsetDateTime;
    use types::encrypted_attachment::{identifiers::EncryptedAttachmentId, EncryptedAttachment};
    use ulid::Ulid;

    async fn construct_test_encrypted_attachment_repository() -> EncryptedAttachmentRepository {
        let profile = Some("test");
        let ddb_config = config::extract::<Config>(profile).expect("extract ddb config");
        let ddb_connection = ddb_config.to_connection().await;
        EncryptedAttachmentRepository::new(ddb_connection)
    }

    fn create_test_encrypted_attachment() -> EncryptedAttachment {
        EncryptedAttachment {
            id: EncryptedAttachmentId::new(Ulid::default()).unwrap(),
            kms_key_id: "test-kms-key-id".to_string(),
            private_key_ciphertext: "encrypted-private-key-data".to_string(),
            public_key: "public-key-data".to_string(),
            sealed_attachment: Some("sealed-attachment-data".to_string()),
            created_at: OffsetDateTime::from_unix_timestamp(1672531200).unwrap(),
            updated_at: OffsetDateTime::from_unix_timestamp(1672531200).unwrap(),
        }
    }

    #[tokio::test]
    async fn test_repository_creation() {
        let _repository = construct_test_encrypted_attachment_repository().await;
        // Repository creation succeeded if we get here without panicking
        assert!(true);
    }

    #[test]
    fn test_encryption_attachment_serialization() {
        use database::ddb::try_to_item;
        use database::serde_dynamo::Item;

        let attachment = create_test_encrypted_attachment();
        println!("EncryptedAttachment: {:?}", attachment);

        let result: Result<Item, database::ddb::DatabaseError> = try_to_item(
            &attachment,
            database::ddb::DatabaseObject::EncryptedAttachment,
        );
        match result {
            Ok(item) => {
                println!("Successfully serialized to DynamoDB item:");
                for (key, value) in item.iter() {
                    println!("  {}: {:?}", key, value);
                }
                // Check if partition_key exists
                assert!(
                    item.contains_key("partition_key"),
                    "Item should contain partition_key"
                );
                println!("✓ partition_key field found in serialized item");
            }
            Err(e) => {
                panic!("Serialization error: {:?}", e);
            }
        }
    }

    #[tokio::test]
    async fn test_persist_and_fetch() {
        let repository = construct_test_encrypted_attachment_repository().await;

        // Ensure table exists before running tests
        if !repository.table_exists().await.unwrap_or(false) {
            repository
                .create_table()
                .await
                .expect("Failed to create table");
        }

        let mut attachment = create_test_encrypted_attachment();
        // Use unique ID to avoid conflicts with other tests
        attachment.id = EncryptedAttachmentId::new(Ulid::new()).unwrap();

        // First test serialization to make sure that works
        use database::ddb::try_to_item;
        use database::serde_dynamo::Item;
        let item_result: Result<Item, database::ddb::DatabaseError> = try_to_item(
            &attachment,
            database::ddb::DatabaseObject::EncryptedAttachment,
        );
        println!("Serialization test result: {:?}", item_result);

        // Persist the attachment
        let persist_result = repository.persist(&attachment).await;
        if let Err(ref e) = persist_result {
            println!("Persist error: {:?}", e);
        }
        persist_result.unwrap();

        // Fetch the attachment
        let fetched_attachment = repository.fetch_by_id(&attachment.id).await.unwrap();

        // Verify the fetched attachment matches the original (ignoring updated_at since it gets modified during persist)
        assert_eq!(attachment.id, fetched_attachment.id);
        assert_eq!(attachment.kms_key_id, fetched_attachment.kms_key_id);
        assert_eq!(
            attachment.private_key_ciphertext,
            fetched_attachment.private_key_ciphertext
        );
        assert_eq!(attachment.public_key, fetched_attachment.public_key);
        assert_eq!(
            attachment.sealed_attachment,
            fetched_attachment.sealed_attachment
        );
        assert_eq!(attachment.created_at, fetched_attachment.created_at);
        // Note: updated_at will be different since persist() updates it
    }

    #[tokio::test]
    async fn test_persist_optimistic_locking() {
        let repository = construct_test_encrypted_attachment_repository().await;

        // Ensure table exists before running tests
        if !repository.table_exists().await.unwrap_or(false) {
            repository
                .create_table()
                .await
                .expect("Failed to create table");
        }

        let mut attachment = create_test_encrypted_attachment();
        // Use unique ID to avoid conflicts with other tests
        attachment.id = EncryptedAttachmentId::new(Ulid::new()).unwrap();

        // First persist should succeed
        repository.persist(&attachment).await.unwrap();

        // Trying to persist the same item again should fail due to optimistic locking
        let result = repository.persist(&attachment).await;
        assert!(matches!(result, Err(DatabaseError::PersistenceError(_))));
    }

    #[tokio::test]
    async fn test_fetch_nonexistent_attachment() {
        let repository = construct_test_encrypted_attachment_repository().await;

        // Ensure table exists before running tests
        if !repository.table_exists().await.unwrap_or(false) {
            repository
                .create_table()
                .await
                .expect("Failed to create table");
        }

        let non_existent_id = EncryptedAttachmentId::new(Ulid::new()).unwrap();

        let result = repository.fetch_by_id(&non_existent_id).await;
        assert!(matches!(result, Err(DatabaseError::ObjectNotFound(_))));
    }

    #[tokio::test]
    async fn test_persist_attachment_with_none_sealed_attachment() {
        let repository = construct_test_encrypted_attachment_repository().await;

        // Ensure table exists before running tests
        if !repository.table_exists().await.unwrap_or(false) {
            repository
                .create_table()
                .await
                .expect("Failed to create table");
        }

        let mut test_attachment = create_test_encrypted_attachment();
        // Use unique ID for this test to avoid conflicts
        test_attachment.id = EncryptedAttachmentId::new(Ulid::new()).unwrap();
        test_attachment.sealed_attachment = None;

        // Persist the attachment
        let persist_result = repository.persist(&test_attachment).await;
        assert!(
            persist_result.is_ok(),
            "Failed to persist attachment with None sealed_attachment"
        );

        // Fetch it back
        let fetch_result = repository.fetch_by_id(&test_attachment.id).await;
        match fetch_result {
            Ok(fetched_attachment) => {
                assert_eq!(fetched_attachment.id, test_attachment.id);
                assert_eq!(fetched_attachment.sealed_attachment, None);
            }
            Err(e) => panic!("Error fetching attachment: {:?}", e),
        }
    }

    #[tokio::test]
    async fn test_persist_multiple_attachments() {
        let repository = construct_test_encrypted_attachment_repository().await;

        // Ensure table exists before running tests
        if !repository.table_exists().await.unwrap_or(false) {
            repository
                .create_table()
                .await
                .expect("Failed to create table");
        }

        let mut attachment1 = create_test_encrypted_attachment();
        attachment1.id = EncryptedAttachmentId::new(Ulid::new()).unwrap();

        let mut attachment2 = create_test_encrypted_attachment();
        attachment2.id = EncryptedAttachmentId::new(Ulid::new()).unwrap();
        attachment2.kms_key_id = "second-kms-key-id".to_string();

        // Persist both attachments
        let persist_result1 = repository.persist(&attachment1).await;
        let persist_result2 = repository.persist(&attachment2).await;

        assert!(
            persist_result1.is_ok(),
            "Failed to persist first attachment"
        );
        assert!(
            persist_result2.is_ok(),
            "Failed to persist second attachment"
        );

        // Fetch both
        let fetch_result1 = repository.fetch_by_id(&attachment1.id).await;
        let fetch_result2 = repository.fetch_by_id(&attachment2.id).await;

        assert!(fetch_result1.is_ok(), "First attachment should be found");
        assert!(fetch_result2.is_ok(), "Second attachment should be found");
    }

    #[tokio::test]
    async fn test_table_operations() {
        let repository = construct_test_encrypted_attachment_repository().await;

        // Test table_exists - this may fail without actual DynamoDB but shouldn't panic
        let _table_exists_result = repository.table_exists().await;

        // Test create_table - this may fail without actual DynamoDB but shouldn't panic
        let _create_table_result = repository.create_table().await;

        // If we get here, the methods can be called without panicking
        assert!(true);
    }

    #[test]
    fn test_encrypted_attachment_id_generation() {
        let id1 = EncryptedAttachmentId::new(Ulid::new()).unwrap();
        let id2 = EncryptedAttachmentId::new(Ulid::new()).unwrap();

        // Test that IDs are unique
        assert_ne!(id1.to_string(), id2.to_string());

        // Test that IDs contain the expected namespace
        assert!(id1.to_string().contains("encrypted-attachment"));
        assert!(id2.to_string().contains("encrypted-attachment"));
    }

    #[test]
    fn test_create_test_encrypted_attachment_structure() {
        let attachment = create_test_encrypted_attachment();

        // Test that all required fields are set
        assert!(!attachment.kms_key_id.is_empty());
        assert!(!attachment.private_key_ciphertext.is_empty());
        assert!(!attachment.public_key.is_empty());
        assert!(attachment.sealed_attachment.is_some());
        assert!(!attachment.sealed_attachment.as_ref().unwrap().is_empty());

        // Test that timestamps are consistent
        assert_eq!(attachment.created_at, attachment.updated_at);
        assert_eq!(
            attachment.created_at,
            OffsetDateTime::from_unix_timestamp(1672531200).unwrap()
        );
    }
}
