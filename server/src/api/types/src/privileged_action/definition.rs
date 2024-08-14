use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use utoipa::ToSchema;

use crate::account::AccountType;

use super::shared::{PrivilegedActionDelayDuration, PrivilegedActionType};

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct HardwareProofOfPossessionDefinition {
    pub skip_during_onboarding: bool,
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct DelayAndNotifyDefinition {
    pub delay_duration_secs: usize,
    pub delay_configurable: bool,
    pub concurrency: bool,
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
