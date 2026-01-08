use crate::error::PersistenceError;
use crate::types::ChangeSet;

use bdk_wallet::{rusqlite::Connection as BdkConnection, WalletPersister};

use std::ops::DerefMut;
use std::sync::{Arc, Mutex};

/// Definition of a wallet persistence implementation.
#[uniffi::export(with_foreign)]
pub trait Persistence: Send + Sync {
    /// Initialize the total aggregate `ChangeSet` for the underlying wallet.
    fn initialize(&self) -> Result<Arc<ChangeSet>, PersistenceError>;

    /// Persist a `ChangeSet` to the total aggregate changeset of the wallet.
    fn persist(&self, changeset: Arc<ChangeSet>) -> Result<(), PersistenceError>;
}

pub(crate) enum PersistenceType {
    Custom(Arc<dyn Persistence>),
    Sql(Mutex<BdkConnection>),
}

/// Wallet backend implementations.
#[derive(uniffi::Object)]
pub struct Persister {
    pub(crate) inner: Mutex<PersistenceType>,
}

#[uniffi::export]
impl Persister {
    /// Create a new Sqlite connection at the specified file path.
    #[uniffi::constructor]
    pub fn new_sqlite(path: String) -> Result<Self, PersistenceError> {
        let conn = BdkConnection::open(path)?;
        Ok(Self {
            inner: PersistenceType::Sql(conn.into()).into(),
        })
    }

    /// Create a new connection in memory.
    #[uniffi::constructor]
    pub fn new_in_memory() -> Result<Self, PersistenceError> {
        let conn = BdkConnection::open_in_memory()?;
        Ok(Self {
            inner: PersistenceType::Sql(conn.into()).into(),
        })
    }

    /// Use a native persistence layer.
    #[uniffi::constructor]
    pub fn custom(persistence: Arc<dyn Persistence>) -> Self {
        Self {
            inner: PersistenceType::Custom(persistence).into(),
        }
    }
}

impl WalletPersister for PersistenceType {
    type Error = PersistenceError;

    fn initialize(persister: &mut Self) -> Result<bdk_wallet::ChangeSet, Self::Error> {
        match persister {
            PersistenceType::Sql(ref conn) => {
                let mut lock = conn.lock().unwrap();
                let deref = lock.deref_mut();
                Ok(BdkConnection::initialize(deref)?)
            }
            PersistenceType::Custom(any) => any
                .initialize()
                .map(|changeset| changeset.as_ref().clone().into()),
        }
    }

    fn persist(persister: &mut Self, changeset: &bdk_wallet::ChangeSet) -> Result<(), Self::Error> {
        match persister {
            PersistenceType::Sql(ref conn) => {
                let mut lock = conn.lock().unwrap();
                let deref = lock.deref_mut();
                Ok(BdkConnection::persist(deref, changeset)?)
            }
            PersistenceType::Custom(any) => {
                let ffi_changeset: ChangeSet = changeset.clone().into();
                any.persist(Arc::new(ffi_changeset))
            }
        }
    }
}
