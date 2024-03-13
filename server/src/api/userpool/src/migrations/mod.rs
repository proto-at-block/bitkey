use crate::userpool::UserPoolService;
use migration::{MigratableService, Migration};

impl MigratableService for UserPoolService {
    fn get_service_identifier(&self) -> &str {
        "userpool_service"
    }

    fn list_migrations(&self) -> Vec<Box<dyn Migration + '_>> {
        vec![]
    }
}
