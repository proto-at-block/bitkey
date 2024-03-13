use crate::entities::{AuthFactor, FullAccount};
use crate::service::FetchAccountByAuthKeyInput;
use crate::{entities::Account, error::AccountError};
use database::ddb::DatabaseError;
use errors::ApiError;
use types::account::PubkeysToAccount;

use super::{FetchAccountInput, Service};

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
}
