use std::collections::HashSet;

use async_trait::async_trait;
use database::{
    aws_sdk_dynamodb::error::ProvideErrorMetadata,
    ddb::{try_from_item, Connection, DatabaseError, DatabaseObject},
};
use migration::{Migration, MigrationError};
use serde::Deserialize;
use tracing::{event, info, Level};
use types::account::identifiers::AccountId;

use crate::{address_repo::errors::Error as AddressRepoError, service::Service};

pub(crate) struct CleanupOldWatchAddressesProd {
    pub service: Service,
}

impl CleanupOldWatchAddressesProd {
    #[allow(dead_code)]
    pub fn new(service: Service) -> Self {
        Self { service }
    }
}

#[async_trait]
impl Migration for CleanupOldWatchAddressesProd {
    fn name(&self) -> &str {
        "20251119_cleanup_old_watch_addresses_prod"
    }

    async fn run(&self) -> Result<(), MigrationError> {
        info!("Starting CleanupOldWatchAddressesProd migration");

        let watchlist_scanner = WatchlistAccountScanner::new(self.service.ddb_connection());
        let account_ids_with_addresses = watchlist_scanner.fetch_account_ids().await?;

        if account_ids_with_addresses.is_empty() {
            info!("No accounts currently have watch addresses. Nothing to clean.");
            return Ok(());
        }

        let mut orphaned_account_ids: HashSet<AccountId> =
            account_ids_with_addresses.iter().cloned().collect();

        let accounts = self
            .service
            .account_repo
            .fetch_accounts(account_ids_with_addresses.clone())
            .await
            .map_err(|err| MigrationError::CantEnumerateTable(err.to_string()))?;

        let mut cleaned_disabled_count = 0;
        let mut cleaned_orphaned_count = 0;

        for account in accounts {
            let account_id = account.get_id();
            orphaned_account_ids.remove(account_id);

            let notifications_preferences = &account
                .get_common_fields()
                .notifications_preferences_state
                .money_movement;

            // Check if money movement notifications are disabled (empty set)
            if notifications_preferences.is_empty() {
                info!(
                    "Account {account_id} has money movement notifications disabled, cleaning up watch addresses"
                );

                self.service
                    .address_service
                    .delete_all_addresses(account_id)
                    .await
                    .map_err(|err| match err {
                        AddressRepoError::DatabaseError(db_err) => {
                            MigrationError::DbPersist(db_err)
                        }
                        other => MigrationError::CantEnumerateTable(other.to_string()),
                    })?;

                info!("Successfully cleaned up watch addresses for account {account_id}");
                cleaned_disabled_count += 1;
            }
        }

        // Clean up orphaned watch addresses with no corresponding account record
        for account_id in orphaned_account_ids {
            info!(
                "Account {account_id} is missing but still has watch addresses recorded, cleaning up"
            );
            self.service
                .address_service
                .delete_all_addresses(&account_id)
                .await
                .map_err(|err| match err {
                    AddressRepoError::DatabaseError(db_err) => MigrationError::DbPersist(db_err),
                    other => MigrationError::CantEnumerateTable(other.to_string()),
                })?;
            cleaned_orphaned_count += 1;
        }

        let total_accounts = account_ids_with_addresses.len();
        let skipped_accounts = total_accounts - cleaned_disabled_count - cleaned_orphaned_count;

        info!(
            "CleanupOldWatchAddressesProd migration completed. Total accounts with watch addresses: {total_accounts}. Cleaned {cleaned_disabled_count} accounts with notifications disabled and {cleaned_orphaned_count} orphaned accounts. Skipped {skipped_accounts} accounts that still have notifications enabled."
        );

        Ok(())
    }
}

struct WatchlistAccountScanner {
    connection: Connection,
}

impl WatchlistAccountScanner {
    fn new(connection: Connection) -> Self {
        Self { connection }
    }

    async fn fetch_account_ids(&self) -> Result<Vec<AccountId>, MigrationError> {
        let table_name = self
            .connection
            .get_table_name(DatabaseObject::AddressWatchlist)
            .map_err(map_db_error)?;
        let mut account_ids: HashSet<AccountId> = HashSet::new();
        let mut exclusive_start_key = None;

        loop {
            let scan_result = self
                .connection
                .client
                .scan()
                .table_name(&table_name)
                .consistent_read(true)
                .set_exclusive_start_key(exclusive_start_key)
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not scan address watchlist: {service_err:?} with message: {:?}",
                        service_err.message()
                    );
                    MigrationError::CantEnumerateTable(format!(
                        "Failed to scan AddressWatchlist table: {:?}",
                        service_err.message()
                    ))
                })?;

            for item in scan_result.items() {
                let watched_address: WatchlistAccount =
                    try_from_item(item.clone(), DatabaseObject::AddressWatchlist)
                        .map_err(map_db_error)?;
                account_ids.insert(watched_address.account_id);
            }

            if let Some(last_evaluated_key) = scan_result.last_evaluated_key() {
                exclusive_start_key = Some(last_evaluated_key.clone());
                continue;
            }
            break;
        }

        Ok(account_ids.into_iter().collect())
    }
}

fn map_db_error(err: DatabaseError) -> MigrationError {
    MigrationError::CantEnumerateTable(err.to_string())
}

#[derive(Deserialize)]
struct WatchlistAccount {
    account_id: AccountId,
}
