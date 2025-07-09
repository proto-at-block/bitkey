use std::fmt::{self, Display, Formatter};
use std::str::FromStr;

use external_identifier::ExternalIdentifier;
use serde::{Deserialize, Serialize};
use ulid::Ulid;
use urn::Urn;

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq, Hash)]
pub struct EncryptedAttachmentId(urn::Urn);

impl FromStr for EncryptedAttachmentId {
    type Err = urn::Error;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(Urn::from_str(s)?.into())
    }
}

impl From<urn::Urn> for EncryptedAttachmentId {
    fn from(urn: urn::Urn) -> Self {
        Self(urn)
    }
}

impl ExternalIdentifier<Ulid> for EncryptedAttachmentId {
    fn namespace() -> &'static str {
        "encrypted-attachment"
    }
}

impl EncryptedAttachmentId {
    pub fn gen() -> Result<Self, external_identifier::Error> {
        Self::new(Ulid::new())
    }
}

impl Display for EncryptedAttachmentId {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}
