use errors::ApiError;
use reqwest::StatusCode;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum CustomerFeedbackClientError {
    #[error("Error: HTTP {0} - {1}")]
    ZendeskCreateTicketResponseError(StatusCode, String),
    #[error("Failed to create help desk ticket")]
    ZendeskCreateTicketError(#[from] reqwest::Error),
}

impl From<CustomerFeedbackClientError> for ApiError {
    fn from(e: CustomerFeedbackClientError) -> Self {
        ApiError::GenericInternalApplicationError(e.to_string())
    }
}

#[derive(Debug, Error)]
pub enum GetTicketFormError {
    #[error("Error: HTTP {0} - {1}")]
    GetFormResponse(StatusCode, String),
    #[error("Failed to get form structure: {0}")]
    GetFormStructure(#[from] reqwest::Error),
    #[error("Failed to deserialize form structure response: {0}")]
    DeserializeResponse(String),
}

impl From<GetTicketFormError> for ApiError {
    fn from(e: GetTicketFormError) -> Self {
        ApiError::GenericInternalApplicationError(e.to_string())
    }
}

#[derive(Debug, Error)]
pub enum UploadAttachmentError {
    #[error("Error: HTTP {0} - {1}")]
    ZendeskUploadAttachmentResponseError(StatusCode, String),
    #[error("Failed to upload attachment")]
    ZendeskUploadAttachmentError(#[from] reqwest::Error),
}

impl From<UploadAttachmentError> for ApiError {
    fn from(e: UploadAttachmentError) -> Self {
        ApiError::GenericInternalApplicationError(e.to_string())
    }
}
