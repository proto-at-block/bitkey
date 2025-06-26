use bitcoin::secp256k1::ecdsa::{SerializedSignature, Signature};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum SignatureUtilsError {
    #[error("Invalid compact signature: {0}")]
    InvalidCompactSignature(#[from] bitcoin::secp256k1::Error),
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DERSerializedSignature(pub SerializedSignature);

impl DERSerializedSignature {
    pub fn to_vec(self) -> Vec<u8> {
        self.0.to_vec()
    }
    pub fn as_slice(&self) -> &[u8] {
        &self.0
    }
}

pub fn encode_signature_to_der(
    compact_sig: &[u8],
) -> Result<DERSerializedSignature, SignatureUtilsError> {
    let signature = Signature::from_compact(compact_sig)
        .map_err(SignatureUtilsError::InvalidCompactSignature)?;

    Ok(DERSerializedSignature(signature.serialize_der()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_encode_signature_to_der_invalid_length() {
        let invalid_compact = [0u8; 63];
        assert!(matches!(
            encode_signature_to_der(&invalid_compact),
            Err(SignatureUtilsError::InvalidCompactSignature(_))
        ));

        let invalid_compact = [0u8; 65];
        assert!(matches!(
            encode_signature_to_der(&invalid_compact),
            Err(SignatureUtilsError::InvalidCompactSignature(_))
        ));
    }
}
