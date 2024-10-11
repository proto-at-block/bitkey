use create_hardware_cognito_users::CreateHardwareCognitoUsers;
use migrate_test_account_cognito_users::MigrateTestAccountCognitoUsers;
use migration::{MigratableService, Migration};
use oneoff_account_deletion::OneoffAccountDeletion;

use crate::service::Service;

mod add_app_auth_pubkey;
mod create_hardware_cognito_users;
mod migrate_test_account_cognito_users;
mod oneoff_account_deletion;

impl MigratableService for Service {
    fn get_service_identifier(&self) -> &str {
        "account_service"
    }

    fn list_migrations(&self) -> Vec<Box<dyn Migration + '_>> {
        vec![
            Box::new(OneoffAccountDeletion::new(self)),
            Box::new(CreateHardwareCognitoUsers::new(
                self,
                &self.userpool_service,
            )),
            Box::new(MigrateTestAccountCognitoUsers::new(
                self,
                &self.userpool_service,
            )),
        ]
    }
}
