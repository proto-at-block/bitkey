use std::collections::HashMap;
use time::OffsetDateTime;
use tracing::instrument;
use types::{
    account::identifiers::AccountId,
    encrypted_attachment::{identifiers::EncryptedAttachmentId, EncryptedAttachment},
};

use crate::service::error::CustomerFeedbackServiceError;

use super::Service;

impl Service {
    /// Create a new encrypted attachment with KMS-generated key pair
    #[instrument(skip(self))]
    pub async fn create_encrypted_attachment(
        &self,
        account_id: &AccountId,
    ) -> Result<EncryptedAttachment, CustomerFeedbackServiceError> {
        let id = EncryptedAttachmentId::gen()?;

        let mut encryption_context = HashMap::new();
        encryption_context.insert("encryptedAttachmentId".to_string(), id.to_string());

        let key_pair = self
            .kms_client
            .generate_encrypted_attachment_key_pair(encryption_context)
            .await?;

        let now = OffsetDateTime::now_utc();

        let encrypted_attachment = EncryptedAttachment {
            id,
            account_id: account_id.clone(),
            kms_key_id: key_pair.kms_key_id,
            private_key_ciphertext: key_pair.private_key_ciphertext,
            public_key: key_pair.public_key,
            sealed_attachment: None,
            created_at: now,
            updated_at: now,
        };

        self.encrypted_attachment_repo
            .persist(&encrypted_attachment)
            .await?;

        Ok(encrypted_attachment)
    }
}
