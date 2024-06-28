use crate::account::identifiers::AccountId;
use serde::{Deserialize, Serialize};
use std::{fmt, str::FromStr};
use thiserror::Error;
use utoipa::ToSchema;

const APP_USER_SUFFIX: &str = "-app";
const HARDWARE_USER_SUFFIX: &str = "-hardware";
const RECOVERY_USER_SUFFIX: &str = "-recovery";

#[derive(Debug, Error)]
pub enum CognitoTypesError {
    #[error("Invalid username")]
    InvalidUsername,
    #[error(transparent)]
    UrnError(#[from] urn::Error),
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq, ToSchema)]
pub struct CognitoUsername(String);

impl CognitoUsername {
    fn new(username: String) -> Self {
        Self(username)
    }
}

impl FromStr for CognitoUsername {
    type Err = CognitoTypesError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let user = CognitoUser::from_str(s)?;
        Ok(user.into())
    }
}

impl AsRef<str> for CognitoUsername {
    fn as_ref(&self) -> &str {
        self.0.as_ref()
    }
}

impl From<CognitoUsername> for String {
    fn from(u: CognitoUsername) -> Self {
        u.0
    }
}

impl fmt::Display for CognitoUsername {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.as_ref())
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum CognitoUser {
    App(AccountId),
    Hardware(AccountId),
    Recovery(AccountId),
}

impl CognitoUser {
    pub fn get_account_id(&self) -> AccountId {
        match self {
            CognitoUser::App(id) => id.to_owned(),
            CognitoUser::Hardware(id) => id.to_owned(),
            CognitoUser::Recovery(id) => id.to_owned(),
        }
    }

    pub fn is_app(&self, account_id: &AccountId) -> bool {
        matches!(self, CognitoUser::App(id) if id == account_id)
    }

    pub fn is_hardware(&self, account_id: &AccountId) -> bool {
        matches!(self, CognitoUser::Hardware(id) if id == account_id)
    }

    pub fn is_recovery(&self, account_id: &AccountId) -> bool {
        matches!(self, CognitoUser::Recovery(id) if id == account_id)
    }
}

impl From<CognitoUser> for CognitoUsername {
    fn from(u: CognitoUser) -> Self {
        (&u).into()
    }
}

impl From<&CognitoUser> for CognitoUsername {
    fn from(u: &CognitoUser) -> Self {
        match u {
            CognitoUser::App(id) => CognitoUsername::new(format!("{}{}", id, APP_USER_SUFFIX)),
            CognitoUser::Hardware(id) => {
                CognitoUsername::new(format!("{}{}", id, HARDWARE_USER_SUFFIX))
            }
            CognitoUser::Recovery(id) => {
                CognitoUsername::new(format!("{}{}", id, RECOVERY_USER_SUFFIX))
            }
        }
    }
}

impl FromStr for CognitoUser {
    type Err = CognitoTypesError;

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        if value.ends_with(APP_USER_SUFFIX) {
            let parts: Vec<&str> = value.split(APP_USER_SUFFIX).collect();
            let account_id = AccountId::from_str(parts[0])?;
            return Ok(CognitoUser::App(account_id));
        }

        if value.ends_with(HARDWARE_USER_SUFFIX) {
            let parts: Vec<&str> = value.split(HARDWARE_USER_SUFFIX).collect();
            let account_id = AccountId::from_str(parts[0])?;
            return Ok(CognitoUser::Hardware(account_id));
        }

        if value.ends_with(RECOVERY_USER_SUFFIX) {
            let parts: Vec<&str> = value.split(RECOVERY_USER_SUFFIX).collect();
            let account_id = AccountId::from_str(parts[0])?;
            return Ok(CognitoUser::Recovery(account_id));
        }

        Err(CognitoTypesError::InvalidUsername)
    }
}
