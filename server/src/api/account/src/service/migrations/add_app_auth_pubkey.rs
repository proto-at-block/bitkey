use async_trait::async_trait;
use migration::{Migration, MigrationError};

use crate::entities::{Account, FullAccount};
use crate::service::Service;

pub(crate) struct AddAppAuthPubkey<'a> {
    service: &'a Service,
}

impl<'a> AddAppAuthPubkey<'a> {
    #[allow(dead_code)]
    pub fn new(service: &'a Service) -> Self {
        Self { service }
    }
}

#[async_trait]
impl<'a> Migration for AddAppAuthPubkey<'a> {
    fn name(&self) -> &str {
        "20230905_add_app_auth_pubkey"
    }

    async fn run(&self) -> Result<(), MigrationError> {
        for account in self
            .service
            .fetch_accounts()
            .await
            .map_err(|err| MigrationError::CantEnumerateTable(err.to_string()))?
        {
            let Account::Full(full_account) = account else {
                continue;
            };
            let auth_keys =
                full_account
                    .active_auth_keys()
                    .ok_or(MigrationError::MissingCriticalField(
                        full_account.id.to_string(),
                        "active auth keys".to_string(),
                    ))?;
            let updated_full_account = FullAccount {
                application_auth_pubkey: Some(auth_keys.app_pubkey.to_owned()),
                ..full_account
            };
            self.service
                .account_repo
                .persist(&updated_full_account.into())
                .await
                .map_err(MigrationError::DbPersist)?;
        }

        Ok(())
    }
}
