use authn_authz::key_claims::KeyClaims;

use notification::payloads::recovery_relationship_deleted::RecoveryRelationshipDeletedPayload;
use notification::service::SendNotificationInput;
use notification::{NotificationPayloadBuilder, NotificationPayloadType};
use tracing::{event, instrument, Level};
use types::account::identifiers::AccountId;
use types::authn_authz::cognito::CognitoUser;
use types::recovery::social::relationship::{
    RecoveryRelationship, RecoveryRelationshipCommonFields, RecoveryRelationshipConnectionFields,
    RecoveryRelationshipId,
};

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

        let delete_recovery_relationship_connection =
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
                    if !matches!(input.cognito_user, CognitoUser::Wallet(_)) {
                        return Err(ServiceError::InvalidOperationForAccessToken);
                    }

                    Ok((
                        false,
                        customer_account_id.to_owned(),
                        common_fields.trusted_contact_alias.clone(),
                    ))
                } else if trusted_contact_account_id == input.acting_account_id {
                    let CognitoUser::Recovery(_) = *input.cognito_user else {
                        return Err(ServiceError::InvalidOperationForAccessToken);
                    };

                    Ok((
                        // Only send notification when it's an established relationship and the
                        // trusted contact is the one deleting the relationship
                        true,
                        customer_account_id.to_owned(),
                        common_fields.trusted_contact_alias.clone(),
                    ))
                } else {
                    Err(ServiceError::UnauthorizedRelationshipDeletion)
                }
            };
        let (send_notification, customer_account_id, trusted_contact_alias) = match &relationship {
            RecoveryRelationship::Invitation(invitation) => {
                let customer_account_id = &invitation.common_fields.customer_account_id;

                if customer_account_id != input.acting_account_id {
                    return Err(ServiceError::UnauthorizedRelationshipDeletion);
                }
                if !matches!(input.cognito_user, CognitoUser::Wallet(_)) {
                    return Err(ServiceError::InvalidOperationForAccessToken);
                }

                Ok((
                    false,
                    customer_account_id.to_owned(),
                    invitation.common_fields.trusted_contact_alias.clone(),
                ))
            }
            RecoveryRelationship::Unendorsed(connection) => {
                delete_recovery_relationship_connection(
                    &connection.common_fields,
                    &connection.connection_fields,
                )
            }
            RecoveryRelationship::Endorsed(connection) => delete_recovery_relationship_connection(
                &connection.common_fields,
                &connection.connection_fields,
            ),
        }?;

        self.repository
            .delete_recovery_relationship(&relationship)
            .await?;

        if send_notification {
            self.notification_service
                .send_notification(SendNotificationInput {
                    account_id: &customer_account_id,
                    payload_type: NotificationPayloadType::RecoveryRelationshipDeleted,
                    payload: &NotificationPayloadBuilder::default()
                        .recovery_relationship_deleted_payload(Some(
                            RecoveryRelationshipDeletedPayload {
                                trusted_contact_alias,
                            },
                        ))
                        .build()?,
                    only_touchpoints: None,
                })
                .await?;
        }

        Ok(())
    }
}
