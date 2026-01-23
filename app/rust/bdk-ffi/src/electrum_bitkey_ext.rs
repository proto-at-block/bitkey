//! ElectrumClient extension methods that are not part of upstream bdk-ffi.
//! Placed in a separate module so vendoring upstream does not clobber our additions.

use crate::bitcoin::{BlockHash, Header, Transaction, Txid};
use crate::electrum::ElectrumClient;
use crate::error::ElectrumError;
use bdk_electrum::electrum_client::ElectrumApi;
use std::sync::Arc;

#[uniffi::export]
impl ElectrumClient {
    /// Fetches a transaction by its txid.
    pub fn transaction_get(&self, txid: Arc<Txid>) -> Result<Arc<Transaction>, ElectrumError> {
        let tx = self
            .bdk_client()
            .inner
            .transaction_get(&txid.0)
            .map_err(ElectrumError::from)?;
        Ok(Arc::new(Transaction::from(tx)))
    }

    /// Gets the block header at the specified height.
    pub fn block_header(&self, height: u64) -> Result<Header, ElectrumError> {
        let header = self
            .bdk_client()
            .inner
            .block_header(height as usize)
            .map_err(ElectrumError::from)?;
        Ok(Header::from(header))
    }

    /// Gets the block hash at the specified height.
    pub fn block_hash(&self, height: u64) -> Result<Arc<BlockHash>, ElectrumError> {
        let header = self
            .bdk_client()
            .inner
            .block_header(height as usize)
            .map_err(ElectrumError::from)?;
        Ok(Arc::new(BlockHash(header.block_hash())))
    }
}
