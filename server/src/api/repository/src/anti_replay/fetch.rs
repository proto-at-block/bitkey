use database::aws_sdk_dynamodb::types::AttributeValue;
use database::ddb::{DatabaseError, Repository};
use tracing::{event, Level};

use super::AntiReplayRepository;

impl AntiReplayRepository {
    /// Check if a content hash has been burned (already used).
    ///
    /// Returns `Ok(Some(response_json))` if the entry exists (replay) — the cached
    /// response from the original successful request is returned for idempotent replay.
    /// Returns `Ok(None)` if not found (fresh request).
    pub async fn exists(&self, content_hash_hex: &str) -> Result<Option<String>, DatabaseError> {
        let table_name = self.get_table_name().await?;

        let result = self
            .get_connection()
            .client
            .get_item()
            .table_name(table_name)
            .consistent_read(true)
            .key(
                "content_hash",
                AttributeValue::S(content_hash_hex.to_string()),
            )
            .projection_expression("content_hash, cached_response")
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Failed to check anti-replay cache: {service_err:?}"
                );
                DatabaseError::FetchError(self.get_database_object())
            })?;

        match result.item() {
            Some(item) => {
                // Extract the cached response JSON from the DynamoDB item.
                // Every entry written by burn() includes a cached_response field.
                let response = item
                    .get("cached_response")
                    .and_then(|v| match v {
                        AttributeValue::S(s) => Some(s.clone()),
                        _ => None,
                    })
                    .ok_or_else(|| {
                        event!(
                            Level::ERROR,
                            content_hash = %content_hash_hex,
                            "Anti-replay entry exists but is missing the cached_response field"
                        );
                        DatabaseError::FetchError(self.get_database_object())
                    })?;
                Ok(Some(response))
            }
            None => Ok(None),
        }
    }
}
