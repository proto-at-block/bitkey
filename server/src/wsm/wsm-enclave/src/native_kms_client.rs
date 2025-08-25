use std::ops::Deref;

use anyhow::{anyhow, Result};
use aws_config::{BehaviorVersion, Region};
use aws_lc_rs::{
    encoding::AsDer,
    rsa::{
        KeySize, OaepPrivateDecryptingKey, PrivateDecryptingKey, PublicEncryptingKey,
        OAEP_SHA256_MGF1SHA256,
    },
};
use aws_nitro_enclaves_nsm_api::{
    api::Request,
    driver::{nsm_init, nsm_process_request},
};
use aws_nitro_enclaves_nsm_api::{
    api::Response::{Attestation, Error},
    driver::nsm_exit,
};
use aws_sdk_kms::{
    config::Credentials,
    error::DisplayErrorContext,
    operation::decrypt::DecryptOutput,
    primitives::Blob,
    types::{KeyEncryptionMechanism, RecipientInfo},
    Client,
};
use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use serde_bytes::ByteBuf;
use subtle::{Choice, ConditionallySelectable, ConstantTimeEq};
use wsm_common::{
    enclave_log::{LogBuffer, MAX_LOG_EVENT_SIZE_BYTES},
    messages::enclave::KmsRequest,
    wsm_log,
};

pub async fn fetch_secret_from_kms(
    request: &KmsRequest,
    log_buffer: &mut LogBuffer,
) -> Result<KmsSecret> {
    wsm_log!(log_buffer, "Starting native KMS secret fetch");
    let keypair = generate_rsa_keypair().map_err(|e| {
        wsm_log!(log_buffer, format!("Failed to generate keypair: {}", e));
        anyhow!("Failed to generate keypair: {}", e)
    })?;

    let attestation_doc = get_nsm_attestation_document(&keypair, log_buffer).map_err(|e| {
        wsm_log!(log_buffer, format!("Failed to get NSM attestation: {}", e));
        anyhow!("Failed to get NSM attestation: {}", e)
    })?;

    let decrypt_output = call_kms_decrypt(request, attestation_doc, log_buffer)
        .await
        .map_err(|e| {
            wsm_log!(log_buffer, format!("Failed to call KMS decrypt: {}", e));
            anyhow!("Failed to call KMS decrypt: {}", e)
        })?;

    wsm_log!(log_buffer, "KMS decrypt completed successfully");

    let ciphertext = decrypt_output.ciphertext_for_recipient.ok_or_else(|| {
        wsm_log!(log_buffer, "Failed to get CiphertextForRecipient");
        anyhow!("Failed to get CiphertextForRecipient")
    })?;

    wsm_log!(log_buffer, "Ciphertext obtained successfully");

    // TODO: [W-11814] Replace the hand-rolled constant-time decoder with a real CMS parser.
    // Background:
    // * The `CiphertextForRecipient` field that KMS returns to a Nitro Enclave is
    //   a CMS `RecipientInfo` (RFC 5652 §6) object that has been ASN.1 **DER-encoded**
    //   and then Base-64-encoded in the JSON response.  It contains the encrypted
    //   key material, wrapped with `RSAES_OAEP_SHA_256`.
    //
    // Why we don't use a library today:
    // * The AWS SDK exposes the raw bytes but does **not** decode CMS.
    // * RustCrypto `der`/`cms` gained BER-indefinite-length support in July 2025 (#779).
    //   At the moment, that code is available **only** on a pre-release version (0.30) of the
    //   library.
    // * Binding to the AWS C helper would drag in OpenSSL, which is difficult to build for the
    //   enclave environment.
    //
    // For now, we'll just decode the ciphertext manually. See `decode_ciphertext_for_recipient`
    // for the implementation.
    let private_key = OaepPrivateDecryptingKey::new(keypair.private_key)?;
    let plaintext = decode_ciphertext_for_recipient(ciphertext.into_inner(), &private_key)?;

    Ok(KmsSecret(plaintext))
}

// Constant-time decoder for the `CiphertextForRecipient` blob that AWS KMS
// returns to a Nitro Enclave.
//
// * Iterates over **every** 256-byte window in the CMS `RecipientInfo`.
// * Uses `subtle` primitives so control-flow is independent of which window
//   decrypts successfully (avoids an RSA-OAEP padding oracle).
// * Returns the single valid plaintext, or an error if zero **or** multiple
//   windows decrypt.
fn decode_ciphertext_for_recipient(
    ciphertext_bytes: Vec<u8>,
    private_key: &OaepPrivateDecryptingKey,
) -> Result<Vec<u8>> {
    let out_len = private_key.min_output_size();
    let mut candidate = vec![0u8; out_len];
    let mut found_cnt = 0u8; // Counts how many windows decrypted OK (0, 1, >1)

    // Iterate over every 256-byte window in the CMS `RecipientInfo`.
    for chunk in ciphertext_bytes.windows(256) {
        let mut tmp = vec![0u8; out_len];

        // Decrypt without early-exit; `ok` is 1 if padding valid, else 0.
        let ok = Choice::from(
            private_key
                .decrypt(&OAEP_SHA256_MGF1SHA256, chunk, &mut tmp, None)
                .is_ok() as u8,
        );

        // This is a conditional, constant-time copy from `tmp` to `candidate`.
        for (c, t) in candidate.iter_mut().zip(&tmp) {
            *c = u8::conditional_select(c, t, ok);
        }

        // Branch-free counter: add 1 when ok == 1
        found_cnt += ok.unwrap_u8();
    }

    // Succeed only if exactly one window decrypted.
    if found_cnt.ct_eq(&1u8).unwrap_u8() == 1 {
        Ok(candidate)
    } else {
        Err(anyhow!("Unable to find exactly one valid encrypted key"))
    }
}

fn generate_rsa_keypair() -> Result<AwsKeyPair> {
    let private_key = PrivateDecryptingKey::generate(KeySize::Rsa2048)?;
    let public_key = private_key.public_key();

    Ok(AwsKeyPair {
        private_key,
        public_key,
    })
}

// TODO: [W-11907] Use global NSM context.
fn get_nsm_attestation_document(
    keypair: &AwsKeyPair,
    log_buffer: &mut LogBuffer,
) -> Result<AttestationDocument> {
    wsm_log!(log_buffer, "Requesting NSM attestation document");

    let nsm_fd = nsm_init();
    if nsm_fd < 0 {
        return Err(anyhow!("Failed to initialize NSM"));
    }

    let public_key_der = keypair.public_key.as_der()?;
    let public_key_der_bytes = public_key_der.deref().as_ref();

    let attestation_request = Request::Attestation {
        user_data: Some(ByteBuf::from(public_key_der_bytes)),
        nonce: None,
        public_key: Some(ByteBuf::from(public_key_der_bytes)),
    };

    let response = nsm_process_request(nsm_fd, attestation_request);

    nsm_exit(nsm_fd);

    match response {
        Attestation { document } => {
            wsm_log!(log_buffer, "NSM attestation document obtained successfully");
            Ok(AttestationDocument(document))
        }
        Error(err) => {
            wsm_log!(log_buffer, format!("NSM attestation failed: {:?}", err));
            Err(anyhow!("NSM attestation failed: {:?}", err))
        }
        _ => {
            wsm_log!(log_buffer, "Unexpected NSM response");
            Err(anyhow!("Unexpected NSM response"))
        }
    }
}

async fn call_kms_decrypt(
    request: &KmsRequest,
    attestation_doc: AttestationDocument,
    log_buffer: &mut LogBuffer,
) -> Result<DecryptOutput> {
    let credentials = Credentials::new(
        request.akid.clone(),
        request.skid.clone(),
        Some(request.session_token.clone()),
        None,
        "wsm-enclave",
    );

    let config = aws_config::defaults(BehaviorVersion::latest())
        .region(Region::new(request.region.clone()))
        // TODO: [W-11813] We want to bring our own http client which uses vsock instead.
        // .http_client(VsockHttpClient::new())
        .credentials_provider(credentials)
        .load()
        .await;

    // The attestation document comes as a signed COSE structure containing a CBOR-encoded
    // AttestationDocument as the payload. The AWS SDK will automatically base64 encode
    // the raw bytes when serializing the request to the KMS API.
    let recipient_info = RecipientInfo::builder()
        .key_encryption_algorithm(KeyEncryptionMechanism::RsaesOaepSha256)
        .attestation_document(Blob::new(attestation_doc.0))
        .build();

    wsm_log!(
        log_buffer,
        format!("Decrypting ciphertext with KMS for key {}", request.cmk_id)
    );

    // Decode the base64-encoded ciphertext
    let ciphertext_bytes = BASE64
        .decode(&request.ciphertext)
        .map_err(|e| anyhow!("Failed to decode base64 ciphertext: {}", e))?;

    Client::new(&config)
        .decrypt()
        .ciphertext_blob(Blob::new(ciphertext_bytes))
        .key_id(request.cmk_id.clone())
        .encryption_algorithm(aws_sdk_kms::types::EncryptionAlgorithmSpec::SymmetricDefault)
        .recipient(recipient_info)
        .send()
        .await
        .map_err(|e| anyhow!("KMS decrypt failed: {}", DisplayErrorContext(&e)))
}

struct AttestationDocument(Vec<u8>);

struct AwsKeyPair {
    private_key: PrivateDecryptingKey,
    public_key: PublicEncryptingKey,
}

/// A wrapper around a KMS secret.
pub struct KmsSecret(Vec<u8>);

impl AsRef<[u8]> for KmsSecret {
    fn as_ref(&self) -> &[u8] {
        &self.0
    }
}

impl From<KmsSecret> for Vec<u8> {
    fn from(secret: KmsSecret) -> Self {
        secret.0
    }
}
