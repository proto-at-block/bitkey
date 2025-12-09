mod cleanup_old_watch_addresses_prod;

use cleanup_old_watch_addresses_prod::CleanupOldWatchAddressesProd;
use migration::{MigratableService, Migration};

use crate::service::Service;

impl MigratableService for Service {
    fn get_service_identifier(&self) -> &str {
        "notification_service"
    }

    fn list_migrations(&self) -> Vec<Box<dyn Migration + '_>> {
        vec![Box::new(CleanupOldWatchAddressesProd::new(self.clone()))]
    }
}
