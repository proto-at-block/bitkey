use bitcoin::Network;
use serde::{Deserialize, Serialize};

use crate::derivation::WSMSupportedDomain;

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
    pub xpub_sig: String,
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
