use serde::{Deserialize, Serialize};
use strum_macros::{Display, EnumDiscriminants};
use time::serde::rfc3339;
use time::OffsetDateTime;
use utoipa::ToSchema;

use super::{
    definition::PrivilegedActionDefinition,
    repository::{AuthorizationStrategyRecord, PrivilegedActionInstanceRecord},
    shared::{PrivilegedActionInstanceId, PrivilegedActionType},
};

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct DelayAndNotify {
    #[serde(with = "rfc3339")]
    pub delay_start_time: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub delay_end_time: OffsetDateTime,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub cancellation_token: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub completion_token: Option<String>,
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema, EnumDiscriminants)]
#[strum_discriminants(
    derive(Display, Serialize, Deserialize),
    serde(rename_all = "SCREAMING_SNAKE_CASE"),
    strum(serialize_all = "SCREAMING_SNAKE_CASE")
)]
#[serde(
    rename_all = "SCREAMING_SNAKE_CASE",
    tag = "authorization_strategy_type"
)]
pub enum AuthorizationStrategy {
    HardwareProofOfPossession {},
    DelayAndNotify(DelayAndNotify),
    OutOfBand {},
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct PrivilegedActionInstance {
    id: PrivilegedActionInstanceId,
    privileged_action_type: PrivilegedActionType,
    authorization_strategy: AuthorizationStrategy,
}

impl<T> From<PrivilegedActionInstanceRecord<T>> for PrivilegedActionInstance {
    fn from(value: PrivilegedActionInstanceRecord<T>) -> Self {
        let expose_tokens_on_fetch =
            PrivilegedActionDefinition::from(&value.privileged_action_type)
                .expose_tokens_on_fetch();
        Self {
            id: value.id,
            privileged_action_type: value.privileged_action_type,
            authorization_strategy: match value.authorization_strategy {
                AuthorizationStrategyRecord::HardwareProofOfPossession => {
                    AuthorizationStrategy::HardwareProofOfPossession {}
                }
                AuthorizationStrategyRecord::DelayAndNotify(d) => {
                    AuthorizationStrategy::DelayAndNotify(DelayAndNotify {
                        delay_start_time: value.created_at,
                        delay_end_time: d.delay_end_time,
                        cancellation_token: if expose_tokens_on_fetch {
                            Some(d.cancellation_token)
                        } else {
                            None
                        },
                        completion_token: if expose_tokens_on_fetch {
                            Some(d.completion_token)
                        } else {
                            None
                        },
                    })
                }
                AuthorizationStrategyRecord::OutOfBand(_) => AuthorizationStrategy::OutOfBand {},
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
    pub struct OutOfBandInput {
        pub web_auth_token: String,
    }

    #[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
    #[serde(tag = "authorization_strategy_type")]
    #[serde(rename_all = "SCREAMING_SNAKE_CASE")]
    pub enum AuthorizationStrategyInput {
        HardwareProofOfPossession,
        DelayAndNotify(DelayAndNotifyInput),
        OutOfBand(OutOfBandInput),
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
        pub delay_start_time: OffsetDateTime,
        #[serde(with = "rfc3339")]
        pub delay_end_time: OffsetDateTime,
        pub cancellation_token: String,
        pub completion_token: String,
    }

    #[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
    #[serde(
        tag = "authorization_strategy_type",
        rename_all = "SCREAMING_SNAKE_CASE"
    )]
    pub enum AuthorizationStrategyOutput {
        HardwareProofOfPossession,
        DelayAndNotify(DelayAndNotifyOutput),
        OutOfBand,
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
                            delay_start_time: value.created_at,
                            delay_end_time: d.delay_end_time,
                            cancellation_token: d.cancellation_token,
                            completion_token: d.completion_token,
                        })
                    }
                    AuthorizationStrategyRecord::OutOfBand(_) => {
                        AuthorizationStrategyOutput::OutOfBand
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

#[cfg(test)]
mod tests {
    use rstest::rstest;
    use std::collections::HashMap;

    use super::*;
    use crate::account::identifiers::AccountId;
    use crate::privileged_action::repository::{DelayAndNotifyRecord, RecordStatus};

    #[rstest]
    #[case::reset_fingerprint(PrivilegedActionType::ResetFingerprint, true)]
    #[case::activate_touchpoint(PrivilegedActionType::ActivateTouchpoint, false)]
    #[case::configure_delays(PrivilegedActionType::ConfigurePrivilegedActionDelays, false)]
    #[tokio::test]
    async fn test_instance_record_to_router_output(
        #[case] privileged_action_type: PrivilegedActionType,
        #[case] expect_expose_tokens_on_fetch: bool,
    ) {
        let instance_record = PrivilegedActionInstanceRecord {
            id: PrivilegedActionInstanceId::gen().unwrap(),
            account_id: AccountId::gen().unwrap(),
            privileged_action_type,
            authorization_strategy: AuthorizationStrategyRecord::DelayAndNotify(
                DelayAndNotifyRecord {
                    status: RecordStatus::Pending,
                    delay_end_time: OffsetDateTime::now_utc(),
                    cancellation_token: "cancellation_token".to_string(),
                    completion_token: "completion_token".to_string(),
                },
            ),
            request: HashMap::<String, String>::new(),
            created_at: OffsetDateTime::now_utc(),
            updated_at: OffsetDateTime::now_utc(),
        };

        let router_output = PrivilegedActionInstance::from(instance_record);

        if let AuthorizationStrategy::DelayAndNotify(delay_and_notify) =
            router_output.authorization_strategy
        {
            assert_eq!(
                delay_and_notify.cancellation_token.is_some(),
                expect_expose_tokens_on_fetch,
                "cancellation_token presence mismatch"
            );
            assert_eq!(
                delay_and_notify.completion_token.is_some(),
                expect_expose_tokens_on_fetch,
                "completion_token presence mismatch"
            );
        } else {
            panic!("Expected DelayAndNotify");
        }
    }
}
