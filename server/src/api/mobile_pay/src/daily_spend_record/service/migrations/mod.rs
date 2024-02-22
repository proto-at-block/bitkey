use crate::daily_spend_record::service::Service;
use migration::{MigratableService, Migration};

impl MigratableService for Service {
    fn get_service_identifier(&self) -> &str {
        "daily_spend_record_service"
    }

    fn list_migrations(&self) -> Vec<Box<dyn Migration + '_>> {
        vec![]
    }
}
