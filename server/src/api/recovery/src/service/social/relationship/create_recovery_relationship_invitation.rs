use notification::payloads::recovery_relationship_benefactor_invitation_pending::RecoveryRelationshipBenefactorInvitationPendingPayload;
use notification::schedule::ScheduleNotificationType;
use notification::service::ScheduleNotificationsInput;
use notification::NotificationPayloadBuilder;
use promotion_code::entities::CodeKey;
use tokio::try_join;
use tracing::instrument;
use types::account::entities::FullAccount;
use types::account::identifiers::AccountId;
use types::recovery::social::relationship::{RecoveryRelationship, RecoveryRelationshipId};
use types::recovery::trusted_contacts::{TrustedContactInfo, TrustedContactRole};

use super::{error::ServiceError, gen_code, gen_expiration, Service};

mod trusted_contact_limits {
    pub const MAX_BENEFICIARIES: usize = 1;
    pub const MAX_SOCIAL_RECOVERY_CONTACTS: usize = 3;
}

/// The input for the `create_recovery_relationship_invitation` function
///
/// # Fields
///
/// * `customer_account` - The account that is issuing the invitation
/// * `trusted_contact_alias` - Allows the Customer to give the Recovery Contact an alias for future reference
/// * `protected_customer_enrollment_pake_pubkey` - The public key that will be used to establish the secure channel between the customer and the Recovery Contact
pub struct CreateRecoveryRelationshipInvitationInput<'a> {
    pub customer_account: &'a FullAccount,
    pub trusted_contact: &'a TrustedContactInfo,
    pub protected_customer_enrollment_pake_pubkey: &'a str,
}

impl Service {
    /// Enables a customer account to generate an invitation which authorizes a friend
    /// or family member to become a Recovery Contact. This Recovery Contact can help to
    /// recover their account in the future. Each issued invitation has a distinct code
    /// attached to it, which will be used to accept the invitation.
    ///
    /// # Arguments
    ///
    /// * `input` - Contains the customer account, the Recovery Contact alias and the customer enrollment pake pubkey
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
        let role = input
            .trusted_contact
            .roles
            .first()
            .ok_or(ServiceError::MissingTrustedContactRoles)?;
        let expires_at = gen_expiration(role, account_properties);

        self.validate_under_max_tc_limit(&input.customer_account.id, &input.trusted_contact.roles)
            .await?;

        let mut relationship = RecoveryRelationship::new_invitation(
            &id,
            &input.customer_account.id,
            input.trusted_contact,
            input.protected_customer_enrollment_pake_pubkey,
            &code,
            code_bit_length,
            &expires_at,
        );

        relationship = self
            .repository
            .persist_recovery_relationship(&relationship)
            .await?;

        if input
            .trusted_contact
            .roles
            .contains(&TrustedContactRole::Beneficiary)
        {
            let customer_account_id = input.customer_account.id.clone();
            let benefactor_key = CodeKey::inheritance_benefactor(customer_account_id.clone());
            let beneficiary_key = CodeKey::inheritance_beneficiary(customer_account_id.clone());
            let _ = try_join!(
                self.promotion_code_service
                    .generate_code(&benefactor_key, &customer_account_id),
                self.promotion_code_service
                    .generate_code(&beneficiary_key, &customer_account_id)
            )
            .map_err(|e| {
                tracing::error!("Failed to generate promotional codes: {e:?}");
            });
        }

        if input
            .trusted_contact
            .roles
            .contains(&TrustedContactRole::Beneficiary)
        {
            self.notification_service
                .schedule_notifications(ScheduleNotificationsInput {
                    account_id: input.customer_account.id.clone(),
                    notification_type:
                        ScheduleNotificationType::RecoveryRelationshipBenefactorInvitationPending,
                    payload: NotificationPayloadBuilder::default()
                        .recovery_relationship_benefactor_invitation_pending_payload(Some(
                            RecoveryRelationshipBenefactorInvitationPendingPayload {
                                recovery_relationship_id: id,
                                trusted_contact_alias: input.trusted_contact.alias.clone(),
                            },
                        ))
                        .build()?,
                })
                .await?;
        }

        Ok(relationship)
    }

    async fn validate_under_max_tc_limit(
        &self,
        id: &AccountId,
        trusted_contact_roles: &Vec<TrustedContactRole>,
    ) -> Result<(), ServiceError> {
        let relationships = self
            .repository
            .fetch_recovery_relationships_for_account(id)
            .await?;

        for role in trusted_contact_roles {
            let max_limit = match role {
                TrustedContactRole::Beneficiary => trusted_contact_limits::MAX_BENEFICIARIES,
                TrustedContactRole::SocialRecoveryContact => {
                    trusted_contact_limits::MAX_SOCIAL_RECOVERY_CONTACTS
                }
            };

            let tc_count = relationships
                .endorsed_trusted_contacts
                .iter()
                .chain(&relationships.unendorsed_trusted_contacts)
                .chain(&relationships.invitations)
                .filter(|tc| tc.has_role(role))
                .count();

            if tc_count >= max_limit {
                return Err(ServiceError::MaxTrustedContactsReached(role.to_owned()));
            }
        }

        Ok(())
    }
}
