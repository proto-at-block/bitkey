use crate::service::{migrations::ext_beta_push_blast::ExtBetaPushBlast, Service};
use migration::{MigratableService, Migration};

mod ext_beta_push_blast;
mod initial_notifications_preferences;

impl MigratableService for Service {
    fn get_service_identifier(&self) -> &str {
        "notification_service"
    }

    fn list_migrations(&self) -> Vec<Box<dyn Migration + '_>> {
        vec![Box::new(ExtBetaPushBlast::new(self))]
    }
}
