use std::collections::HashMap;

use once_cell::sync::Lazy;
use types::{
    account::AccountType,
    privileged_action::{
        definition::{
            AuthorizationStrategyDefinition, DelayAndNotifyDefinition,
            HardwareProofOfPossessionDefinition, PrivilegedActionDefinition,
        },
        shared::PrivilegedActionType,
    },
};

const SEVENTY_TWO_HOURS_SECS: usize = 259_200;

pub static CONFIGURE_PRIVILEGED_ACTION_DELAYS: Lazy<PrivilegedActionDefinition> =
    Lazy::new(|| PrivilegedActionDefinition {
        privileged_action_type: PrivilegedActionType::ConfigurePrivilegedActionDelays,
        authorization_strategies: HashMap::from([(
            AccountType::Software,
            AuthorizationStrategyDefinition::DelayAndNotify(DelayAndNotifyDefinition {
                delay_duration_secs: SEVENTY_TWO_HOURS_SECS,
                delay_configurable: false,
                concurrency: false,
            }),
        )]),
    });

pub static ACTIVATE_TOUCHPOINT: Lazy<PrivilegedActionDefinition> =
    Lazy::new(|| PrivilegedActionDefinition {
        privileged_action_type: PrivilegedActionType::ActivateTouchpoint,
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
                }),
            ),
        ]),
    });

pub static ALL_PRIVILEGED_ACTIONS: Lazy<Vec<&'static PrivilegedActionDefinition>> =
    Lazy::new(|| vec![&CONFIGURE_PRIVILEGED_ACTION_DELAYS, &ACTIVATE_TOUCHPOINT]);
