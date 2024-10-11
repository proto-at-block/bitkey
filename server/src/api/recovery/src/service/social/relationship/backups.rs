use super::{error::ServiceError, Service};
use time::OffsetDateTime;
use types::account::identifiers::AccountId;
use types::recovery::backup::Backup;

impl Service {
    pub async fn upload_recovery_backup(
        &self,
        account_id: AccountId,
        material: String,
    ) -> Result<Backup, ServiceError> {
        let backup = Backup {
            account_id,
            material,

            created_at: OffsetDateTime::now_utc(),
            updated_at: OffsetDateTime::now_utc(),
        };

        self.repository
            .persist_recovery_backup(&backup)
            .await
            .map_err(ServiceError::Database)
    }
}
