use tracing::instrument;
use types::{
    account::{identifiers::AccountId, AccountType},
    privileged_action::definition::{
        all_privileged_action_definitions, ResolvedPrivilegedActionDefinition,
    },
};

use super::{error::ServiceError, Service};

#[derive(Debug)]
pub struct GetPrivilegedActionDefinitionsInput<'a> {
    pub account_id: &'a AccountId,
}

impl Service {
    #[instrument(skip(self))]
    pub async fn get_privileged_action_definitions(
        &self,
        input: GetPrivilegedActionDefinitionsInput<'_>,
    ) -> Result<Vec<ResolvedPrivilegedActionDefinition>, ServiceError> {
        let account = &self.account_repository.fetch(input.account_id).await?;
        let account_type: AccountType = account.into();

        let configured_delay_durations = account
            .get_common_fields()
            .configured_privileged_action_delay_durations
            .clone();

        Ok(all_privileged_action_definitions()
            .iter()
            .filter_map(|definition| {
                definition.resolve(account_type.clone(), configured_delay_durations.clone())
            })
            .collect())
    }
}
