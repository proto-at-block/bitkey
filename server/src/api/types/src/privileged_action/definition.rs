use std::collections::HashMap;

use time::Duration;

use crate::account::AccountType;

use super::shared::PrivilegedActionType;

#[derive(Debug, Clone)]
pub struct DelayAndNotifyDefinition {
    pub delay_duration: Duration,
    pub delay_configurable: bool,
    pub concurrency: bool,
}

#[derive(Debug, Clone)]
pub enum AuthorizationStrategyDefinition {
    HardwareProofOfPossession,
    DelayAndNotify(DelayAndNotifyDefinition),
}

#[derive(Debug, Clone)]
pub struct PrivilegedActionDefinition {
    pub privileged_action_type: PrivilegedActionType,
    pub authorization_strategies: HashMap<AccountType, AuthorizationStrategyDefinition>,
}
