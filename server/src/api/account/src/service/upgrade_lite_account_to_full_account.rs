use types::account::entities::FullAccount;

use super::{Service, UpgradeLiteAccountToFullAccountInput};
use crate::error::AccountError;

impl Service {
    pub async fn upgrade_lite_account_to_full_account(
        &self,
        input: UpgradeLiteAccountToFullAccountInput<'_>,
    ) -> Result<FullAccount, AccountError> {
        let full_account = input.lite_account.upgrade_to_full_account(
            input.keyset_id,
            input.spending_keyset,
            input.auth_key_id,
            input.auth_keys,
        );
        self.account_repo
            .persist(&full_account.clone().into())
            .await?;
        Ok(full_account)
    }
}
