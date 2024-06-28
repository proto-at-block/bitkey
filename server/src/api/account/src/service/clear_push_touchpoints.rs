use crate::{
    entities::{Account, Touchpoint},
    error::AccountError,
};

use super::{ClearPushTouchpointsInput, Service};

impl Service {
    pub async fn clear_push_touchpoints(
        &self,
        input: ClearPushTouchpointsInput<'_>,
    ) -> Result<(), AccountError> {
        let mut account = self.account_repo.fetch(input.account_id).await?;
        let mut touchpoints: Vec<Touchpoint> = account.get_common_fields().touchpoints.clone();

        touchpoints.retain(|t| !matches!(t, Touchpoint::Push { .. }));

        match &mut account {
            Account::Full(full_account) => full_account.common_fields.touchpoints = touchpoints,
            Account::Lite(lite_account) => lite_account.common_fields.touchpoints = touchpoints,
            Account::Software(software_account) => {
                software_account.common_fields.touchpoints = touchpoints
            }
        };
        self.account_repo.persist(&account).await?;
        Ok(())
    }
}
