use migration::{MigratableService, Migration};

use crate::service::Service;

impl MigratableService for Service {
    fn get_service_identifier(&self) -> &str {
        "notification_service"
    }

    fn list_migrations(&self) -> Vec<Box<dyn Migration + '_>> {
        vec![]
    }
}
