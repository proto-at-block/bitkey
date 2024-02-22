use std::fmt::{Display, Formatter};

use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct AccountId(pub String);

impl Display for AccountId {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}

#[derive(Clone, Debug, Deserialize, Serialize, Hash, PartialEq, Eq)]
pub struct KeysetId(pub String);

impl Display for KeysetId {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}

pub mod fromagerie_network {
    use bdk::bitcoin::Network;
    use serde::Deserialize;

    #[derive(Deserialize)]
    enum WalletServerNetwork {
        #[serde(rename = "bitcoin-main")]
        Mainnet,
        #[serde(rename = "bitcoin-test")]
        Testnet,
        #[serde(rename = "bitcoin-signet")]
        Signet,
        #[serde(rename = "bitcoin-regtest")]
        Regtest,
    }

    pub fn deserialize<'de, D>(deserializer: D) -> Result<Network, D::Error>
    where
        D: serde::Deserializer<'de>,
    {
        let value = WalletServerNetwork::deserialize(deserializer)?;
        let value = match value {
            WalletServerNetwork::Mainnet => Network::Bitcoin,
            WalletServerNetwork::Testnet => Network::Testnet,
            WalletServerNetwork::Signet => Network::Signet,
            WalletServerNetwork::Regtest => Network::Regtest,
        };
        Ok(value)
    }
}

pub mod string {
    use std::fmt::Display;
    use std::str::FromStr;

    use serde::de;
    use serde::Serializer;
    use serde::{Deserialize, Deserializer};

    pub fn serialize<T: Display, S: Serializer>(
        value: &T,
        serializer: S,
    ) -> Result<S::Ok, S::Error> {
        serializer.collect_str(value)
    }

    pub fn deserialize<'de, T, D>(deserializer: D) -> Result<T, D::Error>
    where
        T: FromStr,
        T::Err: Display,
        D: Deserializer<'de>,
    {
        String::deserialize(deserializer)?
            .parse()
            .map_err(de::Error::custom)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_string_serde() {
        let id = AccountId("foo".to_string());
        let serialized = serde_json::to_string(&id).unwrap();
        assert_eq!(serialized, r#""foo""#);

        let deserialized: AccountId = serde_json::from_str(&serialized).unwrap();
        assert_eq!(deserialized.0, "foo");
    }
}
