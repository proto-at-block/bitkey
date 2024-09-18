use bitcoin::secp256k1::PublicKey;

#[cfg(feature = "serde")]
use serde::{Deserialize, Serialize};

pub mod dkg;
pub(crate) mod fakes;

#[derive(Clone, Debug, PartialEq)]
#[cfg_attr(feature = "serde", derive(Deserialize, Serialize))]
pub struct Share(pub [u8; 32]);

/// Output of the DKG and Refresh protocol, containing the secret share and VSS commitments.
#[derive(Clone, Debug, PartialEq)]
#[cfg_attr(feature = "serde", derive(Deserialize, Serialize))]
pub struct ShareDetails {
    secret_share: Share,
    pub key_commitments: KeyCommitments,
}

#[derive(Clone, Debug)]
#[cfg_attr(feature = "serde", derive(Deserialize, Serialize))]
pub struct KeyCommitments {
    vss_commitments: Vec<PublicKey>,
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

/// Returns a ShareDetails object containing the participant's share and public key.
///
/// This should **NOT** be shared with other participants.
#[derive(Clone, Debug, PartialEq)]
pub struct ShareAggParams {
    intermediate_share: Share,
    coefficient_commitments: Vec<PublicKey>,
}

#[derive(Copy, Clone, Debug, PartialEq, Eq)]
#[cfg_attr(feature = "serde", derive(Deserialize, Serialize))]
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
