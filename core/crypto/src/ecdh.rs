use crate::keys::{PublicKey, SecretKey};
use bitcoin::secp256k1::ecdh::SharedSecret as RustSecp256k1SharedSecret;
use std::sync::Mutex;

pub struct Secp256k1SharedSecret {
    shared_secret_mutex: Mutex<RustSecp256k1SharedSecret>,
}

impl Secp256k1SharedSecret {
    pub fn new(point: &PublicKey, scalar: &SecretKey) -> Self {
        let shared_secret = RustSecp256k1SharedSecret::new(point, &scalar.inner());
        Self {
            shared_secret_mutex: Mutex::new(shared_secret),
        }
    }

    pub fn secret_bytes(&self) -> Vec<u8> {
        self.shared_secret_mutex
            .lock()
            .unwrap()
            .secret_bytes()
            .to_vec()
    }
}

#[cfg(test)]
mod tests {
    use crate::ecdh::Secp256k1SharedSecret;
    use rand::RngCore;

    #[test]
    fn test_shared_secret() {
        let mut random_bytes = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut random_bytes[..]);
        let sk1 = crate::keys::SecretKey::new(random_bytes.to_vec()).unwrap();
        rand::thread_rng().fill_bytes(&mut random_bytes[..]);
        let sk2 = crate::keys::SecretKey::new(random_bytes.to_vec()).unwrap();

        let sec1 = Secp256k1SharedSecret::new(&sk1.as_public(), &sk2);
        let sec2 = Secp256k1SharedSecret::new(&sk2.as_public(), &sk1);
        let sec_odd = Secp256k1SharedSecret::new(&sk1.as_public(), &sk1);
        assert_eq!(sec1.secret_bytes(), sec2.secret_bytes());
        assert_ne!(sec_odd.secret_bytes(), sec2.secret_bytes());
    }
}
