use account::entities::FullAccount;
use tracing::instrument;
use types::recovery::social::relationship::{RecoveryRelationship, RecoveryRelationshipId};

use super::{error::ServiceError, gen_code, gen_expiration, Service};

const MAX_TRUSTED_CONTACTS: usize = 3;

/// The input for the `create_recovery_relationship_invitation` function
///
/// # Fields
///
/// * `customer_account` - The account that is issuing the invitation
/// * `trusted_contact_alias` - Allows the Customer to give the Trusted Contact an alias for future reference
/// * `protected_customer_enrollment_pake_pubkey` - The public key that will be used to establish the secure channel between the customer and the trusted contact
pub struct CreateRecoveryRelationshipInvitationInput<'a> {
    pub customer_account: &'a FullAccount,
    pub trusted_contact_alias: &'a str,
    pub protected_customer_enrollment_pake_pubkey: &'a str,
}

impl Service {
    /// Enables a customer account to generate an invitation which authorizes a friend
    /// or family member to become a Trusted Contact. This trusted contact can help to
    /// recover their account in the future. Each issued invitation has a distinct code
    /// attached to it, which will be used to accept the invitation.
    ///
    /// # Arguments
    ///
    /// * `input` - Contains the customer account, the trusted contact alias and the customer enrollment pake pubkey
    ///
    /// # Returns
    ///
    /// * The recovery relationship corresponding to the code
    #[instrument(skip(self, input))]
    pub async fn create_recovery_relationship_invitation(
        &self,
        input: CreateRecoveryRelationshipInvitationInput<'_>,
    ) -> Result<RecoveryRelationship, ServiceError> {
        let id = RecoveryRelationshipId::gen()?;

        let account_properties = &input.customer_account.common_fields.properties;
        let (code, code_bit_length) = gen_code();
        let expires_at = gen_expiration(account_properties);

        let relationships = self
            .repository
            .fetch_recovery_relationships_for_account(&input.customer_account.id)
            .await?;
        if relationships.endorsed_trusted_contacts.len()
            + relationships.unendorsed_trusted_contacts.len()
            + relationships.invitations.len()
            >= MAX_TRUSTED_CONTACTS
        {
            return Err(ServiceError::MaxTrustedContactsReached);
        }

        let mut relationship = RecoveryRelationship::new_invitation(
            &id,
            &input.customer_account.id,
            input.trusted_contact_alias,
            input.protected_customer_enrollment_pake_pubkey,
            &code,
            code_bit_length,
            &expires_at,
        );

        relationship = self
            .repository
            .persist_recovery_relationship(&relationship)
            .await?;

        Ok(relationship)
    }
}
