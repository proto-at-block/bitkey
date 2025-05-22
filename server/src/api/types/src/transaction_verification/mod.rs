use std::fmt;
use std::fmt::{Display, Formatter};
use std::str::FromStr;

use external_identifier::ExternalIdentifier;
use serde::{Deserialize, Serialize};
use ulid::Ulid;
use urn::Urn;

pub mod entities;
pub mod router;
pub mod service;

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq, Hash)]
pub struct TransactionVerificationId(urn::Urn);

impl FromStr for TransactionVerificationId {
    type Err = urn::Error;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(Urn::from_str(s)?.into())
    }
}

impl From<urn::Urn> for TransactionVerificationId {
    fn from(urn: urn::Urn) -> Self {
        Self(urn)
    }
}

impl ExternalIdentifier<Ulid> for TransactionVerificationId {
    fn namespace() -> &'static str {
        "tx-verify"
    }
}

impl TransactionVerificationId {
    pub fn gen() -> Result<Self, external_identifier::Error> {
        Self::new(Ulid::new())
    }
}

impl Display for TransactionVerificationId {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}
