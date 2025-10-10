use tracing::error;
use types::account::entities::{Account, Touchpoint};
use types::consent::Consent;

use super::{CompleteOnboardingInput, Service};
use crate::error::AccountError;
use crate::service::descriptor_backup_exists_for_private_keyset;

impl Service {
    pub async fn complete_onboarding(
        &self,
        input: CompleteOnboardingInput<'_>,
    ) -> Result<(), AccountError> {
        let account = self.account_repo.fetch(input.account_id).await?;
        let mut common_fields = account.get_common_fields().to_owned();

        if let Account::Full(full_account) = &account {
            let keyset_id = &full_account.active_keyset_id;
            if !descriptor_backup_exists_for_private_keyset(full_account, keyset_id) {
                error!("Private keyset missing descriptor backup for onboarding completion");
                return Err(AccountError::MissingDescriptorBackup);
            }
        }

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
