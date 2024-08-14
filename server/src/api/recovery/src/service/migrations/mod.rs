use crate::service::migrations::backfill_trusted_contact_roles::BackfillTrustedContactRoles;
use crate::service::social::relationship::Service;
use migration::{MigratableService, Migration};

mod backfill_trusted_contact_roles;

impl MigratableService for Service {
    fn get_service_identifier(&self) -> &str {
        "relationship_service"
    }

    fn list_migrations(&self) -> Vec<Box<dyn Migration + '_>> {
        vec![Box::new(BackfillTrustedContactRoles::new(self.clone()))]
    }
}
