use types::recovery::social::relationship::RecoveryRelationship;

use super::{disambiguate_code_input, error::ServiceError, Service};

pub struct GetRecoveryRelationshipInvitationForCodeInput<'a> {
    pub code: &'a str,
}

impl Service {
    /// Fetches an invitation corresponding to the alphanumeric code.
    ///
    /// # Arguments
    ///
    /// * `code` - Unique alphanumeric code corresponding to an invitation
    pub async fn get_recovery_relationship_invitation_for_code(
        &self,
        input: GetRecoveryRelationshipInvitationForCodeInput<'_>,
    ) -> Result<RecoveryRelationship, ServiceError> {
        let relationship = self
            .repository
            .fetch_recovery_relationship_for_code(&disambiguate_code_input(input.code))
            .await?;

        Ok(relationship)
    }
}
