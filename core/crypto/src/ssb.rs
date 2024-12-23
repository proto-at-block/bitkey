use p256::elliptic_curve;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum SsbError {
    #[error(transparent)]
    EllipticCurveError(#[from] elliptic_curve::Error),
    #[error("Hkdf Invalid Length")]
    HkdfInvalidLength(hkdf::InvalidLength),
    #[error(transparent)]
    CryptoCommonInvalidLength(#[from] crypto_common::InvalidLength),
    #[error("ChaCha20Poly1305 Error")]
    ChaCha20Poly1305Error(chacha20poly1305::Error),
    #[error(transparent)]
    TryFromSliceError(#[from] std::array::TryFromSliceError),
}

pub mod testapp {
    use chacha20poly1305::{aead::Aead, XChaCha20Poly1305};
    use crypto_common::KeyInit;
    use hkdf::Hkdf;
    use p256::{ecdh::diffie_hellman, PublicKey, SecretKey};
    use rand::rngs::OsRng;
    use sha2::Sha256;

    use super::{server::SelfSovereignBackup, SsbError};

    pub fn generate_lka_lkn() -> (SecretKey, SecretKey) {
        let lka = SecretKey::random(&mut OsRng);
        let lkn = SecretKey::random(&mut OsRng);
        (lka, lkn)
    }

    pub fn decrypt_ssb(
        lka: SecretKey,
        lkn: SecretKey,
        backup: SelfSovereignBackup,
    ) -> Result<Vec<u8>, super::SsbError> {
        let s_1 = diffie_hellman(
            lka.to_nonzero_scalar(),
            PublicKey::from_sec1_bytes(&backup.eph_pub)?.as_affine(),
        );
        let s_2 = diffie_hellman(
            lkn.to_nonzero_scalar(),
            PublicKey::from_sec1_bytes(&backup.eph_pub)?.as_affine(),
        );

        let mut k = [0u8; 32];
        Hkdf::<Sha256>::new(
            None,
            [
                s_1.raw_secret_bytes().as_slice(),
                s_2.raw_secret_bytes().as_slice(),
            ]
            .concat()
            .as_slice(),
        )
        .expand(&[], &mut k)
        .map_err(|e| SsbError::HkdfInvalidLength(e))?;

        let cipher = XChaCha20Poly1305::new_from_slice(&k)?;

        let plaintext = cipher
            .decrypt(&backup.nonce.into(), backup.ciphertext.as_slice())
            .map_err(|e| SsbError::ChaCha20Poly1305Error(e))?;

        Ok(plaintext)
    }
}

pub mod server {
    use chacha20poly1305::{aead::Aead, AeadCore, XChaCha20Poly1305};
    use crypto_common::KeyInit;
    use hkdf::Hkdf;
    use p256::{ecdh::EphemeralSecret, PublicKey};
    use rand::rngs::OsRng;
    use sha2::Sha256;

    use super::SsbError;

    pub struct SelfSovereignBackup {
        pub eph_pub: [u8; 65],
        pub nonce: [u8; 24],
        pub ciphertext: Vec<u8>,
    }

    pub fn create_ssb(
        lka_pub: [u8; 65],
        lkn_pub: [u8; 65],
        message: Vec<u8>,
    ) -> Result<SelfSovereignBackup, SsbError> {
        let eph_secret = EphemeralSecret::random(&mut OsRng);

        let s_1 = eph_secret.diffie_hellman(&PublicKey::from_sec1_bytes(&lka_pub)?);
        let s_2 = eph_secret.diffie_hellman(&PublicKey::from_sec1_bytes(&lkn_pub)?);

        let mut k = [0u8; 32];
        Hkdf::<Sha256>::new(
            None,
            [
                s_1.raw_secret_bytes().as_slice(),
                s_2.raw_secret_bytes().as_slice(),
            ]
            .concat()
            .as_slice(),
        )
        .expand(&[], &mut k)
        .map_err(|e| SsbError::HkdfInvalidLength(e))?;

        let cipher = XChaCha20Poly1305::new_from_slice(&k)?;
        let nonce = XChaCha20Poly1305::generate_nonce(&mut OsRng);
        let ciphertext = cipher
            .encrypt(&nonce, message.as_slice())
            .map_err(|e| SsbError::ChaCha20Poly1305Error(e))?;

        Ok(SelfSovereignBackup {
            eph_pub: (*eph_secret.public_key().to_sec1_bytes()).try_into()?,
            nonce: nonce.as_slice().try_into()?,
            ciphertext,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::{
        server::create_ssb,
        testapp::{decrypt_ssb, generate_lka_lkn},
    };

    #[test]
    fn test_ssb_creation() {
        let expected_message = b"Hello world!";

        let (lka, lkn) = generate_lka_lkn();
        let backup = create_ssb(
            (*lka.public_key().to_sec1_bytes()).try_into().unwrap(),
            (*lkn.public_key().to_sec1_bytes()).try_into().unwrap(),
            expected_message.to_vec(),
        )
        .unwrap();
        let actual_message = decrypt_ssb(lka, lkn, backup).unwrap();

        assert_eq!(expected_message, actual_message.as_slice());
    }
}
