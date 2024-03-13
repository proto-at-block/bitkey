use account::service::Service as AccountService;
use notification::service::Service as NotificationService;
use repository::recovery::social::Repository;

use super::relationship::Service as RecoveryRelationshipService;

pub mod clear_social_challenges;
pub mod create_social_challenge;
pub mod error;
pub mod fetch_social_challenge;
pub mod respond_to_social_challenge;

#[derive(Clone)]
pub struct Service {
    pub repository: Repository,
    pub recovery_relationship_service: RecoveryRelationshipService,
    pub notification_service: NotificationService,
    pub account_service: AccountService,
}

impl Service {
    #[must_use]
    pub fn new(
        repository: Repository,
        recovery_relationship_service: RecoveryRelationshipService,
        notification_service: NotificationService,
        account_service: AccountService,
    ) -> Self {
        Self {
            repository,
            recovery_relationship_service,
            notification_service,
            account_service,
        }
    }
}
