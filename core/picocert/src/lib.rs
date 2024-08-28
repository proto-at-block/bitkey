use rand_core::OsRng;
use secp256k1::{
    ecdsa::Signature,
    hashes::{sha256, Hash},
    Keypair, Message, PublicKey, SecretKey,
};

#[cfg(not(test))]
use std::time::{SystemTime, UNIX_EPOCH};

pub const MAX_NAME_LEN: usize = 16;
pub const MAX_PUBKEY_LEN: usize = 33;
pub const MAX_SIG_LEN: usize = 64;
pub const CURRENT_VERSION: u8 = 1;

// Copy bytes from src to a fixed size array, padding with zeros if necessary
fn copy_with_zeros<const N: usize>(src: &[u8]) -> [u8; N] {
    let mut arr = [0u8; N];
    let len = src.len().min(N);
    arr[..len].copy_from_slice(&src[..len]);
    arr
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum Error {
    Ok,
    Invalid,
    Expired,
    Signature,
    Issuer,
    Version,
    NotSelfSigned,
    InvalidValidityPeriod,
    MalformedSignatureInput,
    InvalidPublicKey,
    InvalidPrivateKey,
    InvalidFromTime,
    InvalidToTime,
    InvalidIssuer,
    InvalidSubject,
    InvalidSignature,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Certificate {
    version: u8,
    issuer: [u8; MAX_NAME_LEN],
    subject: [u8; MAX_NAME_LEN],
    valid_from: u64,
    valid_to: u64,
    public_key: [u8; MAX_PUBKEY_LEN],
    signature: [u8; MAX_SIG_LEN],
}

#[derive(Debug, Clone, PartialEq)]
pub struct CertificateWithPrivateKey {
    pub cert: Certificate,
    pub private_key: Vec<u8>, // Raw private key
}

impl CertificateWithPrivateKey {
    pub fn to_keypair(&self) -> Result<Keypair, Error> {
        let secp = secp256k1::global::SECP256K1;
        let keypair = Keypair::from_seckey_slice(secp, &self.private_key)
            .map_err(|_| Error::InvalidPrivateKey)?;
        Ok(keypair)
    }

    pub fn sign(&self, data: &[u8]) -> Result<Vec<u8>, Error> {
        let keypair = self.to_keypair()?;
        let sig = secp256k1_ecdsa_sign(&keypair.secret_key(), data)?;
        Ok(sig.to_vec())
    }
}

impl Default for Certificate {
    fn default() -> Self {
        Self::new()
    }
}

impl Certificate {
    pub fn new() -> Self {
        Self {
            version: CURRENT_VERSION,
            issuer: [0; MAX_NAME_LEN],
            subject: [0; MAX_NAME_LEN],
            valid_from: 0,
            valid_to: 0,
            public_key: [0; MAX_PUBKEY_LEN],
            signature: [0; MAX_SIG_LEN],
        }
    }

    fn parse_null_terminated_string(bytes: &[u8]) -> String {
        let nul_range_end = bytes
            .iter()
            .position(|&c| c == b'\0')
            .unwrap_or(bytes.len()); // default to length if no `\0` present
        String::from_utf8_lossy(&bytes[0..nul_range_end]).to_string()
    }

    pub fn issuer(&self) -> String {
        Self::parse_null_terminated_string(&self.issuer)
    }

    pub fn subject(&self) -> String {
        Self::parse_null_terminated_string(&self.subject)
    }

    pub fn is_self_signed(&self) -> bool {
        self.issuer == self.subject
    }

    pub fn signable(&self) -> Vec<u8> {
        // Everything but the signature
        let mut bytes = Vec::new();
        bytes.push(self.version);
        bytes.extend_from_slice(&self.issuer);
        bytes.extend_from_slice(&self.subject);
        bytes.extend_from_slice(&self.valid_from.to_le_bytes());
        bytes.extend_from_slice(&self.valid_to.to_le_bytes());
        bytes.extend_from_slice(&self.public_key);
        bytes
    }

    pub fn size_without_padding() -> usize {
        1 + MAX_NAME_LEN * 2 + 8 * 2 + MAX_PUBKEY_LEN + MAX_SIG_LEN
    }

    pub fn to_bytes(&self) -> Vec<u8> {
        // Full serialized cert
        let mut bytes = self.signable();
        bytes.extend_from_slice(&self.signature);
        bytes
    }

    pub fn from_bytes(bytes: &[u8]) -> Result<Self, Error> {
        if bytes.len() != Certificate::size_without_padding() {
            return Err(Error::Invalid);
        }

        let mut cursor = 0;

        let version = bytes[cursor];
        cursor += 1;

        let issuer: [u8; MAX_NAME_LEN] = bytes[cursor..cursor + MAX_NAME_LEN]
            .try_into()
            .map_err(|_| Error::InvalidIssuer)?;
        cursor += MAX_NAME_LEN;

        let subject: [u8; MAX_NAME_LEN] = bytes[cursor..cursor + MAX_NAME_LEN]
            .try_into()
            .map_err(|_| Error::InvalidSubject)?;
        cursor += MAX_NAME_LEN;

        let valid_from = u64::from_le_bytes(
            bytes[cursor..cursor + 8]
                .try_into()
                .map_err(|_| Error::InvalidFromTime)?,
        );
        cursor += 8;

        let valid_to = u64::from_le_bytes(
            bytes[cursor..cursor + 8]
                .try_into()
                .map_err(|_| Error::InvalidToTime)?,
        );
        cursor += 8;

        let public_key: [u8; MAX_PUBKEY_LEN] = bytes[cursor..cursor + MAX_PUBKEY_LEN]
            .try_into()
            .map_err(|_| Error::InvalidPublicKey)?;
        cursor += MAX_PUBKEY_LEN;

        let signature: [u8; MAX_SIG_LEN] = bytes[cursor..cursor + MAX_SIG_LEN]
            .try_into()
            .map_err(|_| Error::InvalidSignature)?;

        Ok(Self {
            version,
            issuer,
            subject,
            valid_from,
            valid_to,
            public_key,
            signature,
        })
    }

    pub fn from_file(path: &str) -> Result<Self, Error> {
        let bytes = std::fs::read(path).map_err(|_| Error::Invalid)?;
        Self::from_bytes(&bytes)
    }
}

#[cfg(not(test))]
pub fn current_time() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_secs()
}

#[cfg(test)]
thread_local! {
    static TEST_CURRENT_TIME: std::cell::RefCell<u64> = std::cell::RefCell::new(1717629556);
}

#[cfg(test)]
pub fn current_time() -> u64 {
    TEST_CURRENT_TIME.with(|time| *time.borrow())
}

// Check if data is signed by the private key corresponding to the public key in the certificate.
pub fn verify(cert: &Certificate, data: &[u8], sig: &[u8]) -> Result<(), Error> {
    let secp = secp256k1::global::SECP256K1;

    let hash = <sha256::Hash as Hash>::hash(data).to_byte_array();
    let message = Message::from_digest_slice(&hash).map_err(|_| Error::Invalid)?;
    let signature = Signature::from_compact(sig).map_err(|_| Error::MalformedSignatureInput)?;
    let pubkey = PublicKey::from_slice(&cert.public_key).map_err(|_| Error::InvalidPublicKey)?;

    secp.verify_ecdsa(&message, &signature, &pubkey)
        .map_err(|_| Error::Signature)?;

    Ok(())
}

pub fn validate_cert(issuer: &Certificate, subject: &Certificate) -> Result<(), Error> {
    if issuer.version != CURRENT_VERSION || subject.version != CURRENT_VERSION {
        return Err(Error::Version);
    }

    if issuer.subject != subject.issuer {
        return Err(Error::Issuer);
    }

    let now = current_time();
    if now < subject.valid_from
        || now > subject.valid_to
        || now < issuer.valid_from
        || now > issuer.valid_to
    {
        return Err(Error::Expired);
    }

    // Check if subject's validity period falls within the issuer's validity period
    if subject.valid_from < issuer.valid_from || subject.valid_to > issuer.valid_to {
        return Err(Error::InvalidValidityPeriod);
    }

    let subject_bytes = subject.signable();
    verify(issuer, &subject_bytes, &subject.signature)
}

pub fn validate_cert_chain(cert_chain: &[Certificate]) -> Result<(), Error> {
    if cert_chain.is_empty() {
        return Err(Error::Invalid);
    }

    for i in 0..cert_chain.len() - 1 {
        validate_cert(&cert_chain[i + 1], &cert_chain[i])?;
    }

    let root = &cert_chain[cert_chain.len() - 1];
    if !root.is_self_signed() {
        return Err(Error::NotSelfSigned);
    }

    validate_cert(root, root)
}

pub fn verify_and_validate_chain(
    cert_chain: &[Certificate],
    data: &[u8],
    sig: &[u8],
) -> Result<(), Error> {
    if cert_chain.is_empty() || data.is_empty() {
        return Err(Error::Invalid);
    }

    validate_cert_chain(cert_chain)?;
    verify(&cert_chain[0], data, sig)
}

// If the issuer is NOT set, the certificate is self-signed.
pub fn issue(
    issuer: Option<&CertificateWithPrivateKey>,
    subject: String,
    valid_from: u64,
    valid_to: u64,
) -> Result<CertificateWithPrivateKey, Error> {
    let mut cert = Certificate::new();
    cert.version = CURRENT_VERSION;
    cert.subject = copy_with_zeros::<MAX_NAME_LEN>(subject.as_bytes());
    cert.valid_from = valid_from;
    cert.valid_to = valid_to;

    // Generate a new keypair for the subject
    let keypair = secp256k1_generate_keypair();

    cert.public_key = copy_with_zeros::<MAX_PUBKEY_LEN>(keypair.public_key().serialize().as_ref());

    let mut ckp = CertificateWithPrivateKey {
        cert,
        private_key: keypair.secret_key().secret_bytes().to_vec(),
    };

    if let Some(issuer) = issuer {
        // Sign the new certificate with the issuer's private key
        ckp.cert.issuer = issuer.cert.subject;
        let cert_bytes = ckp.cert.signable();
        let sig = issuer.sign(&cert_bytes)?;
        ckp.cert.signature = copy_with_zeros::<MAX_SIG_LEN>(sig.as_ref());

        validate_cert(&issuer.cert, &ckp.cert)?;
    } else {
        // Self-signed
        ckp.cert.issuer = ckp.cert.subject;
        let cert_bytes = ckp.cert.signable();
        let sig = ckp.sign(&cert_bytes)?;
        ckp.cert.signature = copy_with_zeros::<MAX_SIG_LEN>(sig.as_ref());

        validate_cert(&ckp.cert, &ckp.cert)?;
    }

    Ok(ckp)
}

fn secp256k1_ecdsa_sign(private_key: &SecretKey, data: &[u8]) -> Result<[u8; MAX_SIG_LEN], Error> {
    let secp = secp256k1::global::SECP256K1;

    let hash = <sha256::Hash as Hash>::hash(data).to_byte_array();
    let message = Message::from_digest_slice(&hash).map_err(|_| Error::Invalid)?;

    let sig = secp.sign_ecdsa(&message, private_key);

    Ok(sig.serialize_compact())
}

fn secp256k1_generate_keypair() -> Keypair {
    let secp = secp256k1::global::SECP256K1;
    let mut rng = &mut OsRng;
    let sk = SecretKey::new(&mut rng);
    Keypair::from_secret_key(secp, &sk)
}

#[cfg(test)]
mod tests {
    use super::*;

    pub fn set_test_current_time(time: u64) {
        TEST_CURRENT_TIME.with(|current_time| {
            *current_time.borrow_mut() = time;
        });
    }

    #[test]
    fn test_issue_self_signed_cert() {
        let subject = "selfsigned";
        let valid_from = current_time();
        let valid_to = valid_from + 60 * 60 * 24; // 1 day validity

        let cert_result = issue(None, subject.to_string(), valid_from, valid_to);
        assert!(cert_result.is_ok());

        let cert_with_priv_key = cert_result.unwrap();
        assert_eq!(cert_with_priv_key.cert.subject(), subject);
        assert_eq!(cert_with_priv_key.cert.issuer(), subject);

        // Sign some data with the private key
        let data = b"some data to sign";
        let sig = cert_with_priv_key.sign(data).unwrap();

        // Verify the signature with the public key
        let cert_chain = vec![cert_with_priv_key.cert.clone()];
        let verify_result = verify_and_validate_chain(&cert_chain, data, &sig);
        assert!(verify_result.is_ok());
    }

    #[test]
    fn test_issue_cert() {
        let issuer_subject = "issuer";
        let valid_from = current_time();
        let valid_to = valid_from + 60 * 60 * 24 * 365; // 1 year validity

        let issuer_cert_result = issue(None, issuer_subject.to_string(), valid_from, valid_to);
        assert!(issuer_cert_result.is_ok());

        let issuer_cert_with_priv_key = issuer_cert_result.unwrap();

        let subject = "subject";
        let cert_result = issue(
            Some(&issuer_cert_with_priv_key),
            subject.to_string(),
            valid_from,
            valid_to,
        );
        assert!(cert_result.is_ok());

        let cert_with_priv_key = cert_result.unwrap();
        assert_eq!(cert_with_priv_key.cert.subject(), subject);
        assert_eq!(cert_with_priv_key.cert.issuer(), issuer_subject);

        // Sign some data with the private key
        let data = b"some data to sign";
        let sig = cert_with_priv_key.sign(data).unwrap();

        // Verify the signature with the public key
        let cert_chain = vec![
            cert_with_priv_key.cert.clone(),
            issuer_cert_with_priv_key.cert.clone(),
        ];
        let verify_result = verify_and_validate_chain(&cert_chain, data, &sig);
        assert!(verify_result.is_ok());
    }

    #[test]
    fn test_validate_3_tier_cert_chain() {
        let root_subject = "root";
        let intermediate_subject = "intermediate";
        let leaf_subject = "leaf";

        let valid_from = current_time();
        let valid_to = valid_from + 60 * 60 * 24 * 365; // 1 year validity

        // Issue root certificate
        let root_cert_result = issue(None, root_subject.to_string(), valid_from, valid_to);
        assert!(root_cert_result.is_ok());
        let root_cert_with_priv_key = root_cert_result.unwrap();

        // Issue intermediate certificate
        let intermediate_cert_result = issue(
            Some(&root_cert_with_priv_key),
            intermediate_subject.to_string(),
            valid_from,
            valid_to,
        );
        assert!(intermediate_cert_result.is_ok());
        let intermediate_cert_with_priv_key = intermediate_cert_result.unwrap();

        // Issue leaf certificate
        let leaf_cert_result = issue(
            Some(&intermediate_cert_with_priv_key),
            leaf_subject.to_string(),
            valid_from,
            valid_to,
        );
        assert!(leaf_cert_result.is_ok());
        let leaf_cert_with_priv_key = leaf_cert_result.unwrap();

        // Create certificate chain
        let cert_chain = vec![
            leaf_cert_with_priv_key.cert.clone(),
            intermediate_cert_with_priv_key.cert.clone(),
            root_cert_with_priv_key.cert.clone(),
        ];

        // Validate the certificate chain
        let validate_result = validate_cert_chain(&cert_chain);
        assert!(validate_result.is_ok());
    }

    #[test]
    fn test_invalid_signature() {
        let subject = "selfsigned";
        let valid_from = current_time();
        let valid_to = valid_from + 60 * 60 * 24; // 1 day validity

        let cert_result = issue(None, subject.to_string(), valid_from, valid_to);
        assert!(cert_result.is_ok());

        let cert_with_priv_key = cert_result.unwrap();
        assert_eq!(cert_with_priv_key.cert.subject(), subject);
        assert_eq!(cert_with_priv_key.cert.issuer(), subject);

        // Sign some data with the private key
        let data = b"some data to sign";
        let sig = cert_with_priv_key.sign(data).unwrap();

        // Modify the data to invalidate the signature
        let invalid_data = b"some tampered data";

        // Verify the signature with the public key
        let cert_chain = vec![cert_with_priv_key.cert.clone()];
        let verify_result = verify_and_validate_chain(&cert_chain, invalid_data, &sig);
        assert!(verify_result.is_err());
    }

    #[test]
    fn test_issue_expired_certificate() {
        let subject = "selfsigned";
        let valid_from = current_time() - 60 * 60 * 24 * 2; // 2 days ago
        let valid_to = valid_from + 60 * 60 * 24; // 1 day validity

        let cert_result = issue(None, subject.to_string(), valid_from, valid_to);
        assert!(cert_result.is_err());
        assert_eq!(cert_result.err().unwrap(), Error::Expired);
    }

    #[test]
    fn test_expired_certificate() {
        let subject = "selfsigned";
        let valid_from = current_time();
        let valid_to = valid_from + 60 * 60 * 24; // 1 day validity

        let cert_result = issue(None, subject.to_string(), valid_from, valid_to);
        assert!(cert_result.is_ok());

        let cert_with_priv_key = cert_result.unwrap();

        // Create a certificate chain
        let cert_chain = vec![cert_with_priv_key.cert.clone()];

        // Expire the certificate
        set_test_current_time(99999999999);

        // Validate the certificate chain (should be expired)
        let validate_result = validate_cert_chain(&cert_chain);
        assert!(validate_result.is_err());
        assert_eq!(validate_result.err().unwrap(), Error::Expired);
    }

    #[test]
    fn test_non_self_signed_root_certificate() {
        let root_subject = "selfsigned";
        let valid_from = current_time();
        let valid_to = valid_from + 60 * 60 * 24 * 365; // 1 year validity

        let root_cert_result = issue(None, root_subject.to_string(), valid_from, valid_to);
        assert!(root_cert_result.is_ok());

        let mut root_cert_with_priv_key = root_cert_result.unwrap();

        // Tamper with issuer, this also invalidates the signature
        let new_issuer = "nope";
        root_cert_with_priv_key.cert.issuer =
            copy_with_zeros::<MAX_NAME_LEN>(new_issuer.as_bytes());

        // Create a certificate chain
        let cert_chain = vec![root_cert_with_priv_key.cert.clone()];

        let validate_result = validate_cert_chain(&cert_chain);
        assert!(validate_result.is_err());
        assert_eq!(validate_result.err().unwrap(), Error::NotSelfSigned);
    }

    #[test]
    fn test_roundtrip_serialization() {
        let subject = "selfsigned";
        let valid_from = current_time();
        let valid_to = valid_from + 60 * 60 * 24; // 1 day validity

        let cert_result = issue(None, subject.to_string(), valid_from, valid_to);
        assert!(cert_result.is_ok());

        let original = cert_result.unwrap();

        let cert_bytes = original.cert.to_bytes();
        let cert_from_bytes = Certificate::from_bytes(&cert_bytes).unwrap();

        assert_eq!(original.cert, cert_from_bytes);
    }

    #[test]
    fn test_cross_issuance() {
        // Issue the root
        let root_subject = "root";
        let valid_from = current_time();
        let valid_to = valid_from + 60 * 60 * 24 * 365; // 1 year validity

        let root_cert_result = issue(None, root_subject.to_string(), valid_from, valid_to);
        assert!(root_cert_result.is_ok());

        let root_cert_with_priv_key = root_cert_result.unwrap();

        // Issue two intermediates.
        let intermediate1_subject = "intermediate1";
        let intermediate2_subject = "intermediate2";

        let intermediate1_cert = issue(
            Some(&root_cert_with_priv_key),
            intermediate1_subject.to_string(),
            valid_from,
            valid_to,
        )
        .unwrap();

        let intermediate2_cert = issue(
            Some(&root_cert_with_priv_key),
            intermediate2_subject.to_string(),
            valid_from,
            valid_to,
        )
        .unwrap();

        // Issue a leaf from each intermediate.
        let leaf1_subject = "leaf1";
        let leaf2_subject = "leaf2";

        let leaf1_cert = issue(
            Some(&intermediate1_cert),
            leaf1_subject.to_string(),
            valid_from,
            valid_to,
        )
        .unwrap();

        let leaf2_cert = issue(
            Some(&intermediate2_cert),
            leaf2_subject.to_string(),
            valid_from,
            valid_to,
        )
        .unwrap();

        // Create certificate chains for each leaf.
        let leaf1_chain = vec![
            leaf1_cert.cert.clone(),
            intermediate1_cert.cert.clone(),
            root_cert_with_priv_key.cert.clone(),
        ];

        let leaf2_chain = vec![
            leaf2_cert.cert.clone(),
            intermediate2_cert.cert.clone(),
            root_cert_with_priv_key.cert.clone(),
        ];

        // Ensure valid chains are vaild.
        let validate_result1 = validate_cert_chain(&leaf1_chain);
        assert!(validate_result1.is_ok());
        let validate_result2 = validate_cert_chain(&leaf2_chain);
        assert!(validate_result2.is_ok());

        // Create invalid chains by mixing the intermediates.
        let invalid_chain1 = vec![
            leaf1_cert.cert.clone(),
            intermediate2_cert.cert.clone(),
            root_cert_with_priv_key.cert.clone(),
        ];

        let invalid_chain2 = vec![
            leaf2_cert.cert.clone(),
            intermediate1_cert.cert.clone(),
            root_cert_with_priv_key.cert.clone(),
        ];

        // Ensure invalid chains are invalid.
        let validate_result3 = validate_cert_chain(&invalid_chain1);
        assert!(validate_result3.is_err());

        let validate_result4 = validate_cert_chain(&invalid_chain2);
        assert!(validate_result4.is_err());
    }

    #[test]
    fn test_subject_validity_within_issuer_validity() {
        let issuer_subject = "issuer";
        let valid_from = current_time();
        let valid_to = valid_from + 60 * 60 * 24 * 365; // 1 year validity

        let issuer_cert_result = issue(None, issuer_subject.to_string(), valid_from, valid_to);
        assert!(issuer_cert_result.is_ok());

        let issuer_cert_with_priv_key = issuer_cert_result.unwrap();

        let subject = "subject";
        let subject_valid_from = valid_from + 60 * 60 * 24 * 30; // 30 days after issuer valid_from
        let subject_valid_to = subject_valid_from + 60 * 60 * 24 * 365; // 1 year validity for subject

        set_test_current_time(subject_valid_from + 60 * 60 * 24); // Set current time within the subject's validity period

        let cert_result = issue(
            Some(&issuer_cert_with_priv_key),
            subject.to_string(),
            subject_valid_from,
            subject_valid_to,
        );
        assert!(cert_result.is_err());
        assert_eq!(cert_result.err().unwrap(), Error::InvalidValidityPeriod);
    }

    #[test]
    fn test_copy_with_zeros() {
        // Test with exact length
        let input = [1, 2, 3, 4, 5];
        let result = copy_with_zeros::<5>(&input);
        assert_eq!(result, [1, 2, 3, 4, 5]);

        // Test with shorter length
        let result = copy_with_zeros::<10>(&input);
        assert_eq!(result, [1, 2, 3, 4, 5, 0, 0, 0, 0, 0]);

        // Test with longer length
        let long_input = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];
        let result = copy_with_zeros::<5>(&long_input);
        assert_eq!(result, [1, 2, 3, 4, 5]);
    }

    #[test]
    #[rustfmt::skip]
    fn test_parse_raw() {
        let bytes = vec![
            CURRENT_VERSION, // version
            // issuer (16 bytes)
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
            // subject (16 bytes)
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
            // valid_from (8 bytes)
            0, 0, 0, 0, 0, 0, 0, 0,
            // valid_to (8 bytes)
            0, 0, 0, 0, 0, 0, 0, 0,
            // public_key (33 bytes)
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            // signature (64 bytes)
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        ];

        let result = Certificate::from_bytes(&bytes);
        assert!(result.is_ok());
    }

    #[test]
    fn test_too_short_cert() {
        let short_bytes = vec![1, 2, 3];
        let result = Certificate::from_bytes(&short_bytes);
        assert!(result.is_err());
        assert_eq!(result.err().unwrap(), Error::Invalid);
    }

    #[test]
    fn test_reading_non_existent_files() {
        let result = Certificate::from_file("non_existent.pcrt");
        assert!(result.is_err());
        assert_eq!(result.err().unwrap(), Error::Invalid);

        let result = Certificate::from_file("non_existent.priv.der");
        assert!(result.is_err());
        assert_eq!(result.err().unwrap(), Error::Invalid);
    }
}
