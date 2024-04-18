use crate::service::{migrations::oneoff_account_deletion::OneoffAccountDeletion, Service};
use migration::{MigratableService, Migration};

mod add_app_auth_pubkey;
mod oneoff_account_deletion;

impl MigratableService for Service {
    fn get_service_identifier(&self) -> &str {
        "account_service"
    }

    fn list_migrations(&self) -> Vec<Box<dyn Migration + '_>> {
        vec![Box::new(OneoffAccountDeletion::new(self))]
    }
}
