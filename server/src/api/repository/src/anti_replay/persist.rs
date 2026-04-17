use database::ddb::{DatabaseError, Repository};
use database::serde_dynamo;
use serde::Serialize;
use tracing::{event, Level};

use super::AntiReplayRepository;

#[derive(Serialize)]
struct AntiReplayEntry {
    content_hash: String,
    expiring_at: i64,
    created_at: String,
    /// JSON-serialized response from the original successful request.
    /// Returned on idempotent replay instead of re-executing the closure.
    cached_response: String,
}

impl AntiReplayRepository {
    /// Persist a content hash as "burned" in the anti-replay cache.
    ///
    /// Uses a conditional write to ensure we only insert if the entry doesn't already exist.
    /// This provides atomic check-and-burn semantics.
    ///
    /// Returns `Ok(true)` if the entry was inserted (first use).
    /// Returns `Ok(false)` if the entry already existed (replay).
    pub async fn burn(
        &self,
        content_hash_hex: &str,
        expiring_at_epoch_secs: i64,
        created_at: &str,
        response_json: &str,
    ) -> Result<bool, DatabaseError> {
        let table_name = self.get_table_name().await?;

        let entry = AntiReplayEntry {
            content_hash: content_hash_hex.to_string(),
            expiring_at: expiring_at_epoch_secs,
            created_at: created_at.to_string(),
            cached_response: response_json.to_string(),
        };

        let item = serde_dynamo::to_item(&entry).map_err(|e| {
            event!(Level::ERROR, "Failed to serialize anti-replay entry: {e:?}");
            DatabaseError::PersistenceError(self.get_database_object())
        })?;

        let result = self
            .get_connection()
            .client
            .put_item()
            .table_name(table_name)
            .set_item(Some(item))
            // Only insert if content_hash doesn't already exist — atomic dedup
            .condition_expression("attribute_not_exists(content_hash)")
            .send()
            .await;

        match result {
            Ok(_) => Ok(true),
            Err(err) => {
                let service_err = err.into_service_error();
                if service_err.is_conditional_check_failed_exception() {
                    // Entry already exists — this is a replay
                    Ok(false)
                } else {
                    event!(
                        Level::ERROR,
                        "Failed to burn anti-replay entry: {service_err:?}"
                    );
                    Err(DatabaseError::PersistenceError(self.get_database_object()))
                }
            }
        }
    }
}
