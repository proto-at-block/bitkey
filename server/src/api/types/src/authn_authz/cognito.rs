use crate::account::identifiers::AccountId;
use serde::{Deserialize, Serialize};
use std::{fmt, str::FromStr};
use utoipa::ToSchema;

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq, ToSchema)]
pub struct CognitoUsername(String);

impl CognitoUsername {
    fn new(username: String) -> Self {
        Self(username)
    }
}

impl FromStr for CognitoUsername {
    type Err = urn::Error;

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
    Wallet(AccountId),
    Recovery(AccountId),
}

impl CognitoUser {
    pub fn get_account_id(&self) -> AccountId {
        match self {
            CognitoUser::Wallet(id) => id.to_owned(),
            CognitoUser::Recovery(id) => id.to_owned(),
        }
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
            CognitoUser::Wallet(id) => CognitoUsername::new(format!("{}", id)),
            CognitoUser::Recovery(id) => CognitoUsername::new(format!("{}-recovery", id)),
        }
    }
}

impl FromStr for CognitoUser {
    type Err = urn::Error;

    fn from_str(value: &str) -> Result<Self, Self::Err> {
        let (use_recovery_domain, account_id_str) = if value.ends_with("-recovery") {
            let parts: Vec<&str> = value.split("-recovery").collect();
            (true, parts[0])
        } else {
            (false, value)
        };
        let account_id = AccountId::from_str(account_id_str)?;
        if use_recovery_domain {
            Ok(CognitoUser::Recovery(account_id))
        } else {
            Ok(CognitoUser::Wallet(account_id))
        }
    }
}
