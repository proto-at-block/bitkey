use tracing::instrument;
use types::account::identifiers::AccountId;

use super::{error::ServiceError, Service};

pub struct ClearSocialChallengesInput<'a> {
    pub customer_account_id: &'a AccountId,
}

impl Service {
    #[instrument(skip(self, input))]
    pub async fn clear_social_challenges(
        &self,
        input: ClearSocialChallengesInput<'_>,
    ) -> Result<(), ServiceError> {
        self.repository
            .delete_challenges_for_customer(input.customer_account_id)
            .await?;

        Ok(())
    }
}
