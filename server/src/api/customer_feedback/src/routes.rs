use std::collections::HashMap;

use account::service::Service as AccountService;
use axum::{
    body::Bytes,
    extract::Query,
    extract::State,
    http::{header::CONTENT_TYPE, HeaderMap},
    routing::get,
    routing::post,
    Json, Router,
};
use errors::ApiError;
use http_server::swagger::{SwaggerEndpoint, Url};
use serde::{Deserialize, Serialize};
use tracing::instrument;
use utoipa::{OpenApi, ToSchema};

use crate::clients::{
    entities::{
        ChildFieldVisibilityPayload, CreateRequestPayload, CustomFieldValuePayload,
        RequestCommentPayload, RequesterPayload, TicketFieldOptionPayload, TicketFieldTypePayload,
        TicketFormEndUserConditionPayload, TicketFormEndUserConditionValuePayload,
    },
    zendesk::{ZendeskClient, ZendeskMode},
};

#[derive(Clone, Deserialize)]
pub struct Config {
    pub zendesk: ZendeskMode,
    pub known_fields: HashMap<String, TicketFieldKnownType>,
}

#[derive(Clone, Deserialize, Debug)]
pub struct TicketFormKnownFields {
    pub fields: HashMap<i64, TicketFieldKnownType>,
}

impl From<HashMap<String, TicketFieldKnownType>> for TicketFormKnownFields {
    fn from(data: HashMap<String, TicketFieldKnownType>) -> Self {
        TicketFormKnownFields {
            fields: data
                .into_iter()
                .map(|(key, value)| (key.parse::<i64>().unwrap(), value))
                .collect(),
        }
    }
}

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub AccountService,
    pub ZendeskClient,
    pub TicketFormKnownFields,
);

impl RouteState {
    pub fn unauthed_router(&self) -> Router {
        Router::new().with_state(self.to_owned())
    }

    pub fn basic_validation_router(&self) -> Router {
        Router::new()
            .route("/api/customer_feedback", post(create_task))
            .route("/api/support/ticket-form", get(load_form))
            .route("/api/support/attachments", post(upload_attachment))
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Customer Feedback", "/docs/customer_feedback/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        create_task,
        load_form,
    ),
    components(
        schemas(
            CreateTaskRequest,
            CreateTaskResponse,
            SupportTicketAttachmentUpload,
            SupportTicketDebugData,
            TicketFieldValue,
        )
    ),
    tags(
        (name = "Customer Feedback", description = "Customer Feedback from the mobile app")
    )
)]
struct ApiDoc;

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct CreateTaskRequest {
    pub email: String,
    pub form_id: i64,
    pub subject: String,
    pub description: String,
    pub custom_field_values: HashMap<i64, TicketFieldValue>,
    pub attachments: Vec<SupportTicketAttachmentUpload>,
    pub debug_data: Option<SupportTicketDebugData>,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(tag = "type")]
pub enum SupportTicketAttachmentUpload {
    Success {
        token: String,
    },
    Failure {
        filename: String,
        mime_type: String,
        error: String,
    },
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct SupportTicketDebugData {
    pub app_version: String,
    pub app_installation_id: String,
    pub phone_make_and_model: String,
    pub system_name_and_version: String,
    pub hardware_firmware_version: String,
    pub hardware_serial_number: String,
    pub feature_flags: HashMap<String, String>,
}

impl CreateTaskRequest {
    fn requester(&self) -> RequesterPayload {
        RequesterPayload {
            name: self
                .email
                .split('@')
                .next()
                .unwrap_or("Anonymous Requester")
                .to_owned(),
            email: self.email.to_owned(),
        }
    }

    fn comment(&self) -> RequestCommentPayload {
        RequestCommentPayload {
            body: self.body(),
            uploads: self.attachment_tokens(),
        }
    }

    fn custom_fields(&self) -> Vec<CustomFieldValuePayload> {
        self.custom_field_values
            .iter()
            .map(|(id, value)| CustomFieldValuePayload {
                id: *id,
                value: value.to_owned().into(),
            })
            .collect()
    }

    fn body(&self) -> String {
        let mut description = format!(
            "## Description\n\n\
            {}\n\n",
            self.description,
        );

        if let Some(debug_data) = &self.debug_data {
            let SupportTicketDebugData {
                app_version,
                app_installation_id,
                phone_make_and_model,
                system_name_and_version,
                hardware_firmware_version,
                hardware_serial_number,
                feature_flags,
            } = debug_data;
            let debug_data_description = format!(
                "## Debug Data\n\n\
                - App version: {app_version}\n\
                - Installation ID: {app_installation_id}\n\
                - Phone make and model: {phone_make_and_model}\n\
                - OS version: {system_name_and_version}\n\
                - Firmware version: {hardware_firmware_version}\n\
                - Serial number: {hardware_serial_number}\n\
                - Feature flags: {feature_flags:?}\n"
            );
            description.push_str(&debug_data_description);
        }

        let attachment_upload_errors: Vec<String> = self
            .attachments
            .iter()
            .filter_map(|attachment| match attachment {
                SupportTicketAttachmentUpload::Success { .. } => None,
                SupportTicketAttachmentUpload::Failure {
                    filename,
                    mime_type,
                    error,
                } => Some(format!(
                    "### {filename}\n\
                    - MIME: {mime_type}\n\
                    - Error: {error}\n\n"
                )),
            })
            .collect();

        if !attachment_upload_errors.is_empty() {
            description.push_str("## Failed attachments\n\n");
            for error_description in attachment_upload_errors {
                description.push_str(&error_description);
            }
        }

        description
    }

    fn attachment_tokens(&self) -> Vec<String> {
        self.attachments
            .iter()
            .filter_map(|attachment| match attachment {
                SupportTicketAttachmentUpload::Success { token } => Some(token.to_owned()),
                SupportTicketAttachmentUpload::Failure {
                    filename: _,
                    mime_type: _,
                    error: _,
                } => None,
            })
            .collect()
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct CreateTaskResponse {
    pub request_id: i64,
}

#[utoipa::path(
    post,
    path = "/api/customer_feedback",
    request_body = CreateTaskRequest,
    responses(
        (status = 200, description = "Customer Feedback ticket was successfully created", body=CreateTaskResponse),
        (status = 500, description = "Ticket couldn't be created"),

    ),
)]
#[instrument(err, skip(zendesk_client,))]
pub async fn create_task(
    State(zendesk_client): State<ZendeskClient>,
    Json(request): Json<CreateTaskRequest>,
) -> Result<Json<CreateTaskResponse>, ApiError> {
    let response = zendesk_client
        .create_ticket(CreateRequestPayload {
            requester: request.requester(),
            subject: request.subject.to_owned(),
            comment: request.comment(),
            ticket_form_id: request.form_id,
            custom_fields: request.custom_fields(),
        })
        .await?;
    Ok(Json(CreateTaskResponse {
        request_id: response.request.id,
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct TicketFormResponse {
    pub id: i64,
    pub ticket_fields: Vec<TicketField>,
    pub conditions: Vec<TicketFormCondition>,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct TicketField {
    pub id: i64,
    #[serde(rename = "type")]
    pub field_type: TicketFieldType,
    pub known_type: Option<TicketFieldKnownType>,
    pub required: bool,
    pub title: String,
    pub options: Option<Vec<TicketFieldOption>>,
}

#[derive(Debug, Serialize, Deserialize, ToSchema, Clone, Copy)]
pub enum TicketFieldKnownType {
    Subject,
    Description,
    Country,
    AppVersion,
    AppInstallationID,
    PhoneMakeAndModel,
    SystemNameAndVersion,
    HardwareSerialNumber,
    HardwareFirmwareVersion,
}

#[derive(Debug, Serialize, Deserialize, Copy, Clone, PartialEq, Eq)]
pub enum TicketFieldType {
    /// Default custom field type when type is not specified
    Text,
    /// For multi-line text
    TextArea,
    /// To capture a boolean value. Allowed values are true or false
    CheckBox,
    /// Example: 2021-04-16
    Date,
    /**
     * Single-select dropdown menu.
     * It contains one or more tag values belonging to the field's options.
     * Example: ( {"id": 21938362, "value": ["hd_3000", "hd_5555"]})
     */
    Picker,
    /// Enables users to choose multiple options from a dropdown menu
    MultiSelect,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct TicketFieldOption {
    pub id: i64,
    pub name: String,
    pub value: String,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct TicketFormCondition {
    pub parent_field_id: i64,
    pub value: TicketFieldValue,
    pub child_fields: Vec<TicketFormChildFieldVisibility>,
}

#[derive(Debug, Serialize, Deserialize, ToSchema, Clone)]
#[serde(tag = "type")]
pub enum TicketFieldValue {
    String { value: String },
    Bool { value: bool },
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct TicketFormChildFieldVisibility {
    pub id: i64,
    pub required: bool,
}

impl From<TicketFieldTypePayload> for Option<TicketFieldType> {
    fn from(value: TicketFieldTypePayload) -> Self {
        Some(match value {
            TicketFieldTypePayload::Subject => TicketFieldType::Text,
            TicketFieldTypePayload::Description => TicketFieldType::TextArea,
            TicketFieldTypePayload::Text => TicketFieldType::Text,
            TicketFieldTypePayload::TextArea => TicketFieldType::TextArea,
            TicketFieldTypePayload::CheckBox => TicketFieldType::CheckBox,
            TicketFieldTypePayload::Date => TicketFieldType::Date,
            TicketFieldTypePayload::MultiSelect => TicketFieldType::MultiSelect,
            TicketFieldTypePayload::Tagger => TicketFieldType::Picker,
            _ => return None,
        })
    }
}

impl From<TicketFieldTypePayload> for Option<TicketFieldKnownType> {
    fn from(value: TicketFieldTypePayload) -> Self {
        Some(match value {
            TicketFieldTypePayload::Subject => TicketFieldKnownType::Subject,
            TicketFieldTypePayload::Description => TicketFieldKnownType::Description,
            _ => return None,
        })
    }
}

impl From<TicketFieldOptionPayload> for TicketFieldOption {
    fn from(value: TicketFieldOptionPayload) -> Self {
        TicketFieldOption {
            id: value.id,
            name: value.name,
            value: value.value,
        }
    }
}

impl From<TicketFormEndUserConditionValuePayload> for TicketFieldValue {
    fn from(value: TicketFormEndUserConditionValuePayload) -> Self {
        match value {
            TicketFormEndUserConditionValuePayload::String(value) => {
                TicketFieldValue::String { value }
            }
            TicketFormEndUserConditionValuePayload::Bool(value) => TicketFieldValue::Bool { value },
        }
    }
}

impl From<TicketFieldValue> for TicketFormEndUserConditionValuePayload {
    fn from(value: TicketFieldValue) -> Self {
        match value {
            TicketFieldValue::String { value } => {
                TicketFormEndUserConditionValuePayload::String(value)
            }
            TicketFieldValue::Bool { value } => TicketFormEndUserConditionValuePayload::Bool(value),
        }
    }
}

impl From<ChildFieldVisibilityPayload> for TicketFormChildFieldVisibility {
    fn from(value: ChildFieldVisibilityPayload) -> Self {
        TicketFormChildFieldVisibility {
            id: value.id,
            required: value.is_required,
        }
    }
}

impl From<TicketFormEndUserConditionPayload> for TicketFormCondition {
    fn from(value: TicketFormEndUserConditionPayload) -> Self {
        TicketFormCondition {
            parent_field_id: value.parent_field_id,
            value: value.value.into(),
            child_fields: value
                .child_fields
                .into_iter()
                .map(|child| child.into())
                .collect(),
        }
    }
}

#[utoipa::path(
    get,
    path = "/api/support/ticket-form",
    responses(
        (status = 200, description = "Customer Feedback form structure", body=TicketFormAndFieldsResponse),
        (status = 500, description = "Couldn't fetch feedback form structure")
    )
)]
#[instrument(err, skip(zendesk_client,))]
pub async fn load_form(
    State(zendesk_client): State<ZendeskClient>,
    State(known_fields): State<TicketFormKnownFields>,
) -> Result<Json<TicketFormResponse>, ApiError> {
    let response = zendesk_client.load_form().await?;

    let mut fields_by_id = HashMap::new();
    for field in response.ticket_fields {
        fields_by_id.insert(field.id, field);
    }

    let mut fields = Vec::new();
    for field_id in response.ticket_form.ticket_field_ids {
        // We remove the field_id from the map as each field can only be in the form once
        let Some(field_dto) = fields_by_id.remove(&field_id) else {
            continue;
        };
        if !field_dto.visible_in_portal {
            continue;
        }

        let Some(field_type) = field_dto.field_type.into() else {
            continue;
        };
        let known_type = known_fields
            .fields
            .get(&field_id)
            .copied()
            .or_else(|| field_dto.field_type.into());

        let field = TicketField {
            id: field_id,
            field_type,
            known_type,
            required: field_dto.required_in_portal,
            title: field_dto.title_in_portal,
            options: field_dto.custom_field_options.map(|custom_field_options| {
                custom_field_options
                    .into_iter()
                    .map(|option| option.into())
                    .collect()
            }),
        };
        fields.push(field);
    }
    Ok(Json(TicketFormResponse {
        id: response.ticket_form.id,
        ticket_fields: fields,
        conditions: response
            .ticket_form
            .end_user_conditions
            .into_iter()
            .map(|condition| condition.into())
            .collect(),
    }))
}

#[derive(Debug, Serialize, Deserialize)]
pub struct TicketAttachmentResponse {
    pub token: String,
}

#[derive(Debug, Deserialize)]
struct TicketAttachmentQuery {
    filename: String,
}

#[utoipa::path(
    get,
    path = "/api/support/attachments",
    responses(
        (status = 200, description = "Ticket attachment uploaded successfully", body=TicketAttachmentResponse),
        (status = 500, description = "Ticket attachment upload failed")
    )
)]
#[instrument(err, skip(zendesk_client,))]
pub async fn upload_attachment(
    State(zendesk_client): State<ZendeskClient>,
    headers: HeaderMap,
    Query(query): Query<TicketAttachmentQuery>,
    body: Bytes,
) -> Result<Json<TicketAttachmentResponse>, ApiError> {
    let content_type = headers
        .get(CONTENT_TYPE)
        .ok_or(ApiError::GenericBadRequest(
            "Content-Type header wasn't provided".to_owned(),
        ))?;
    let mime_type = content_type
        .to_str()
        .map_err(|_| ApiError::GenericBadRequest("Content-Type header wasn't ASCII".to_owned()))?;

    let response = zendesk_client
        .upload_attachment(query.filename, mime_type.to_owned(), body)
        .await?;
    Ok(Json(TicketAttachmentResponse {
        token: response.token,
    }))
}
