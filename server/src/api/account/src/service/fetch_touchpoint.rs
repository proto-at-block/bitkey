use tracing::{event, Level};
use types::account::identifiers::TouchpointId;

use crate::{
    entities::{CommonAccountFields, Touchpoint},
    error::AccountError,
};

use super::{
    FetchOrCreateEmailTouchpointInput, FetchOrCreatePhoneTouchpointInput, FetchTouchpointByIdInput,
    Service,
};

impl Service {
    pub async fn fetch_touchpoint_by_id(
        &self,
        input: FetchTouchpointByIdInput<'_>,
    ) -> Result<Touchpoint, AccountError> {
        let account = self.account_repo.fetch(input.account_id).await?;

        if let Some(touchpoint) = account.get_touchpoint_by_id(input.touchpoint_id) {
            Ok(touchpoint.to_owned())
        } else {
            event!(Level::ERROR, "Touchpoint not found",);
            Err(AccountError::TouchpointNotFound)
        }
    }

    pub async fn fetch_or_create_email_touchpoint(
        &self,
        input: FetchOrCreateEmailTouchpointInput<'_>,
    ) -> Result<Touchpoint, AccountError> {
        let account = self.account_repo.fetch(input.account_id).await?;

        if let Some(existing_touchpoint) =
            account.get_touchpoint_by_email_address(input.email_address.to_owned())
        {
            return Ok(existing_touchpoint.to_owned());
        }

        let common_fields = account.get_common_fields().to_owned();
        let mut touchpoints = account.get_common_fields().touchpoints.clone();

        // Purge inactive email touchpoints
        touchpoints.retain(|t| !matches!(t, Touchpoint::Email { active: false, .. }));

        let new_touchpoint =
            Touchpoint::new_email(TouchpointId::gen()?, input.email_address, false);

        // Add new touchpoint
        touchpoints.push(new_touchpoint.to_owned());

        let updated_account = account.update(CommonAccountFields {
            touchpoints,
            ..common_fields
        })?;
        self.account_repo.persist(&updated_account).await?;

        Ok(new_touchpoint)
    }

    pub async fn fetch_or_create_phone_touchpoint(
        &self,
        input: FetchOrCreatePhoneTouchpointInput<'_>,
    ) -> Result<Touchpoint, AccountError> {
        let account = self.account_repo.fetch(input.account_id).await?;

        if let Some(existing_touchpoint) =
            account.get_touchpoint_by_phone_number(input.phone_number.to_owned())
        {
            if matches!(existing_touchpoint, Touchpoint::Phone{ country_code, .. }
                if *country_code == input.country_code)
            {
                return Ok(existing_touchpoint.to_owned());
            }
        }

        // Purge inactive phone touchpoints
        let common = account.get_common_fields().to_owned();
        let mut touchpoints = common.touchpoints;
        touchpoints.retain(|t| !matches!(t, Touchpoint::Phone { active: false, .. }));

        let new_touchpoint = Touchpoint::new_phone(
            TouchpointId::gen()?,
            input.phone_number,
            input.country_code,
            false,
        );

        // Add new touchpoint
        touchpoints.push(new_touchpoint.to_owned());
        let updated_account = account.update(CommonAccountFields {
            touchpoints,
            ..common
        })?;
        self.account_repo.persist(&updated_account).await?;

        Ok(new_touchpoint)
    }
}
