use bitcoin::bip32::{ChildNumber, DerivationPath};
use bitcoin::Network;
use serde::{Deserialize, Serialize};
use std::fmt;

trait PathValue {
    fn path_value(&self) -> ChildNumber;
}

#[derive(Serialize, Deserialize, Copy, Clone, Debug, PartialEq)]
pub enum CoinType {
    Bitcoin,
    Testnet,
}

impl PathValue for CoinType {
    fn path_value(&self) -> ChildNumber {
        match self {
            CoinType::Bitcoin => ChildNumber::Hardened { index: 0 },
            CoinType::Testnet => ChildNumber::Hardened { index: 1 },
        }
    }
}

impl From<Network> for CoinType {
    fn from(value: Network) -> Self {
        match value {
            Network::Bitcoin => CoinType::Bitcoin,
            _ => CoinType::Testnet,
        }
    }
}

#[derive(Serialize, Deserialize, Copy, Clone, Debug, PartialEq)]
pub enum WSMSupportedDomain {
    Config,
    Spend(CoinType),
}

impl PathValue for WSMSupportedDomain {
    fn path_value(&self) -> ChildNumber {
        match self {
            WSMSupportedDomain::Spend(_) => ChildNumber::Hardened { index: 84 },
            WSMSupportedDomain::Config => ChildNumber::Hardened { index: 212152 }, // b-l-o-b = 2-12-15-2
        }
    }
}

impl From<WSMSupportedDomain> for DerivationPath {
    fn from(value: WSMSupportedDomain) -> Self {
        DerivationPath::from(value.derivation_path())
    }
}

impl WSMSupportedDomain {
    pub fn as_str(&self) -> &'static str {
        match self {
            WSMSupportedDomain::Config => "config",
            WSMSupportedDomain::Spend(_) => "spend",
        }
    }

    fn derivation_path(&self) -> Vec<ChildNumber> {
        match self {
            WSMSupportedDomain::Config => vec![
                self.path_value(),
                ChildNumber::Hardened { index: 0 },
                ChildNumber::Hardened { index: 0 },
            ],
            WSMSupportedDomain::Spend(coin_type) => vec![
                self.path_value(),
                coin_type.path_value(),
                ChildNumber::Hardened { index: 0 },
            ],
        }
    }
}

impl fmt::Display for WSMSupportedDomain {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self {
            WSMSupportedDomain::Config => write!(f, "config"),
            WSMSupportedDomain::Spend(_) => write!(f, "spend"),
        }
    }
}

#[cfg(test)]
mod tests {
    use crate::derivation::{CoinType, WSMSupportedDomain};
    use bitcoin::bip32::{ChildNumber, DerivationPath};

    #[test]
    fn test_derivation_path() {
        assert_eq!(
            DerivationPath::from(WSMSupportedDomain::Config),
            DerivationPath::from(vec![
                ChildNumber::Hardened { index: 212152 },
                ChildNumber::Hardened { index: 0 },
                ChildNumber::Hardened { index: 0 }
            ]),
        );
        assert_eq!(
            DerivationPath::from(WSMSupportedDomain::Spend(CoinType::Bitcoin)),
            DerivationPath::from(vec![
                ChildNumber::Hardened { index: 84 },
                ChildNumber::Hardened { index: 0 },
                ChildNumber::Hardened { index: 0 }
            ]),
        );
        assert_eq!(
            DerivationPath::from(WSMSupportedDomain::Spend(CoinType::Testnet)),
            DerivationPath::from(vec![
                ChildNumber::Hardened { index: 84 },
                ChildNumber::Hardened { index: 1 },
                ChildNumber::Hardened { index: 0 }
            ]),
        )
    }

    #[test]
    fn test_database_string() {
        assert_eq!(
            WSMSupportedDomain::Spend(CoinType::Testnet).as_str(),
            "spend"
        );
        assert_eq!(
            WSMSupportedDomain::Spend(CoinType::Bitcoin).as_str(),
            "spend"
        );
        assert_eq!(WSMSupportedDomain::Config.as_str(), "config");
    }
}
