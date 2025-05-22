use tracing::instrument;
use types::account::entities::FullAccount;
use types::recovery::social::relationship::{RecoveryRelationship, RecoveryRelationshipId};

use super::{error::ServiceError, Service};
use super::{gen_code, gen_expiration};

/// The input for the `reissue_recovery_relationship_invitation` function
///
/// # Fields
///
/// * `customer_account` - The account for the customer that is trying to reissue the invitation
/// * `recovery_relationship_id` - The ID of the invitation that requires reissuing.
pub struct ReissueRecoveryRelationshipInvitationInput<'a> {
    pub customer_account: &'a FullAccount,
    pub recovery_relationship_id: &'a RecoveryRelationshipId,
}

impl Service {
    /// This function allows customers to reissue invitations to Recovery Contacts. This is useful
    /// if the original invitation has expired.
    ///
    /// # Arguments
    ///
    /// * `input` - Contains the customer account and the recovery relationship id
    ///
    /// # Returns
    ///
    /// * The recovery relationship corresponding to the code
    #[instrument(skip(self, input))]
    pub async fn reissue_recovery_relationship_invitation(
        &self,
        input: ReissueRecoveryRelationshipInvitationInput<'_>,
    ) -> Result<RecoveryRelationship, ServiceError> {
        let prev_relationship = self
            .repository
            .fetch_recovery_relationship(input.recovery_relationship_id)
            .await?;

        let RecoveryRelationship::Invitation(invitation) = &prev_relationship else {
            return Err(ServiceError::RelationshipAlreadyEstablished);
        };

        let prev_common_fields = &invitation.common_fields;

        if prev_common_fields.customer_account_id != input.customer_account.id {
            return Err(ServiceError::UnauthorizedRelationshipUpdate);
        }

        let account_properties = &input.customer_account.common_fields.properties;
        let (code, code_bit_length) = gen_code();
        let role = invitation
            .common_fields
            .trusted_contact_info
            .roles
            .first()
            .ok_or(ServiceError::MissingTrustedContactRoles)?;
        let expires_at = gen_expiration(role, account_properties);

        let mut relationship = RecoveryRelationship::Invitation(invitation.reissue(
            &code,
            code_bit_length,
            &expires_at,
        ));

        relationship = self
            .repository
            .persist_recovery_relationship(&relationship)
            .await?;

        Ok(relationship)
    }
}
