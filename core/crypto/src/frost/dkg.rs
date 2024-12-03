use bitcoin::secp256k1::{
    serde::{Deserialize, Serialize},
    PublicKey,
};
use rand::{rngs::StdRng, RngCore, SeedableRng};
use secp256k1_zkp::{
    self as zkp,
    frost::{
        generate_frost_shares, CoefficientCommitment, FrostPublicKey, FrostShare, VerificationShare,
    },
};
use thiserror::Error;

use super::{KeyCommitments, Participant, ShareDetails, ZkpPublicKey};

static DKG_THRESHOLD: usize = 2;
static DKG_PARTICIPANTS: usize = 2;

pub fn generate_share_packages() -> Result<Vec<SharePackage>, KeygenError> {
    let mut seed = [0u8; 32];
    let mut rng = StdRng::from_entropy();
    rng.fill_bytes(&mut seed);

    let participants = [Participant::App, Participant::Server]
        .iter()
        .map(|participant| (*participant).into())
        .collect::<Vec<zkp::PublicKey>>();
    let participants_refs = participants.iter().collect::<Vec<&zkp::PublicKey>>();

    let (shares, commitments, pok) =
        generate_frost_shares(zkp::SECP256K1, &seed, DKG_THRESHOLD, &participants_refs)
            .expect("Should always succeed since we hardcode the threshold.");

    let share_packages = shares
        .into_iter()
        .enumerate()
        .map(|(index, share)| SharePackage {
            index: participants[index],
            coefficient_commitments: commitments.to_public_keys(),
            proof_of_knowledge: pok,
            intermediate_share: share,
        })
        .collect();

    Ok(share_packages)
}

/// Aggregate the shares and generates key commitments.
///
/// package – The participant's share package.
/// peer_package – The peer's share package.
/// share_agg_params – The participant's share aggregation parameters.
pub fn aggregate_shares(
    participant: Participant,
    share_packages: &[&SharePackage],
) -> Result<ShareDetails, KeygenError> {
    let intermediate_shares = share_packages
        .iter()
        .map(|package| &package.intermediate_share)
        .collect::<Vec<&FrostShare>>();
    let participants = share_packages
        .iter()
        .map(|package| &package.index)
        .collect::<Vec<&zkp::PublicKey>>();
    let vss_commitments = share_packages
        .iter()
        .map(|package| {
            CoefficientCommitment::from_public_keys(package.coefficient_commitments.clone())
        })
        .collect::<Vec<CoefficientCommitment>>();
    let vss_commitment_refs = vss_commitments
        .iter()
        .collect::<Vec<&CoefficientCommitment>>();

    let poks = share_packages
        .iter()
        .map(|package| &package.proof_of_knowledge)
        .collect::<Vec<&zkp::schnorr::Signature>>();

    let (secret_share, vss_commitments) = FrostShare::aggregate(
        zkp::SECP256K1,
        &intermediate_shares,
        &vss_commitment_refs,
        &poks,
        &participant.into(),
        DKG_THRESHOLD,
    )
    .map_err(|_| KeygenError::ShareAggregationFailed)?;

    let app_verification_share = VerificationShare::new(
        zkp::SECP256K1,
        &vss_commitments,
        &Participant::App.into(),
        DKG_PARTICIPANTS,
    )
    .map_err(|_| KeygenError::VerificationShareGenerationFailed)?;
    let server_verification_share = VerificationShare::new(
        zkp::SECP256K1,
        &vss_commitments,
        &Participant::Server.into(),
        DKG_PARTICIPANTS,
    )
    .map_err(|_| KeygenError::VerificationShareGenerationFailed)?;

    let aggregate_public_key = FrostPublicKey::from_verification_shares(
        zkp::SECP256K1,
        &[&app_verification_share, &server_verification_share],
        &participants,
    );

    Ok(ShareDetails {
        key_commitments: KeyCommitments {
            aggregate_public_key: ZkpPublicKey(aggregate_public_key.public_key(zkp::SECP256K1))
                .into(),
            vss_commitments: vss_commitments
                .to_public_keys()
                .into_iter()
                .map(|zkp_public_key| ZkpPublicKey(zkp_public_key).into())
                .collect::<Vec<PublicKey>>(),
        },
        secret_share,
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

#[derive(Clone, Debug, PartialEq, Deserialize, Serialize)]
#[serde(crate = "bitcoin::secp256k1::serde")]
pub struct SharePackage {
    index: zkp::PublicKey,
    coefficient_commitments: Vec<zkp::PublicKey>,
    proof_of_knowledge: zkp::schnorr::Signature,
    intermediate_share: FrostShare,
}

#[derive(Error, Debug, PartialEq)]
pub enum KeygenError {
    #[error("Generator is missing a share package. Did you forget to generate a share package?")]
    MissingSharePackage,
    #[error("Unable to run DKG for the given participants.")]
    InvalidParticipants,
    #[error("Invalid proof of knowledge")]
    InvalidProofOfKnowledge,
    #[error("Invalid intermediate share")]
    InvalidIntermediateShare,
    #[error("Invalid key commitments")]
    InvalidKeyCommitments,
    #[error("Unable to aggregate shares")]
    ShareAggregationFailed,
    #[error("Unable to generate verification share")]
    VerificationShareGenerationFailed,
}

pub mod server {
    use crate::frost::{KeyCommitments, Participant, ShareDetails};
    use bitcoin::secp256k1::serde::{Deserialize, Serialize};

    use super::{
        aggregate_shares, equality_check, generate_share_packages, KeygenError, SharePackage,
    };

    #[derive(Deserialize, Serialize)]
    #[serde(crate = "bitcoin::secp256k1::serde")]
    pub struct InitiateDkgResult {
        pub share_package: SharePackage,
        pub share_details: ShareDetails,
    }

    pub fn initiate_dkg(
        peer_share_package: &SharePackage,
    ) -> Result<InitiateDkgResult, KeygenError> {
        let mut share_packages = generate_share_packages()?;

        let share_package_for_server = share_packages
            .pop()
            .expect("server share package should exist.");

        let share_package_for_app = share_packages
            .pop()
            .expect("app share package should exist.");

        let share_details = aggregate_shares(
            Participant::Server,
            &[peer_share_package, &share_package_for_server],
        )?;

        Ok(InitiateDkgResult {
            share_package: share_package_for_app,
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
        aggregate_shares, equality_check, generate_share_packages, KeygenError, SharePackage,
    };

    pub struct InitialSharePackage {
        pub share_package: SharePackage,
        pub share_package_for_peer: SharePackage,
    }

    pub fn initiate_dkg() -> Result<InitialSharePackage, KeygenError> {
        let mut share_packages = generate_share_packages()?;

        let share_package_for_server = share_packages
            .pop()
            .expect("Should have a SharePackage for Server");

        let share_package_for_app = share_packages
            .pop()
            .expect("Should have an App SharePackage");

        Ok(InitialSharePackage {
            share_package: share_package_for_app,
            share_package_for_peer: share_package_for_server,
        })
    }

    pub fn continue_dkg(
        share_package: &SharePackage,
        peer_share_package: &SharePackage,
        peer_key_commitments: &KeyCommitments,
    ) -> Result<ShareDetails, KeygenError> {
        let share_details =
            aggregate_shares(Participant::App, &[share_package, peer_share_package])?;
        equality_check(peer_key_commitments, share_details)
    }
}

#[cfg(test)]
mod tests {
    use rand::{thread_rng, RngCore};
    use secp256k1_zkp::frost::{FrostPublicKey, FrostSession, FrostSessionId, VerificationShare};
    use secp256k1_zkp::{self as zkp};
    use secp256k1_zkp::{new_frost_nonce_pair, Message, Scalar};

    use crate::frost::dkg::{equality_check, generate_share_packages};
    use crate::frost::Participant::{self, App, Server};

    use super::{aggregate_shares, app, server, DKG_PARTICIPANTS};

    #[test]
    fn test_equality_check() {
        // Run DKG twice, and check their outputs are equal

        let app_share_packages = generate_share_packages().unwrap();
        let server_share_packages = generate_share_packages().unwrap();

        let app_share_details = aggregate_shares(
            App,
            &[
                app_share_packages.first().unwrap(),
                server_share_packages.first().unwrap(),
            ],
        )
        .unwrap();
        let server_share_details = aggregate_shares(
            Server,
            &[
                server_share_packages.get(1).unwrap(),
                app_share_packages.get(1).unwrap(),
            ],
        )
        .unwrap();

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
        let server_initiate_result =
            server::initiate_dkg(&app_initiate_result.share_package_for_peer).unwrap();

        let app_continue_result = app::continue_dkg(
            &app_initiate_result.share_package,
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

    #[test]
    fn test_sign() {
        let app_share_packages = generate_share_packages().unwrap();
        let server_share_packages = generate_share_packages().unwrap();

        let app_share_packages_to_agg = vec![
            app_share_packages.first().unwrap(),
            server_share_packages.first().unwrap(),
        ];
        let app_share_details = aggregate_shares(App, &app_share_packages_to_agg).unwrap();

        let server_share_packages_to_agg = vec![
            server_share_packages.get(1).unwrap(),
            app_share_packages.get(1).unwrap(),
        ];
        let server_share_details = aggregate_shares(Server, &server_share_packages_to_agg).unwrap();

        let app_verification_share = VerificationShare::new(
            zkp::SECP256K1,
            &app_share_details
                .key_commitments
                .aggregate_coefficient_commitment(),
            &Participant::App.into(),
            DKG_PARTICIPANTS,
        )
        .unwrap();

        let server_verification_share = VerificationShare::new(
            zkp::SECP256K1,
            &app_share_details
                .key_commitments
                .aggregate_coefficient_commitment(),
            &Participant::Server.into(),
            DKG_PARTICIPANTS,
        )
        .unwrap();

        let participants = app_share_packages
            .iter()
            .map(|package| &package.index)
            .collect::<Vec<&zkp::PublicKey>>();
        let mut aggregate_pubkey = FrostPublicKey::from_verification_shares(
            zkp::SECP256K1,
            &[&app_verification_share, &server_verification_share],
            &participants,
        );
        aggregate_pubkey.add_tweak(zkp::SECP256K1, Scalar::random());
        aggregate_pubkey.add_x_only_tweak(zkp::SECP256K1, Scalar::random());
        let (final_pubkey, _) = aggregate_pubkey
            .public_key(zkp::SECP256K1)
            .x_only_public_key();

        let mut msg = [0u8; 32];
        thread_rng().fill_bytes(&mut msg[..]);
        let msg = Message::from_digest(msg);

        let (app_secret_nonce, app_public_nonce) = new_frost_nonce_pair(
            zkp::SECP256K1,
            FrostSessionId::random(),
            &app_share_details.secret_share,
            &aggregate_pubkey,
            &msg,
            None,
        );

        let (server_secret_nonce, server_public_nonce) = new_frost_nonce_pair(
            zkp::SECP256K1,
            FrostSessionId::random(),
            &server_share_details.secret_share,
            &aggregate_pubkey,
            &msg,
            None,
        );

        let app_frost_session = FrostSession::new(
            zkp::SECP256K1,
            &[&app_public_nonce, &server_public_nonce],
            &msg,
            &aggregate_pubkey,
            &Participant::App.into(),
            &participants,
            None,
        );

        let server_frost_session = FrostSession::new(
            zkp::SECP256K1,
            &[&app_public_nonce, &server_public_nonce],
            &msg,
            &aggregate_pubkey,
            &Participant::Server.into(),
            &participants,
            None,
        );

        let app_partial_sig = app_frost_session.partial_sign(
            zkp::SECP256K1,
            app_secret_nonce,
            &app_share_details.secret_share,
            &aggregate_pubkey,
        );
        let server_partial_sig = server_frost_session.partial_sign(
            zkp::SECP256K1,
            server_secret_nonce,
            &server_share_details.secret_share,
            &aggregate_pubkey,
        );

        let app_agg_sig = app_frost_session
            .aggregate_partial_sigs(zkp::SECP256K1, &[&app_partial_sig, &server_partial_sig]);
        let server_agg_sig = server_frost_session
            .aggregate_partial_sigs(zkp::SECP256K1, &[&app_partial_sig, &server_partial_sig]);

        assert_eq!(app_agg_sig, server_agg_sig);
        zkp::SECP256K1
            .verify_schnorr(&app_agg_sig, &msg, &final_pubkey)
            .unwrap()
    }
}
