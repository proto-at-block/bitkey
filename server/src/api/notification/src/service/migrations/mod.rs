use crate::service::{
    migrations::initial_notifications_preferences::InitialNotificationsPreferences, Service,
};
use migration::{MigratableService, Migration};

mod initial_notifications_preferences;

impl MigratableService for Service {
    fn get_service_identifier(&self) -> &str {
        "notification_service"
    }

    fn list_migrations(&self) -> Vec<Box<dyn Migration + '_>> {
        vec![Box::new(InitialNotificationsPreferences::new(self))]
    }
}
