use hmac::{Hmac, Mac};
use sha2::Sha256;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum MacError {
    #[error("Failed to generate MAC")]
    GenerateFailed,
    #[error("Failed to verify MAC")]
    VerifyFailed,
}

pub type HmacSha256 = Hmac<Sha256>;

pub fn generate_mac(key: &[u8], data: &[u8]) -> Result<Vec<u8>, MacError> {
    let mut mac = HmacSha256::new_from_slice(key).map_err(|_| MacError::GenerateFailed)?;
    mac.update(data);
    Ok(mac.finalize().into_bytes().to_vec())
}

pub fn verify_mac(key: &[u8], data: &[u8], expected_mac: &[u8]) -> Result<(), MacError> {
    let mut mac = HmacSha256::new_from_slice(key).map_err(|_| MacError::VerifyFailed)?;
    mac.update(data);
    mac.verify_slice(expected_mac)
        .map_err(|_| MacError::VerifyFailed)
}
