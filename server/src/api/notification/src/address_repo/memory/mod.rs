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

#[derive(Debug, Default, Clone)]
pub struct Service {
    repository: Arc<Mutex<HashMap<Address<NetworkUnchecked>, AccountId>>>,
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

        for AddressAndKeysetId { address, .. } in addresses {
            if let Some(id) = repo.get(&address.clone()) {
                if id != account_id {
                    return Err(Error::AccountMismatchError(
                        address.clone().assume_checked().to_string(),
                        account_id.clone(),
                    ));
                }
            }

            repo.insert(address.clone(), account_id.clone());
        }
        Ok(())
    }

    async fn get(
        &self,
        addrs: &[Address<NetworkUnchecked>],
    ) -> Result<HashMap<Address<NetworkUnchecked>, AccountId>, Error> {
        let repo = self
            .repository
            .lock()
            .map_err(|err| InternalError(err.to_string()))?;

        return Ok(addrs
            .iter()
            .filter_map(|k| repo.get(k).map(|v| (k.clone(), v.clone())))
            .collect());
    }
}
