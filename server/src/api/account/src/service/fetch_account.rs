use std::collections::HashMap;

use database::ddb::DatabaseError;
use errors::ApiError;
use types::account::entities::{Account, AuthFactor, FullAccount, SoftwareAccount};
use types::account::identifiers::AccountId;
use types::account::PubkeysToAccount;

use super::{FetchAccountInput, Service};
use crate::error::AccountError;
use crate::service::FetchAccountByAuthKeyInput;

impl Service {
    pub async fn fetch_account(
        &self,
        input: FetchAccountInput<'_>,
    ) -> Result<Account, AccountError> {
        self.account_repo
            .fetch(input.account_id)
            .await
            .map_err(Into::into)
    }

    pub async fn fetch_accounts(&self) -> Result<Vec<Account>, AccountError> {
        self.account_repo
            .fetch_all_accounts()
            .await
            .map_err(Into::into)
    }

    pub async fn fetch_account_id_by_hw_pubkey(
        &self,
        input: FetchAccountByAuthKeyInput,
    ) -> Result<PubkeysToAccount, ApiError> {
        self.account_repo
            .fetch_account_by_auth_pubkey(input.pubkey, AuthFactor::Hw)
            .await
            .map(Into::into)
            .map_err(Into::into)
    }

    pub async fn fetch_account_id_by_recovery_pubkey(
        &self,
        input: FetchAccountByAuthKeyInput,
    ) -> Result<PubkeysToAccount, ApiError> {
        self.account_repo
            .fetch_account_by_auth_pubkey(input.pubkey, AuthFactor::Recovery)
            .await
            .map(Into::into)
            .map_err(Into::into)
    }

    pub async fn fetch_account_id_by_app_pubkey(
        &self,
        input: FetchAccountByAuthKeyInput,
    ) -> Result<PubkeysToAccount, ApiError> {
        self.account_repo
            .fetch_account_by_auth_pubkey(input.pubkey, AuthFactor::App)
            .await
            .map(Into::into)
            .map_err(Into::into)
    }

    pub async fn fetch_account_by_hw_pubkey(
        &self,
        input: FetchAccountByAuthKeyInput,
    ) -> Result<Option<Account>, DatabaseError> {
        self.account_repo
            .fetch_optional_account_by_auth_pubkey(input.pubkey, AuthFactor::Hw)
            .await
    }

    pub async fn fetch_account_by_app_pubkey(
        &self,
        input: FetchAccountByAuthKeyInput,
    ) -> Result<Option<Account>, DatabaseError> {
        self.account_repo
            .fetch_optional_account_by_auth_pubkey(input.pubkey, AuthFactor::App)
            .await
            .map_err(Into::into)
    }

    pub async fn fetch_account_by_recovery_pubkey(
        &self,
        input: FetchAccountByAuthKeyInput,
    ) -> Result<Option<Account>, DatabaseError> {
        self.account_repo
            .fetch_optional_account_by_auth_pubkey(input.pubkey, AuthFactor::Recovery)
            .await
            .map_err(Into::into)
    }

    pub async fn fetch_full_account(
        &self,
        input: FetchAccountInput<'_>,
    ) -> Result<FullAccount, AccountError> {
        let account = self.account_repo.fetch(input.account_id).await?;
        let Account::Full(full_account) = account else {
            return Err(AccountError::InvalidAccountType);
        };
        Ok(full_account)
    }

    pub async fn fetch_software_account(
        &self,
        input: FetchAccountInput<'_>,
    ) -> Result<SoftwareAccount, AccountError> {
        let account = self.account_repo.fetch(input.account_id).await?;
        let Account::Software(software_account) = account else {
            return Err(AccountError::InvalidAccountType);
        };
        Ok(software_account)
    }

    pub async fn fetch_full_accounts_by_account_ids<I>(
        &self,
        accounts_ids: I,
    ) -> Result<HashMap<AccountId, FullAccount>, AccountError>
    where
        I: IntoIterator<Item = AccountId> + std::fmt::Debug,
    {
        let accounts = self.account_repo.fetch_accounts(accounts_ids).await?;
        let full_accounts = accounts
            .into_iter()
            .filter_map(|account| {
                if let Account::Full(full_account) = account {
                    Some(full_account)
                } else {
                    None
                }
            })
            .collect::<Vec<FullAccount>>();
        Ok(full_accounts
            .into_iter()
            .map(|account| (account.id.clone(), account))
            .collect())
    }
}
