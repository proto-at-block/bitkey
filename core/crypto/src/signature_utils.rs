use bitcoin::secp256k1::ecdsa::{SerializedSignature, Signature};
use std::convert::AsRef;
use std::convert::TryFrom;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum SignatureUtilsError {
    #[error("Invalid compact signature: {0}")]
    InvalidCompactSignature(#[from] bitcoin::secp256k1::Error),

    #[error("Invalid DER signature: {0}")]
    InvalidDerSignature(bitcoin::secp256k1::Error),
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DERSignature(pub SerializedSignature);

impl DERSignature {
    pub fn to_vec(self) -> Vec<u8> {
        self.0.to_vec()
    }
    pub fn as_slice(&self) -> &[u8] {
        &self.0
    }
}

impl AsRef<[u8]> for DERSignature {
    fn as_ref(&self) -> &[u8] {
        &self.0
    }
}

impl TryFrom<&[u8]> for DERSignature {
    type Error = SignatureUtilsError;

    fn try_from(slice: &[u8]) -> Result<Self, Self::Error> {
        let sig = Signature::from_der(slice).map_err(SignatureUtilsError::InvalidDerSignature)?;
        Ok(DERSignature(sig.serialize_der()))
    }
}

impl TryFrom<Vec<u8>> for DERSignature {
    type Error = SignatureUtilsError;

    fn try_from(vec: Vec<u8>) -> Result<Self, Self::Error> {
        Self::try_from(vec.as_slice())
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct CompactSignature(pub [u8; 64]);

impl CompactSignature {
    pub fn to_vec(self) -> Vec<u8> {
        self.0.to_vec()
    }

    pub fn as_slice(&self) -> &[u8] {
        &self.0
    }
}

impl AsRef<[u8]> for CompactSignature {
    fn as_ref(&self) -> &[u8] {
        &self.0
    }
}

impl TryFrom<&[u8]> for CompactSignature {
    type Error = SignatureUtilsError;

    fn try_from(slice: &[u8]) -> Result<Self, Self::Error> {
        let sig =
            Signature::from_compact(slice).map_err(SignatureUtilsError::InvalidCompactSignature)?;
        Ok(CompactSignature(sig.serialize_compact()))
    }
}

impl TryFrom<Vec<u8>> for CompactSignature {
    type Error = SignatureUtilsError;

    fn try_from(vec: Vec<u8>) -> Result<Self, Self::Error> {
        Self::try_from(vec.as_slice())
    }
}

pub fn compact_signature_to_der(
    compact_sig: &CompactSignature,
) -> Result<DERSignature, SignatureUtilsError> {
    let signature = Signature::from_compact(compact_sig.as_ref())
        .map_err(SignatureUtilsError::InvalidCompactSignature)?;

    Ok(DERSignature(signature.serialize_der()))
}

pub fn compact_signature_from_der(
    der_sig: &DERSignature,
) -> Result<CompactSignature, SignatureUtilsError> {
    let signature =
        Signature::from_der(der_sig.as_ref()).map_err(SignatureUtilsError::InvalidDerSignature)?;

    let compact = signature.serialize_compact();
    Ok(CompactSignature(compact))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_compact_signature_to_der_invalid_signature() {
        // Array of zeros as compact signature serializes to DER of zeros
        let invalid_compact = [0u8; 64];
        let compact_sig = CompactSignature(invalid_compact);
        let der = compact_signature_to_der(&compact_sig).unwrap();
        // DER encoding of zero r and s is a sequence of two DER integers (0)
        assert_eq!(
            der.as_slice(),
            &[0x30, 0x06, 0x02, 0x01, 0x00, 0x02, 0x01, 0x00]
        );
    }

    #[test]
    fn test_compact_signature_from_der_invalid_format() {
        // All-zero buffer should fail to parse as DER signature.
        let result = DERSignature::try_from(&[0u8; 72][..]);
        assert!(matches!(
            result,
            Err(SignatureUtilsError::InvalidDerSignature(_))
        ));
    }

    #[test]
    fn test_roundtrip_signature_conversion() {
        // Create a valid compact signature with non-zero values
        let mut original_compact = [0u8; 64];
        for i in 0..64 {
            original_compact[i] = (i % 255) as u8;
        }

        // Convert compact to DER
        let der_sig = compact_signature_to_der(&CompactSignature(original_compact)).unwrap();

        // Convert DER back to compact
        let recovered_compact = compact_signature_from_der(&der_sig).unwrap();

        // Verify we got the same signature back
        assert_eq!(original_compact.to_vec(), recovered_compact.0.to_vec());
    }
}
