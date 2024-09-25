use notification::payloads::recovery_relationship_invitation_accepted::RecoveryRelationshipInvitationAcceptedPayload;
use notification::service::SendNotificationInput;
use notification::{NotificationPayloadBuilder, NotificationPayloadType};
use time::OffsetDateTime;
use tracing::instrument;
use types::account::identifiers::AccountId;
use types::recovery::social::relationship::{
    RecoveryRelationship, RecoveryRelationshipConnectionFieldsBuilder, RecoveryRelationshipId,
    RecoveryRelationshipUnendorsedBuilder,
};

use super::{error::ServiceError, Service};

const MAX_PROTECTED_CUSTOMERS: usize = 10;

/// The input for the `accept_recovery_relationship_invitation` function
///
/// # Fields
///
/// * `trusted_contact_account_id` - The account id of the Trusted that's accepting the invitation
/// * `recovery_relationship_id` - The invitation id to be redeemed by the Trusted Contact
/// * `code` - The code corresponding to the recovery_relationship_id which was fetched before calling the endpoint
/// * `customer_alias` - Allows the Trusted Contact to give the customer an alias for future reference
/// * `trusted_contact_enrollment_pake_pubkey` - The public key of the Trusted Contact, used to encrypt the Social Recovery payload
/// * `enrollment_pake_confirmation` - The confirmation code used in PAKE to ensure nothing was tampered in transit.
/// * `sealed_delegated_decryption_pubkey` - The sealed delegated decryption pubkey
pub struct AcceptRecoveryRelationshipInvitationInput<'a> {
    pub trusted_contact_account_id: &'a AccountId,
    pub recovery_relationship_id: &'a RecoveryRelationshipId,
    pub code: &'a str,
    pub customer_alias: &'a str,
    pub trusted_contact_enrollment_pake_pubkey: &'a str,
    pub enrollment_pake_confirmation: &'a str,
    pub sealed_delegated_decryption_pubkey: &'a str,
}

impl Service {
    /// When a valid invitation is provided, this method forms a social recovery relationship between
    /// the account that issued the invitation and the one that calls this method. After the invitation
    /// is accepted, the Trusted Contact can assist the customer in retrieving their account via Social
    /// Recovery.
    ///
    /// # Arguments
    ///
    /// * `input` - Contains the information needed to accept the invite
    ///
    /// # Returns
    ///
    /// * The unendorsed recovery relationship that was accepted by the Trusted Contact
    #[instrument(skip(self, input))]
    pub async fn accept_recovery_relationship_invitation(
        &self,
        input: AcceptRecoveryRelationshipInvitationInput<'_>,
    ) -> Result<RecoveryRelationship, ServiceError> {
        let prev_relationship = self
            .repository
            .fetch_recovery_relationship(input.recovery_relationship_id)
            .await?;

        let invitation = match &prev_relationship {
            RecoveryRelationship::Invitation(invitation) => invitation,
            RecoveryRelationship::Unendorsed(connection) => {
                if connection.connection_fields.trusted_contact_account_id
                    == *input.trusted_contact_account_id
                {
                    return Ok(prev_relationship);
                }
                return Err(ServiceError::RelationshipAlreadyEstablished);
            }
            RecoveryRelationship::Endorsed(endorsed_connection) => {
                if endorsed_connection
                    .connection_fields
                    .trusted_contact_account_id
                    == *input.trusted_contact_account_id
                {
                    return Ok(prev_relationship);
                }
                return Err(ServiceError::RelationshipAlreadyEstablished);
            }
        };

        if OffsetDateTime::now_utc() > invitation.expires_at {
            return Err(ServiceError::InvitationExpired);
        }

        if invitation.code != input.code {
            return Err(ServiceError::InvitationCodeMismatch);
        }

        let prev_common_fields = prev_relationship.common_fields();
        let customer_account_id = &prev_common_fields.customer_account_id;
        let trusted_contact_alias = &prev_common_fields.trusted_contact_info.alias;
        let trusted_contact_roles = &prev_common_fields.trusted_contact_info.roles;

        if customer_account_id == input.trusted_contact_account_id {
            return Err(ServiceError::CustomerIsTrustedContact);
        }

        let relationship = self
            .repository
            .fetch_optional_recovery_relationship_for_account_ids(
                customer_account_id,
                input.trusted_contact_account_id,
            )
            .await?;
        match relationship {
            Some(RecoveryRelationship::Unendorsed(_)) | Some(RecoveryRelationship::Endorsed(_)) => {
                return Err(ServiceError::AccountAlreadyTrustedContact);
            }
            _ => {}
        }

        let relationships = self
            .repository
            .fetch_recovery_relationships_for_account(input.trusted_contact_account_id)
            .await?;
        if relationships.customers.len() >= MAX_PROTECTED_CUSTOMERS {
            return Err(ServiceError::MaxProtectedCustomersReached);
        }

        let connection_fields = RecoveryRelationshipConnectionFieldsBuilder::default()
            .customer_alias(input.customer_alias.to_owned())
            .trusted_contact_account_id(input.trusted_contact_account_id.to_owned())
            .build()?;

        let connection = RecoveryRelationshipUnendorsedBuilder::default()
            .common_fields(prev_common_fields.to_owned())
            .connection_fields(connection_fields.to_owned())
            .trusted_contact_enrollment_pake_pubkey(
                input.trusted_contact_enrollment_pake_pubkey.to_owned(),
            )
            .enrollment_pake_confirmation(input.enrollment_pake_confirmation.to_owned())
            .sealed_delegated_decryption_pubkey(input.sealed_delegated_decryption_pubkey.to_owned())
            .protected_customer_enrollment_pake_pubkey(
                invitation
                    .protected_customer_enrollment_pake_pubkey
                    .to_owned(),
            )
            .build()?;

        let relationship = self
            .repository
            .persist_recovery_relationship(&RecoveryRelationship::Unendorsed(connection))
            .await?;

        self.notification_service
            .send_notification(SendNotificationInput {
                account_id: customer_account_id,
                payload_type: NotificationPayloadType::RecoveryRelationshipInvitationAccepted,
                payload: &NotificationPayloadBuilder::default()
                    .recovery_relationship_invitation_accepted_payload(Some(
                        RecoveryRelationshipInvitationAcceptedPayload {
                            trusted_contact_alias: trusted_contact_alias.to_owned(),
                            trusted_contact_roles: trusted_contact_roles.to_owned(),
                        },
                    ))
                    .build()?,
                only_touchpoints: None,
            })
            .await?;

        Ok(relationship)
    }
}
