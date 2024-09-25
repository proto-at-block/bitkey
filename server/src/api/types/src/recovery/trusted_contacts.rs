use serde::{Deserialize, Serialize};
use std::marker::PhantomData;
use strum_macros::Display;
use thiserror::Error;

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct TrustedContactInfo {
    #[serde(rename = "trusted_contact_alias")]
    pub alias: String,
    #[serde(rename = "trusted_contact_roles")]
    pub roles: Vec<TrustedContactRole>,
    #[serde(skip)]
    _phantom: PhantomData<()>,
}

impl TrustedContactInfo {
    pub fn new(alias: String, roles: Vec<TrustedContactRole>) -> Result<Self, TrustedContactError> {
        if roles.is_empty() {
            return Err(TrustedContactError::NoRoles);
        }
        if alias.is_empty() {
            return Err(TrustedContactError::BlankAlias);
        }
        Ok(Self {
            alias,
            roles,
            _phantom: PhantomData,
        })
    }
}

#[derive(Serialize, Deserialize, Display, Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum TrustedContactRole {
    #[serde(alias = "Beneficiary")]
    Beneficiary,
    #[serde(alias = "SocialRecoveryContact")]
    SocialRecoveryContact,
}

#[derive(Debug, Error, PartialEq)]
pub enum TrustedContactError {
    #[error("Blank alias")]
    BlankAlias,
    #[error("No roles assigned to trusted contact")]
    NoRoles,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_create_trusted_contact_info_valid() {
        // arrange
        let alias = "trusted_contact";
        let roles = vec![TrustedContactRole::Beneficiary];

        // act
        let result = TrustedContactInfo::new(alias.to_string(), roles.clone());

        // assert
        assert!(result.is_ok());

        let trusted_contact_info = result.unwrap();
        assert_eq!(trusted_contact_info.alias, alias);
        assert_eq!(trusted_contact_info.roles, roles);
    }

    #[test]
    fn test_create_trusted_contact_info_empty_alias() {
        // arrange
        let alias = "";
        let roles = vec![TrustedContactRole::Beneficiary];

        // act
        let result = TrustedContactInfo::new(alias.to_string(), roles);

        // assert
        assert!(result.is_err());
        assert_eq!(result.unwrap_err(), TrustedContactError::BlankAlias);
    }

    #[test]
    fn test_create_trusted_contact_info_no_roles() {
        // arrange
        let alias = "trusted_contact";
        let roles = vec![];

        // act
        let result = TrustedContactInfo::new(alias.to_string(), roles);

        // assert
        assert!(result.is_err());
        assert_eq!(result.unwrap_err(), TrustedContactError::NoRoles);
    }
}
