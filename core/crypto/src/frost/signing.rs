use bitcoin::bip32::{ChainCode, DerivationPath};
use bitcoin::hashes::{sha512, Hash, HashEngine, Hmac, HmacEngine};
use bitcoin::psbt::{Input, Psbt};
use bitcoin::secp256k1::{
    serde::{Deserialize, Serialize},
    PublicKey,
};
use bitcoin::sighash::SighashCache;
use bitcoin::taproot::{TapNodeHash, TapTweakHash};
use miniscript::psbt::{PsbtExt, PsbtSighashMsg, SighashError};
use secp256k1_zkp::frost::{
    FrostPartialSignature, FrostPublicKey, FrostPublicNonce, FrostSecretNonce, FrostSession,
    FrostSessionId, VerificationShare,
};

use secp256k1_zkp::{new_frost_nonce_pair, Message};

use thiserror::Error;

use super::{Participant, ShareDetails, ZkpPublicKey, ZkpSchnorrSignature, FROST_CHAINCODE};

static NUM_PARTICIPANTS: usize = 2;

/// A signer struct used to sign a PSBT.
pub struct Signer {
    participant: Participant,
    pub psbt: Psbt,
    signables: Vec<Signable>,
    share_details: ShareDetails,
}

impl Signer {
    /// Create a new signer.
    ///
    /// # Arguments
    ///
    /// * `participant` - The participant to sign the PSBT
    /// * `psbt` - The PSBT to sign
    /// * `share_details` - The FROST secret share to use for signing
    ///
    /// # Returns
    ///
    /// Returns a `Result` containing the new `Signer` instance if successful, or a `SigningError` if creation fails
    pub fn new(
        participant: Participant,
        psbt: Psbt,
        share_details: ShareDetails,
    ) -> Result<Self, SigningError> {
        let signables = Signer::generate_signables(&psbt, &share_details)?;
        Ok(Self {
            participant,
            psbt,
            signables,
            share_details,
        })
    }

    /// Get the public signing commitments for the signer.
    ///
    /// # Returns
    ///
    /// Returns a `Vec` of `SigningCommitment` instances.
    pub fn public_signing_commitments(&self) -> Vec<SigningCommitment> {
        self.signables
            .iter()
            .map(|signable| signable.signing_nonce_pair.signing_commitment.clone())
            .collect()
    }

    /// Generate partial signatures for the PSBT using the signing commitments from the counterparty.
    ///
    /// # Arguments
    ///
    /// * `counterparty_commitments` - The signing commitments from the counterparty
    ///
    /// # Returns
    ///
    /// Returns a `Vec` of `FrostPartialSignature` instances if successful, or a `SigningError` if generation fails
    pub fn generate_partial_signatures(
        &mut self,
        counterparty_commitments: Vec<SigningCommitment>,
    ) -> Result<Vec<FrostPartialSignature>, SigningError> {
        if self.signables.len() != counterparty_commitments.len() {
            return Err(SigningError::InvalidCounterpartyCommitments);
        }

        let zipped: Vec<_> = counterparty_commitments
            .iter()
            .zip(self.signables.iter_mut())
            .collect();

        let mut partial_signatures = Vec::with_capacity(zipped.len());
        for (counterparty_commitment, signable) in zipped {
            // Verify that the counterparty commitments match the signables
            if counterparty_commitment.signable_idx
                != signable.signing_nonce_pair.signing_commitment.signable_idx
            {
                return Err(SigningError::CommitmentMismatch {
                    expected: signable.signing_nonce_pair.signing_commitment.signable_idx,
                    got: counterparty_commitment.signable_idx,
                });
            }

            // Ordering of nonces should be based on the ordering of the participants
            let public_nonces = if self.participant == Participant::App {
                vec![
                    &signable.signing_nonce_pair.signing_commitment.public_nonce,
                    &counterparty_commitment.public_nonce,
                ]
            } else {
                vec![
                    &counterparty_commitment.public_nonce,
                    &signable.signing_nonce_pair.signing_commitment.public_nonce,
                ]
            };

            let session = FrostSession::new(
                secp256k1_zkp::SECP256K1,
                &public_nonces,
                &signable.msg,
                &signable.signing_public_key,
                &self.participant.into(),
                &[&Participant::App.into(), &Participant::Server.into()],
                None,
            );

            let secret_nonce = signable
                .signing_nonce_pair
                .secret_nonce
                .take()
                .ok_or(SigningError::NonceAlreadyUsed)?;

            let partial_sig = session.partial_sign(
                secp256k1_zkp::SECP256K1,
                secret_nonce,
                &self.share_details.secret_share,
                &signable.signing_public_key,
            );

            signable.frost_session = Some(session);
            partial_signatures.push(partial_sig);
        }

        Ok(partial_signatures)
    }

    /// A function that aggregates partial signatures and signs the PSBT.
    ///
    /// # Arguments
    ///
    /// * `partial_signatures` - The partial signatures to use for signing
    /// * `counterparty_partial_signatures` - The partial signatures from the counterparty
    ///
    /// # Returns
    ///
    /// Returns a `Psbt` instance if successful, or a `SigningError` if signing fails
    pub fn sign_psbt(
        &mut self,
        partial_signatures: Vec<FrostPartialSignature>,
        counterparty_partial_signatures: Vec<FrostPartialSignature>,
    ) -> Result<Psbt, SigningError> {
        for signable in self.signables.iter_mut() {
            let signable_idx = signable.signing_nonce_pair.signing_commitment.signable_idx as usize;
            let session = signable
                .frost_session
                .as_ref()
                .ok_or(SigningError::MissingCounterpartyNonces)?;

            let partial_sig = partial_signatures[signable_idx];
            let counterparty_partial_sig = counterparty_partial_signatures[signable_idx];
            let final_sig = session.aggregate_partial_sigs(
                secp256k1_zkp::SECP256K1,
                &[&partial_sig, &counterparty_partial_sig],
            );

            let input = &mut self.psbt.inputs[signable_idx];
            input.tap_key_sig = Some(bitcoin::taproot::Signature {
                sig: ZkpSchnorrSignature(final_sig).into(),
                hash_ty: input
                    .taproot_hash_ty()
                    .map_err(|_| SigningError::InvalidPsbt)?,
            });

            // Zero out the frost session to prevent reuse.
            signable.frost_session = None;
        }

        Ok(self.psbt.clone())
    }

    fn generate_signables(
        psbt: &Psbt,
        share_details: &ShareDetails,
    ) -> Result<Vec<Signable>, SigningError> {
        let mut signables = Vec::with_capacity(psbt.inputs.len());
        let mut sighash_cache = SighashCache::new(&psbt.unsigned_tx);

        let aggregate_coefficient_commitment = share_details
            .key_commitments
            .aggregate_coefficient_commitment();
        let app_verification_share: VerificationShare = VerificationShare::new(
            secp256k1_zkp::SECP256K1,
            &aggregate_coefficient_commitment,
            &Participant::App.into(),
            NUM_PARTICIPANTS,
        )
        .expect("Failed to generate verification share");
        let server_verification_share = VerificationShare::new(
            secp256k1_zkp::SECP256K1,
            &aggregate_coefficient_commitment,
            &Participant::Server.into(),
            NUM_PARTICIPANTS,
        )
        .expect("Failed to generate verification share");

        let frost_public_key = FrostPublicKey::from_verification_shares(
            secp256k1_zkp::SECP256K1,
            &[&app_verification_share, &server_verification_share],
            &[&Participant::App.into(), &Participant::Server.into()],
        );

        for idx in 0..psbt.inputs.len() {
            let sighash_msg = psbt
                .sighash_msg(idx, &mut sighash_cache, None)
                .map_err(SigningError::UnableToRetrieveSighash)?;
            let msg = Message::from_digest(match sighash_msg {
                PsbtSighashMsg::TapSighash(sighash) => sighash.to_raw_hash().to_byte_array(),
                _ => return Err(SigningError::InvalidPsbt),
            });

            let mut frost_public_key = frost_public_key.clone();

            let input: &Input = &psbt.inputs[idx];
            if input.tap_key_origins.len() != 1 {
                return Err(SigningError::InvalidPsbt);
            }

            // Get the derivation path from the tap key origin
            let (_, (_, (_, derivation_path))) = input
                .tap_key_origins
                .first_key_value()
                .expect("Length checked above");

            derive_frost_bip32_tweak(&mut frost_public_key, derivation_path);
            derive_frost_tap_tweak(&mut frost_public_key, None);

            let (secret_nonce, public_nonce) = new_frost_nonce_pair(
                secp256k1_zkp::SECP256K1,
                FrostSessionId::random(),
                &share_details.secret_share,
                &frost_public_key,
                &msg,
                None,
            );

            signables.push(Signable {
                msg,
                signing_nonce_pair: SigningNoncePair {
                    secret_nonce: Some(secret_nonce),
                    signing_commitment: SigningCommitment {
                        signable_idx: idx as u32,
                        public_nonce,
                    },
                },
                signing_public_key: frost_public_key,
                frost_session: None,
            });
        }

        Ok(signables)
    }
}

struct Signable {
    msg: Message,
    signing_nonce_pair: SigningNoncePair,
    signing_public_key: FrostPublicKey,
    frost_session: Option<FrostSession>,
}

struct SigningNoncePair {
    // We can only use the secret nonce once, so we need to take it, hence the Option.
    secret_nonce: Option<FrostSecretNonce>,
    signing_commitment: SigningCommitment,
}

/// A struct that represents a signing commitment.
#[derive(Debug, Clone, PartialEq, Deserialize, Serialize)]
#[serde(crate = "bitcoin::secp256k1::serde")]
pub struct SigningCommitment {
    /// The index of the signable in the PSBT.
    signable_idx: u32,
    /// The public nonce.
    public_nonce: FrostPublicNonce,
}

/// Derives a series of BIP 32 tweaks based on the given derivation path and applies them to the
/// FROST public key.
fn derive_frost_bip32_tweak(
    frost_public_key: &mut FrostPublicKey,
    derivation_path: &DerivationPath,
) {
    let mut chain_code: ChainCode = FROST_CHAINCODE.into();
    for child in derivation_path {
        let mut hmac_engine: HmacEngine<sha512::Hash> = HmacEngine::new(&chain_code[..]);
        hmac_engine.input(
            &frost_public_key
                .public_key(secp256k1_zkp::SECP256K1)
                .serialize()[..],
        );
        hmac_engine.input(&Into::<u32>::into(*child).to_be_bytes());
        let hmac: Hmac<sha512::Hash> = Hmac::from_engine(hmac_engine);

        let tweak_bytes: [u8; 32] = hmac[..32]
            .try_into()
            .expect("SHA512 produces 64 byte hashes");
        let tweak =
            secp256k1_zkp::Scalar::from_be_bytes(tweak_bytes).expect("Hash must be valid scalar");
        frost_public_key.add_tweak(secp256k1_zkp::SECP256K1, tweak);

        let chain_code_bytes: [u8; 32] = hmac[32..]
            .try_into()
            .expect("SHA512 produces 64 byte hashes");
        chain_code = ChainCode::from(chain_code_bytes);
    }
}

/// Derives the taproot tweak using the FROST public key as the internal key and the given taproot
/// merkle root, and applies it as an X-only tweak to the FROST public key.
///
/// A `None` merkle_root can be used to compute a key-spend tweak.
fn derive_frost_tap_tweak(frost_public_key: &mut FrostPublicKey, merkle_root: Option<TapNodeHash>) {
    let internal_key: PublicKey =
        ZkpPublicKey(frost_public_key.public_key(secp256k1_zkp::SECP256K1)).into();
    let tap_tweak =
        TapTweakHash::from_key_and_tweak(internal_key.x_only_public_key().0, merkle_root);
    frost_public_key.add_x_only_tweak(
        secp256k1_zkp::SECP256K1,
        secp256k1_zkp::Scalar::from_be_bytes(tap_tweak.to_raw_hash().to_byte_array())
            .expect("Tap tweaks must be valid scalars"),
    );
}

#[derive(Error, Debug, PartialEq)]
pub enum SigningError {
    #[error("Invalid PSBT")]
    InvalidPsbt,
    #[error("Unable to retrieve sighash: {0}")]
    UnableToRetrieveSighash(SighashError),
    #[error("Invalid counterparty commitments")]
    InvalidCounterpartyCommitments,
    #[error("Nonce already used")]
    NonceAlreadyUsed,
    #[error("Commitment index mismatch: expected {expected}, got {got}")]
    CommitmentMismatch { expected: u32, got: u32 },
    #[error("Missing counterparty nonces")]
    MissingCounterpartyNonces,
}
