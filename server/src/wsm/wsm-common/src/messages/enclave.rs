use bitcoin::util::bip32::{DerivationPath, ExtendedPubKey};
use bitcoin::Network;
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Debug)]
pub struct LoadSecretRequest {
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

#[derive(Serialize, Deserialize, Debug)]
pub struct EnclaveDeriveKeyRequest {
    pub key_id: String,
    pub dek_id: String,
    pub wrapped_xprv: String,
    pub key_nonce: String,
    pub derivation_path: DerivationPath,
    pub network: Option<Network>,
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub struct CreatedKey {
    pub xpub: ExtendedPubKey,
    pub dpub: String,
    #[serde(default)]
    pub dpub_sig: String,
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
    pub dpub_sig: String,
}
#[derive(Serialize, Deserialize, Debug)]
pub struct DeriveResponse(pub DerivedKey);
