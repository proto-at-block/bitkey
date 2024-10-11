use async_trait::async_trait;
use migration::{Migration, MigrationError};
use types::account::entities::{Account, Touchpoint};
use userpool::userpool::UserPoolService;

use crate::service::Service as AccountService;

pub(crate) struct CreateHardwareCognitoUsers<'a> {
    account_service: &'a AccountService,
    userpool_service: &'a UserPoolService,
}

impl<'a> CreateHardwareCognitoUsers<'a> {
    #[allow(dead_code)]
    pub fn new(account_service: &'a AccountService, userpool_service: &'a UserPoolService) -> Self {
        Self {
            account_service,
            userpool_service,
        }
    }
}

#[async_trait]
impl<'a> Migration for CreateHardwareCognitoUsers<'a> {
    fn name(&self) -> &str {
        "20240613_create_hardware_cognito_users"
    }

    async fn run(&self) -> Result<(), MigrationError> {
        for account in self
            .account_service
            .fetch_accounts()
            .await
            .map_err(|err| MigrationError::CantEnumerateTable(err.to_string()))?
        {
            let is_test_account = account.get_common_fields().properties.is_test_account;
            let is_integration_account = account.get_common_fields().touchpoints.iter().any(|t| matches!(t, Touchpoint::Email { email_address, .. } if email_address == "integration-test@wallet.build"));

            // Skip internal accounts
            if is_test_account || is_integration_account {
                continue;
            }

            let (account_id, hardware_auth_pubkey) = match &account {
                Account::Full(full_account) => {
                    let auth_keys = full_account.active_auth_keys();

                    match auth_keys {
                        Some(auth_keys) => (full_account.id.clone(), auth_keys.hardware_pubkey),
                        None => {
                            tracing::warn!(
                                "Account {} is missing active auth keys; skipping migration",
                                full_account.id
                            );
                            continue;
                        }
                    }
                }
                // Skip Lite and Software accounts
                Account::Lite(_) | Account::Software(_) => continue,
            };

            self.userpool_service
                .create_or_update_account_users_if_necessary(
                    &account_id,
                    None,
                    Some(hardware_auth_pubkey),
                    None,
                )
                .await
                .map_err(|_| MigrationError::CreateHardwareCognitoUser)?;
        }

        Ok(())
    }
}
