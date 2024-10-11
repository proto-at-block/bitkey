use tracing::instrument;
use types::account::entities::CommonAccountFields;
use types::{
    account::{identifiers::AccountId, AccountType},
    privileged_action::{
        definition::{AuthorizationStrategyDefinition, PrivilegedActionDefinition},
        shared::PrivilegedActionDelayDuration,
    },
};

use super::{error::ServiceError, Service};

#[derive(Debug)]
pub struct ConfigurePrivilegedActionDelayDurationsInput<'a> {
    pub account_id: &'a AccountId,
    pub configured_delay_durations: Vec<PrivilegedActionDelayDuration>,
    pub dry_run: bool,
}

impl Service {
    #[instrument(skip(self))]
    pub async fn configure_privileged_action_delay_durations(
        &self,
        input: ConfigurePrivilegedActionDelayDurationsInput<'_>,
    ) -> Result<(), ServiceError> {
        let account = &self.account_repository.fetch(input.account_id).await?;
        let account_type: AccountType = account.into();

        let mut updated_delay_durations = Vec::new();

        for delay_duration in input.configured_delay_durations {
            let definition: PrivilegedActionDefinition =
                (&delay_duration.privileged_action_type).into();
            match definition.resolve(account_type.clone(), vec![]) {
                Some(resolved_definition) => match resolved_definition.authorization_strategy {
                    AuthorizationStrategyDefinition::DelayAndNotify(
                        delay_and_notify_definition,
                    ) => {
                        if !delay_and_notify_definition.delay_configurable {
                            return Err(ServiceError::CannotConfigureDelay(
                                delay_duration.privileged_action_type,
                                account_type,
                            ));
                        }

                        updated_delay_durations.push(PrivilegedActionDelayDuration {
                            privileged_action_type: delay_duration.privileged_action_type,
                            delay_duration_secs: delay_duration.delay_duration_secs,
                        });
                    }
                    AuthorizationStrategyDefinition::HardwareProofOfPossession(_) => {
                        return Err(ServiceError::CannotConfigureDelay(
                            delay_duration.privileged_action_type,
                            account_type,
                        ));
                    }
                },
                None => {
                    return Err(ServiceError::CannotConfigureDelay(
                        delay_duration.privileged_action_type,
                        account_type,
                    ));
                }
            }
        }

        let updated_common_fields = CommonAccountFields {
            configured_privileged_action_delay_durations: updated_delay_durations,
            ..account.get_common_fields().clone()
        };

        let updated_account = account.update(updated_common_fields)?;

        if !input.dry_run {
            self.account_repository.persist(&updated_account).await?;
        }

        Ok(())
    }
}
