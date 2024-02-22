use tracing::instrument;

use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::bitcoin::Txid;
use database::ddb::DatabaseError;
use errors::ApiError;

use crate::signed_psbt_cache::entities::CachedPsbt;
use crate::signed_psbt_cache::repository::Repository;

#[derive(Clone)]
pub struct Service {
    repo: Repository,
}

impl Service {
    pub fn new(repo: Repository) -> Self {
        Self { repo }
    }

    #[instrument(skip(self))]
    pub async fn get(&self, txid: Txid) -> Result<Option<CachedPsbt>, ApiError> {
        match self.repo.fetch(txid).await {
            Ok(record) => Ok(Some(record)),
            Err(err) => match err {
                DatabaseError::ObjectNotFound(_) => Ok(None),
                _ => Err(err.into()),
            },
        }
    }

    #[instrument(skip(self))]
    pub async fn put(&self, psbt: Psbt) -> Result<(), ApiError> {
        self.repo
            .persist(&CachedPsbt::try_new(psbt)?)
            .await
            .map_err(|err| {
                ApiError::GenericInternalApplicationError(format!(
                    "Database error caching psbt: {:?}",
                    err
                ))
            })
    }
}
