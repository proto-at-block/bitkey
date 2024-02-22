use crate::service::Service;
use migration::{MigratableService, Migration};

mod add_app_auth_pubkey;

impl MigratableService for Service {
    fn get_service_identifier(&self) -> &str {
        "account_service"
    }

    fn list_migrations(&self) -> Vec<Box<dyn Migration + '_>> {
        vec![
            // Box::new(AddAppAuthPubkey::new(self)) This exists as an example
        ]
    }
}
