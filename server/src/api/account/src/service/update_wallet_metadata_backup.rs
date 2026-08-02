use database::ddb::DatabaseError;
use types::account::entities::{Account, FullAccount};

use super::{Service, UpdateWalletMetadataBackupInput};
use crate::error::AccountError;

/// Total persist attempts when racing other writers. The metadata backup write is
/// last-write-wins, so losing the account repository's `updated_at` compare-and-swap to a
/// concurrent writer is not a conflict for this field — refetch the account and re-apply the
/// backup on top of the fresh state instead of surfacing a 500 to the client.
const MAX_PERSIST_ATTEMPTS: usize = 3;

impl Service {
    pub async fn update_wallet_metadata_backup(
        &self,
        input: UpdateWalletMetadataBackupInput<'_>,
    ) -> Result<Account, AccountError> {
        let account_id = input.account.id.clone();
        let mut account = input.account.clone();

        for attempt in 1..=MAX_PERSIST_ATTEMPTS {
            let updated_account: Account = FullAccount {
                wallet_metadata_backup: Some(input.wallet_metadata_backup.clone()),
                ..account
            }
            .into();

            match self.account_repo.persist(&updated_account).await {
                Ok(()) => return Ok(updated_account),
                // A lost conditional write means another writer updated the account between our
                // fetch and this persist. Reload and retry so concurrent, unrelated account
                // writes (or another device's metadata upload) don't intermittently fail this
                // last-write-wins endpoint.
                Err(DatabaseError::PersistenceError(_)) if attempt < MAX_PERSIST_ATTEMPTS => {
                    let refetched = self.account_repo.fetch(&account_id).await?;
                    let Account::Full(full_account) = refetched else {
                        return Err(AccountError::InvalidAccountType);
                    };
                    account = full_account;
                }
                Err(err) => return Err(err.into()),
            }
        }

        // The loop always returns: success, a non-retryable error, or the final attempt's error.
        Err(AccountError::Unexpected)
    }
}
