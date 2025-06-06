use std::collections::HashMap;

use types::account::{
    entities::{Account, FullAccount},
    identifiers::KeysetId,
};

use super::{Service, UpdateDescriptorBackupsInput};
use crate::error::AccountError;

impl Service {
    pub async fn update_descriptor_backups(
        &self,
        input: UpdateDescriptorBackupsInput<'_>,
    ) -> Result<Account, AccountError> {
        let descriptor_backups: HashMap<KeysetId, String> = input
            .descriptor_backups
            .into_iter()
            .map(|b| {
                if input.account.spending_keysets.contains_key(&b.keyset_id) {
                    Ok((b.keyset_id, b.sealed_descriptor))
                } else {
                    Err(AccountError::UnrecognizedKeysetIds)
                }
            })
            .collect::<Result<_, _>>()?;

        if descriptor_backups.len() < input.account.spending_keysets.len() {
            return Err(AccountError::MissingKeysetIds);
        }

        let updated_account = FullAccount {
            descriptor_backups,
            ..input.account.clone()
        }
        .into();

        self.account_repo.persist(&updated_account).await?;
        Ok(updated_account)
    }
}
