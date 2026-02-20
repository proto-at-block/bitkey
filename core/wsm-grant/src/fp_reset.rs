use anyhow::{Context, Error};
use bitcoin::hashes::Hash;
use bitcoin::secp256k1::ecdsa::Signature;
use bitcoin::secp256k1::Message;
pub use bitcoin::secp256k1::PublicKey;
use bitcoin::{hashes::sha256, key::Secp256k1};

pub const GRANT_REQUEST_SIG_PREFIX: &[u8] = b"BKGrantReq";

pub fn verify_grant_request_signature(
    serialized_request: &[u8],
    signature: &Signature,
    hw_auth_public_key: &PublicKey,
) -> Result<(), Error> {
    let mut data = Vec::new();

    data.extend_from_slice(GRANT_REQUEST_SIG_PREFIX);
    data.extend_from_slice(serialized_request);

    let message = Message::from_digest(sha256::Hash::hash(&data).to_byte_array());

    let secp = Secp256k1::new();
    secp.verify_ecdsa(&message, signature, hw_auth_public_key)
        .context("Failed to verify ECDSA signature")?;

    Ok(())
}
