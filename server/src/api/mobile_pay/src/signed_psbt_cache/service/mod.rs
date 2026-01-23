mod tests;

use tracing::instrument;

use crate::error::SigningError;
use crate::signed_psbt_cache::entities::CachedPsbtTxid;
use crate::signed_psbt_cache::repository::PsbtTxidCacheRepository;
use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::bitcoin::Txid;
use database::ddb::DatabaseError;

#[derive(Clone)]
pub struct Service {
    repo: PsbtTxidCacheRepository,
}

impl Service {
    pub fn new(repo: PsbtTxidCacheRepository) -> Self {
        Self { repo }
    }

    #[instrument(skip(self))]
    pub async fn get(&self, txid: Txid) -> Result<Option<CachedPsbtTxid>, SigningError> {
        match self.repo.fetch(txid).await {
            Ok(record) => Ok(Some(record)),
            Err(err) => match err {
                DatabaseError::ObjectNotFound(_) => Ok(None),
                _ => Err(err.into()),
            },
        }
    }

    #[instrument(skip(self, psbt))]
    pub async fn put(&self, psbt: Psbt) -> Result<(), SigningError> {
        Ok(self.repo.persist(&CachedPsbtTxid::try_new(psbt)?).await?)
    }

    #[instrument(skip(self))]
    pub async fn delete(&self, txid: Txid) -> Result<(), SigningError> {
        Ok(self.repo.delete(txid).await?)
    }
}
