use crate::service::migrations::backfill_trusted_contact_roles::BackfillTrustedContactRoles;
use crate::service::migrations::cleanup_old_invalid_data::CleanupOldInvalidData;
use crate::service::social::relationship::Service;
use migration::{MigratableService, Migration};

mod backfill_trusted_contact_roles;
mod cleanup_old_invalid_data;

impl MigratableService for Service {
    fn get_service_identifier(&self) -> &str {
        "relationship_service"
    }

    fn list_migrations(&self) -> Vec<Box<dyn Migration + '_>> {
        vec![
            Box::new(CleanupOldInvalidData::new(self.clone())),
            Box::new(BackfillTrustedContactRoles::new(self.clone())),
        ]
    }
}
