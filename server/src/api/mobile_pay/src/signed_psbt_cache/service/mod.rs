mod tests;

use tracing::instrument;

use crate::error::SigningError;
use crate::signed_psbt_cache::entities::CachedPsbt;
use crate::signed_psbt_cache::repository::SignedPsbtCacheRepository;
use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::bitcoin::Txid;
use database::ddb::DatabaseError;

#[derive(Clone)]
pub struct Service {
    repo: SignedPsbtCacheRepository,
}

impl Service {
    pub fn new(repo: SignedPsbtCacheRepository) -> Self {
        Self { repo }
    }

    #[instrument(skip(self))]
    pub async fn get(&self, txid: Txid) -> Result<Option<CachedPsbt>, SigningError> {
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
        Ok(self.repo.persist(&CachedPsbt::try_new(psbt)?).await?)
    }
}
