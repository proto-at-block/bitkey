use tracing::instrument;
use types::recovery::social::relationship::RecoveryRelationship;
use types::recovery::trusted_contacts::TrustedContactRole;

use super::{error::ServiceError, Service};

/// The input for the `get_recovery_relationship_invitation_for_code` function
///
/// # Fields
///
/// * `code` - Unique alphanumeric code corresponding to an invitation
/// * `expected_role` - Optional role (Social Recovery Contact or Inheritance) that the user is expected to have
pub struct GetRecoveryRelationshipInvitationForCodeInput<'a> {
    pub code: &'a str,
    pub expected_role: Option<TrustedContactRole>,
}

impl Service {
    /// Fetches an invitation corresponding to the alphanumeric code.
    ///
    /// # Arguments
    ///
    /// * `input` - Contains the code corresponding to the recovery_relationship_id which is being fetched
    ///
    /// # Returns
    ///
    /// * The recovery relationship corresponding to the code
    #[instrument(skip(self, input))]
    pub async fn get_recovery_relationship_invitation_for_code(
        &self,
        input: GetRecoveryRelationshipInvitationForCodeInput<'_>,
    ) -> Result<RecoveryRelationship, ServiceError> {
        let relationship = self
            .repository
            .fetch_recovery_relationship_for_code(input.code)
            .await?;

        if let Some(expected_role) = input.expected_role {
            let roles = &relationship.common_fields().trusted_contact_info.roles;
            if !roles.contains(&expected_role) {
                return Err(ServiceError::InvitationRoleMismatch);
            }
        }

        Ok(relationship)
    }
}
