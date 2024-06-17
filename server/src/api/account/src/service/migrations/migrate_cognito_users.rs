use async_trait::async_trait;

use migration::{Migration, MigrationError};
use types::authn_authz::cognito::{CognitoUser, CognitoUsername};
use userpool::userpool::{UserPoolError, UserPoolService};

use crate::{
    entities::{Account, Touchpoint},
    service::Service as AccountService,
};

pub(crate) struct MigrateCognitoUsers<'a> {
    account_service: &'a AccountService,
    userpool_service: &'a UserPoolService,
}

impl<'a> MigrateCognitoUsers<'a> {
    #[allow(dead_code)]
    pub fn new(account_service: &'a AccountService, userpool_service: &'a UserPoolService) -> Self {
        Self {
            account_service,
            userpool_service,
        }
    }
}

#[async_trait]
impl<'a> Migration for MigrateCognitoUsers<'a> {
    fn name(&self) -> &str {
        "20240605_migrate_cognito_users"
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

            let (account_id, recovery_auth_key) = match &account {
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
            };

            // Rotate the recovery key if it exists; this ensures that the publickey is populated
            // on the new `publicKey` field instead of the old `recoveryPubKey` field.
            if recovery_auth_key.is_some() {
                self.userpool_service
                    .rotate_account_auth_keys(&account_id, None, None, recovery_auth_key)
                    .await
                    .map_err(|_| MigrationError::RotateRecoveryAuthKey)?;
            };

            // Sign out the wallet user. This will cause the client to re-login, resulting in
            // new access / refresh tokens corresponding with the factor-specific cognito user.
            let wallet_username: CognitoUsername = CognitoUser::Wallet(account_id.clone()).into();
            let result = self.userpool_service.sign_out_user(&wallet_username).await;
            if let Err(e) = result {
                // Swallow UserNotFoundExceptions; some accounts (like Lite) don't have these users.
                if !matches!(e, UserPoolError::PerformUserSignOut(f) if f.is_user_not_found_exception())
                {
                    return Err(MigrationError::SignOutWalletUser);
                }
            }
        }

        Ok(())
    }
}
