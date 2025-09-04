use types::account::{
    entities::{AccountProperties, FullAccount},
    keys::FullAccountAuthKeys,
    spending::SpendingKeyset,
};

use super::{CreateAccountAndKeysetsInput, Service};
use crate::error::AccountError;

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
            AccountProperties { is_test_account },
        );
        self.account_repo
            .persist(&full_account.clone().into())
            .await?;
        Ok(full_account)
    }
}

impl From<CreateAccountAndKeysetsInput> for SpendingKeyset {
    fn from(input: CreateAccountAndKeysetsInput) -> Self {
        input.keyset.spending
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
