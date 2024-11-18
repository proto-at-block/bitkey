use bitcoin::secp256k1::{
    serde::{Deserialize, Serialize},
    PublicKey,
};

pub use secp256k1_zkp::frost::FrostShare;

pub mod dkg;

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
