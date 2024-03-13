use anyhow::{anyhow, Error, Result};

use serde::{de::DeserializeOwned, Serialize};
use sled::Db;

use crate::entities::{Account, AuthenticationToken, SignerHistory};

use super::{DB_ACCOUNT, DB_AUTHENTICATION_TOKEN, DB_SIGNER_HISTORY};

fn get<T: DeserializeOwned>(tree: &sled::Tree, key: &str) -> Result<T> {
    let value = tree
        .get(key)?
        .ok_or_else(|| anyhow!("db read fail (key: {})", key))?;
    serde_json::from_slice::<T>(&value).map_err(Error::msg)
}

fn set<T: Serialize>(tree: &sled::Tree, key: &str, value: T) -> Result<()> {
    tree.insert(key, serde_json::to_vec(&value)?)?;
    Ok(())
}

pub trait ToDatabase {
    fn to_database(self, db: &Db) -> Result<()>;
}

pub trait FromDatabase {
    fn from_database(db: &Db) -> Result<Self>
    where
        Self: Sized;
}

impl ToDatabase for SignerHistory {
    fn to_database(self, db: &Db) -> Result<()> {
        set(db, DB_SIGNER_HISTORY, self)?;
        Ok(())
    }
}

impl FromDatabase for SignerHistory {
    fn from_database(db: &Db) -> Result<Self>
    where
        Self: Sized,
    {
        get(db, DB_SIGNER_HISTORY)
    }
}

impl ToDatabase for Account {
    fn to_database(self, db: &Db) -> Result<()> {
        set(db, DB_ACCOUNT, self)?;
        Ok(())
    }
}

impl FromDatabase for Account {
    fn from_database(db: &Db) -> Result<Self>
    where
        Self: Sized,
    {
        get(db, DB_ACCOUNT)
    }
}

impl ToDatabase for AuthenticationToken {
    fn to_database(self, db: &Db) -> Result<()> {
        set(db, DB_AUTHENTICATION_TOKEN, self)?;
        Ok(())
    }
}

impl FromDatabase for AuthenticationToken {
    fn from_database(db: &Db) -> Result<Self>
    where
        Self: Sized,
    {
        get(db, DB_AUTHENTICATION_TOKEN)
    }
}
