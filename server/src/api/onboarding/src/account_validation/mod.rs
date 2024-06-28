use account::entities::{
    Account, FullAccountAuthKeysPayload, LiteAccountAuthKeysPayload, Network,
    SoftwareAccountAuthKeysPayload, SpendingKeysetRequest, UpgradeLiteAccountAuthKeysPayload,
};
use account::service::Service as AccountService;
use async_trait::async_trait;

use recovery::repository::Repository as RecoveryService;
use tracing::instrument;

use crate::routes::Config;

use self::no_recovery_with_hardware_auth_pubkey::NoRecoveryWithHardwareAuthPubkeyRule;
use self::no_recovery_with_recovery_auth_pubkey::NoRecoveryWithRecoveryAuthPubkeyRule;
use self::test_account_with_mainnet_keysets::TestAccountsWithMainnetKeysetsRule;
use self::unique_app_auth_pubkey_for_account::UniqueAppAuthPubkeyForAccountRule;
use self::unique_hardware_auth_pubkey_for_account::UniqueHardwareAuthPubkeyForAccountRule;
use self::unique_recovery_auth_pubkey_for_account::UniqueRecoveryAuthPubkeyForAccountRule;
use self::{
    error::AccountValidationError,
    no_recovery_with_app_auth_pubkey::NoRecoveryWithAppAuthPubkeyRule,
};

use errors::ApiError;

pub mod error;
pub(crate) mod no_recovery_with_app_auth_pubkey;
pub(crate) mod no_recovery_with_hardware_auth_pubkey;
pub(crate) mod no_recovery_with_recovery_auth_pubkey;
pub(crate) mod test_account_with_mainnet_keysets;
pub(crate) mod unique_app_auth_pubkey_for_account;
pub(crate) mod unique_hardware_auth_pubkey_for_account;
pub(crate) mod unique_recovery_auth_pubkey_for_account;

#[derive(Debug)]
pub(crate) enum AccountValidationRequest {
    CreateFullAccount {
        auth: FullAccountAuthKeysPayload,
        spending: SpendingKeysetRequest,
        is_test_account: bool,
        spending_network: Network,
    },
    CreateLiteAccount {
        auth: LiteAccountAuthKeysPayload,
    },
    UpgradeAccount {
        auth: UpgradeLiteAccountAuthKeysPayload,
        is_test_account: bool,
        spending_network: Network,
    },
    CreateSoftwareAccount {
        auth: SoftwareAccountAuthKeysPayload,
    },
}

#[derive(PartialEq, Debug)]
pub(crate) struct AccountValidationResponse {
    pub existing_account: Account,
}

#[async_trait]
trait Rule {
    async fn validate(
        &self,
        request: &AccountValidationRequest,
        config: &Config,
        account_service: &AccountService,
        recovery_service: &RecoveryService,
    ) -> Result<(), AccountValidationError>;
}

pub(crate) struct AccountValidation {
    rules: Vec<Box<dyn Rule + Sync + Send>>,
}

impl Default for AccountValidation {
    fn default() -> Self {
        AccountValidation {
            rules: vec![
                Box::new(NoRecoveryWithHardwareAuthPubkeyRule {}),
                Box::new(NoRecoveryWithAppAuthPubkeyRule {}),
                Box::new(NoRecoveryWithRecoveryAuthPubkeyRule {}),
                Box::new(UniqueAppAuthPubkeyForAccountRule {}),
                Box::new(UniqueHardwareAuthPubkeyForAccountRule {}),
                Box::new(UniqueRecoveryAuthPubkeyForAccountRule {}),
                Box::new(TestAccountsWithMainnetKeysetsRule {}),
            ],
        }
    }
}

impl AccountValidation {
    #[instrument(skip(self, config, account_service, recovery_service))]
    pub async fn validate(
        &self,
        request: AccountValidationRequest,
        config: &Config,
        account_service: &AccountService,
        recovery_service: &RecoveryService,
    ) -> Result<Option<AccountValidationResponse>, ApiError> {
        for rule in self.rules.iter() {
            match rule
                .validate(&request, config, account_service, recovery_service)
                .await
            {
                Err(AccountValidationError::DuplicateAccountForKeys(existing_account)) => {
                    return Ok(Some(AccountValidationResponse { existing_account }));
                }
                Err(e) => Err::<(), ApiError>(e.into()),
                Ok(_) => Ok(()),
            }?;
        }
        Ok(None)
    }
}

fn is_repeat_account_creation(
    request: &AccountValidationRequest,
    existing_account: &Account,
) -> bool {
    match request {
        AccountValidationRequest::CreateFullAccount { auth, spending, .. } => {
            let Account::Full(full_account) = existing_account else {
                return false;
            };

            let (Some(active_auth_keys), Some(active_spending_keyset)) = (
                full_account.active_auth_keys(),
                full_account.active_spending_keyset(),
            ) else {
                return false;
            };

            active_auth_keys.app_pubkey == auth.app
                && active_auth_keys.hardware_pubkey == auth.hardware
                && active_auth_keys.recovery_pubkey == auth.recovery
                && active_spending_keyset.app_dpub == spending.app
                && active_spending_keyset.hardware_dpub == spending.hardware
        }
        AccountValidationRequest::CreateLiteAccount { auth, .. } => {
            let Account::Lite(lite_account) = existing_account else {
                return false;
            };

            let Some(active_auth_keys) = lite_account.active_auth_keys() else {
                return false;
            };

            active_auth_keys.recovery_pubkey == auth.recovery
        }
        AccountValidationRequest::CreateSoftwareAccount { auth, .. } => {
            let Account::Software(software_account) = existing_account else {
                return false;
            };

            let Some(active_auth_keys) = software_account.active_auth_keys() else {
                return false;
            };

            active_auth_keys.app_pubkey == auth.app
                && active_auth_keys.recovery_pubkey == auth.recovery
        }
        _ => false,
    }
}
