use serde::{Deserialize, Serialize};
use time::OffsetDateTime;
use utoipa::ToSchema;

use super::shared::{PrivilegedActionInstanceId, PrivilegedActionType};

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct DelayAndNotifyInput {
    pub completion_token: String,
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
#[serde(tag = "authorization_strategy_type")]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AuthorizationStrategyInput {
    DelayAndNotify(DelayAndNotifyInput),
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct PrivilegedActionInstanceInput {
    pub id: PrivilegedActionInstanceId,
    pub authorization_strategy: AuthorizationStrategyInput,
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct CompletePrivilegedActionRequest {
    pub privileged_action_instance: PrivilegedActionInstanceInput,
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
#[serde(untagged)]
pub enum PrivilegedActionRequest<T> {
    Request(T),
    Complete(CompletePrivilegedActionRequest),
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct DelayAndNotifyOutput {
    pub delay_end_time: OffsetDateTime,
    pub cancellation_token: String,
    pub completion_token: String,
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
#[serde(tag = "authorization_strategy_type")]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AuthorizationStrategyOutput {
    DelayAndNotify(DelayAndNotifyOutput),
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct PrivilegedActionInstanceOutput {
    pub id: PrivilegedActionInstanceId,
    pub privileged_action_type: PrivilegedActionType,
    pub authorization_strategy: AuthorizationStrategyOutput,
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct PendingPrivilegedActionResponse {
    pub privileged_action_instance: PrivilegedActionInstanceOutput,
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
#[serde(untagged)]
pub enum PrivilegedActionResponse<T> {
    Pending(PendingPrivilegedActionResponse),
    Response(T),
}

impl<T> From<T> for PrivilegedActionResponse<T> {
    fn from(value: T) -> Self {
        PrivilegedActionResponse::Response(value)
    }
}
