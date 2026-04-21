use std::collections::HashMap;

use account::service::Service as AccountService;
use async_trait::async_trait;
use database::ddb::WRITE_BATCH_MAX;
use migration::{Migration, MigrationError};
use repository::public_key::{KeyType, PublicKeyRecord, PublicKeyRepository};
use time::format_description::well_known::Rfc3339;
use time::OffsetDateTime;
use tracing::{error, info};
use types::account::entities::Account;

pub(crate) struct BackfillHwAuthPublicKeys<'a> {
    account_service: &'a AccountService,
    public_key_repo: &'a PublicKeyRepository,
}

impl<'a> BackfillHwAuthPublicKeys<'a> {
    pub fn new(
        account_service: &'a AccountService,
        public_key_repo: &'a PublicKeyRepository,
    ) -> Self {
        Self {
            account_service,
            public_key_repo,
        }
    }

    async fn write_batches(
        &self,
        records: &[PublicKeyRecord],
        phase: &'static str,
    ) -> Result<(), MigrationError> {
        for chunk in records.chunks(WRITE_BATCH_MAX) {
            if let Err(e) = self
                .public_key_repo
                .batch_persist_public_keys(chunk)
                .await
            {
                error!(
                    phase,
                    error = ?e,
                    chunk_size = chunk.len(),
                    "Failed to persist batch during backfill"
                );
                return Err(MigrationError::DbPersist(e));
            }
        }
        Ok(())
    }
}

#[async_trait]
impl Migration for BackfillHwAuthPublicKeys<'_> {
    fn name(&self) -> &str {
        "20260416_backfill_hw_auth_public_keys"
    }

    async fn run(&self) -> Result<(), MigrationError> {
        let accounts = self
            .account_service
            .fetch_accounts()
            .await
            .map_err(|err| MigrationError::CantEnumerateTable(err.to_string()))?;

        let now = OffsetDateTime::now_utc()
            .format(&Rfc3339)
            .unwrap_or_default();

        // Split records into two phases so that, for any duplicate hw pubkey
        // across accounts, the account that currently has the key as its
        // active hw auth key wins ownership in the public_keys table.
        //
        // Dedupe within each phase by public_key: BatchWriteItem rejects a
        // request containing duplicate primary keys, and duplicates can arise
        // when the same hw pubkey appears in multiple auth_keys entries on a
        // single account or across multiple accounts. HashMap::insert keeps
        // the last insertion, which preserves last-writer-wins semantics
        // within a phase.
        let mut historical_by_key: HashMap<String, PublicKeyRecord> = HashMap::new();
        let mut active_by_key: HashMap<String, PublicKeyRecord> = HashMap::new();

        for account in &accounts {
            let Account::Full(full_account) = account else {
                continue;
            };

            for auth_keys in full_account.auth_keys.values() {
                let public_key = auth_keys.hardware_pubkey.to_string();
                let record = PublicKeyRecord {
                    public_key: public_key.clone(),
                    account_id: full_account.id.clone(),
                    key_type: KeyType::HardwareAuth,
                    created_at: now.clone(),
                };
                if auth_keys.hardware_pubkey == full_account.hardware_auth_pubkey {
                    active_by_key.insert(public_key, record);
                } else {
                    historical_by_key.insert(public_key, record);
                }
            }
        }

        let historical_records: Vec<PublicKeyRecord> =
            historical_by_key.into_values().collect();
        let active_records: Vec<PublicKeyRecord> = active_by_key.into_values().collect();

        info!(
            historical = historical_records.len(),
            active = active_records.len(),
            "Backfilling hw auth public keys"
        );

        // Phase 1: write historical (rotated-away) hw auth pubkeys.
        self.write_batches(&historical_records, "historical").await?;

        // Phase 2: write currently-active hw auth pubkeys. These land after
        // phase 1 so any historical entry for the same key from a different
        // account is overwritten, making the active holder the authoritative
        // owner.
        self.write_batches(&active_records, "active").await?;

        Ok(())
    }
}
