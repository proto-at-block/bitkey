use std::str::FromStr;

use crate::BdkUtilError;
use bdk_wallet::bitcoin::hashes::Hash;
use bdk_wallet::bitcoin::secp256k1::SecretKey;
use bdk_wallet::bitcoin::{
    hashes::sha256::Hash as Sha256Hash,
    secp256k1,
    secp256k1::{ecdsa, Message, PublicKey, Secp256k1},
};

pub fn check_signature(
    message: &str,
    signature: &str,
    public_key: PublicKey,
) -> Result<(), BdkUtilError> {
    let msg = message_to_digest(message);
    let sig = ecdsa::Signature::from_str(signature)?;
    let secp = Secp256k1::new();
    let verification_result = secp.verify_ecdsa(&msg, &sig, &public_key);

    verification_result
        .map_err(|_| BdkUtilError::SignatureMismatch(message.to_string(), signature.to_string()))
}

#[cfg(feature = "test-helpers")]
pub fn sign_message(
    secp: &Secp256k1<secp256k1::All>,
    message: &str,
    secret_key: &SecretKey,
) -> String {
    let message = message_to_digest(message);
    secp.sign_ecdsa(&message, secret_key).to_string()
}

pub fn message_to_digest(message: &str) -> Message {
    let hash = Sha256Hash::hash(message.as_bytes());
    Message::from_digest(hash.to_byte_array())
}
