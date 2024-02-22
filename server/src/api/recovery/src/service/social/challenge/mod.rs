use repository::recovery::social::Repository;

use notification::service::Service as NotificationService;

use super::relationship::Service as RecoveryRelationshipService;

pub mod create_social_challenge;
pub mod error;
pub mod fetch_social_challenge;
pub mod respond_to_social_challenge;

#[derive(Clone)]
pub struct Service {
    pub repository: Repository,
    pub recovery_relationship_service: RecoveryRelationshipService,
    pub notification_service: NotificationService,
}

impl Service {
    #[must_use]
    pub fn new(
        repository: Repository,
        recovery_relationship_service: RecoveryRelationshipService,
        notification_service: NotificationService,
    ) -> Self {
        Self {
            repository,
            recovery_relationship_service,
            notification_service,
        }
    }
}
