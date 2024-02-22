use bdk::bitcoin::Network;
use serde::Serialize;
use std::error::Error;
use std::fmt::{Debug, Display, Formatter};

#[derive(Debug, Serialize)]
pub struct Aad {
    root_key_id: String,
    network: Option<Network>, // NOTE: remove Option once keys are migrated
}

#[derive(Debug)]
pub struct AadError {
    message: String,
}

impl Display for AadError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.message)
    }
}

impl Error for AadError {}

impl<T: Debug> From<ciborium::ser::Error<T>> for AadError {
    fn from(err: ciborium::ser::Error<T>) -> Self {
        AadError {
            message: format!("Could not serialize the Aad with CBOR: {err:?}"),
        }
    }
}

impl Aad {
    pub fn new(root_key_id: String, network: Option<Network>) -> Aad {
        Aad {
            root_key_id,
            network,
        }
    }

    pub fn serialize(&self) -> Result<Vec<u8>, AadError> {
        match self.network {
            None => Ok(self.root_key_id.as_bytes().to_vec()),
            _ => {
                let mut output = vec![];
                ciborium::ser::into_writer(self, &mut output)?;
                Ok(output)
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use bdk::bitcoin::Network;

    use super::Aad;

    #[test]
    fn aad_roundtrips() {
        assert_eq!(
            Aad::new("root_key_id".to_string(), None)
                .serialize()
                .unwrap(),
            "root_key_id".as_bytes()
        );

        assert_eq!(
            Aad::new("root_key_id".to_string(), Some(Network::Signet))
                .serialize()
                .unwrap(),
            vec![
                0xA2, // map(2)
                0x6B, // text(11)
                0x72, 0x6F, 0x6F, 0x74, 0x5F, 0x6B, 0x65, 0x79, 0x5F, 0x69,
                0x64, // "root_key_id"
                0x6B, // text(11)
                0x72, 0x6F, 0x6F, 0x74, 0x5F, 0x6B, 0x65, 0x79, 0x5F, 0x69,
                0x64, // "root_key_id"
                0x67, // text(7)
                0x6E, 0x65, 0x74, 0x77, 0x6F, 0x72, 0x6B, // "network"
                0x66, // text(6)
                0x73, 0x69, 0x67, 0x6E, 0x65, 0x74 // "signet"
            ]
        );
    }
}
