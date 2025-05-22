use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize)]
pub struct CreateRequestPayload {
    pub requester: RequesterPayload,
    pub subject: String,
    pub comment: RequestCommentPayload,
    pub ticket_form_id: i64,
    pub custom_fields: Vec<CustomFieldValuePayload>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct RequestCommentPayload {
    pub body: String,
    /// Tokens of uploaded attachments to be assigned to this
    pub uploads: Vec<String>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct RequesterPayload {
    pub name: String,
    pub email: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct CustomFieldValuePayload {
    pub id: i64,
    pub value: TicketFormEndUserConditionValuePayload,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct CreateRequestResponse {
    pub request: TicketRequestPayload,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct UploadAttachmentResponse {
    pub token: String,
}

#[allow(clippy::struct_excessive_bools)]
#[derive(Debug, Serialize, Deserialize)]
pub struct TicketRequestPayload {
    pub id: i64,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct TicketFormAndFieldsResponse {
    pub ticket_form: TicketFormPayload,
    pub ticket_fields: Vec<TicketFieldPayload>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct TicketFormPayload {
    pub id: i64,
    pub ticket_field_ids: Vec<i64>,
    pub end_user_conditions: Vec<TicketFormEndUserConditionPayload>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct TicketFormEndUserConditionPayload {
    pub parent_field_id: i64,
    pub value: TicketFormEndUserConditionValuePayload,
    pub child_fields: Vec<ChildFieldVisibilityPayload>,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(untagged)]
pub enum TicketFormEndUserConditionValuePayload {
    String(String),
    Bool(bool),
    MultiChoice(Vec<String>),
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ChildFieldVisibilityPayload {
    pub id: i64,
    pub is_required: bool,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct TicketFieldPayload {
    pub id: i64,
    #[serde(rename = "type")]
    pub field_type: TicketFieldTypePayload,
    pub required_in_portal: bool,
    pub visible_in_portal: bool,
    pub title_in_portal: String,
    pub custom_field_options: Option<Vec<TicketFieldOptionPayload>>,
}

#[derive(Debug, Serialize, Deserialize, Copy, Clone, PartialEq, Eq)]
pub enum TicketFieldTypePayload {
    /// Default custom field type when type is not specified
    #[serde(rename = "text")]
    Text,
    /// For multi-line text
    #[serde(rename = "textarea")]
    TextArea,
    /// To capture a boolean value. Allowed values are true or false
    #[serde(rename = "checkbox")]
    CheckBox,
    /// Example: 2021-04-16
    #[serde(rename = "date")]
    Date,
    /// String composed of numbers. May contain an optional decimal point
    #[serde(rename = "integer")]
    Integer,
    /// For numbers containing decimals
    #[serde(rename = "decimal")]
    Decimal,
    /// Matches the Regex pattern found in the custom field settings
    #[serde(rename = "regexp")]
    RegExp,
    /// A credit card number. Only the last 4 digits are retained
    #[serde(rename = "partialcreditcard")]
    PartialCreditCard,
    /// Enables users to choose multiple options from a dropdown menu
    #[serde(rename = "multiselect")]
    MultiSelect,
    /**
     * Single-select dropdown menu.
     * It contains one or more tag values belonging to the field's options.
     * Example: ( {"id": 21938362, "value": ["hd_3000", "hd_5555"]})
     */
    #[serde(rename = "tagger")]
    Tagger,
    /// A field to create a relationship (see lookup relationships) to another object such as a user, ticket, or organization
    #[serde(rename = "lookup")]
    Lookup,
    #[serde(rename = "subject")]
    Subject,
    #[serde(rename = "description")]
    Description,

    #[serde(alias = "group")]
    #[serde(alias = "custom_status")]
    #[serde(alias = "requester")]
    #[serde(alias = "follower")]
    #[serde(alias = "assignee")]
    #[serde(alias = "ccs")]
    #[serde(alias = "share")]
    #[serde(alias = "status")]
    #[serde(alias = "type")]
    #[serde(alias = "priority")]
    #[serde(alias = "tags")]
    UnusedSystemType,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct TicketFieldOptionPayload {
    pub id: i64,
    pub name: String,
    pub value: String,
}
