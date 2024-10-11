use migration::{MigratableService, Migration};

use crate::service::{migrations::ext_beta_push_blast::ExtBetaPushBlast, Service};

mod ext_beta_push_blast;

impl MigratableService for Service {
    fn get_service_identifier(&self) -> &str {
        "notification_service"
    }

    fn list_migrations(&self) -> Vec<Box<dyn Migration + '_>> {
        vec![Box::new(ExtBetaPushBlast::new(self))]
    }
}
