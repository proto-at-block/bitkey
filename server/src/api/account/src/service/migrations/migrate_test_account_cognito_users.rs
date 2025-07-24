use async_trait::async_trait;
use migration::{Migration, MigrationError};
use types::account::entities::{Account, Touchpoint};
use types::authn_authz::cognito::{CognitoUser, CognitoUsername};
use userpool::userpool::{UserPoolError, UserPoolService};

use crate::service::Service as AccountService;

pub(crate) struct MigrateTestAccountCognitoUsers<'a> {
    account_service: &'a AccountService,
    userpool_service: &'a UserPoolService,
}

impl<'a> MigrateTestAccountCognitoUsers<'a> {
    #[allow(dead_code)]
    pub fn new(account_service: &'a AccountService, userpool_service: &'a UserPoolService) -> Self {
        Self {
            account_service,
            userpool_service,
        }
    }
}

#[async_trait]
impl Migration for MigrateTestAccountCognitoUsers<'_> {
    fn name(&self) -> &str {
        "20240614_migrate_test_account_cognito_users"
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

            // Skip non-test accounts (already migrated) and integration accounts (not used)
            if !is_test_account || is_integration_account {
                continue;
            }

            let (account_id, recovery_auth_pubkey) = match &account {
                Account::Full(full_account) => {
                    let auth_keys = full_account.active_auth_keys();

                    match auth_keys {
                        Some(auth_keys) => (full_account.id.clone(), auth_keys.recovery_pubkey),
                        None => {
                            tracing::warn!(
                                "Account {} is missing active auth keys; skipping migration",
                                full_account.id
                            );
                            continue;
                        }
                    }
                }
                Account::Lite(lite_account) => {
                    let auth_keys = lite_account.active_auth_keys();

                    match auth_keys {
                        Some(auth_keys) => {
                            (lite_account.id.clone(), Some(auth_keys.recovery_pubkey))
                        }
                        None => {
                            tracing::warn!(
                                "Account {} is missing active auth keys; skipping migration",
                                lite_account.id
                            );
                            continue;
                        }
                    }
                }
                // Skip software accounts
                Account::Software(_) => continue,
            };

            // First: update recovery user, because it may be using old schema
            if let Some(recovery_auth_pubkey) = recovery_auth_pubkey {
                let result = self
                    .userpool_service
                    .direct_replace_user_pubkey(
                        &CognitoUser::Recovery(account_id.clone()).into(),
                        recovery_auth_pubkey,
                    )
                    .await;

                if let Err(e) = result {
                    // Ignore if user doesn't exist (missing ones get created on reauth anyway)
                    if !matches!(&e, UserPoolError::ChangeUserAttributes(f) if f.is_user_not_found_exception())
                    {
                        tracing::error!(
                            "Account {} failed to change recovery user attributes: {:?}",
                            &account_id,
                            &e,
                        );
                        return Err(MigrationError::TestAccountCognitoUsers);
                    }
                }
            }

            // Second: sign out all possible cognito users; when the app re-auths, it will create any missing cognito users and ensure their publicKeys are up to date
            let usernames: [CognitoUsername; 4] = [
                CognitoUser::App(account_id.clone()).into(),
                CognitoUser::Hardware(account_id.clone()).into(),
                CognitoUser::Recovery(account_id.clone()).into(),
                serde_json::from_str(format!("\"{}\"", account_id).as_str())
                    .map_err(|_| MigrationError::TestAccountCognitoUsers)?,
            ];

            for username in usernames {
                let result = self.userpool_service.sign_out_user(&username).await;

                if let Err(e) = result {
                    // Ignore if user doesn't exist (missing ones get created on reauth anyway)
                    if !matches!(&e, UserPoolError::PerformUserSignOut(f) if f.is_user_not_found_exception())
                    {
                        tracing::error!(
                            "Account {} failed to sign out user {}: {:?}",
                            &account_id,
                            &username,
                            &e,
                        );
                        return Err(MigrationError::TestAccountCognitoUsers);
                    }
                }
            }
        }

        Ok(())
    }
}
