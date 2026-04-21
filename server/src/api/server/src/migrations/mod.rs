use account::service::Service as AccountService;
use migration::{MigratableService, Migration};
use repository::public_key::PublicKeyRepository;

use self::backfill_hw_auth_public_keys::BackfillHwAuthPublicKeys;

mod backfill_hw_auth_public_keys;

pub struct CrossServiceMigrationService {
    pub account_service: AccountService,
    pub public_key_repo: PublicKeyRepository,
}

impl MigratableService for CrossServiceMigrationService {
    fn get_service_identifier(&self) -> &str {
        "cross_service"
    }

    fn list_migrations(&self) -> Vec<Box<dyn Migration + '_>> {
        vec![Box::new(BackfillHwAuthPublicKeys::new(
            &self.account_service,
            &self.public_key_repo,
        ))]
    }
}
