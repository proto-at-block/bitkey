use aes_gcm_siv::{
    aead::{rand_core::RngCore, Aead, OsRng},
    Aes256GcmSiv, KeyInit, Nonce,
};

pub type AeadError = aes_gcm_siv::aead::Error;
pub type UnsealedKey = [u8; 32];
pub type SealedKey = Vec<u8>;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct UnsealedEnvelope {
    key: UnsealedKey,
    nonce: Nonce,
    pub(crate) plaintext: Vec<u8>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SealedEnvelope {
    pub(crate) sealant: SealedKey,
    pub(crate) nonce: Nonce,
    pub(crate) ciphertext: Vec<u8>,
}

fn random_key() -> UnsealedKey {
    Aes256GcmSiv::generate_key(&mut OsRng).into()
}

fn random_nonce() -> Nonce {
    // TODO: find a better way to get a random nonce
    let rng = &mut OsRng;
    let mut raw_nonce = [0_u8; 12];
    rng.fill_bytes(&mut raw_nonce);
    Nonce::clone_from_slice(&raw_nonce)
}

impl UnsealedEnvelope {
    pub fn new(plaintext: Vec<u8>) -> Self {
        Self {
            key: random_key(),
            nonce: random_nonce(),
            plaintext,
        }
    }

    pub fn key(&self) -> UnsealedKey {
        self.key
    }

    pub fn seal(&self, sealed_key: SealedKey) -> Result<SealedEnvelope, AeadError> {
        let cipher = Aes256GcmSiv::new(&self.key.into());
        let ciphertext = cipher.encrypt(&self.nonce, self.plaintext.as_slice())?;

        Ok(SealedEnvelope {
            sealant: sealed_key,
            nonce: self.nonce,
            ciphertext,
        })
    }
}

impl SealedEnvelope {
    pub fn sealant(&self) -> SealedKey {
        self.sealant.to_owned()
    }

    pub fn unseal(&self, key: UnsealedKey) -> Result<UnsealedEnvelope, AeadError> {
        let cipher = Aes256GcmSiv::new(&key.into());
        let plaintext = cipher.decrypt(&self.nonce, self.ciphertext.as_slice())?;

        Ok(UnsealedEnvelope {
            key,
            nonce: self.nonce,
            plaintext,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::{AeadError, SealedKey, UnsealedEnvelope};

    #[test]
    fn seal_roundtrip() -> Result<(), AeadError> {
        let plaintext = b"plaintext".to_vec();

        let unsealed_envelope = UnsealedEnvelope::new(plaintext.clone());
        let key = unsealed_envelope.key();
        let sealant: SealedKey = Default::default();

        let sealed_envelope = unsealed_envelope.seal(sealant.clone())?;
        assert_eq!(sealed_envelope.sealant, sealant);
        assert_eq!(sealed_envelope.nonce, unsealed_envelope.nonce);
        assert_ne!(sealed_envelope.ciphertext, unsealed_envelope.plaintext);

        let roundtripped_envelope = sealed_envelope.unseal(key)?;
        assert_eq!(roundtripped_envelope, unsealed_envelope);
        assert_eq!(roundtripped_envelope.plaintext, plaintext);

        Ok(())
    }
}
