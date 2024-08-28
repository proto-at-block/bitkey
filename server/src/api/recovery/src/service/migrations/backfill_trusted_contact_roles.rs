use crate::service::social::relationship::Service;
use async_trait::async_trait;
use migration::{Migration, MigrationError};
use repository::recovery::social::Repository;
use tracing::info;
use types::recovery::trusted_contacts::TrustedContactRole;

pub(crate) struct BackfillTrustedContactRoles {
    pub repository: Repository,
}

impl BackfillTrustedContactRoles {
    #[allow(dead_code)]
    pub fn new(service: Service) -> Self {
        Self {
            repository: service.repository,
        }
    }
}

#[async_trait]
impl Migration for BackfillTrustedContactRoles {
    fn name(&self) -> &str {
        "20240806_backfill_trusted_contact_roles"
    }

    async fn run(&self) -> Result<(), MigrationError> {
        let all_recovery_relationships = self
            .repository
            .fetch_recovery_relationships_without_roles()
            .await
            .map_err(|err| MigrationError::CantEnumerateTable(err.to_string()))?;

        let mut updated_count = 0;

        for relationship in all_recovery_relationships {
            let trusted_contact_roles = vec![TrustedContactRole::SocialRecoveryContact];

            let mut updated_common_fields = relationship.common_fields().clone();
            let trusted_contact_info = &mut updated_common_fields.trusted_contact_info;

            trusted_contact_info.roles = trusted_contact_roles;
            updated_common_fields.trusted_contact_info = trusted_contact_info.to_owned();
            let updated_relationship = relationship.with_common_fields(&updated_common_fields);

            self.repository
                .persist_recovery_relationship(&updated_relationship)
                .await
                .map_err(MigrationError::DbPersist)?;

            updated_count += 1;
        }

        info!(
            "BackfillTrustedContactRoles migration completed. Updated {updated_count} relationships."
        );

        Ok(())
    }
}
