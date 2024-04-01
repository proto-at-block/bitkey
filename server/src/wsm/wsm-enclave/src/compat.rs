use std::str::FromStr;

// Temporary enum used to translate between 0.29.2's Network and 0.30.0 Network so WSM-API and
// enclave can talk to each other.
pub(crate) enum BitcoinNetwork {
    V30(bdk::bitcoin::Network),
}

// Converts from 0.30 Network to version-aware enum
impl From<bdk::bitcoin::Network> for BitcoinNetwork {
    fn from(value: bdk::bitcoin::Network) -> Self {
        BitcoinNetwork::V30(value)
    }
}

impl BitcoinNetwork {
    pub fn as_v29_network(&self) -> wsm_common::bitcoin::Network {
        match self {
            BitcoinNetwork::V30(network) => match network {
                bdk::bitcoin::Network::Bitcoin => wsm_common::bitcoin::Network::Bitcoin,
                bdk::bitcoin::Network::Signet => wsm_common::bitcoin::Network::Signet,
                bdk::bitcoin::Network::Testnet => wsm_common::bitcoin::Network::Testnet,
                bdk::bitcoin::Network::Regtest => wsm_common::bitcoin::Network::Regtest,
                _ => wsm_common::bitcoin::Network::Signet,
            },
        }
    }

    pub fn as_v30_network(&self) -> bdk::bitcoin::Network {
        match self {
            BitcoinNetwork::V30(network) => *network,
        }
    }
}

pub(crate) enum BitcoinDerivationPath {
    V30(bdk::bitcoin::bip32::DerivationPath),
}

impl From<bdk::bitcoin::bip32::DerivationPath> for BitcoinDerivationPath {
    fn from(value: bdk::bitcoin::bip32::DerivationPath) -> Self {
        BitcoinDerivationPath::V30(value)
    }
}

impl BitcoinDerivationPath {
    pub fn as_v30_path(&self) -> bdk::bitcoin::bip32::DerivationPath {
        match self {
            BitcoinDerivationPath::V30(path) => path.clone(),
        }
    }
}

pub(crate) struct BitcoinV30ExtendedPubKey(pub bdk::bitcoin::bip32::ExtendedPubKey);

impl From<BitcoinV30ExtendedPubKey> for wsm_common::bitcoin::bip32::ExtendedPubKey {
    fn from(value: BitcoinV30ExtendedPubKey) -> Self {
        wsm_common::bitcoin::bip32::ExtendedPubKey {
            network: BitcoinNetwork::V30(value.0.network).as_v29_network(),
            depth: value.0.depth,
            parent_fingerprint: BitcoinV30Fingerprint(value.0.parent_fingerprint).into(),
            child_number: BitcoinV30ChildNumber(value.0.child_number).into(),
            chain_code: BitcoinV30ChainCode(value.0.chain_code).into(),
            public_key: SecpV30PublicKey(value.0.public_key).into(),
        }
    }
}

struct BitcoinV30Fingerprint(bdk::bitcoin::bip32::Fingerprint);

impl From<BitcoinV30Fingerprint> for wsm_common::bitcoin::bip32::Fingerprint {
    fn from(value: BitcoinV30Fingerprint) -> Self {
        wsm_common::bitcoin::bip32::Fingerprint::from_str(&value.0.to_string()).unwrap()
    }
}

struct BitcoinV30ChildNumber(bdk::bitcoin::bip32::ChildNumber);

impl From<BitcoinV30ChildNumber> for wsm_common::bitcoin::bip32::ChildNumber {
    fn from(value: BitcoinV30ChildNumber) -> Self {
        match value.0 {
            bdk::bitcoin::bip32::ChildNumber::Normal { index } => {
                wsm_common::bitcoin::bip32::ChildNumber::Normal { index }
            }
            bdk::bitcoin::bip32::ChildNumber::Hardened { index } => {
                wsm_common::bitcoin::bip32::ChildNumber::Hardened { index }
            }
        }
    }
}

struct BitcoinV30ChainCode(bdk::bitcoin::bip32::ChainCode);

impl From<BitcoinV30ChainCode> for wsm_common::bitcoin::bip32::ChainCode {
    fn from(value: BitcoinV30ChainCode) -> Self {
        wsm_common::bitcoin::bip32::ChainCode::from_str(&value.0.to_string()).unwrap()
    }
}

struct SecpV30PublicKey(bdk::bitcoin::secp256k1::PublicKey);

impl From<SecpV30PublicKey> for wsm_common::bitcoin::secp256k1::PublicKey {
    fn from(value: SecpV30PublicKey) -> Self {
        wsm_common::bitcoin::secp256k1::PublicKey::from_slice(&value.0.serialize()).unwrap()
    }
}
