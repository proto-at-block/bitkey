use authn_authz::key_claims::KeyClaims;
use notification::payloads::recovery_relationship_deleted::RecoveryRelationshipDeletedPayload;
use notification::service::SendNotificationInput;
use notification::{NotificationPayloadBuilder, NotificationPayloadType};
use tokio::try_join;
use tracing::{event, instrument, Level};
use types::account::identifiers::AccountId;
use types::authn_authz::cognito::CognitoUser;
use types::recovery::social::relationship::{
    RecoveryRelationship, RecoveryRelationshipCommonFields, RecoveryRelationshipConnectionFields,
    RecoveryRelationshipId, RecoveryRelationshipRole,
};
use types::recovery::trusted_contacts::TrustedContactRole;

use super::{error::ServiceError, Service};

/// The input for the `delete_recovery_relationship` function
///
/// # Fields
///
/// * `acting_account_id` - The account that is trying to server the recovery relationship
/// * `recovery_relationship_id` - The ID of the recovery relationship to be terminated
/// * `key_proof` - The keyproof containing checks for both app and hardware signatures over the access token
/// * `cognito_user` - The Cognito user linked to the access token
pub struct DeleteRecoveryRelationshipInput<'a> {
    pub acting_account_id: &'a AccountId,
    pub recovery_relationship_id: &'a RecoveryRelationshipId,
    pub key_proof: &'a KeyClaims,
    pub cognito_user: &'a CognitoUser,
}

struct NotificationParams {
    acting_account_role: RecoveryRelationshipRole,
    customer_account_id: AccountId,
    customer_alias: String,
    trusted_contact_account_id: AccountId,
    trusted_contact_alias: String,
    trusted_contact_roles: Vec<TrustedContactRole>,
}

impl Service {
    /// This function allows either a customer or a Recovery Contact to sever a recovery relationship
    /// they belong to. Additionally, customers can withdraw invitations they have previously sent to
    /// Recovery Contacts. For a customer to dissolve the recovery relationship or withdraw an invitation,
    /// they must use a valid account access token as well as a keyproof containing both app and hardware
    ///
    /// # Arguments
    ///
    /// * `input` - Contains the account id of the acting account, the recovery relationship id to be terminated, the keyproof and the cognito user
    #[instrument(skip(self, input))]
    pub async fn delete_recovery_relationship(
        &self,
        input: DeleteRecoveryRelationshipInput<'_>,
    ) -> Result<(), ServiceError> {
        let relationship = self
            .repository
            .fetch_recovery_relationship(input.recovery_relationship_id)
            .await?;

        let prepare_delete_connection =
            |common_fields: &RecoveryRelationshipCommonFields,
             connection_fields: &RecoveryRelationshipConnectionFields| {
                let customer_account_id = &common_fields.customer_account_id;
                let trusted_contact_account_id = &connection_fields.trusted_contact_account_id;

                if customer_account_id == input.acting_account_id {
                    if !(input.key_proof.app_signed && input.key_proof.hw_signed) {
                        event!(
                            Level::WARN,
                            "valid signature over access token required both app and hw auth key"
                        );
                        return Err(ServiceError::InvalidKeyProof);
                    }

                    if !input.cognito_user.is_app(input.acting_account_id)
                        && !input.cognito_user.is_hardware(input.acting_account_id)
                    {
                        return Err(ServiceError::InvalidOperationForAccessToken);
                    }

                    Ok(Some(NotificationParams {
                        acting_account_role: RecoveryRelationshipRole::ProtectedCustomer,
                        customer_account_id: customer_account_id.to_owned(),
                        customer_alias: connection_fields.customer_alias.clone(),
                        trusted_contact_account_id: trusted_contact_account_id.to_owned(),
                        trusted_contact_alias: common_fields.trusted_contact_info.alias.clone(),
                        trusted_contact_roles: common_fields.trusted_contact_info.roles.clone(),
                    }))
                } else if trusted_contact_account_id == input.acting_account_id {
                    let CognitoUser::Recovery(_) = *input.cognito_user else {
                        return Err(ServiceError::InvalidOperationForAccessToken);
                    };

                    Ok(Some(NotificationParams {
                        acting_account_role: RecoveryRelationshipRole::TrustedContact,
                        customer_account_id: customer_account_id.to_owned(),
                        customer_alias: connection_fields.customer_alias.clone(),
                        trusted_contact_account_id: trusted_contact_account_id.to_owned(),
                        trusted_contact_alias: common_fields.trusted_contact_info.alias.clone(),
                        trusted_contact_roles: common_fields.trusted_contact_info.roles.clone(),
                    }))
                } else {
                    Err(ServiceError::UnauthorizedRelationshipDeletion)
                }
            };
        let notification_params = match &relationship {
            RecoveryRelationship::Invitation(invitation) => {
                let customer_account_id = &invitation.common_fields.customer_account_id;

                if customer_account_id != input.acting_account_id {
                    return Err(ServiceError::UnauthorizedRelationshipDeletion);
                }

                if !input.cognito_user.is_app(input.acting_account_id)
                    && !input.cognito_user.is_hardware(input.acting_account_id)
                {
                    return Err(ServiceError::InvalidOperationForAccessToken);
                }

                Ok(None)
            }
            RecoveryRelationship::Unendorsed(connection) => {
                prepare_delete_connection(&connection.common_fields, &connection.connection_fields)
            }
            RecoveryRelationship::Endorsed(connection) => {
                prepare_delete_connection(&connection.common_fields, &connection.connection_fields)
            }
        }?;

        self.repository
            .delete_recovery_relationship(&relationship)
            .await?;

        // TODO: Do we need to do any socrec challenge or inheritance claim cleanup here? [W-9715]

        if let Some(notification_params) = notification_params {
            let customer_payload = NotificationPayloadBuilder::default()
                .recovery_relationship_deleted_payload(Some(RecoveryRelationshipDeletedPayload {
                    trusted_contact_alias: notification_params.trusted_contact_alias.clone(),
                    customer_alias: notification_params.customer_alias.clone(),
                    trusted_contact_roles: notification_params.trusted_contact_roles.clone(),
                    acting_account_role: notification_params.acting_account_role.clone(),
                    recipient_account_role: RecoveryRelationshipRole::ProtectedCustomer,
                }))
                .build()?;

            let trusted_contact_payload = NotificationPayloadBuilder::default()
                .recovery_relationship_deleted_payload(Some(RecoveryRelationshipDeletedPayload {
                    trusted_contact_alias: notification_params.trusted_contact_alias,
                    customer_alias: notification_params.customer_alias,
                    trusted_contact_roles: notification_params.trusted_contact_roles,
                    acting_account_role: notification_params.acting_account_role,
                    recipient_account_role: RecoveryRelationshipRole::TrustedContact,
                }))
                .build()?;

            try_join!(
                self.notification_service
                    .send_notification(SendNotificationInput {
                        account_id: &notification_params.customer_account_id,
                        payload_type: NotificationPayloadType::RecoveryRelationshipDeleted,
                        payload: &customer_payload,
                        only_touchpoints: None,
                    }),
                self.notification_service
                    .send_notification(SendNotificationInput {
                        account_id: &notification_params.trusted_contact_account_id,
                        payload_type: NotificationPayloadType::RecoveryRelationshipDeleted,
                        payload: &trusted_contact_payload,
                        only_touchpoints: None,
                    }),
            )?;
        }

        Ok(())
    }
}
