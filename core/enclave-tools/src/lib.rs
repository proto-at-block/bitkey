use aws_nitro_enclaves_cose::crypto::{
    MessageDigest, Openssl, SignatureAlgorithm, SigningPublicKey,
};
use aws_nitro_enclaves_cose::error::CoseError;
use aws_nitro_enclaves_cose::CoseSign1;
use aws_nitro_enclaves_image_format::utils::get_pcrs;
use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use ciborium::from_reader;
use oid_registry::{OID_SIG_ECDSA_WITH_SHA256, OID_SIG_ECDSA_WITH_SHA384};
use openssl::ec::EcKey;
use openssl::ecdsa::EcdsaSig;
use openssl::sha::sha384;
use public_key::PublicKey;
use serde::{Deserialize, Serialize};
use serde_bytes::ByteBuf;
use sha2::{Digest, Sha384};
use std::collections::{BTreeMap, HashMap};
use std::io::{self};
use std::result::Result;
use thiserror::Error;
use x509_parser::prelude::*;

use aws_nitro_enclaves_image_format::utils::eif_reader::EifReader;

#[derive(Error, Debug)]
pub enum EnclaveToolsError {
    #[error("I/O error")]
    IoError(#[from] io::Error),
    #[error("Certificate parse error")]
    ParseError,
    #[error("Signature verification error")]
    SignatureVerificationError,
    #[error("Cert chain verification error")]
    CertChainValidationError,
    #[error("Internal error")]
    InternalError(String),
    #[error("Cert issuance error")]
    IssuanceError(String),
    #[error("COSE error")]
    CoseError(#[from] CoseError),
}

pub enum EnclaveCodesigningKeyType {
    Development, // Includes staging and named stacks.
    Production,
}

impl EnclaveCodesigningKeyType {
    fn root_public_key(&self) -> Vec<u8> {
        match self {
            // Extract from:
            // openssl x509 -in src/wsm/keys/nitro-enclave-codesigning-cert-staging-root.der -inform der -noout -text
            EnclaveCodesigningKeyType::Development => vec![
                0x04, 0x6c, 0x64, 0x5f, 0x13, 0x02, 0x71, 0xe0, 0x48, 0x4e, 0x0c, 0x78, 0x93, 0xa1,
                0x36, 0x6a, 0xf1, 0xbb, 0x9e, 0x90, 0xeb, 0xe0, 0xe3, 0xdc, 0x7d, 0x20, 0xd3, 0x23,
                0x94, 0xa7, 0xd6, 0xd0, 0x7e, 0x20, 0x70, 0xa0, 0x13, 0x7c, 0xe8, 0x0d, 0x9d, 0xd7,
                0x76, 0x12, 0x63, 0xa8, 0x99, 0x89, 0x54, 0x1f, 0x59, 0x0c, 0xb1, 0x75, 0x77, 0x1f,
                0x50, 0x1b, 0x9d, 0x06, 0xfb, 0x3e, 0x34, 0x9c, 0x28,
            ],
            EnclaveCodesigningKeyType::Production => todo!("Generate the production PKI"),
        }
    }

    pub fn check_root_public_key(&self, public_key: Vec<u8>) -> bool {
        let root_public_key = self.root_public_key();
        public_key == root_public_key
    }
}

#[derive(Debug, Serialize, Deserialize)]
struct AttestationDocument {
    module_id: String,
    timestamp: u64,
    digest: String,
    pcrs: HashMap<u8, ByteBuf>,
    certificate: ByteBuf,
    cabundle: Vec<ByteBuf>,
    #[serde(skip_serializing_if = "Option::is_none")]
    public_key: Option<ByteBuf>,
    #[serde(skip_serializing_if = "Option::is_none")]
    user_data: Option<ByteBuf>,
    #[serde(skip_serializing_if = "Option::is_none")]
    nonce: Option<ByteBuf>,
}

#[derive(Debug, Serialize, Deserialize)]
struct EifSectionSignatureElement {
    signing_certificate: ByteBuf, // PEM-encoded certificate!
    signature: ByteBuf,
}

#[derive(Debug, Serialize, Deserialize)]
struct EifSectionSignature(Vec<EifSectionSignatureElement>);

#[derive(Clone, Debug)]
struct CertWrapper {
    der: Vec<u8>,
}

impl CertWrapper {
    // This function parses the DER-encoded certificate every time, and provides a zero-copy view
    // into the buffer. Do NOT call this function multiple times unless needed.
    //
    // The alternative here is to store the parsed certificate in the struct, but that would
    // require extending the lifetime of that variable using std::mem::transmute. Although that can definitely
    // be made safe, we'll keep this code simple for now, and if perf is an issue, we can revisit this.
    fn cert(&self) -> Result<X509Certificate<'_>, EnclaveToolsError> {
        let (rem, cert) =
            X509Certificate::from_der(&self.der).map_err(|_| EnclaveToolsError::ParseError)?;
        if !rem.is_empty() {
            return Err(EnclaveToolsError::ParseError);
        }
        Ok(cert)
    }
}

fn bytebuf_to_x509_cert(buf: &ByteBuf) -> Result<X509Certificate<'_>, EnclaveToolsError> {
    let (rem, cert) = X509Certificate::from_der(buf).map_err(|_| EnclaveToolsError::ParseError)?;
    if !rem.is_empty() {
        return Err(EnclaveToolsError::ParseError);
    }
    Ok(cert)
}

impl AttestationDocument {
    fn verify_aws_nitro_enclave_cert_chain(
        &self,
        signed_cose_document: &CoseSign1,
        root_public_key: &[u8],
    ) -> Result<(), EnclaveToolsError> {
        if self.cabundle.is_empty() {
            return Err(EnclaveToolsError::CertChainValidationError);
        }

        // Root comes first and is self-signed.
        let root = &bytebuf_to_x509_cert(&self.cabundle[0])?;
        verify_directly_issued_by(root, root)?;

        // The root public key must match the hardcoded AWS Nitro Enclave root CA public key.
        match root.subject_pki.parsed() {
            Ok(PublicKey::EC(ec)) => {
                if ec.data() != root_public_key {
                    return Err(EnclaveToolsError::CertChainValidationError);
                }
            }
            _ => return Err(EnclaveToolsError::CertChainValidationError),
        }

        // Verify the cabundle.
        for i in 1..self.cabundle.len() {
            let subject = &bytebuf_to_x509_cert(&self.cabundle[i])?;
            let issuer = &bytebuf_to_x509_cert(&self.cabundle[i - 1])?;
            verify_directly_issued_by(subject, issuer)?;
        }

        // Verify the leaf.
        let leaf = &bytebuf_to_x509_cert(&self.certificate)?;
        let issuer = &bytebuf_to_x509_cert(&self.cabundle[self.cabundle.len() - 1])?;
        verify_directly_issued_by(leaf, issuer)?;

        // Do the actual signature verification over the signed attestation document.
        let pubkey = EnclaveToolsSigningPublicKey::new_from_x509_cert(leaf)?;
        let signature_result = signed_cose_document
            .verify_signature::<Openssl>(&pubkey)
            .map_err(|_| EnclaveToolsError::SignatureVerificationError)?;

        // Hand the document back if the signature is good.
        match signature_result {
            true => Ok(()),
            false => Err(EnclaveToolsError::SignatureVerificationError),
        }
    }
}

struct CertificateBundle {
    leaf: CertWrapper,
    cabundle: Vec<CertWrapper>,
}

#[derive(Debug, Clone)]
pub struct EnclaveToolsSigningPublicKey {
    leaf_public_key: Vec<u8>, // DER-encoded
    signature_algorithm: SignatureAlgorithm,
    message_digest: MessageDigest,
}

impl EnclaveToolsSigningPublicKey {
    fn new_from_x509_cert(cert: &X509Certificate) -> Result<Self, EnclaveToolsError> {
        // Hardcode the mapping of supported OIDs.
        let ecdsa_with_sha384 = OID_SIG_ECDSA_WITH_SHA384;
        let ecdsa_with_sha256 = OID_SIG_ECDSA_WITH_SHA256;

        let oid = &cert.signature_algorithm.algorithm;
        let parameters = if oid == &ecdsa_with_sha384 {
            (SignatureAlgorithm::ES384, MessageDigest::Sha384)
        } else if oid == &ecdsa_with_sha256 {
            (SignatureAlgorithm::ES256, MessageDigest::Sha256)
        } else {
            return Err(EnclaveToolsError::InternalError(
                "Unsupported signature algorithm".to_string(),
            ));
        };

        Ok(Self {
            leaf_public_key: cert.public_key().raw.to_vec(),
            signature_algorithm: parameters.0,
            message_digest: parameters.1,
        })
    }
}

fn cert_pem_to_der(pem_bytes: Vec<u8>) -> Result<Vec<u8>, EnclaveToolsError> {
    // Not a general purpose PEM->DER function!
    let pem_str = std::str::from_utf8(&pem_bytes).map_err(|_| EnclaveToolsError::ParseError)?;

    // Strip the PEM armor.
    let pem_str = pem_str
        .replace("-----BEGIN CERTIFICATE-----", "")
        .replace("-----END CERTIFICATE-----", "")
        .replace('\n', "");

    // Decode the base64-encoded PEM
    let der_bytes = BASE64
        .decode(pem_str.as_bytes())
        .map_err(|_| EnclaveToolsError::ParseError)?;

    Ok(der_bytes)
}

fn verify_signature(
    digest: &[u8],
    signature: &[u8],
    pubkey_der: &[u8],
) -> Result<bool, EnclaveToolsError> {
    let ec_key = EcKey::public_key_from_der(pubkey_der)
        .map_err(|_| EnclaveToolsError::SignatureVerificationError)?;

    let r = openssl::bn::BigNum::from_slice(&signature[..signature.len() / 2])
        .map_err(|_| EnclaveToolsError::SignatureVerificationError)?;
    let s = openssl::bn::BigNum::from_slice(&signature[signature.len() / 2..])
        .map_err(|_| EnclaveToolsError::SignatureVerificationError)?;

    let result = EcdsaSig::from_private_components(r, s)
        .map_err(|_| EnclaveToolsError::SignatureVerificationError)?
        .verify(digest, &ec_key);

    match result {
        Ok(res) => Ok(res),
        Err(_) => Ok(false),
    }
}

impl SigningPublicKey for EnclaveToolsSigningPublicKey {
    fn get_parameters(&self) -> Result<(SignatureAlgorithm, MessageDigest), CoseError> {
        Ok((self.signature_algorithm, self.message_digest))
    }

    // This is the verify callback that is invoked by the COSE library.
    fn verify(&self, digest: &[u8], signature: &[u8]) -> Result<bool, CoseError> {
        // We have to use OpenSSL here because Ring does not allow you to verify a signature
        // over an already-hashed message. The COSE library hashes the message before calling this, so
        // the workaround is to use OpenSSL.
        verify_signature(digest, signature, &self.leaf_public_key)
            .map_err(|_| CoseError::UnverifiedSignature)
    }
}

// In test configuration, allow the expiry check to be bypassed.
#[cfg(test)]
const BYPASS_EXPIRY_CHECK: bool = true;
#[cfg(not(test))]
const BYPASS_EXPIRY_CHECK: bool = false;
fn check_cert_expiry(
    cert: &X509Certificate,
    issuer: &X509Certificate,
) -> Result<(), EnclaveToolsError> {
    if !BYPASS_EXPIRY_CHECK {
        if !cert.validity().is_valid() {
            return Err(EnclaveToolsError::IssuanceError(
                "Subject expired".to_string(),
            ));
        }
        if !issuer.validity().is_valid() {
            return Err(EnclaveToolsError::IssuanceError(
                "Issuer expired".to_string(),
            ));
        }
    }

    if cert.issuer() != issuer.subject() {
        return Err(EnclaveToolsError::IssuanceError(
            "Issuer mismatch".to_string(),
        ));
    }

    Ok(())
}

fn verify_directly_issued_by(
    cert: &X509Certificate,
    issuer: &X509Certificate,
) -> Result<(), EnclaveToolsError> {
    if cert.issuer() != issuer.subject() {
        return Err(EnclaveToolsError::IssuanceError(
            "Issuer mismatch".to_string(),
        ));
    }

    if !issuer.is_ca() {
        return Err(EnclaveToolsError::IssuanceError("Not a CA".to_string()));
    }

    check_cert_expiry(cert, issuer)?;

    if cert.verify_signature(Some(issuer.public_key())).is_err() {
        return Err(EnclaveToolsError::IssuanceError(
            "Signature verification failed".to_string(),
        ));
    }

    Ok(())
}

fn collect_der_encoded_certs(
    cabundle: Vec<Vec<u8>>,
    leaf: Vec<u8>,
) -> Result<CertificateBundle, EnclaveToolsError> {
    if cabundle.is_empty() {
        return Err(EnclaveToolsError::CertChainValidationError);
    }

    let cabundle_owned = cabundle
        .into_iter()
        .map(|cert| CertWrapper { der: cert })
        .collect();

    Ok(CertificateBundle {
        leaf: CertWrapper { der: leaf },
        cabundle: cabundle_owned,
    })
}

fn pcr8_hash(data: &[u8]) -> Vec<u8> {
    // https://github.com/aws/aws-nitro-enclaves-image-format#pcr8
    // https://blog.trailofbits.com/2024/02/16/a-few-notes-on-aws-nitro-enclaves-images-and-attestation/
    // PCR-8 = sha384(‘\0’*48 | sha384(SignatureSection[0].certificate))

    let inner_hash = [0u8; 48]
        .to_vec()
        .into_iter()
        .chain(sha384(data).to_vec())
        .collect::<Vec<u8>>();

    sha384(&inner_hash).to_vec()
}

// Implementation of the SigningPublicKey trait with hardcoded AWS Nitro Enclave root CA public key
// and ECDSA P-384/SHA-384 algorithm.
pub struct NitroEnclaveVerifier {
    root_public_key: Vec<u8>,
}

impl Default for NitroEnclaveVerifier {
    fn default() -> Self {
        Self::new()
    }
}

impl NitroEnclaveVerifier {
    pub fn new() -> Self {
        Self {
            // AWS Nitro Enclave root CA public key.
            // https://aws-nitro-enclaves.amazonaws.com/AWS_NitroEnclaves_Root-G1.zip
            root_public_key: vec![
                0x04, 0xfc, 0x02, 0x54, 0xeb, 0xa6, 0x08, 0xc1, 0xf3, 0x68, 0x70, 0xe2, 0x9a, 0xda,
                0x90, 0xbe, 0x46, 0x38, 0x32, 0x92, 0x73, 0x6e, 0x89, 0x4b, 0xff, 0xf6, 0x72, 0xd9,
                0x89, 0x44, 0x4b, 0x50, 0x51, 0xe5, 0x34, 0xa4, 0xb1, 0xf6, 0xdb, 0xe3, 0xc0, 0xbc,
                0x58, 0x1a, 0x32, 0xb7, 0xb1, 0x76, 0x07, 0x0e, 0xde, 0x12, 0xd6, 0x9a, 0x3f, 0xea,
                0x21, 0x1b, 0x66, 0xe7, 0x52, 0xcf, 0x7d, 0xd1, 0xdd, 0x09, 0x5f, 0x6f, 0x13, 0x70,
                0xf4, 0x17, 0x08, 0x43, 0xd9, 0xdc, 0x10, 0x01, 0x21, 0xe4, 0xcf, 0x63, 0x01, 0x28,
                0x09, 0x66, 0x44, 0x87, 0xc9, 0x79, 0x62, 0x84, 0x30, 0x4d, 0xc5, 0x3f, 0xf4,
            ],
        }
    }

    fn verify_chain_and_doc(
        &self,
        signed_cose_document: &CoseSign1,
    ) -> Result<AttestationDocument, EnclaveToolsError> {
        let doc_bytes = signed_cose_document.get_payload::<Openssl>(None)?;
        let attestation_document: AttestationDocument =
            ciborium::de::from_reader(&doc_bytes[..]).map_err(|_| EnclaveToolsError::ParseError)?;
        match attestation_document
            .verify_aws_nitro_enclave_cert_chain(signed_cose_document, &self.root_public_key)
        {
            Ok(_) => Ok(attestation_document),
            Err(e) => Err(e),
        }
    }
}

fn calculate_pcrs(eif: &mut EifReader) -> Result<BTreeMap<String, String>, EnclaveToolsError> {
    let pcrs = get_pcrs(
        &mut eif.image_hasher,
        &mut eif.bootstrap_hasher,
        &mut eif.app_hasher,
        &mut eif.cert_hasher,
        Sha384::new(),
        true,
    )
    .map_err(|e| EnclaveToolsError::InternalError(e.to_string()))?;

    // Check that PCR0, PCR1, PCR2, and PCR8 are present.
    let _ = pcrs.get("PCR0").ok_or(EnclaveToolsError::InternalError(
        "PCR0 not found".to_string(),
    ))?;
    let _ = pcrs.get("PCR1").ok_or(EnclaveToolsError::InternalError(
        "PCR1 not found".to_string(),
    ))?;
    let _ = pcrs.get("PCR2").ok_or(EnclaveToolsError::InternalError(
        "PCR2 not found".to_string(),
    ))?;
    let _ = pcrs.get("PCR8").ok_or(EnclaveToolsError::InternalError(
        "PCR8 not found".to_string(),
    ))?;

    Ok(pcrs)
}

// All inputs are TRUSTED; caller must ensure this.
fn verify_enclave_signature(
    eif_path: &str,
    codesigning_leaf: CertWrapper,
) -> Result<bool, EnclaveToolsError> {
    let eif =
        EifReader::from_eif(eif_path.to_string()).map_err(EnclaveToolsError::InternalError)?;

    // https://github.com/aws/aws-nitro-enclaves-image-format/commit/6ac2eb61416b871069c90e7c0b89a6120eaa53e5#diff-b335630551682c19a781afebcf4d07bf978fb1f8ac04c6bf87428ed5106870f5R356

    let signature_section_bytes = match eif.signature_section {
        Some(ref sig) => sig,
        None => {
            return Err(EnclaveToolsError::InternalError(
                "No signature section found".to_string(),
            ))
        }
    };

    let signature_section: EifSectionSignature = from_reader(&signature_section_bytes[..])
        .map_err(|_| {
            EnclaveToolsError::InternalError("Error deserializing signature section".to_string())
        })?;

    if signature_section.0.len() != 1 {
        return Err(EnclaveToolsError::InternalError(
            "Signature section has more than one element".to_string(),
        ));
    }

    let signature_section = signature_section
        .0
        .first()
        .ok_or(EnclaveToolsError::InternalError(
            "Signature section is empty".to_string(),
        ))?;

    // Signature is a CBOR serialized COSE_Sign1 object.
    let cose_sig = CoseSign1::from_bytes(&signature_section.signature)?;
    let leaf = codesigning_leaf.cert()?;

    // The certificate in the signature_section should match the leaf certificate.
    let reference_cert_der = cert_pem_to_der(signature_section.signing_certificate.to_vec())?;
    if codesigning_leaf.der != reference_cert_der {
        return Err(EnclaveToolsError::InternalError(
            "Leaf certificate does not match the reference certificate".to_string(),
        ));
    }

    let pubkey = EnclaveToolsSigningPublicKey::new_from_x509_cert(&leaf)?;
    cose_sig
        .verify_signature::<Openssl>(&pubkey)
        .map_err(|_| EnclaveToolsError::SignatureVerificationError)
}

fn parse_and_verify_signed_attestation_document(
    signed_document: Vec<u8>,
    challenge: Option<Vec<u8>>,
) -> Result<AttestationDocument, EnclaveToolsError> {
    // References:
    // https://github.com/aws/aws-nitro-enclaves-nsm-api/blob/main/docs/attestation_process.md
    // https://aws.amazon.com/blogs/compute/validating-attestation-documents-produced-by-aws-nitro-enclaves/
    let cose_doc = CoseSign1::from_bytes(&signed_document)?;
    let verifier = NitroEnclaveVerifier::new();
    let doc = verifier.verify_chain_and_doc(&cose_doc)?;

    match challenge {
        Some(challenge) => {
            let doc_nonce = doc.nonce.as_ref().ok_or(EnclaveToolsError::InternalError(
                "No nonce in attestation document".to_string(),
            ))?;

            if doc_nonce == &challenge {
                Ok(doc)
            } else {
                Err(EnclaveToolsError::InternalError(format!(
                    "Challenge mismatch: got {} but expected {}",
                    hex::encode(doc_nonce),
                    hex::encode(&challenge)
                )))
            }
        }
        None => Ok(doc),
    }
}

fn pcr_string_to_u8(pcr: &str) -> Result<u8, EnclaveToolsError> {
    let pcr_num = pcr
        .strip_prefix("PCR")
        .ok_or(EnclaveToolsError::InternalError(
            "Invalid PCR string".to_string(),
        ))?;
    let pcr_num = pcr_num
        .parse::<u8>()
        .map_err(|_| EnclaveToolsError::InternalError("Failed to parse PCR number".to_string()))?;
    Ok(pcr_num)
}

fn compare_pcrs(
    attestation_document: &AttestationDocument,
    pcrs_from_eif: &BTreeMap<String, String>,
) -> Result<(), EnclaveToolsError> {
    // Compare the PCR values that were calculated from the EIF with the PCR values that were
    // provided in the attestation document.

    // The attestation document will include more PCRs than we care about, including some
    // reserved ones. We ignore those.
    for (pcr_num, pcr_value) in pcrs_from_eif.iter() {
        // This field is included in the map, but it's not a PCR.
        if pcr_num == "HashAlgorithm" {
            continue;
        }

        let pcr_num = pcr_string_to_u8(pcr_num)?;
        let pcr_from_doc = attestation_document.pcrs.get(&pcr_num);

        match pcr_from_doc {
            Some(pcr_from_doc) => {
                let h = hex::encode(pcr_from_doc);
                if *pcr_value != h {
                    return Err(EnclaveToolsError::InternalError(format!(
                        "PCR{} mismatch: attestation document: {}, EIF: {}",
                        pcr_num, h, pcr_value
                    )));
                }
            }
            None => {
                return Err(EnclaveToolsError::InternalError(format!(
                    "PCR{} not found in attestation document",
                    pcr_num
                )));
            }
        }
    }

    Ok(())
}

fn verify_codesigning_certificate_chain(
    leaf: Vec<u8>,
    cabundle: Vec<Vec<u8>>,
    key_type: EnclaveCodesigningKeyType,
    pcr8: Vec<u8>,
) -> Result<CertWrapper, EnclaveToolsError> {
    let certs = collect_der_encoded_certs(cabundle, leaf)?;

    let cabundle = certs.cabundle;
    let root = &cabundle[0].cert()?;

    verify_directly_issued_by(root, root)?;

    // The root public key must match our codesigning root CA public key for the specific key type.
    match root.subject_pki.parsed() {
        Ok(PublicKey::EC(ec)) => {
            if !key_type.check_root_public_key(ec.data().to_vec()) {
                return Err(EnclaveToolsError::CertChainValidationError);
            }
        }
        _ => return Err(EnclaveToolsError::CertChainValidationError),
    };

    // Verify the cabundle.
    for i in 1..cabundle.len() {
        let subject = &cabundle[i].cert()?;
        let issuer = &cabundle[i - 1].cert()?;
        verify_directly_issued_by(subject, issuer)?;
    }

    // Verify the leaf.
    let leaf = certs.leaf.cert()?;
    let issuer = &cabundle[cabundle.len() - 1].cert()?;
    verify_directly_issued_by(&leaf, issuer)?;

    // At this point, we trust the certificate chain and the leaf.

    // Does PCR8 match the hash of the leaf certificate, in both the attestation document and the EIF?
    match pcr8 == pcr8_hash(&certs.leaf.der) {
        true => Ok(certs.leaf),
        false => Err(EnclaveToolsError::CertChainValidationError),
    }
}

pub fn verify_enclave(
    eif_path: &str,
    signed_attestation_document: Vec<u8>,
    codesigning_leaf_der: Vec<u8>,
    codesigning_cabundle: Vec<Vec<u8>>,
    key_type: EnclaveCodesigningKeyType,
    challenge: Option<Vec<u8>>,
) -> Result<(), EnclaveToolsError> {
    // First, validate and parse the attestation document. This ensures that the document directly comes from an AWS Nitro Enclave.
    let attestation_document = match parse_and_verify_signed_attestation_document(
        signed_attestation_document,
        challenge,
    ) {
        Ok(doc) => doc,
        Err(e) => {
            return Err(e);
        }
    };

    // We now trust the attestation document.

    // The EIF is the Enclave Image Format, which is a binary format that describes the code
    // and data that will be loaded into the enclave.
    let mut eif =
        EifReader::from_eif(eif_path.to_string()).map_err(EnclaveToolsError::InternalError)?;

    // The PCRs are the Platform Configuration Registers, which are registers that store hash values
    // of various parts of the enclave image.
    // Read more here: https://security.stackexchange.com/questions/252391/understanding-tpm-pcrs-pcr-banks-indexes-and-their-relations
    let pcrs = calculate_pcrs(&mut eif)?;

    // Ensure validity of the PCRs.
    match compare_pcrs(&attestation_document, &pcrs) {
        Ok(_) => {}
        Err(e) => {
            return Err(e);
        }
    }

    // We now trust the PCRs. That is, we just recomputed the PCRs from the EIF ourselves, and compared them against the
    // attestation document.

    // Now let's check the codesigning signature.

    let pcr8 = attestation_document
        .pcrs
        .get(&8)
        .ok_or(EnclaveToolsError::InternalError(
            "PCR8 not found".to_string(),
        ))?;

    let codesigning_leaf = match verify_codesigning_certificate_chain(
        codesigning_leaf_der,
        codesigning_cabundle,
        key_type,
        pcr8.to_vec(),
    ) {
        Ok(l) => l,
        Err(e) => {
            return Err(e);
        }
    };

    // The chain is valid, and we ensured that PCR8 (in both the EIF and document) matches the hash of the leaf certificate.
    // That means we are using the correct certificate to verify the enclave signature.

    // Now lets verify the enclave signature.
    match verify_enclave_signature(eif_path, codesigning_leaf) {
        Ok(true) => Ok(()),
        Ok(false) => Err(EnclaveToolsError::SignatureVerificationError),
        Err(e) => Err(e),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn get_test_data() -> (
        String,
        Vec<u8>,
        Vec<u8>,
        Vec<u8>,
        Vec<Vec<u8>>,
        Option<Vec<u8>>,
    ) {
        let workspace = env!("CARGO_MANIFEST_DIR");
        let eif_path = format!("{}/test-data/wsm-enclave.eif", workspace);

        let signed_doc_b64 = include_str!("../test-data/signed-attestation-document.b64");
        let signed_doc_bytes = BASE64.decode(signed_doc_b64.as_bytes()).unwrap();

        let codesigning_leaf_der = include_bytes!(concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/../../server/src/wsm/keys/nitro-enclave-codesigning-cert-staging-leaf.der"
        ))
        .to_vec();

        let codesigning_intermediate_der = include_bytes!(concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/../../server/src/wsm/keys/nitro-enclave-codesigning-cert-staging-intermediate.der"
        ))
        .to_vec();

        let codesigning_root_der = include_bytes!(concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/../../server/src/wsm/keys/nitro-enclave-codesigning-cert-staging-root.der"
        ))
        .to_vec();

        let codesigning_cabundle = vec![
            codesigning_root_der.clone(),
            codesigning_intermediate_der.clone(),
        ];

        let challenge = Some(vec![4, 5, 6]);

        (
            eif_path,
            signed_doc_bytes,
            codesigning_leaf_der,
            codesigning_intermediate_der,
            codesigning_cabundle,
            challenge,
        )
    }

    #[test]
    fn test_calculate_pcr_values() {
        let (eif_path, _, _, _, _, _) = get_test_data();
        let mut eif = EifReader::from_eif(eif_path).unwrap();
        let pcrs = calculate_pcrs(&mut eif).unwrap();

        let expected_pcrs = BTreeMap::from([
            ("HashAlgorithm".to_string(), "Sha384 { ... }".to_string()),
            ("PCR0".to_string(), "cf1c8e372a01e48e6d1cf4639ecb6c40b3ccf6fc89d68b1f119efd0491448c4e34354c66f24964ccff992c613318bb4d".to_string()),
            ("PCR1".to_string(), "5d3938eb05288e20a981038b1861062ff4174884968a39aee5982b312894e60561883576cc7381d1a7d05b809936bd16".to_string()),
            ("PCR2".to_string(), "0066e9542c2ad76af8304c5c2549663d36146238c6b41ea074b21d03b95ae42cc34183a58941e6a7d3a3c511788a69cb".to_string()),
            ("PCR8".to_string(), "180ed42dffb8e71132b21c3180388e412973099f267e831d9c3dd71ad9ffd5f405515d1029dc4cd92364b082a8ed65e1".to_string()),
        ]);

        assert_eq!(pcrs, expected_pcrs);
    }

    #[test]
    fn test_verify_enclave() {
        let (eif_path, signed_doc_bytes, codesigning_leaf_der, _, codesigning_cabundle, challenge) =
            get_test_data();

        let result = verify_enclave(
            &eif_path,
            signed_doc_bytes,
            codesigning_leaf_der,
            codesigning_cabundle,
            EnclaveCodesigningKeyType::Development,
            challenge,
        );
        assert!(result.is_ok());
    }

    #[test]
    fn test_invalid_signature_for_attestation_doc() {
        let (_, mut signed_doc_bytes, _, _, _, challenge) = get_test_data();
        signed_doc_bytes[0] ^= 1;

        let result = parse_and_verify_signed_attestation_document(signed_doc_bytes, challenge);
        assert!(result.is_err());
    }

    #[test]
    fn test_invalid_challenge() {
        let (_, signed_doc_bytes, _, _, _, _) = get_test_data();

        let invalid_challenge = Some(vec![0, 0, 0]);

        let result =
            parse_and_verify_signed_attestation_document(signed_doc_bytes, invalid_challenge);
        assert!(result.is_err());
    }

    #[test]
    fn test_verify_invalid_enclave() {
        let (
            eif_path,
            signed_doc_bytes,
            codesigning_leaf_der,
            _,
            mut codesigning_cabundle,
            challenge,
        ) = get_test_data();

        // Modify the cabundle to simulate an invalid certificate chain
        codesigning_cabundle[0][0] ^= 1;

        let result = verify_enclave(
            &eif_path,
            signed_doc_bytes,
            codesigning_leaf_der,
            codesigning_cabundle,
            EnclaveCodesigningKeyType::Development,
            challenge,
        );
        assert!(result.is_err());
    }
}
