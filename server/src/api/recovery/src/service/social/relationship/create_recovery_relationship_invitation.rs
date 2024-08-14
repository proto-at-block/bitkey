use super::{error::ServiceError, gen_code, gen_expiration, Service};
use account::entities::FullAccount;
use tracing::instrument;
use types::account::identifiers::AccountId;
use types::recovery::social::relationship::{RecoveryRelationship, RecoveryRelationshipId};
use types::recovery::trusted_contacts::{TrustedContactInfo, TrustedContactRole};

mod trusted_contact_limits {
    pub const MAX_BENEFICIARIES: usize = 1;
    pub const MAX_SOCIAL_RECOVERY_CONTACTS: usize = 3;
}

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
        let trusted_contact_role = TrustedContactRole::SocialRecoveryContact;

        self.validate_under_max_tc_limit(&input.customer_account.id, &trusted_contact_role)
            .await?;

        let trusted_contact = TrustedContactInfo::new(
            input.trusted_contact_alias.to_string(),
            vec![trusted_contact_role],
        )?;

        let mut relationship = RecoveryRelationship::new_invitation(
            &id,
            &input.customer_account.id,
            &trusted_contact,
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

    async fn validate_under_max_tc_limit(
        &self,
        id: &AccountId,
        trusted_contact_role: &TrustedContactRole,
    ) -> Result<(), ServiceError> {
        let max_limit = match trusted_contact_role {
            TrustedContactRole::Beneficiary => trusted_contact_limits::MAX_BENEFICIARIES,
            TrustedContactRole::SocialRecoveryContact => {
                trusted_contact_limits::MAX_SOCIAL_RECOVERY_CONTACTS
            }
        };
        let relationships = self
            .repository
            .fetch_recovery_relationships_for_account(id)
            .await?;
        let tc_count = relationships
            .endorsed_trusted_contacts
            .iter()
            .chain(&relationships.unendorsed_trusted_contacts)
            .chain(&relationships.invitations)
            .filter(|tc| tc.has_role(trusted_contact_role))
            .count();

        if tc_count >= max_limit {
            return Err(ServiceError::MaxTrustedContactsReached(
                trusted_contact_role.to_owned(),
            ));
        }
        Ok(())
    }
}
