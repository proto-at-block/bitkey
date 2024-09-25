use account::service::Service as AccountService;
use notification::service::Service as NotificationService;
use repository::recovery::inheritance::InheritanceRepository;

use super::social::relationship::Service as RecoveryRelationshipService;

pub mod cancel_inheritance_claim;
pub mod create_inheritance_claim;
pub mod packages;

mod error;
pub mod get_inheritance_claims;

#[derive(Clone)]
pub struct Service {
    pub repository: InheritanceRepository,
    pub recovery_relationship_service: RecoveryRelationshipService,
    pub notification_service: NotificationService,
    pub account_service: AccountService,
}

impl Service {
    #[must_use]
    pub fn new(
        repository: InheritanceRepository,
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
