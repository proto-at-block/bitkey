use tracing::{event, Level};
use types::account::entities::{Account, Touchpoint};

use super::{ActivateTouchpointForAccountInput, Service};
use crate::error::AccountError;

impl Service {
    pub async fn activate_touchpoint_for_account(
        &self,
        input: ActivateTouchpointForAccountInput<'_>,
    ) -> Result<Touchpoint, AccountError> {
        let mut account = self.account_repo.fetch(input.account_id).await?;

        let new_touchpoint = if let Some(existing_touchpoint) =
            account.get_touchpoint_by_id(input.touchpoint_id.to_owned())
        {
            if existing_touchpoint.is_active() {
                event!(Level::ERROR, "Touchpoint already active",);
                return Err(AccountError::TouchpointAlreadyActive);
            }

            match existing_touchpoint {
                Touchpoint::Email {
                    id, email_address, ..
                } => Ok(Touchpoint::new_email(
                    id.to_owned(),
                    email_address.to_owned(),
                    true,
                )),
                Touchpoint::Phone {
                    id,
                    phone_number,
                    country_code,
                    ..
                } => Ok(Touchpoint::new_phone(
                    id.to_owned(),
                    phone_number.to_owned(),
                    country_code.to_owned(),
                    true,
                )),
                _ => Err(AccountError::Unexpected),
            }
        } else {
            event!(Level::ERROR, "Touchpoint not found",);
            Err(AccountError::TouchpointNotFound)
        }?;

        let mut touchpoints = account.get_common_fields().touchpoints.clone();

        // Purge all touchpoints of the same type
        match new_touchpoint.to_owned() {
            Touchpoint::Email { .. } => {
                touchpoints.retain(|t| !matches!(t, Touchpoint::Email { .. }));
            }
            Touchpoint::Phone { .. } => {
                touchpoints.retain(|t| !matches!(t, Touchpoint::Phone { .. }));
            }
            _ => {}
        }

        // Add new touchpoint
        touchpoints.push(new_touchpoint.to_owned());

        match &mut account {
            Account::Full(full_account) => full_account.common_fields.touchpoints = touchpoints,
            Account::Lite(lite_account) => lite_account.common_fields.touchpoints = touchpoints,
            Account::Software(software_account) => {
                software_account.common_fields.touchpoints = touchpoints
            }
        };

        if !input.dry_run {
            self.account_repo.persist(&account).await?;
        }

        Ok(new_touchpoint)
    }
}
