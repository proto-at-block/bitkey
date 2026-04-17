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
/// * `signed_by_both_factors` - Whether the request was signed by both app and hardware keys
/// * `acting_account_is_w3` - Whether the acting account is a W3 Full Account
/// * `cognito_user` - The Cognito user linked to the access token
pub struct DeleteRecoveryRelationshipInput<'a> {
    pub acting_account_id: &'a AccountId,
    pub recovery_relationship_id: &'a RecoveryRelationshipId,
    pub signed_by_both_factors: bool,
    pub acting_account_is_w3: bool,
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
    /// This function allows either a customer or a trusted contact to sever a recovery relationship
    /// they belong to. Additionally, customers can withdraw invitations they have previously sent to
    /// trusted contacts. For a customer to dissolve the recovery relationship or withdraw an invitation,
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
                    if !input.signed_by_both_factors {
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
                    // W3 TCs with hardware must prove via action proof to prevent
                    // a compromised app from silently removing RC relationships at scale.
                    if input.acting_account_is_w3 && !input.signed_by_both_factors {
                        event!(
                            Level::WARN,
                            "W3 trusted contact must sign with both factors to remove relationship"
                        );
                        return Err(ServiceError::InvalidActionProof);
                    }

                    // W3 TCs must use a Global-scoped token (App/Hardware) since the
                    // hardware auth flow refreshes with Global scope.
                    // Non-W3 TCs may use either Global or Recovery scope since we
                    // cannot enforce which scope W1 TCs use.
                    let has_global_scope = input.cognito_user.is_app(input.acting_account_id)
                        || input.cognito_user.is_hardware(input.acting_account_id);
                    let cognito_user_valid = if input.acting_account_is_w3 {
                        has_global_scope
                    } else {
                        has_global_scope || input.cognito_user.is_recovery(input.acting_account_id)
                    };
                    if !cognito_user_valid {
                        return Err(ServiceError::InvalidOperationForAccessToken);
                    }

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
