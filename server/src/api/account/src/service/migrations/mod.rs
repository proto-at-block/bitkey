use crate::service::Service;
use migrate_cognito_users::MigrateCognitoUsers;
use migration::{MigratableService, Migration};
use oneoff_account_deletion::OneoffAccountDeletion;

mod add_app_auth_pubkey;
mod migrate_cognito_users;
mod oneoff_account_deletion;

impl MigratableService for Service {
    fn get_service_identifier(&self) -> &str {
        "account_service"
    }

    fn list_migrations(&self) -> Vec<Box<dyn Migration + '_>> {
        vec![
            Box::new(OneoffAccountDeletion::new(self)),
            Box::new(MigrateCognitoUsers::new(self, &self.userpool_service)),
        ]
    }
}
