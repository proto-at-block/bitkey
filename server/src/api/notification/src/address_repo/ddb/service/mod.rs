use crate::address_repo::ddb::entities::WatchedAddress;
use crate::address_repo::ddb::repository::AddressRepository;
use crate::address_repo::errors::Error;
use crate::address_repo::{AccountIdAndKeysetId, AddressAndKeysetId, AddressWatchlistTrait};
use async_trait::async_trait;
use bdk_utils::bdk::bitcoin::address::NetworkUnchecked;
use bdk_utils::bdk::bitcoin::Address;
use database::ddb::Repository;
use std::collections::HashMap;
use std::fmt;
use time::OffsetDateTime;
use types::account::identifiers::AccountId;

#[derive(Clone)]
pub struct Service {
    repo: AddressRepository,
}

impl fmt::Debug for Service {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("Service")
            .field("repo", &"AddressRepository")
            .finish()
    }
}

impl Service {
    pub async fn create(repo: AddressRepository) -> Result<Self, Error> {
        repo.create_table_if_necessary().await.map_err(|error| {
            Error::InternalError(format!("Error creating database table: {:?}", error))
        })?;

        Ok(Self { repo })
    }
}

#[async_trait]
impl AddressWatchlistTrait for Service {
    async fn insert(
        &self,
        addresses: &[AddressAndKeysetId],
        account_id: &AccountId,
    ) -> Result<(), Error> {
        // Error if any known addresses are from different AccountIds
        let known_addresses = self
            .get(
                &addresses
                    .iter()
                    .map(|item| item.address.clone())
                    .collect::<Vec<Address<NetworkUnchecked>>>(),
            )
            .await?;
        for (known_address, v) in known_addresses {
            if v.account_id != *account_id {
                return Err(Error::AccountMismatchError(
                    known_address.assume_checked().to_string(),
                    account_id.clone(),
                ));
            }
        }

        let watched_addresses = addresses
            .iter()
            .map(|address| {
                WatchedAddress::new(
                    &address.address,
                    &address.spending_keyset_id,
                    account_id,
                    &OffsetDateTime::now_utc(),
                )
            })
            .collect();

        self.repo.persist_batch(watched_addresses).await?;

        Ok(())
    }

    async fn get(
        &self,
        addresses: &[Address<NetworkUnchecked>],
    ) -> Result<HashMap<Address<NetworkUnchecked>, AccountIdAndKeysetId>, Error> {
        let watched_addresses = self.repo.fetch_batch(addresses).await?;

        return Ok(watched_addresses
            .into_iter()
            .map(|item| {
                (
                    item.address,
                    AccountIdAndKeysetId {
                        account_id: item.account_id,
                        spending_keyset_id: item.spending_keyset_id,
                    },
                )
            })
            .collect());
    }

    async fn delete_all_addresses(&self, account_id: &AccountId) -> Result<(), Error> {
        self.repo
            .delete_all_addresses_for_account(account_id)
            .await?;

        Ok(())
    }
}
