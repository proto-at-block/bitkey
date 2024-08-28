use crate::service::social::relationship::Service;
use async_trait::async_trait;
use migration::{Migration, MigrationError};
use repository::recovery::social::Repository;
use time::format_description::well_known::Rfc3339;
use time::OffsetDateTime;
use tracing::info;

const GUARD_DATE: &str = "2024-03-11T00:00:00Z";
const IS_TEST_RUN: bool = false;

pub(crate) struct CleanupOldInvalidData {
    pub repository: Repository,
}

impl CleanupOldInvalidData {
    #[allow(dead_code)]
    pub fn new(service: Service) -> Self {
        Self {
            repository: service.repository,
        }
    }
}

#[async_trait]
impl Migration for CleanupOldInvalidData {
    fn name(&self) -> &str {
        "20240805_cleanup_old_invalid_socrec_data"
    }

    async fn run(&self) -> Result<(), MigrationError> {
        let date = OffsetDateTime::parse(GUARD_DATE, &Rfc3339).expect("valid date");
        let raw_row_data = self
            .repository
            .fetch_invalid_relationships_before_date(&date)
            .await
            .map_err(|err| MigrationError::CantEnumerateTable(err.to_string()))?;

        info!("CleanupOldInvalidData migration started. Deleting invalid rows.");

        let mut deleted_count = 0;

        for row_data in raw_row_data {
            let partition_key =
                row_data
                    .get("partition_key")
                    .ok_or(MigrationError::MissingCriticalField(
                        "SocialRecovery".to_string(),
                        "partition_key".to_string(),
                    ))?;
            if IS_TEST_RUN {
                info!(
                    "Would delete row with partition key {pkey:?} and created_at {created_at:?}",
                    pkey = partition_key,
                    created_at = row_data.get("created_at")
                );
            } else {
                self.repository
                    .delete_row_by_partition_key(partition_key, &date)
                    .await
                    .map_err(MigrationError::DbPersist)?;

                deleted_count += 1;
            }
        }

        info!("CleanupOldInvalidData migration completed. Deleted {deleted_count} rows.");

        Ok(())
    }
}
