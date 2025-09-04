use bitcoin::bip32::{DerivationPath, ExtendedPubKey};
use bitcoin::secp256k1::ecdsa::Signature;
use bitcoin::Network;
use crypto::keys::PublicKey;
use serde::{Deserialize, Serialize};
use serde_with::{base64::Base64, serde_as};

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct KmsRequest {
    pub region: String,
    pub proxy_port: String,
    pub akid: String,
    pub skid: String,
    pub session_token: String,
    pub ciphertext: String,
    pub cmk_id: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct LoadSecretRequest {
    // This is mostly duplicated from KmsRequest above because
    // changing the API of existing endpoints is breaking, due to
    // wsm-api and wsm-enclave not being deployed at the same time.
    // We can change this later.
    pub region: String,
    pub proxy_port: String,
    pub akid: String,
    pub skid: String,
    pub session_token: String,
    pub dek_id: String,
    pub ciphertext: String,
    pub cmk_id: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct LoadIntegrityKeyRequest {
    pub request: KmsRequest,
    pub use_test_key: bool,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct LoadedSecret {
    pub status: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveSignRequest {
    pub root_key_id: String,
    pub wrapped_xprv: String,
    pub dek_id: String,
    pub key_nonce: String,
    pub descriptor: String,
    pub change_descriptor: String,
    pub psbt: String,
    pub network: Option<Network>,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveGenericSignRequest {
    pub root_key_id: String,
    pub wrapped_xprv: String,
    pub dek_id: String,
    pub key_nonce: String,
    pub blob: String,
    pub network: Option<Network>,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveCreateKeyRequest {
    pub root_key_id: String,
    pub dek_id: String,
    pub network: Network,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveInitiateDistributedKeygenRequest {
    pub root_key_id: String,
    pub dek_id: String,
    pub network: Network,
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    pub noise_session_id: String,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveInitiateDistributedKeygenResponse {
    pub aggregate_public_key: PublicKey,
    pub wrapped_share_details: String,
    pub wrapped_share_details_nonce: String,
    #[serde_as(as = "Base64")]
    pub sealed_response: Vec<u8>,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveContinueDistributedKeygenRequest {
    pub root_key_id: String,
    pub dek_id: String,
    pub network: Network,
    pub wrapped_share_details: String,
    pub wrapped_share_details_nonce: String,
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    pub noise_session_id: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveContinueDistributedKeygenResponse {}

#[serde_as]
#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveEvaluatePinRequest {
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    pub noise_session_id: String,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveEvaluatePinResponse {
    #[serde_as(as = "Base64")]
    pub sealed_response: Vec<u8>,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveGeneratePartialSignaturesRequest {
    pub root_key_id: String,
    pub dek_id: String,
    pub network: Network,
    pub wrapped_share_details: String,
    pub wrapped_share_details_nonce: String,
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    pub noise_session_id: String,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveGeneratePartialSignaturesResponse {
    #[serde_as(as = "Base64")]
    pub sealed_response: Vec<u8>,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveDeriveKeyRequest {
    pub key_id: String,
    pub dek_id: String,
    pub wrapped_xprv: String,
    pub key_nonce: String,
    pub derivation_path: DerivationPath,
    pub network: Option<Network>,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveCreateSelfSovereignBackupRequest {
    pub root_key_id: String,
    pub dek_id: String,
    pub network: Network,
    pub wrapped_share_details: String,
    pub wrapped_share_details_nonce: String,
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    pub noise_session_id: String,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveCreateSelfSovereignBackupResponse {
    #[serde_as(as = "Base64")]
    pub sealed_response: Vec<u8>,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveInitiateShareRefreshRequest {
    pub root_key_id: String,
    pub dek_id: String,
    pub network: Network,
    pub wrapped_share_details: String,
    pub wrapped_share_details_nonce: String,
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    pub noise_session_id: String,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveInitiateShareRefreshResponse {
    pub wrapped_pending_share_details: String,
    pub wrapped_pending_share_details_nonce: String,
    #[serde_as(as = "Base64")]
    pub sealed_response: Vec<u8>,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveContinueShareRefreshRequest {
    pub root_key_id: String,
    pub dek_id: String,
    pub network: Network,
    pub wrapped_pending_share_details: String,
    pub wrapped_pending_share_details_nonce: String,
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    pub noise_session_id: String,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveContinueShareRefreshResponse {}

#[serde_as]
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct GrantRequest {
    pub version: u8,
    #[serde_as(as = "Base64")]
    pub device_id: Vec<u8>,
    #[serde_as(as = "Base64")]
    pub challenge: Vec<u8>,
    pub action: u8,
    pub signature: Signature,
}

impl GrantRequest {
    pub fn serialize(&self, include_signature: bool) -> Vec<u8> {
        let mut data = Vec::new();

        data.push(self.version);
        data.extend_from_slice(&self.device_id);
        data.extend_from_slice(&self.challenge);
        data.push(self.action);

        if include_signature {
            data.extend_from_slice(&self.signature.serialize_compact());
        }

        data
    }
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveSignGrantRequest {
    pub hw_auth_public_key: PublicKey,
    #[serde(flatten)]
    pub grant_request: GrantRequest,
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub struct CreatedKey {
    pub wrapped_xprv: String,
    pub wrapped_xprv_nonce: String,
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(untagged)]
pub enum CreateResponse {
    Single(CreatedKey),
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub struct DerivedKey {
    pub xpub: ExtendedPubKey,
    pub dpub: String,
    #[serde(default)]
    pub xpub_sig: String,
    #[serde(default)]
    pub pub_sig: String,
}
#[derive(Serialize, Deserialize, Debug)]
pub struct DeriveResponse(pub DerivedKey);
