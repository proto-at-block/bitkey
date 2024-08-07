//! Repository watched addresses and their associated account_id

use async_trait::async_trait;
use dyn_clone::DynClone;
use std::collections::HashMap;
use std::fmt;

use serde::{Deserialize, Serialize};

use crate::address_repo::errors::Error;
use bdk_utils::bdk::bitcoin::address::NetworkUnchecked;
use bdk_utils::bdk::bitcoin::Address;
use types::account::identifiers::{AccountId, KeysetId};

pub mod ddb;
pub mod errors;
pub mod memory;

#[cfg(test)]
mod tests;

#[async_trait]
pub trait AddressWatchlistTrait: DynClone + fmt::Debug + Send + Sync {
    /// Insert a list of addresses associated with a single AccountId
    async fn insert(
        &mut self,
        addresses: &[AddressAndKeysetId],
        account_id: &AccountId,
    ) -> Result<(), Error>;

    /// Query for a set of addresses, returning a map of Address->AccountId for the addresses
    /// that are known by the AddressWatchlist
    async fn get(
        &self,
        addresses: &[Address<NetworkUnchecked>],
    ) -> Result<HashMap<Address<NetworkUnchecked>, AccountIdAndKeysetId>, Error>;
}

dyn_clone::clone_trait_object!(AddressWatchlistTrait);

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct AddressAndKeysetId {
    pub address: Address<NetworkUnchecked>,
    spending_keyset_id: KeysetId,
}

impl AddressAndKeysetId {
    pub fn new(address: Address<NetworkUnchecked>, spending_keyset_id: KeysetId) -> Self {
        Self {
            address,
            spending_keyset_id,
        }
    }
}

/// Metadata for a registered address we want to store
#[derive(Clone, Debug, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub struct AccountIdAndKeysetId {
    pub account_id: AccountId,
    pub spending_keyset_id: KeysetId,
}

impl AccountIdAndKeysetId {
    pub fn new(account_id: AccountId, spending_keyset_id: KeysetId) -> Self {
        Self {
            account_id,
            spending_keyset_id,
        }
    }
}
