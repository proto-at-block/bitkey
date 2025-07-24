use std::collections::HashMap;

use aws_config::BehaviorVersion;
use aws_sdk_kms::error::ProvideErrorMetadata;
use aws_sdk_kms::types::DataKeyPairSpec;
use aws_sdk_kms::Client as KmsClient;
use serde::Deserialize;
use strum_macros::EnumString;
use thiserror::Error;
use tracing::{event, instrument, Level};

/// KMS client mode: Test or Environment
#[derive(Deserialize, EnumString, Clone, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum KmsMode {
    Test,
    Environment,
}

#[derive(Clone, Deserialize)]
pub struct Config {
    pub kms: KmsMode,
    pub encrypted_attachment_kms_key_id: String,
}

impl Config {
    pub async fn to_wrapper(self) -> KmsWrapper {
        KmsWrapper::new(self).await
    }
}

/// KMS wrapper for generating encrypted attachment key pairs
#[derive(Clone)]
pub enum KmsWrapper {
    Real {
        client: KmsClient,
        encrypted_attachment_kms_key_id: String,
    },
    Test,
}

impl KmsWrapper {
    /// Create a new KMS wrapper
    pub async fn new(config: Config) -> Self {
        match config.kms {
            KmsMode::Environment => {
                let sdk_config = aws_config::load_defaults(BehaviorVersion::latest()).await;
                let client = KmsClient::new(&sdk_config);
                Self::Real {
                    client,
                    encrypted_attachment_kms_key_id: config.encrypted_attachment_kms_key_id,
                }
            }
            KmsMode::Test => Self::Test,
        }
    }

    /// Generate an ECC NIST P-256 key pair for encrypted attachments
    #[instrument(skip(self))]
    pub async fn generate_encrypted_attachment_key_pair(
        &self,
        encryption_context: HashMap<String, String>,
    ) -> Result<GenerateEncryptedAttachmentKeyPairOutput, KmsError> {
        match self {
            Self::Real {
                client,
                encrypted_attachment_kms_key_id,
            } => {
                let mut request = client
                    .generate_data_key_pair_without_plaintext()
                    .key_id(encrypted_attachment_kms_key_id)
                    .key_pair_spec(DataKeyPairSpec::EccNistP256);

                for (key, value) in encryption_context {
                    request = request.encryption_context(key, value);
                }

                let response = request.send().await.map_err(|e| {
                    let service_err = e.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not generate data key pair from KMS due to error: {service_err:?} with message: {:?}",
                        service_err.message()
                    );
                    KmsError::GenerateDataKeyPair(service_err)
                })?;

                Ok(GenerateEncryptedAttachmentKeyPairOutput {
                    private_key_ciphertext: response
                        .private_key_ciphertext_blob
                        .map(|blob| blob.into_inner())
                        .expect("Private key should always be present"),
                    public_key: response
                        .public_key
                        .map(|key| key.into_inner())
                        .expect("Public key should always be present"),
                    kms_key_id: response
                        .key_id
                        .expect("KMS key ID should always be present"),
                })
            }
            Self::Test => Ok(GenerateEncryptedAttachmentKeyPairOutput {
                private_key_ciphertext: vec![0u8; 32],
                public_key: vec![0u8; 64],
                kms_key_id: "fake-key-id".to_string(),
            }),
        }
    }
}

/// Key pair generation output
#[derive(Debug)]
pub struct GenerateEncryptedAttachmentKeyPairOutput {
    /// Encrypted private key
    pub private_key_ciphertext: Vec<u8>,
    /// Plaintext public key
    pub public_key: Vec<u8>,
    /// KMS key ID used
    pub kms_key_id: String,
}

/// KMS operation errors
#[derive(Error, Debug)]
pub enum KmsError {
    #[error("Failed to generate data key pair from KMS")]
    GenerateDataKeyPair(#[from] aws_sdk_kms::operation::generate_data_key_pair_without_plaintext::GenerateDataKeyPairWithoutPlaintextError),
}
