use anyhow::{Context, Error};
use bitcoin::key::Secp256k1;
use bitcoin::secp256k1::ecdsa::Signature;
use bitcoin::secp256k1::Message;
use sha2::{Digest, Sha256};

pub use bitcoin::secp256k1::PublicKey;

pub const GRANT_REQUEST_SIG_PREFIX: &[u8] = b"BKGrantReq";

pub fn verify_grant_request_signature(
    serialized_request: &[u8],
    signature: &Signature,
    hw_auth_public_key: &PublicKey,
) -> Result<(), Error> {
    let mut data = Vec::new();

    data.extend_from_slice(GRANT_REQUEST_SIG_PREFIX);
    data.extend_from_slice(serialized_request);

    let hash = Sha256::digest(data);
    let message = Message::from_slice(&hash).context("Failed to create message from hash")?;

    let secp = Secp256k1::new();
    secp.verify_ecdsa(&message, signature, hw_auth_public_key)
        .context("Failed to verify ECDSA signature")?;

    Ok(())
}
