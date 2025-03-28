use std::env;

use axum::body::Bytes;
use reqwest::{
    header::{ACCEPT, AUTHORIZATION, CONTENT_TYPE},
    Client, Response,
};
use rust_embed::RustEmbed;
use serde::{Deserialize, Serialize};
use tracing::instrument;

use strum_macros::EnumString;

use super::entities::{
    CreateRequestPayload, TicketFormAndFieldsResponse, TicketRequestPayload,
    UploadAttachmentResponse,
};
use super::error::{CustomerFeedbackClientError, GetTicketFormError, UploadAttachmentError};
use crate::clients::entities::CreateRequestResponse;
use crate::clients::error::GetTicketFormError::ZendeskGetFormResponseError;

const ZENDESK_API_URL: &str = "https://bitkeysupport.zendesk.com/api/";
const ZENDESK_FORM_ID: &str = "24564153085204";

#[derive(Deserialize, EnumString, Clone)]
#[serde(rename_all = "lowercase", tag = "mode")]
pub enum ZendeskMode {
    Test,
    Environment,
}

impl ZendeskMode {
    pub fn to_client(&self) -> ZendeskClient {
        ZendeskClient::new(self.to_owned())
    }
}

#[derive(RustEmbed)]
#[folder = "$CARGO_MANIFEST_DIR/forms"]
#[include = "*.json"]
struct Asset;

#[derive(Clone)]
pub enum ZendeskClient {
    Real {
        endpoint: reqwest::Url,
        client: Client,
        authorization: String,
    },
    Test,
}

#[derive(Serialize)]
struct CreateZendeskRequestRequest {
    request: CreateRequestPayload,
}

#[derive(Debug, Deserialize)]
struct UploadAttachmentResponseEnvelope {
    upload: UploadAttachmentResponse,
}

impl ZendeskClient {
    pub fn new(mode: ZendeskMode) -> Self {
        match mode {
            ZendeskMode::Environment => Self::Real {
                endpoint: reqwest::Url::parse(ZENDESK_API_URL).unwrap(),
                client: Client::new(),
                authorization: env::var("ZENDESK_AUTHORIZATION")
                    .expect("ZENDESK_AUTHORIZATION environment variable not set"),
            },
            ZendeskMode::Test => Self::Test,
        }
    }

    #[instrument(skip(self))]
    pub async fn create_ticket(
        &self,
        request: CreateRequestPayload,
    ) -> Result<CreateRequestResponse, CustomerFeedbackClientError> {
        match self {
            Self::Real {
                endpoint, client, ..
            } => {
                let request = CreateZendeskRequestRequest { request };

                let response = client
                    .post(endpoint.join("v2/requests.json").unwrap())
                    .json(&request)
                    .send()
                    .await
                    .map_err(CustomerFeedbackClientError::ZendeskCreateTicketError)?;

                if !response.status().is_success() {
                    Err(
                        CustomerFeedbackClientError::ZendeskCreateTicketResponseError(
                            response.status(),
                            Self::extract_error_text(response).await,
                        ),
                    )
                } else {
                    let create_ticket_response: CreateRequestResponse = response.json().await?;
                    Ok(create_ticket_response)
                }
            }
            Self::Test => Ok(CreateRequestResponse {
                request: TicketRequestPayload { id: 123456 },
            }),
        }
    }

    pub async fn upload_attachment(
        &self,
        filename: String,
        mime_type: String,
        bytes: Bytes,
    ) -> Result<UploadAttachmentResponse, UploadAttachmentError> {
        match self {
            Self::Real {
                endpoint, client, ..
            } => {
                let response = client
                    .post(
                        endpoint
                            .join(format!("v2/uploads.json?filename={}", filename).as_str())
                            .unwrap(),
                    )
                    .header(CONTENT_TYPE, mime_type)
                    .body(bytes)
                    .send()
                    .await
                    .map_err(UploadAttachmentError::ZendeskUploadAttachmentError)?;

                if !response.status().is_success() {
                    Err(UploadAttachmentError::ZendeskUploadAttachmentResponseError(
                        response.status(),
                        Self::extract_error_text(response).await,
                    ))
                } else {
                    let upload_attachment_response: UploadAttachmentResponseEnvelope =
                        response.json().await?;
                    Ok(upload_attachment_response.upload)
                }
            }
            Self::Test => Ok(UploadAttachmentResponse {
                token: "123456".to_owned(),
            }),
        }
    }

    pub async fn load_form(&self) -> Result<TicketFormAndFieldsResponse, GetTicketFormError> {
        match self {
            Self::Real {
                endpoint,
                client,
                authorization,
            } => {
                let response = client
                    .get(
                        endpoint
                            .join(
                                format!("v2/ticket_forms/{ZENDESK_FORM_ID}?include=ticket_fields")
                                    .as_str(),
                            )
                            .unwrap(),
                    )
                    .header(ACCEPT, "application/json")
                    .header(AUTHORIZATION, format!("Basic {authorization}"))
                    .send()
                    .await
                    .map_err(GetTicketFormError::ZendeskGetFormStructureError)?;

                if !response.status().is_success() {
                    Err(ZendeskGetFormResponseError(
                        response.status(),
                        Self::extract_error_text(response).await,
                    ))
                } else {
                    let form_structure_response: TicketFormAndFieldsResponse =
                        response.json().await?;
                    Ok(form_structure_response)
                }
            }
            Self::Test => {
                let json = Asset::get("test.json")
                    .ok_or_else(|| {
                        GetTicketFormError::ZendeskDeserializeResponseError(
                            "Invalid test json".to_string(),
                        )
                    })?
                    .data;
                let test_form_structure: TicketFormAndFieldsResponse =
                    serde_json::from_slice(json.as_ref()).map_err(|e| {
                        GetTicketFormError::ZendeskDeserializeResponseError(e.to_string())
                    })?;
                Ok(test_form_structure)
            }
        }
    }

    async fn extract_error_text(response: Response) -> String {
        response
            .text()
            .await
            .unwrap_or_else(|_| "Unknown error".to_string())
    }
}
