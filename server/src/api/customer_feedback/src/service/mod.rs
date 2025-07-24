use crate::clients::kms::{self, KmsWrapper};
use repository::encrypted_attachment::EncryptedAttachmentRepository;
use serde::Deserialize;

mod create_encrypted_attachment;
mod error;
mod upload_sealed_attachment;

#[derive(Clone, Deserialize)]
pub struct Config {
    #[serde(flatten)]
    pub kms_config: kms::Config,
}

/// Service for managing encrypted attachments in customer feedback
#[derive(Clone)]
pub struct Service {
    kms_client: KmsWrapper,
    encrypted_attachment_repo: EncryptedAttachmentRepository,
}

impl Service {
    /// Create a new encrypted attachment service
    pub fn new(
        kms_client: KmsWrapper,
        encrypted_attachment_repo: EncryptedAttachmentRepository,
    ) -> Self {
        Self {
            kms_client,
            encrypted_attachment_repo,
        }
    }
}
