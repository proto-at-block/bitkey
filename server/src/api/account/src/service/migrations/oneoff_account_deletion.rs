use std::str::FromStr;

use async_trait::async_trait;
use migration::{Migration, MigrationError};
use types::account::identifiers::AccountId;

use crate::service::{DeleteAccountInput, Service};

pub(crate) struct OneoffAccountDeletion<'a> {
    service: &'a Service,
}

impl<'a> OneoffAccountDeletion<'a> {
    #[allow(dead_code)]
    pub fn new(service: &'a Service) -> Self {
        Self { service }
    }
}

#[async_trait]
impl<'a> Migration for OneoffAccountDeletion<'a> {
    fn name(&self) -> &str {
        "20240401_oneoff_account_deletion"
    }

    async fn run(&self) -> Result<(), MigrationError> {
        self.service
            .delete_account(DeleteAccountInput {
                account_id: &AccountId::from_str("urn:wallet-account:01HSX5TTBPYC8XTZTYA7KWDVMH")
                    .map_err(|_| MigrationError::DeleteAccount)?,
            })
            .await
            .map_err(|_| MigrationError::DeleteAccount)?;

        Ok(())
    }
}
