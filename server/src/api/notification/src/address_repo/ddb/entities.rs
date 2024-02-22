use bdk_utils::bdk::bitcoin::address::NetworkUnchecked;
use bdk_utils::bdk::bitcoin::Address;
use serde::{Deserialize, Serialize};
use time::OffsetDateTime;
use types::account::identifiers::{AccountId, KeysetId};

#[derive(Clone, Debug, Serialize, Deserialize)]
pub(crate) struct WatchedAddress {
    pub(crate) account_id: AccountId,
    pub(crate) address: Address<NetworkUnchecked>,
    spending_keyset_id: KeysetId,
    created_at: OffsetDateTime,
}

impl WatchedAddress {
    pub(super) fn new(
        address: &Address<NetworkUnchecked>,
        spending_keyset_id: &KeysetId,
        account_id: &AccountId,
        created_at: &OffsetDateTime,
    ) -> Self {
        Self {
            account_id: account_id.clone(),
            address: address.clone(),
            spending_keyset_id: spending_keyset_id.clone(),
            created_at: *created_at,
        }
    }
}
