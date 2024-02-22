use crate::address_repo::ddb::entities::WatchedAddress;
use crate::address_repo::ddb::repository::Repository;
use crate::address_repo::errors::Error;
use crate::address_repo::{AddressAndKeysetId, AddressWatchlistTrait};
use async_trait::async_trait;
use bdk_utils::bdk::bitcoin::address::NetworkUnchecked;
use bdk_utils::bdk::bitcoin::Address;
use database::ddb::{Connection, DDBService};
use std::collections::HashMap;
use time::OffsetDateTime;
use types::account::identifiers::AccountId;

#[derive(Debug, Clone)]
pub struct Service {
    repo: Repository,
}

impl Service {
    pub async fn create(connection: Connection) -> Result<Self, Error> {
        let repo = Repository::new(connection);
        repo.create_table_if_necessary().await.map_err(|error| {
            Error::InternalError(format!("Error creating database table: {:?}", error))
        })?;

        Ok(Self { repo })
    }
}

#[async_trait]
impl AddressWatchlistTrait for Service {
    async fn insert(
        &mut self,
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
        for (known_address, expected_account_id) in known_addresses {
            if expected_account_id != *account_id {
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
    ) -> Result<HashMap<Address<NetworkUnchecked>, AccountId>, Error> {
        let watched_addresses = self.repo.fetch_batch(addresses).await?;

        return Ok(watched_addresses
            .into_iter()
            .map(|item| (item.address, item.account_id))
            .collect());
    }
}
