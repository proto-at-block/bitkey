use bitcoin::secp256k1::ecdsa::Signature;
use bitcoin::Network;
use crypto::keys::PublicKey;
use serde::{Deserialize, Serialize};
use serde_with::{base64::Base64, serde_as, DisplayFromStr};

use crate::derivation::WSMSupportedDomain;

#[serde_as]
#[derive(Deserialize, Serialize, Debug)]
pub struct InitiateDistributedKeygenRequest {
    pub root_key_id: String,
    pub network: Network,
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    pub noise_session_id: String,
}

#[serde_as]
#[derive(Deserialize, Serialize, Debug)]
pub struct InitiateDistributedKeygenResponse {
    pub root_key_id: String,
    pub aggregate_public_key: PublicKey,
    #[serde_as(as = "Base64")]
    pub sealed_response: Vec<u8>,
}

#[serde_as]
#[derive(Deserialize, Serialize, Debug)]
pub struct ContinueDistributedKeygenRequest {
    pub root_key_id: String,
    pub network: Network,
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    pub noise_session_id: String,
}

#[derive(Deserialize, Serialize, Debug)]
pub struct ContinueDistributedKeygenResponse {}

#[serde_as]
#[derive(Deserialize, Serialize, Debug)]
pub struct CreateSelfSovereignBackupRequest {
    pub root_key_id: String,
    pub network: Network,
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    pub noise_session_id: String,
}

#[serde_as]
#[derive(Deserialize, Serialize, Debug)]
pub struct CreateSelfSovereignBackupResponse {
    #[serde_as(as = "Base64")]
    pub sealed_response: Vec<u8>,
}

#[serde_as]
#[derive(Deserialize, Serialize, Debug)]
pub struct InitiateShareRefreshRequest {
    pub root_key_id: String,
    pub network: Network,
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    pub noise_session_id: String,
}

#[derive(Deserialize, Serialize, Debug)]
pub struct InitiateShareRefreshResponse {
    pub sealed_response: Vec<u8>,
}

#[serde_as]
#[derive(Deserialize, Serialize, Debug)]
pub struct ContinueShareRefreshRequest {
    pub root_key_id: String,
    pub network: Network,
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    pub noise_session_id: String,
}

#[derive(Deserialize, Serialize, Debug)]
pub struct ContinueShareRefreshResponse {}

#[serde_as]
#[derive(Deserialize, Serialize, Debug)]
pub struct GeneratePartialSignaturesRequest {
    pub root_key_id: String,
    pub network: Network,
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    pub noise_session_id: String,
}

#[serde_as]
#[derive(Deserialize, Serialize, Debug)]
pub struct GeneratePartialSignaturesResponse {
    #[serde_as(as = "Base64")]
    pub sealed_response: Vec<u8>,
}

#[derive(Deserialize, Serialize, Debug)]
pub struct CreateRootKeyRequest {
    pub root_key_id: String,
    pub network: Network,
}

#[derive(Deserialize, Serialize, Debug)]
pub struct DeriveKeyRequest {
    pub root_key_id: String,
    pub domain: WSMSupportedDomain,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct CreatedSigningKey {
    pub root_key_id: String,
    pub xpub: String,
    #[serde(default)]
    pub xpub_sig: String,
    #[serde(default)]
    pub pub_sig: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct CreatedSigningKeyShare {
    pub root_key_id: String,
    pub aggregate_public_key: PublicKey,
}

#[derive(Deserialize, Serialize, Debug)]
pub struct SignPsbtRequest {
    pub root_key_id: String,
    pub descriptor: String,
    pub change_descriptor: String,
    pub psbt: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct SignedPsbt {
    pub psbt: String,
    pub root_key_id: String,
}

#[derive(Deserialize, Serialize, Debug)]
pub struct SignBlobRequest {
    pub root_key_id: String,
    pub blob: String,
}

#[derive(Deserialize, Serialize, Debug)]
pub struct GenerateIntegrityKeyResponse {
    pub wrapped_privkey: String,
    pub pubkey: String,
}

#[derive(Deserialize, Serialize, Debug)]
pub struct GetIntegritySigRequest {
    pub root_key_id: String,
}

#[derive(Deserialize, Serialize, Debug)]
pub struct GetIntegritySigResponse {
    #[serde(alias = "signature")]
    pub xpub_sig: String,
    pub pub_sig: String,
}

#[derive(Deserialize, Serialize, Debug)]
pub struct AttestationDocRequest {}

#[derive(Deserialize, Serialize, Debug)]
pub struct AttestationDocResponse {
    pub document: Vec<u8>,
}

#[serde_as]
#[derive(Deserialize, Serialize, Debug)]
pub struct NoiseInitiateBundleRequest {
    #[serde_as(as = "Base64")]
    pub bundle: Vec<u8>,
    pub server_static_pubkey: String,
}

#[serde_as]
#[derive(Deserialize, Serialize, Debug)]
pub struct NoiseInitiateBundleResponse {
    #[serde_as(as = "Base64")]
    pub bundle: Vec<u8>,
    pub noise_session_id: String,
}

#[serde_as]
#[derive(Deserialize, Serialize, Debug)]
pub struct EvaluatePinRequest {
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    pub noise_session_id: String,
}

#[serde_as]
#[derive(Deserialize, Serialize, Debug)]
pub struct EvaluatePinResponse {
    #[serde_as(as = "Base64")]
    pub sealed_response: Vec<u8>,
}

#[serde_as]
#[derive(Deserialize, Serialize, Debug, Clone)]
pub struct GrantRequest {
    pub version: u8,
    pub action: u8,
    #[serde_as(as = "Base64")]
    pub device_id: Vec<u8>,
    #[serde_as(as = "Base64")]
    pub challenge: Vec<u8>,
    #[serde_as(as = "DisplayFromStr")]
    pub signature: Signature,
    pub hw_auth_public_key: PublicKey,
}

#[serde_as]
#[derive(Deserialize, Serialize, Debug)]
pub struct GrantResponse {
    pub version: u8,
    #[serde_as(as = "Base64")]
    pub serialized_request: Vec<u8>,
    #[serde_as(as = "DisplayFromStr")]
    pub signature: Signature,
}

#[serde_as]
#[derive(Deserialize, Serialize, Debug)]
pub struct TransactionVerificationGrant {
    pub version: u8,
    pub hw_auth_public_key: PublicKey,
    #[serde_as(as = "Base64")]
    pub commitment: Vec<u8>,
    #[serde_as(as = "Vec<Base64>")]
    pub reverse_hash_chain: Vec<Vec<u8>>,
    #[serde_as(as = "DisplayFromStr")]
    pub signature: Signature,
}

#[serde_as]
#[derive(Deserialize, Serialize, Debug)]
pub struct ApprovePsbtRequest {
    pub psbt: String,
    pub hw_auth_public_key: PublicKey,
}

#[derive(Deserialize, Serialize, Debug)]
pub struct ApprovePsbtResponse {
    pub approval: TransactionVerificationGrant,
}
