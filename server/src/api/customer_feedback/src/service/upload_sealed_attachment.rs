use tracing::{error, instrument};
use types::{
    account::identifiers::AccountId,
    encrypted_attachment::{identifiers::EncryptedAttachmentId, EncryptedAttachment},
};

use crate::service::error::CustomerFeedbackServiceError;

use super::Service;

impl Service {
    /// Upload a sealed attachment to an existing attachment
    #[instrument(skip(self, sealed_attachment))]
    pub async fn upload_sealed_attachment(
        &self,
        id: &EncryptedAttachmentId,
        account_id: &AccountId,
        sealed_attachment: String,
    ) -> Result<EncryptedAttachment, CustomerFeedbackServiceError> {
        let mut encrypted_attachment = self.encrypted_attachment_repo.fetch_by_id(id).await?;

        if &encrypted_attachment.account_id != account_id {
            error!("Invalid account ID for attachment: {}", account_id);
            return Err(CustomerFeedbackServiceError::InvalidAccountId);
        }

        if let Some(existing_attachment) = encrypted_attachment.sealed_attachment.as_ref() {
            if existing_attachment != &sealed_attachment {
                error!("Attachment already uploaded: {}", id);
                return Err(CustomerFeedbackServiceError::AttachmentAlreadyUploaded);
            } else {
                return Ok(encrypted_attachment.clone());
            }
        }

        encrypted_attachment.sealed_attachment = Some(sealed_attachment);

        self.encrypted_attachment_repo
            .persist(&encrypted_attachment)
            .await?;

        Ok(encrypted_attachment)
    }
}
