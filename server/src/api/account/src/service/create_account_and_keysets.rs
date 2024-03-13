use crate::{
    entities::{AccountProperties, FullAccount, FullAccountAuthKeys, SpendingKeyset},
    error::AccountError,
};

use super::{CreateAccountAndKeysetsInput, Service};

impl Service {
    pub async fn create_account_and_keysets(
        &self,
        input: CreateAccountAndKeysetsInput,
    ) -> Result<FullAccount, AccountError> {
        let account_id = input.clone().account_id;
        let is_test_account = input.is_test_account;
        let full_account = FullAccount::new(
            account_id,
            input.clone().keyset_id,
            input.clone().auth_key_id,
            input.clone().into(),
            input.into(),
            AccountProperties {
                is_test_account,
                ..AccountProperties::default()
            },
        );
        self.account_repo
            .persist(&full_account.clone().into())
            .await?;
        Ok(full_account)
    }
}

impl From<CreateAccountAndKeysetsInput> for SpendingKeyset {
    fn from(input: CreateAccountAndKeysetsInput) -> Self {
        SpendingKeyset::new(
            input.network,
            input.keyset.spending.app_dpub,
            input.keyset.spending.hardware_dpub,
            input.keyset.spending.server_dpub,
        )
    }
}

impl From<CreateAccountAndKeysetsInput> for FullAccountAuthKeys {
    fn from(input: CreateAccountAndKeysetsInput) -> Self {
        FullAccountAuthKeys::new(
            input.keyset.auth.app_pubkey,
            input.keyset.auth.hardware_pubkey,
            input.keyset.auth.recovery_pubkey,
        )
    }
}
