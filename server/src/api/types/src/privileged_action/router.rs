use serde::{Deserialize, Serialize};
use time::serde::rfc3339;
use time::OffsetDateTime;
use utoipa::ToSchema;

use super::{
    repository::{AuthorizationStrategyRecord, PrivilegedActionInstanceRecord},
    shared::{PrivilegedActionInstanceId, PrivilegedActionType},
};

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct DelayAndNotify {
    #[serde(with = "rfc3339")]
    pub delay_end_time: OffsetDateTime,
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
#[serde(tag = "authorization_strategy_type")]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AuthorizationStrategy {
    HardwareProofOfPossession,
    DelayAndNotify(DelayAndNotify),
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct PrivilegedActionInstance {
    id: PrivilegedActionInstanceId,
    privileged_action_type: PrivilegedActionType,
    authorization_strategy: AuthorizationStrategy,
}

impl<T> From<PrivilegedActionInstanceRecord<T>> for PrivilegedActionInstance {
    fn from(value: PrivilegedActionInstanceRecord<T>) -> Self {
        Self {
            id: value.id,
            privileged_action_type: value.privileged_action_type,
            authorization_strategy: match value.authorization_strategy {
                AuthorizationStrategyRecord::HardwareProofOfPossession => {
                    AuthorizationStrategy::HardwareProofOfPossession
                }
                AuthorizationStrategyRecord::DelayAndNotify(d) => {
                    AuthorizationStrategy::DelayAndNotify(DelayAndNotify {
                        delay_end_time: d.delay_end_time,
                    })
                }
            },
        }
    }
}

// Generic router types; used by any API endpoint that is a privileged action
pub mod generic {
    use serde::{Deserialize, Serialize};
    use time::serde::rfc3339;
    use time::OffsetDateTime;
    use utoipa::ToSchema;

    use crate::privileged_action::{
        repository::{AuthorizationStrategyRecord, PrivilegedActionInstanceRecord},
        shared::{PrivilegedActionInstanceId, PrivilegedActionType},
    };

    #[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
    pub struct DelayAndNotifyInput {
        pub completion_token: String,
    }

    #[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
    #[serde(tag = "authorization_strategy_type")]
    #[serde(rename_all = "SCREAMING_SNAKE_CASE")]
    pub enum AuthorizationStrategyInput {
        HardwareProofOfPossession,
        DelayAndNotify(DelayAndNotifyInput),
    }

    #[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
    pub struct PrivilegedActionInstanceInput {
        pub id: PrivilegedActionInstanceId,
        pub authorization_strategy: AuthorizationStrategyInput,
    }

    #[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
    pub struct ContinuePrivilegedActionRequest {
        pub privileged_action_instance: PrivilegedActionInstanceInput,
    }

    #[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
    #[serde(untagged)]
    pub enum PrivilegedActionRequest<T> {
        // Since this is untagged, define Continue variant first, since it's guaranteed to have fields.
        // If the Initiate type has no fields and is defined first, a Continue request would be deserialized
        //   as an Initiate request since unknown fields are ignored.
        Continue(ContinuePrivilegedActionRequest),
        Initiate(T),
    }

    #[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
    pub struct DelayAndNotifyOutput {
        #[serde(with = "rfc3339")]
        pub delay_end_time: OffsetDateTime,
        pub cancellation_token: String,
        pub completion_token: String,
    }

    #[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
    #[serde(tag = "authorization_strategy_type")]
    #[serde(rename_all = "SCREAMING_SNAKE_CASE")]
    pub enum AuthorizationStrategyOutput {
        HardwareProofOfPossession,
        DelayAndNotify(DelayAndNotifyOutput),
    }

    #[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
    pub struct PrivilegedActionInstanceOutput {
        pub id: PrivilegedActionInstanceId,
        pub privileged_action_type: PrivilegedActionType,
        pub authorization_strategy: AuthorizationStrategyOutput,
    }

    impl<T> From<PrivilegedActionInstanceRecord<T>> for PrivilegedActionInstanceOutput {
        fn from(value: PrivilegedActionInstanceRecord<T>) -> Self {
            Self {
                id: value.id,
                privileged_action_type: value.privileged_action_type,
                authorization_strategy: match value.authorization_strategy {
                    AuthorizationStrategyRecord::HardwareProofOfPossession => {
                        AuthorizationStrategyOutput::HardwareProofOfPossession
                    }
                    AuthorizationStrategyRecord::DelayAndNotify(d) => {
                        AuthorizationStrategyOutput::DelayAndNotify(DelayAndNotifyOutput {
                            delay_end_time: d.delay_end_time,
                            cancellation_token: d.cancellation_token,
                            completion_token: d.completion_token,
                        })
                    }
                },
            }
        }
    }

    #[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
    pub struct PendingPrivilegedActionResponse {
        pub privileged_action_instance: PrivilegedActionInstanceOutput,
    }

    #[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
    #[serde(untagged)]
    pub enum PrivilegedActionResponse<T> {
        // Since this is untagged, define Pending variant first, since it's guaranteed to have fields.
        // If the Completed type has no fields and is defined first, a Pending response would be deserialized
        //   as an Completed response since unknown fields are ignored.
        Pending(PendingPrivilegedActionResponse),
        Completed(T),
    }

    impl<P, Q> From<PrivilegedActionInstanceRecord<P>> for PrivilegedActionResponse<Q> {
        fn from(value: PrivilegedActionInstanceRecord<P>) -> Self {
            PrivilegedActionResponse::Pending(PendingPrivilegedActionResponse {
                privileged_action_instance: value.into(),
            })
        }
    }
}
