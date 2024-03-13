use crate::{
    entities::{Account, CommonAccountFields, FullAccount, FullAccountAuthKeys},
    error::AccountError,
};
use types::account::identifiers::AuthKeysId;

use super::{CreateAndRotateAuthKeysInput, Service};

impl Service {
    pub async fn create_and_rotate_auth_keys(
        &self,
        input: CreateAndRotateAuthKeysInput<'_>,
    ) -> Result<Account, AccountError> {
        let Account::Full(full_account) = self.account_repo.fetch(input.account_id).await? else {
            return Err(AccountError::InvalidAccountType);
        };
        let common_fields = full_account.common_fields.to_owned();

        let hardware_auth_pubkey = input.hardware_auth_pubkey;
        let application_auth_pubkey = Some(input.app_auth_pubkey);
        let recovery_auth_pubkey = input.recovery_auth_pubkey;

        // Update authentication keys
        let auth_keys_id = AuthKeysId::gen().map_err(AccountError::from)?;
        let mut auth_keys = full_account.auth_keys.clone();
        auth_keys.insert(auth_keys_id.clone(), input.into());

        let updated_common_fields = CommonAccountFields {
            active_auth_keys_id: auth_keys_id,
            recovery_auth_pubkey,
            ..common_fields
        };
        let updated_account = FullAccount {
            auth_keys,
            common_fields: updated_common_fields,
            application_auth_pubkey,
            hardware_auth_pubkey,
            ..full_account
        }
        .into();
        self.account_repo.persist(&updated_account).await?;
        Ok(updated_account)
    }
}

impl From<CreateAndRotateAuthKeysInput<'_>> for FullAccountAuthKeys {
    fn from(input: CreateAndRotateAuthKeysInput) -> Self {
        FullAccountAuthKeys::new(
            input.app_auth_pubkey,
            input.hardware_auth_pubkey,
            input.recovery_auth_pubkey,
        )
    }
}
