use std::str::FromStr;

use crate::BdkUtilError;
use bdk::bitcoin::secp256k1::SecretKey;
use bdk::bitcoin::{
    hashes::sha256,
    secp256k1,
    secp256k1::{ecdsa, Message, PublicKey, Secp256k1},
};

pub fn check_signature(
    message: &str,
    signature: &str,
    public_key: PublicKey,
) -> Result<(), BdkUtilError> {
    let msg = Message::from_hashed_data::<sha256::Hash>(message.as_bytes());
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
    let message = Message::from_hashed_data::<sha256::Hash>(message.as_bytes());
    secp.sign_ecdsa(&message, secret_key).to_string()
}
