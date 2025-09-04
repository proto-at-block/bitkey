use crate::service::social::relationship::Service;
use async_trait::async_trait;
use migration::{Migration, MigrationError};
use repository::recovery::social::SocialRecoveryRepository;
use tracing::info;
use types::recovery::social::relationship::RecoveryRelationship;

const IS_TEST_RUN: bool = true;

pub(crate) struct AddExpiryToPendingInvitations {
    pub repository: SocialRecoveryRepository,
}

impl AddExpiryToPendingInvitations {
    #[allow(dead_code)]
    pub fn new(service: Service) -> Self {
        Self {
            repository: service.repository,
        }
    }
}

#[async_trait]
impl Migration for AddExpiryToPendingInvitations {
    fn name(&self) -> &str {
        "20250712_add_expiry_to_pending_invitations"
    }

    async fn run(&self) -> Result<(), MigrationError> {
        let pending_relationships = self
            .repository
            .fetch_pending_invitations()
            .await
            .map_err(|err| MigrationError::CantEnumerateTable(err.to_string()))?;

        info!("AddExpiryToPendingInvitations migration started. Adding expiry to pending invitations.");

        let mut updated_count = 0;
        for relationship in pending_relationships {
            let invitation = match relationship {
                RecoveryRelationship::Invitation(invitation) => invitation,
                _ => continue,
            };

            let updated_relationship = invitation.backfill_expiring_at();

            if IS_TEST_RUN {
                info!(
                    "Updating invitation expiring_at {expiring_at:?} with partition key {pkey:?}",
                    pkey = invitation.common_fields.id.to_string(),
                    expiring_at = updated_relationship.expiring_at
                );
            } else {
                self.repository
                    .persist_recovery_relationship(&RecoveryRelationship::Invitation(
                        updated_relationship,
                    ))
                    .await
                    .map_err(MigrationError::DbPersist)?;

                updated_count += 1;
            }
        }

        info!("AddExpiryToPendingInvitations migration completed. Updated {updated_count} rows.");
        Ok(())
    }
}
