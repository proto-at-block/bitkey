use bdk_utils::bdk::bitcoin::Network as BitcoinNetwork;
use serde::{Deserialize, Serialize};

#[derive(Deserialize, Serialize, Clone, Copy, Debug, PartialEq, Eq, Default)]
pub enum Network {
    #[default]
    #[serde(rename = "bitcoin-main")]
    BitcoinMain,
    #[serde(rename = "bitcoin-test")]
    BitcoinTest,
    #[serde(rename = "bitcoin-signet")]
    BitcoinSignet,
    #[serde(rename = "bitcoin-regtest")]
    BitcoinRegtest,
}

impl From<Network> for BitcoinNetwork {
    fn from(value: Network) -> Self {
        match value {
            Network::BitcoinMain => BitcoinNetwork::Bitcoin,
            Network::BitcoinTest => BitcoinNetwork::Testnet,
            Network::BitcoinSignet => BitcoinNetwork::Signet,
            Network::BitcoinRegtest => BitcoinNetwork::Regtest,
        }
    }
}

impl From<BitcoinNetwork> for Network {
    fn from(value: BitcoinNetwork) -> Self {
        match value {
            BitcoinNetwork::Bitcoin => Network::BitcoinMain,
            BitcoinNetwork::Testnet => Network::BitcoinTest,
            BitcoinNetwork::Signet => Network::BitcoinSignet,
            BitcoinNetwork::Regtest => Network::BitcoinRegtest,
            _ => panic!("network not supported"),
        }
    }
}
