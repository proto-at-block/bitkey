//! Implementation of an AddressWatchlistTrait that uses in-memory persistence. Useful for testing
//! and prototyping.

use crate::address_repo::errors::Error::InternalError;
use crate::address_repo::{errors::Error, AddressAndKeysetId, AddressWatchlistTrait};
use async_trait::async_trait;
use bdk_utils::bdk::bitcoin::address::NetworkUnchecked;
use bdk_utils::bdk::bitcoin::Address;
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use types::account::identifiers::AccountId;

use super::AccountIdAndKeysetId;

#[derive(Debug, Default, Clone)]
pub struct Service {
    repository: Arc<Mutex<HashMap<Address<NetworkUnchecked>, AccountIdAndKeysetId>>>,
}

#[async_trait]
impl AddressWatchlistTrait for Service {
    async fn insert(
        &mut self,
        addresses: &[AddressAndKeysetId],
        account_id: &AccountId,
    ) -> Result<(), Error> {
        let mut repo = self
            .repository
            .lock()
            .map_err(|err| InternalError(err.to_string()))?;

        for AddressAndKeysetId {
            address,
            spending_keyset_id,
        } in addresses
        {
            if let Some(v) = repo.get(&address.clone()) {
                if v.account_id != *account_id {
                    return Err(Error::AccountMismatchError(
                        address.clone().assume_checked().to_string(),
                        account_id.clone(),
                    ));
                }
            }

            repo.insert(
                address.clone(),
                AccountIdAndKeysetId {
                    account_id: account_id.clone(),
                    spending_keyset_id: spending_keyset_id.clone(),
                },
            );
        }
        Ok(())
    }

    async fn get(
        &self,
        addrs: &[Address<NetworkUnchecked>],
    ) -> Result<HashMap<Address<NetworkUnchecked>, AccountIdAndKeysetId>, Error> {
        let repo = self
            .repository
            .lock()
            .map_err(|err| InternalError(err.to_string()))?;

        return Ok(addrs
            .iter()
            .filter_map(|k| repo.get(k).map(|v| (k.clone(), v.clone())))
            .collect());
    }

    async fn delete_all_addresses(&mut self, account_id: &AccountId) -> Result<(), Error> {
        let mut repo = self
            .repository
            .lock()
            .map_err(|err| InternalError(err.to_string()))?;

        let addresses_to_remove: Vec<Address<NetworkUnchecked>> = repo
            .iter()
            .filter_map(|(address, account_info)| {
                if account_info.account_id == *account_id {
                    Some(address.clone())
                } else {
                    None
                }
            })
            .collect();

        for address in addresses_to_remove {
            repo.remove(&address);
        }

        Ok(())
    }
}
