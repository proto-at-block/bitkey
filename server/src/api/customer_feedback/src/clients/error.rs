use errors::ApiError;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum CustomerFeedbackClientError {
    #[error("Failed to create help desk ticket")]
    ZendeskCreateTicketError,
    #[error("Failed to deserialize response for creating help desk ticket: {0}")]
    ZendeskDeserializeResponseError(#[from] reqwest::Error),
}

impl From<CustomerFeedbackClientError> for ApiError {
    fn from(e: CustomerFeedbackClientError) -> Self {
        match e {
            CustomerFeedbackClientError::ZendeskCreateTicketError
            | CustomerFeedbackClientError::ZendeskDeserializeResponseError(_) => {
                ApiError::GenericInternalApplicationError(e.to_string())
            }
        }
    }
}

#[derive(Debug, Error)]
pub enum GetTicketFormError {
    #[error("Failed to get form structure")]
    ZendeskGetFormStructureError,

    #[error("Failed to deserialize form structure response: {0}")]
    ZendeskDeserializeResponseError(#[from] reqwest::Error),
}

impl From<GetTicketFormError> for ApiError {
    fn from(e: GetTicketFormError) -> Self {
        match e {
            GetTicketFormError::ZendeskGetFormStructureError
            | GetTicketFormError::ZendeskDeserializeResponseError(_) => {
                ApiError::GenericInternalApplicationError(e.to_string())
            }
        }
    }
}

#[derive(Debug, Error)]
pub enum UploadAttachmentError {
    #[error("Failed to upload attachment")]
    ZendeskUploadAttachmentError,

    #[error("Failed to deserialize attachment upload response: {0}")]
    ZendeskDeserializeResponseError(#[from] reqwest::Error),
}

impl From<UploadAttachmentError> for ApiError {
    fn from(e: UploadAttachmentError) -> Self {
        match e {
            UploadAttachmentError::ZendeskUploadAttachmentError
            | UploadAttachmentError::ZendeskDeserializeResponseError(_) => {
                ApiError::GenericInternalApplicationError(e.to_string())
            }
        }
    }
}
