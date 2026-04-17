use async_trait::async_trait;
use database::{
    aws_sdk_dynamodb::types::{KeyType, ScalarAttributeType, TimeToLiveSpecification},
    ddb::{
        create_dynamodb_table, BaseRepository, Connection, DatabaseError, DatabaseObject,
        Repository, TableKey,
    },
};

pub mod fetch;
pub mod persist;

const PARTITION_KEY: &str = "content_hash";

#[derive(Clone)]
pub struct AntiReplayRepository {
    base: BaseRepository,
}

#[async_trait]
impl Repository for AntiReplayRepository {
    fn new(connection: Connection) -> Self {
        Self {
            base: BaseRepository::new(connection, DatabaseObject::ActionProofAntiReplay),
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

        create_dynamodb_table(
            &self.get_connection().client,
            table_name.clone(),
            database_object,
            partition_key,
            None,
            vec![],
        )
        .await?;

        // Enable TTL on the expiring_at attribute for automatic cleanup
        self.get_connection()
            .client
            .update_time_to_live()
            .set_time_to_live_specification(Some(
                TimeToLiveSpecification::builder()
                    .enabled(true)
                    .attribute_name("expiring_at")
                    .build()
                    .map_err(|_| DatabaseError::TimeToLiveSpecification(database_object))?,
            ))
            .table_name(table_name)
            .send()
            .await
            .map_err(|_| DatabaseError::TimeToLiveSpecification(database_object))?;

        Ok(())
    }
}

#[cfg(all(test, feature = "anti_replay"))]
mod tests {
    use super::*;
    use database::ddb::Config;
    use tokio::sync::OnceCell;

    static INIT: OnceCell<()> = OnceCell::const_new();

    async fn create_test_repository() -> AntiReplayRepository {
        let profile = Some("test");
        let ddb_config =
            http_server::config::extract::<Config>(profile).expect("extract ddb config");
        let ddb_connection = ddb_config.to_connection().await;
        AntiReplayRepository::new(ddb_connection)
    }

    async fn ensure_table() {
        INIT.get_or_init(|| async {
            let repo = create_test_repository().await;
            let _ = repo.create_table().await;
        })
        .await;
    }

    /// Generate a unique hash hex for test isolation.
    fn unique_hash(label: &str) -> String {
        format!("{}-{}", label, ulid::Ulid::new())
    }

    // -- Repository-level anti-replay tests --
    // These test the DynamoDB conditional-write and consistent-read semantics
    // without coupling to any specific route or service.

    #[tokio::test]
    async fn test_burn_fresh_hash_succeeds() {
        ensure_table().await;
        let repo = create_test_repository().await;
        let hash = unique_hash("fresh");

        let result = repo
            .burn(&hash, 9999999999, "test", r#"{"status":"ok"}"#)
            .await
            .unwrap();
        assert!(result, "Burning a fresh hash should return true");
    }

    #[tokio::test]
    async fn test_burn_duplicate_hash_returns_false() {
        ensure_table().await;
        let repo = create_test_repository().await;
        let hash = unique_hash("dup");

        let first = repo
            .burn(&hash, 9999999999, "test", r#"{"status":"ok"}"#)
            .await
            .unwrap();
        assert!(first, "First burn should succeed");

        let second = repo
            .burn(&hash, 9999999999, "test", r#"{"status":"ok"}"#)
            .await
            .unwrap();
        assert!(
            !second,
            "Second burn of same hash should return false (conditional write)"
        );
    }

    #[tokio::test]
    async fn test_exists_returns_none_for_unknown_hash() {
        ensure_table().await;
        let repo = create_test_repository().await;
        let hash = unique_hash("unknown");

        let cached = repo.exists(&hash).await.unwrap();
        assert!(cached.is_none(), "A never-burned hash should return None");
    }

    #[tokio::test]
    async fn test_exists_returns_cached_response_after_burn() {
        ensure_table().await;
        let repo = create_test_repository().await;
        let hash = unique_hash("burned");
        let response_json = r#"{"data":"hello"}"#;

        repo.burn(&hash, 9999999999, "test", response_json)
            .await
            .unwrap();

        let cached = repo.exists(&hash).await.unwrap();
        assert!(
            cached.is_some(),
            "A burned hash should return the cached response"
        );
        assert_eq!(
            cached.unwrap(),
            response_json,
            "Cached response should match what was stored during burn"
        );
    }

    #[tokio::test]
    async fn test_different_hashes_are_independent() {
        ensure_table().await;
        let repo = create_test_repository().await;
        let hash_a = unique_hash("a");
        let hash_b = unique_hash("b");

        repo.burn(&hash_a, 9999999999, "test", r#"{"status":"ok"}"#)
            .await
            .unwrap();

        let a_cached = repo.exists(&hash_a).await.unwrap();
        let b_cached = repo.exists(&hash_b).await.unwrap();
        assert!(a_cached.is_some(), "Hash A should exist after burn");
        assert!(b_cached.is_none(), "Hash B should NOT exist (independent)");
    }
}
