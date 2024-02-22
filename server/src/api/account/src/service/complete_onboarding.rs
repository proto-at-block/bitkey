use crate::error::AccountError;

use super::{CompleteOnboardingInput, Service};

impl Service {
    pub async fn complete_onboarding(
        &self,
        input: CompleteOnboardingInput<'_>,
    ) -> Result<(), AccountError> {
        let account = self.repo.fetch(input.account_id).await?;
        let mut common_fields = account.get_common_fields().to_owned();

        if !common_fields.onboarding_complete {
            common_fields.onboarding_complete = true;
            let updated_account = account.update(common_fields)?;
            self.repo.persist(&updated_account).await?;
        }

        Ok(())
    }
}
