use std::fmt::{self, Display, Formatter};
use std::str::FromStr;

use external_identifier::ExternalIdentifier;
use serde::{Deserialize, Serialize};
use ulid::Ulid;
use urn::Urn;

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq, Hash)]
pub struct AccountId(urn::Urn);

impl FromStr for AccountId {
    type Err = urn::Error;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(Urn::from_str(s)?.into())
    }
}

impl From<urn::Urn> for AccountId {
    fn from(urn: urn::Urn) -> Self {
        Self(urn)
    }
}

impl ExternalIdentifier<Ulid> for AccountId {
    fn namespace() -> &'static str {
        "account"
    }
}

impl AccountId {
    pub fn gen() -> Result<Self, external_identifier::Error> {
        Self::new(Ulid::new())
    }
}

impl Display for AccountId {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq, Hash)]
pub struct KeysetId(urn::Urn);

impl From<urn::Urn> for KeysetId {
    fn from(urn: urn::Urn) -> Self {
        Self(urn)
    }
}

impl ExternalIdentifier<Ulid> for KeysetId {
    fn namespace() -> &'static str {
        "keyset"
    }
}

impl KeysetId {
    pub fn gen() -> Result<Self, external_identifier::Error> {
        Self::new(Ulid::new())
    }
}

impl Display for KeysetId {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

// TouchpointId uniquely & opaquely identifies a piece of contact information
//   associated with an account (e.g. email address, phone number)
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq, Hash)]
pub struct AuthKeysId(urn::Urn);

impl From<urn::Urn> for AuthKeysId {
    fn from(urn: urn::Urn) -> Self {
        Self(urn)
    }
}

impl ExternalIdentifier<Ulid> for AuthKeysId {
    fn namespace() -> &'static str {
        "keys-auth"
    }
}

impl AuthKeysId {
    pub fn gen() -> Result<Self, external_identifier::Error> {
        Self::new(Ulid::new())
    }
}

impl Display for AuthKeysId {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq, Hash)]
pub struct TouchpointId(urn::Urn);

impl From<urn::Urn> for TouchpointId {
    fn from(urn: urn::Urn) -> Self {
        Self(urn)
    }
}

impl ExternalIdentifier<Ulid> for TouchpointId {
    fn namespace() -> &'static str {
        "touchpoint"
    }
}

impl TouchpointId {
    pub fn gen() -> Result<Self, external_identifier::Error> {
        Self::new(Ulid::new())
    }
}

impl Display for TouchpointId {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq, Hash)]
pub struct KeyDefinitionId(urn::Urn);

impl From<urn::Urn> for KeyDefinitionId {
    fn from(urn: urn::Urn) -> Self {
        Self(urn)
    }
}

impl ExternalIdentifier<Ulid> for KeyDefinitionId {
    fn namespace() -> &'static str {
        "key-definition"
    }
}

impl KeyDefinitionId {
    pub fn gen() -> Result<Self, external_identifier::Error> {
        Self::new(Ulid::new())
    }
}

impl Display for KeyDefinitionId {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

#[cfg(test)]
mod tests {
    use external_identifier::ExternalIdentifier;
    use ulid::Ulid;

    #[test]
    fn test_account_default() {
        let id = super::AccountId::new(Ulid::default()).unwrap();
        assert_eq!(
            id.to_string(),
            "urn:wallet-account:00000000000000000000000000"
        );
        assert_eq!(
            serde_json::to_string(&id).unwrap(),
            "\"urn:wallet-account:00000000000000000000000000\""
        );
    }

    #[test]
    fn test_keyset_default() {
        let id = super::KeysetId::new(Ulid::default()).unwrap();
        assert_eq!(
            id.to_string(),
            "urn:wallet-keyset:00000000000000000000000000"
        );
        assert_eq!(
            serde_json::to_string(&id).unwrap(),
            "\"urn:wallet-keyset:00000000000000000000000000\""
        );
    }

    #[test]
    fn test_auth_keyset_default() {
        let id = super::AuthKeysId::new(Ulid::default()).unwrap();
        assert_eq!(
            id.to_string(),
            "urn:wallet-keys-auth:00000000000000000000000000"
        );
        assert_eq!(
            serde_json::to_string(&id).unwrap(),
            "\"urn:wallet-keys-auth:00000000000000000000000000\""
        );
    }

    #[test]
    fn test_touchpoint_default() {
        let id = super::TouchpointId::new(Ulid::default()).unwrap();
        assert_eq!(
            id.to_string(),
            "urn:wallet-touchpoint:00000000000000000000000000"
        );
        assert_eq!(
            serde_json::to_string(&id).unwrap(),
            "\"urn:wallet-touchpoint:00000000000000000000000000\""
        );
    }

    #[test]
    fn test_key_definition_default() {
        let id = super::KeyDefinitionId::new(Ulid::default()).unwrap();
        assert_eq!(
            id.to_string(),
            "urn:wallet-key-definition:00000000000000000000000000"
        );
        assert_eq!(
            serde_json::to_string(&id).unwrap(),
            "\"urn:wallet-key-definition:00000000000000000000000000\""
        );
    }
}
