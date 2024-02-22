use notification::payloads::recovery_relationship_invitation_accepted::RecoveryRelationshipInvitationAcceptedPayload;
use notification::service::SendNotificationInput;
use notification::{NotificationPayloadBuilder, NotificationPayloadType};
use time::OffsetDateTime;
use types::account::identifiers::AccountId;
use types::recovery::social::relationship::{
    RecoveryRelationship, RecoveryRelationshipConnectionFieldsBuilder, RecoveryRelationshipId,
    RecoveryRelationshipUnendorsedBuilder,
};

use super::{disambiguate_code_input, error::ServiceError, Service};

pub struct AcceptRecoveryRelationshipInvitationInput<'a> {
    pub trusted_contact_account_id: &'a AccountId,
    pub recovery_relationship_id: &'a RecoveryRelationshipId,
    pub code: &'a str,
    pub customer_alias: &'a str,
    pub trusted_contact_identity_pubkey: &'a str,
    pub trusted_contact_enrollment_pubkey: &'a str,
    pub trusted_contact_identity_pubkey_mac: &'a str,
    pub enrollment_key_confirmation: &'a str,
}

impl Service {
    /// When a valid invitation is provided, this method forms a social recovery relationship between
    /// the account that issued the invitation and the one that calls this method. After the invitation
    /// is accepted, the Trusted Contact can assist the customer in retrieving their account via Social
    /// Recovery.
    ///
    /// # Arguments
    ///
    /// * `trusted_contact_account_id` - The account id of the Trusted that's accepting the invitation
    /// * `recovery_relationship_id` - The invitation id to be redeemed by the Trusted Contact
    /// * `code` - The code corresponding to the recovery_relationship_id which was fetched before calling the endpoint
    /// * `customer_alias` - Allows the Trusted Contact to give the customer an alias for future reference
    /// * `trusted_contact_identity_pubkey` - The public key of the Trusted Contact, used to encrypt the Social Recovery payload
    /// * `trusted_contact_identity_pubkey_mac` - The message authentication code used to ensure that keys weren't altered in transit.
    /// * `enrollment_key_confirmation` - The key confirmation used to verify that both parties have calculated the same session key without revealing the key to potential eavesdroppers
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

        if invitation.code != disambiguate_code_input(input.code) {
            return Err(ServiceError::InvitationCodeMismatch);
        }

        let prev_common_fields = prev_relationship.common_fields();
        let customer_account_id = &prev_common_fields.customer_account_id;
        let trusted_contact_alias = &prev_common_fields.trusted_contact_alias;

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

        let connection_fields = RecoveryRelationshipConnectionFieldsBuilder::default()
            .customer_alias(input.customer_alias.to_owned())
            .trusted_contact_account_id(input.trusted_contact_account_id.to_owned())
            .trusted_contact_identity_pubkey(input.trusted_contact_identity_pubkey.to_owned())
            .build()?;

        let connection = RecoveryRelationshipUnendorsedBuilder::default()
            .common_fields(prev_common_fields.to_owned())
            .connection_fields(connection_fields.to_owned())
            .customer_enrollment_pubkey(invitation.customer_enrollment_pubkey.to_owned())
            .trusted_contact_enrollment_pubkey(input.trusted_contact_enrollment_pubkey.to_owned())
            .trusted_contact_identity_pubkey_mac(
                input.trusted_contact_identity_pubkey_mac.to_owned(),
            )
            .enrollment_key_confirmation(input.enrollment_key_confirmation.to_owned())
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
                        },
                    ))
                    .build()?,
                only_touchpoints: None,
            })
            .await?;

        Ok(relationship)
    }
}
