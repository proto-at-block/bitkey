use bitcoin::secp256k1::{schnorr::Signature, PublicKey};
use thiserror::Error;

use super::{
    fakes::*, KeyCommitments, Participant, ParticipantIndex, Share, ShareAggParams, ShareDetails,
    APP_PARTICIPANT_INDEX, SERVER_PARTICIPANT_INDEX,
};

/// Returns a SharePackage object containing the peer's share and the necessary commitments.
///
/// participant – The participant generating the share.
/// peer – The peer the participant is generating the share for.
pub fn generate_share_package(
    participant: Participant,
    peer: Participant,
) -> Result<SharePackage, KeygenError> {
    let server_pok = {
        let mut pok = [0u8; 64];
        pok[..32].copy_from_slice(&SERVER_POK_R);
        pok[32..].copy_from_slice(&SERVER_POK_S);
        pok
    };

    let app_pok = {
        let mut pok = [0u8; 64];
        pok[..32].copy_from_slice(&APP_POK_R);
        pok[32..].copy_from_slice(&APP_POK_S);
        pok
    };

    let app_coefficient_commitments =
        APP_COEFFICIENT_COMMITMENTS.map(|coefficient| PublicKey::from_slice(&coefficient).unwrap());
    let server_coefficient_commitments = SERVER_COEFFICIENT_COMMITMENTS
        .map(|coefficient| PublicKey::from_slice(&coefficient).unwrap());

    let share_package = match (participant, peer) {
        (Participant::App, Participant::Server) => SharePackage {
            index: participant.into(),
            coefficient_commitments: app_coefficient_commitments.to_vec(),
            proof_of_knowledge: Signature::from_slice(&app_pok).unwrap(),
            intermediate_share: Share(APP_SERVER_DKG_INTERMEDIATE_SHARE),
        },
        (Participant::Server, Participant::App) => SharePackage {
            index: participant.into(),
            coefficient_commitments: server_coefficient_commitments.to_vec(),
            proof_of_knowledge: Signature::from_slice(&server_pok).unwrap(),
            intermediate_share: Share(SERVER_APP_DKG_INTERMEDIATE_SHARE),
        },
        _ => return Err(KeygenError::InvalidParticipantIndex),
    };

    Ok(share_package)
}

/// Returns a ShareAggParams object containing the participant's intermediate share and coefficient
/// commitments.
///
/// This should **NOT** be shared with other participants.
///
/// participant – The participant generating the share.
pub fn share_agg_params(share_package: SharePackage) -> Result<ShareAggParams, KeygenError> {
    let share_agg_params = ShareAggParams {
        intermediate_share: match share_package.index {
            APP_PARTICIPANT_INDEX => Share(APP_DKG_INTERMEDIATE_SHARE),
            SERVER_PARTICIPANT_INDEX => Share(SERVER_DKG_INTERMEDIATE_SHARE),
            _ => return Err(KeygenError::InvalidParticipantIndex),
        },
        coefficient_commitments: share_package.coefficient_commitments,
    };

    Ok(share_agg_params)
}

/// Verifies the share package received from a peer.
///
/// participant – The participant verifying the share package.
/// share_package – The share package to verify.
pub fn verify_share_package(
    _participant: Participant,
    share_package: &SharePackage,
) -> Result<bool, KeygenError> {
    if !verify_proof_of_knowledge(share_package) {
        return Err(KeygenError::InvalidProofOfKnowledge);
    }

    if !verify_share(share_package) {
        return Err(KeygenError::InvalidIntermediateShare);
    }

    Ok(true)
}

fn verify_proof_of_knowledge(_share_package: &SharePackage) -> bool {
    true
}

fn verify_share(_share_package: &SharePackage) -> bool {
    true
}

/// Finalizes the share by aggregating the shares and generating the key commitments.
///
/// package – The participant's share package.
/// peer_package – The peer's share package.
/// share_agg_params – The participant's share aggregation parameters.
pub fn finalize_share(
    package: SharePackage,
    peer_package: SharePackage,
    _share_agg_params: ShareAggParams,
) -> Result<ShareDetails, KeygenError> {
    let secret_share = match (package.index, peer_package.index) {
        (APP_PARTICIPANT_INDEX, SERVER_PARTICIPANT_INDEX) => APP_SHAMIR_SHARE,
        (SERVER_PARTICIPANT_INDEX, APP_PARTICIPANT_INDEX) => SERVER_SHAMIR_SHARE,
        _ => return Err(KeygenError::InvalidParticipantIndex),
    };

    let aggregate_vss_commitments =
        AGGREGATE_VSS_COMMITMENTS.map(|coefficient| PublicKey::from_slice(&coefficient).unwrap());

    Ok(ShareDetails {
        key_commitments: KeyCommitments {
            vss_commitments: aggregate_vss_commitments.to_vec(),
            aggregate_public_key: PublicKey::from_slice(&AGGREGATE_PUBLIC_KEY).unwrap(),
        },
        secret_share: Share(secret_share),
    })
}

#[derive(Clone, Debug, PartialEq)]
pub struct SharePackage {
    index: ParticipantIndex,
    coefficient_commitments: Vec<PublicKey>,
    proof_of_knowledge: Signature,
    intermediate_share: Share,
}

#[derive(Error, Debug)]
pub enum KeygenError {
    #[error("Generator is missing a share package. Did you forget to generate a share package?")]
    MissingSharePackage,
    #[error("Invalid participant index")]
    InvalidParticipantIndex,
    #[error("Invalid proof of knowledge")]
    InvalidProofOfKnowledge,
    #[error("Invalid intermediate share")]
    InvalidIntermediateShare,
}
