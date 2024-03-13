use types::consent::Consent;

use crate::{entities::Touchpoint, error::AccountError};

use super::{CompleteOnboardingInput, Service};

impl Service {
    pub async fn complete_onboarding(
        &self,
        input: CompleteOnboardingInput<'_>,
    ) -> Result<(), AccountError> {
        let account = self.account_repo.fetch(input.account_id).await?;
        let mut common_fields = account.get_common_fields().to_owned();

        if !common_fields.onboarding_complete {
            common_fields.onboarding_complete = true;
            let updated_account = account.update(common_fields)?;
            self.account_repo.persist(&updated_account).await?;
        }

        // Record onboarding TOS acceptance consent
        let email_address = account
            .get_common_fields()
            .touchpoints
            .iter()
            .find_map(|t| {
                if let Touchpoint::Email { email_address, .. } = t {
                    Some(email_address.to_owned())
                } else {
                    None
                }
            });
        let consent = &Consent::new_onboarding_tos_acceptance(input.account_id, email_address);
        self.consent_repo.persist(consent).await?;

        Ok(())
    }
}
