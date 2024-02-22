use account::entities::FullAccount;
use types::recovery::social::relationship::{RecoveryRelationship, RecoveryRelationshipId};

use super::{error::ServiceError, gen_code, gen_expiration, Service};

pub struct CreateRecoveryRelationshipInvitationInput<'a> {
    pub customer_account: &'a FullAccount,
    pub trusted_contact_alias: &'a str,
    pub customer_enrollment_pubkey: &'a str,
}

impl Service {
    /// Enables a customer account to generate an invitation which authorizes a friend
    /// or family member to become a Trusted Contact. This trusted contact can help to
    /// recover their account in the future. Each issued invitation has a distinct code
    /// attached to it, which will be used to accept the invitation.
    ///
    /// # Arguments
    ///
    /// * `customer_account` - The account that is issuing the invitation
    /// * `trusted_contact_alias` - Allows the Customer to give the Trusted Contact an alias for future reference
    /// * `customer_enrollment_pubkey` - The public key that will be used to create a secure channel
    pub async fn create_recovery_relationship_invitation(
        &self,
        input: CreateRecoveryRelationshipInvitationInput<'_>,
    ) -> Result<RecoveryRelationship, ServiceError> {
        let id = RecoveryRelationshipId::gen()?;

        let account_properties = &input.customer_account.common_fields.properties;
        let code = gen_code();
        let expires_at = gen_expiration(account_properties);

        let mut relationship = RecoveryRelationship::new_invitation(
            &id,
            &input.customer_account.id,
            input.trusted_contact_alias,
            input.customer_enrollment_pubkey,
            &code,
            &expires_at,
        );

        relationship = self
            .repository
            .persist_recovery_relationship(&relationship)
            .await?;

        Ok(relationship)
    }
}
