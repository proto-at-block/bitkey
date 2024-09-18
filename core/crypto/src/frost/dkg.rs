use bitcoin::secp256k1::{schnorr::Signature, PublicKey};
use thiserror::Error;

#[cfg(feature = "serde")]
use serde::{Deserialize, Serialize};

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
pub fn share_agg_params(share_package: &SharePackage) -> Result<ShareAggParams, KeygenError> {
    let share_agg_params = ShareAggParams {
        intermediate_share: match share_package.index {
            APP_PARTICIPANT_INDEX => Share(APP_DKG_INTERMEDIATE_SHARE),
            SERVER_PARTICIPANT_INDEX => Share(SERVER_DKG_INTERMEDIATE_SHARE),
            _ => return Err(KeygenError::InvalidParticipantIndex),
        },
        coefficient_commitments: share_package.coefficient_commitments.clone(),
    };

    Ok(share_agg_params)
}

/// Aggregate the shares and generates key commitments.
///
/// package – The participant's share package.
/// peer_package – The peer's share package.
/// share_agg_params – The participant's share aggregation parameters.
pub fn aggregate_shares(
    _share_agg_params: &ShareAggParams,
    peer_package: &SharePackage,
) -> Result<ShareDetails, KeygenError> {
    let secret_share = match peer_package.index {
        SERVER_PARTICIPANT_INDEX => APP_SHAMIR_SHARE,
        APP_PARTICIPANT_INDEX => SERVER_SHAMIR_SHARE,
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

pub fn equality_check(
    peer_key_commitments: &KeyCommitments,
    share_details: ShareDetails,
) -> Result<ShareDetails, KeygenError> {
    if peer_key_commitments != &share_details.key_commitments {
        return Err(KeygenError::InvalidKeyCommitments);
    }

    Ok(share_details)
}

#[derive(Clone, Debug, PartialEq)]
#[cfg_attr(feature = "serde", derive(Deserialize, Serialize))]
pub struct SharePackage {
    index: ParticipantIndex,
    coefficient_commitments: Vec<PublicKey>,
    proof_of_knowledge: Signature,
    intermediate_share: Share,
}

#[derive(Error, Debug, PartialEq)]
pub enum KeygenError {
    #[error("Generator is missing a share package. Did you forget to generate a share package?")]
    MissingSharePackage,
    #[error("Generator is missing share aggregation parameters. Did you forget to generate a share package?")]
    MissingShareAggParams,
    #[error("Invalid participant index")]
    InvalidParticipantIndex,
    #[error("Invalid proof of knowledge")]
    InvalidProofOfKnowledge,
    #[error("Invalid intermediate share")]
    InvalidIntermediateShare,
    #[error("Invalid key commitments")]
    InvalidKeyCommitments,
}

pub mod server {
    use crate::frost::{KeyCommitments, Participant, ShareDetails};

    use super::{
        aggregate_shares, equality_check, generate_share_package, share_agg_params, KeygenError,
        SharePackage,
    };

    #[cfg(feature = "serde")]
    use serde::{Deserialize, Serialize};

    #[cfg_attr(feature = "serde", derive(Deserialize, Serialize))]
    pub struct InitiateDkgResult {
        pub share_package: SharePackage,
        pub share_details: ShareDetails,
    }

    pub fn initiate_dkg(
        peer_share_package: &SharePackage,
    ) -> Result<InitiateDkgResult, KeygenError> {
        let share_package = generate_share_package(Participant::Server, Participant::App)?;
        let share_agg_params = share_agg_params(&share_package)?;
        let share_details = aggregate_shares(&share_agg_params, peer_share_package)?;
        Ok(InitiateDkgResult {
            share_package,
            share_details,
        })
    }

    pub fn continue_dkg(
        share_details: ShareDetails,
        peer_key_commitments: &KeyCommitments,
    ) -> Result<ShareDetails, KeygenError> {
        equality_check(peer_key_commitments, share_details)
    }
}

pub mod app {
    use crate::frost::{KeyCommitments, Participant, ShareDetails};

    use super::{
        aggregate_shares, equality_check, generate_share_package, share_agg_params, KeygenError,
        SharePackage,
    };

    pub fn initiate_dkg() -> Result<SharePackage, KeygenError> {
        generate_share_package(Participant::App, Participant::Server)
    }

    pub fn continue_dkg(
        share_package: &SharePackage,
        peer_share_package: &SharePackage,
        peer_key_commitments: &KeyCommitments,
    ) -> Result<ShareDetails, KeygenError> {
        let share_agg_params = share_agg_params(share_package)?;
        let share_details = aggregate_shares(&share_agg_params, peer_share_package)?;
        equality_check(peer_key_commitments, share_details)
    }
}

#[cfg(test)]
mod tests {
    use crate::frost::dkg::{equality_check, generate_share_package, KeygenError};
    use crate::frost::Participant::{App, Server};

    use super::{aggregate_shares, app, server, share_agg_params};

    #[test]
    fn test_generate_share_package() {
        // Valid permutations
        assert!(generate_share_package(App, Server).is_ok());
        assert!(generate_share_package(Server, App).is_ok());

        // Invalid Permutations
        assert_eq!(
            generate_share_package(App, App),
            Err(KeygenError::InvalidParticipantIndex)
        );
        assert_eq!(
            generate_share_package(Server, Server),
            Err(KeygenError::InvalidParticipantIndex)
        )
    }

    #[test]
    fn test_equality_check() {
        // Run DKG twice, and check their outputs are equal

        let app_share_package = generate_share_package(App, Server).unwrap();
        let app_share_agg_params = share_agg_params(&app_share_package).unwrap();

        let server_share_package = generate_share_package(Server, App).unwrap();
        let server_share_agg_params = share_agg_params(&server_share_package).unwrap();

        let app_share_details =
            aggregate_shares(&app_share_agg_params, &server_share_package).unwrap();
        let server_share_details =
            aggregate_shares(&server_share_agg_params, &app_share_package).unwrap();

        // Server checks
        assert!(equality_check(
            &app_share_details.key_commitments,
            server_share_details.clone()
        )
        .is_ok());
        // App checks
        assert!(equality_check(&server_share_details.key_commitments, app_share_details).is_ok())
    }

    #[test]
    fn test_wrappers() {
        let app_initiate_result = app::initiate_dkg().unwrap();
        let server_initiate_result = server::initiate_dkg(&app_initiate_result).unwrap();
        let app_continue_result = app::continue_dkg(
            &app_initiate_result,
            &server_initiate_result.share_package,
            &server_initiate_result.share_details.key_commitments,
        )
        .unwrap();
        server::continue_dkg(
            server_initiate_result.share_details,
            &app_continue_result.key_commitments,
        )
        .unwrap();
    }
}
