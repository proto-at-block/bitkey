use chacha20poly1305::{
    aead::{Aead, KeyInit, Payload},
    XChaCha20Poly1305 as RustCryptoXChaCha20Poly1305, XNonce,
};
use std::sync::Mutex;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum ChaCha20Poly1305Error {
    #[error("Failed to encrypt")]
    EncryptError,
    #[error("Failed to decrypt")]
    DecryptError,
}

pub struct XChaCha20Poly1305 {
    xchacha20poly1305_mutex: Mutex<RustCryptoXChaCha20Poly1305>,
}

impl XChaCha20Poly1305 {
    pub fn new(key: &[u8]) -> Self {
        let xchacha20poly1305 = RustCryptoXChaCha20Poly1305::new_from_slice(key).unwrap();

        Self {
            xchacha20poly1305_mutex: Mutex::new(xchacha20poly1305),
        }
    }

    pub fn encrypt(
        &self,
        nonce: &[u8],
        plaintext: &[u8],
        aad: &[u8],
    ) -> Result<Vec<u8>, ChaCha20Poly1305Error> {
        let nonce = XNonce::from_slice(nonce);
        let cipher = self.xchacha20poly1305_mutex.lock().unwrap();

        let payload = Payload {
            msg: plaintext,
            aad,
        };

        cipher
            .encrypt(nonce, payload)
            .map_err(|_| ChaCha20Poly1305Error::EncryptError)
    }

    pub fn decrypt(
        &self,
        nonce: &[u8],
        ciphertext: &[u8],
        aad: &[u8],
    ) -> Result<Vec<u8>, ChaCha20Poly1305Error> {
        let nonce = XNonce::from_slice(nonce);
        let cipher = self.xchacha20poly1305_mutex.lock().unwrap();

        let payload = Payload {
            msg: ciphertext,
            aad,
        };

        cipher
            .decrypt(nonce, payload)
            .map_err(|_| ChaCha20Poly1305Error::DecryptError)
    }
}

#[cfg(test)]
mod tests {
    use crate::chacha20poly1305::XChaCha20Poly1305;
    use chacha20poly1305::{aead::AeadCore, XChaCha20Poly1305 as RustCryptoXChaCha20Poly1305};
    use rand::RngCore;
    use typenum::Unsigned;

    #[test]
    fn test_encryption_without_aad() {
        let mut key = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut key);
        let cipher = XChaCha20Poly1305::new(&key);
        let plaintext = b"Hello world!";
        let mut nonce = [0u8; 24];
        rand::thread_rng().fill_bytes(&mut nonce);

        let ciphertext = cipher.encrypt(&nonce, plaintext, &[]).unwrap();
        let decrypted = cipher.decrypt(&nonce, &ciphertext, &[]).unwrap();
        assert_eq!(plaintext, &decrypted[..]);
    }

    #[test]
    fn test_encryption_with_aad() {
        let mut key = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut key);
        let cipher = XChaCha20Poly1305::new(&key);
        let plaintext = b"Hello world!";
        let mut nonce = [0u8; 24];
        rand::thread_rng().fill_bytes(&mut nonce);
        let aad = b"Lorem Ipsum";

        let ciphertext = cipher.encrypt(&nonce, plaintext, aad).unwrap();
        let decrypted = cipher.decrypt(&nonce, &ciphertext, aad).unwrap();
        assert_eq!(plaintext, &decrypted[..]);
        assert!(cipher.decrypt(&nonce, &ciphertext, b"Wrong aad").is_err());
        assert!(cipher.decrypt(&nonce, &ciphertext, &[]).is_err());
    }

    #[test]
    fn test_authentication() {
        let mut key = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut key);
        let cipher = XChaCha20Poly1305::new(&key);
        let plaintext = b"Hello world!";
        let mut nonce = [0u8; 24];
        rand::thread_rng().fill_bytes(&mut nonce);

        let ciphertext = cipher.encrypt(&nonce, plaintext, &[]).unwrap();
        let mut modified_ciphertext = ciphertext.clone();
        modified_ciphertext[0] ^= 1;
        assert!(cipher.decrypt(&nonce, &modified_ciphertext, &[]).is_err());
    }

    // https://datatracker.ietf.org/doc/html/draft-arciszewski-xchacha-03#appendix-A.3.1
    #[test]
    fn test_rfc_vector() {
        let plaintext = hex::decode(concat!(
            "4c616469657320616e642047656e746c656d656e206f662074686520636c6173",
            "73206f66202739393a204966204920636f756c64206f6666657220796f75206f",
            "6e6c79206f6e652074697020666f7220746865206675747572652c2073756e73",
            "637265656e20776f756c642062652069742e"
        ))
        .unwrap();
        let aad = hex::decode("50515253c0c1c2c3c4c5c6c7").unwrap();
        let key = hex::decode("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f")
            .unwrap();
        let iv = hex::decode("404142434445464748494a4b4c4d4e4f5051525354555657").unwrap();
        let expected_ciphertext = hex::decode(concat!(
            "bd6d179d3e83d43b9576579493c0e939572a1700252bfaccbed2902c21396cbb",
            "731c7f1b0b4aa6440bf3a82f4eda7e39ae64c6708c54c216cb96b72e1213b452",
            "2f8c9ba40db5d945b11b69b982c1bb9e3f3fac2bc369488f76b2383565d3fff9",
            "21f9664c97637da9768812f615c68b13b52e"
        ))
        .unwrap();
        let expected_tag = hex::decode("c0875924c1c7987947deafd8780acf49").unwrap();
        let tag_size = <RustCryptoXChaCha20Poly1305 as AeadCore>::TagSize::to_usize();

        let cipher = XChaCha20Poly1305::new(&key);
        let out = cipher.encrypt(&iv, &plaintext, &aad).unwrap();
        let tag_index = out.len() - tag_size;
        let (ciphertext, tag) = out.split_at(tag_index);
        assert_eq!(ciphertext, expected_ciphertext);
        assert_eq!(tag, expected_tag);
    }
}
