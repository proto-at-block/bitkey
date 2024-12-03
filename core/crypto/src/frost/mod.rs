use bitcoin::{
    bip32::{ChildNumber, ExtendedPubKey},
    secp256k1::{
        schnorr::Signature,
        serde::{Deserialize, Serialize},
        PublicKey,
    },
    Network,
};

use secp256k1_zkp::frost::CoefficientCommitment;
pub use secp256k1_zkp::{
    self as zkp,
    constants::{GENERATOR_X, GENERATOR_Y},
    frost::FrostShare,
};

pub mod dkg;
pub mod signing;

#[cfg(test)]
mod tests;

/// Output of the DKG and Refresh protocol, containing the secret share and VSS commitments.
#[derive(Clone, Debug, PartialEq, Deserialize, Serialize)]
#[serde(crate = "bitcoin::secp256k1::serde")]
pub struct ShareDetails {
    pub secret_share: FrostShare,
    pub key_commitments: KeyCommitments,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(crate = "bitcoin::secp256k1::serde")]
pub struct KeyCommitments {
    pub vss_commitments: Vec<PublicKey>,
    pub aggregate_public_key: PublicKey,
}

impl PartialEq for KeyCommitments {
    fn eq(&self, other: &Self) -> bool {
        self.vss_commitments == other.vss_commitments
            && self.aggregate_public_key == other.aggregate_public_key
    }
}

impl KeyCommitments {
    fn aggregate_coefficient_commitment(&self) -> CoefficientCommitment {
        CoefficientCommitment::from_public_keys(
            self.vss_commitments
                .iter()
                .map(|public_key| {
                    let zkp_public_key: ZkpPublicKey = (*public_key).into();
                    zkp_public_key.0
                })
                .collect(),
        )
    }
}

/// A participant in the DKG protocol.
#[derive(Copy, Clone, Debug, PartialEq)]
pub enum Participant {
    App,
    Server,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq, Deserialize, Serialize)]
#[serde(crate = "bitcoin::secp256k1::serde")]
pub struct ParticipantIndex(pub u8);

pub const APP_PARTICIPANT_INDEX: ParticipantIndex = ParticipantIndex(1);
pub const SERVER_PARTICIPANT_INDEX: ParticipantIndex = ParticipantIndex(2);

impl From<Participant> for ParticipantIndex {
    fn from(participant: Participant) -> Self {
        match participant {
            Participant::App => APP_PARTICIPANT_INDEX,
            Participant::Server => SERVER_PARTICIPANT_INDEX,
        }
    }
}

struct ZkpPublicKey(zkp::PublicKey);
struct ZkpSchnorrSignature(zkp::schnorr::Signature);

// We expose and ingest non-secpZKP publicly outside this crate, so we'd need some way to
// "translate" between them.
impl From<PublicKey> for ZkpPublicKey {
    fn from(value: PublicKey) -> Self {
        ZkpPublicKey(unsafe { core::mem::transmute::<_, zkp::PublicKey>(value) })
    }
}

impl From<ZkpPublicKey> for PublicKey {
    fn from(value: ZkpPublicKey) -> Self {
        unsafe { core::mem::transmute::<_, Self>(value.0) }
    }
}

impl From<ZkpSchnorrSignature> for Signature {
    fn from(value: ZkpSchnorrSignature) -> Self {
        unsafe { core::mem::transmute::<_, Self>(value.0) }
    }
}

/// We use participant indices (1, 2, ...) to derive an identity public key.
impl From<Participant> for zkp::PublicKey {
    fn from(participant: Participant) -> Self {
        let generator_point = get_generator_point();
        let index: ParticipantIndex = participant.into();
        let mut index_bytes = [0u8; 32];
        index_bytes[31] = index.0;
        let participant_index_tweak =
            zkp::Scalar::from_be_bytes(index_bytes).expect("Scalar should always be valid.");

        generator_point
            .mul_tweak(zkp::SECP256K1, &participant_index_tweak)
            .expect("Non-zero scalar multiplication of generator should always be valid.")
    }
}

fn get_generator_point() -> zkp::PublicKey {
    let mut g_bytes = [0u8; 65];
    g_bytes[0] = 0x04;
    g_bytes[1..33].copy_from_slice(&GENERATOR_X);
    g_bytes[33..65].copy_from_slice(&GENERATOR_Y);

    zkp::PublicKey::from_slice(&g_bytes)
        .expect("Should always succeed since we use the generator point.")
}

/// The synthetic chaincode applied to a master xpub for a FROST aggregate public key. Its value
/// represents the SHA256 hash of "FROSTFROSTFROST", similar to MuSig2's synthetic chaincode as
/// described in BIP 328.
pub const FROST_CHAINCODE: [u8; 32] = [
    0xca, 0x14, 0x8b, 0x78, 0x09, 0x33, 0xe5, 0x52, 0xe9, 0xcc, 0xf5, 0x95, 0xf9, 0xd4, 0x48, 0x2d,
    0x40, 0x37, 0x2a, 0x55, 0xce, 0xc5, 0x82, 0x43, 0xee, 0xe8, 0x79, 0x90, 0x62, 0x33, 0x15, 0x5c,
];

pub fn compute_frost_master_xpub(
    public_key: bitcoin::secp256k1::PublicKey,
    network: Network,
) -> ExtendedPubKey {
    ExtendedPubKey {
        depth: 0,
        parent_fingerprint: Default::default(),
        child_number: ChildNumber::Normal { index: 0 },
        chain_code: FROST_CHAINCODE.into(),
        network,
        public_key,
    }
}
