use types::account::entities::{AccountProperties, SoftwareAccount};

use super::{CreateSoftwareAccountInput, Service};
use crate::error::AccountError;

impl Service {
    pub async fn create_software_account(
        &self,
        input: CreateSoftwareAccountInput<'_>,
    ) -> Result<SoftwareAccount, AccountError> {
        let is_test_account = input.is_test_account;
        let software_account = SoftwareAccount::new(
            input.account_id.to_owned(),
            input.auth_key_id,
            input.auth,
            AccountProperties {
                is_test_account,
                ..AccountProperties::default()
            },
        );
        self.account_repo
            .persist(&software_account.clone().into())
            .await?;
        Ok(software_account)
    }
}
