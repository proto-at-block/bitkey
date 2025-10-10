use types::account::entities::{Account, FullAccount};

use super::{Service, UpdateDescriptorBackupsInput};
use crate::error::AccountError;

impl Service {
    pub async fn update_descriptor_backups(
        &self,
        input: UpdateDescriptorBackupsInput<'_>,
    ) -> Result<Account, AccountError> {
        for descriptor_backup in &input.descriptor_backups_set.descriptor_backups {
            if !input
                .account
                .spending_keysets
                .contains_key(&descriptor_backup.keyset_id)
            {
                return Err(AccountError::UnrecognizedKeysetIds);
            }
        }

        if !input.descriptor_backups_set.is_superset(
            &input
                .account
                .descriptor_backups_set
                .clone()
                .unwrap_or_default(),
        ) {
            return Err(AccountError::MissingKeysetIds);
        }

        let updated_account: Account = FullAccount {
            descriptor_backups_set: Some(input.descriptor_backups_set),
            ..input.account.clone()
        }
        .into();

        self.account_repo.persist(&updated_account).await?;
        Ok(updated_account)
    }
}
