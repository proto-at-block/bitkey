use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use strum::IntoEnumIterator;
use utoipa::ToSchema;

use crate::account::AccountType;

use super::shared::{PrivilegedActionDelayDuration, PrivilegedActionType};

const SEVENTY_TWO_HOURS_SECS: usize = 259_200;

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct HardwareProofOfPossessionDefinition {
    pub skip_during_onboarding: bool,
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct DelayAndNotifyDefinition {
    pub delay_duration_secs: usize,
    pub delay_configurable: bool,
    pub concurrency: bool,
    pub skip_during_onboarding: bool,
    pub notification_summary: String,
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
#[serde(tag = "authorization_strategy_type")]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AuthorizationStrategyDefinition {
    HardwareProofOfPossession(HardwareProofOfPossessionDefinition),
    DelayAndNotify(DelayAndNotifyDefinition),
}

#[derive(Debug, Clone)]
pub struct PrivilegedActionDefinition {
    pub privileged_action_type: PrivilegedActionType,
    pub authorization_strategies: HashMap<AccountType, AuthorizationStrategyDefinition>,
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct ResolvedPrivilegedActionDefinition {
    pub privileged_action_type: PrivilegedActionType,
    pub authorization_strategy: AuthorizationStrategyDefinition,
}

impl PrivilegedActionDefinition {
    pub fn resolve(
        &self,
        account_type: AccountType,
        configured_delay_durations: Vec<PrivilegedActionDelayDuration>,
    ) -> Option<ResolvedPrivilegedActionDefinition> {
        self.authorization_strategies
            .get(&account_type)
            .cloned()
            .map(|strategy| match strategy {
                AuthorizationStrategyDefinition::DelayAndNotify(mut definition) => {
                    if let (true, Some(configured_delay_duration_secs)) = (
                        definition.delay_configurable,
                        configured_delay_durations
                            .iter()
                            .find(|c| c.privileged_action_type == self.privileged_action_type)
                            .map(|c| c.delay_duration_secs),
                    ) {
                        definition.delay_duration_secs = configured_delay_duration_secs;
                    }
                    AuthorizationStrategyDefinition::DelayAndNotify(definition)
                }
                _ => strategy,
            })
            .map(|strategy| ResolvedPrivilegedActionDefinition {
                privileged_action_type: self.privileged_action_type.clone(),
                authorization_strategy: strategy,
            })
    }
}

impl From<&PrivilegedActionType> for PrivilegedActionDefinition {
    fn from(value: &PrivilegedActionType) -> Self {
        match value {
            PrivilegedActionType::ConfigurePrivilegedActionDelays => PrivilegedActionDefinition {
                privileged_action_type: value.clone(),
                authorization_strategies: HashMap::from([(
                    AccountType::Software,
                    AuthorizationStrategyDefinition::DelayAndNotify(DelayAndNotifyDefinition {
                        delay_duration_secs: SEVENTY_TWO_HOURS_SECS,
                        delay_configurable: false,
                        concurrency: false,
                        skip_during_onboarding: false,
                        notification_summary: "update your security delay duration(s)".to_string(),
                    }),
                )]),
            },
            PrivilegedActionType::ActivateTouchpoint => PrivilegedActionDefinition {
                privileged_action_type: value.clone(),
                authorization_strategies: HashMap::from([
                    (
                        AccountType::Full,
                        AuthorizationStrategyDefinition::HardwareProofOfPossession(
                            HardwareProofOfPossessionDefinition {
                                skip_during_onboarding: true,
                            },
                        ),
                    ),
                    (
                        AccountType::Software,
                        AuthorizationStrategyDefinition::DelayAndNotify(DelayAndNotifyDefinition {
                            delay_duration_secs: SEVENTY_TWO_HOURS_SECS,
                            delay_configurable: true,
                            concurrency: false,
                            skip_during_onboarding: true,
                            notification_summary: "update your contact information".to_string(),
                        }),
                    ),
                ]),
            },
        }
    }
}

impl From<PrivilegedActionType> for PrivilegedActionDefinition {
    fn from(value: PrivilegedActionType) -> Self {
        (&value).into()
    }
}

pub fn all_privileged_action_definitions() -> Vec<PrivilegedActionDefinition> {
    PrivilegedActionType::iter().map(|t| t.into()).collect()
}