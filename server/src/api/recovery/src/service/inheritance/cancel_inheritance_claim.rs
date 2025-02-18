use notification::payloads::inheritance_claim_canceled::InheritanceClaimCanceledPayload;
use notification::service::SendNotificationInput;
use notification::{NotificationPayloadBuilder, NotificationPayloadType};
use tokio::try_join;

use tracing::instrument;
use types::account::entities::FullAccount;

use types::recovery::inheritance::claim::{
    InheritanceClaim, InheritanceClaimCanceled, InheritanceClaimCanceledBy, InheritanceClaimId,
};
use types::recovery::social::relationship::{RecoveryRelationship, RecoveryRelationshipRole};

use super::{error::ServiceError, fetch_relationships_and_claim, Service};

pub struct CancelInheritanceClaimInput<'a> {
    pub account: &'a FullAccount,
    pub inheritance_claim_id: InheritanceClaimId,
}

impl Service {
    /// This function cancels an inheritance claim for a valid benefactor and beneficiary.
    /// The claim must be in a pending state in order for it to be canceled and there must
    /// be a valid recovery relationship between the benefactor and beneficiary.
    ///
    /// If the claim has already been canceled, the existing canceled claim will be returned.
    ///
    /// # Arguments
    ///
    /// * `input` - Contains the beneficiary or benefactor account and inheritance claim id to be cancelled
    ///
    /// # Returns
    ///
    /// * The canceled inheritance claim
    ///     
    #[instrument(skip(self, input))]
    pub async fn cancel_claim(
        &self,
        input: CancelInheritanceClaimInput<'_>,
    ) -> Result<(InheritanceClaimCanceledBy, InheritanceClaim), ServiceError> {
        // Check to see if the given Recovery Relationships are valid
        let (relationships, claim) =
            fetch_relationships_and_claim(self, &input.account.id, &input.inheritance_claim_id)
                .await?;

        if !matches!(claim, InheritanceClaim::Pending(_))
            && !matches!(claim, InheritanceClaim::Canceled(_))
        {
            return Err(ServiceError::InvalidClaimStateForCancellation);
        }

        // Determine the role of the account in the inheritance claim
        let recovery_relationship_id = claim.common_fields().recovery_relationship_id.to_owned();
        let (endorsed_relationship, canceled_by) =
            if let Some(RecoveryRelationship::Endorsed(endorsed_relationship)) = relationships
                .endorsed_trusted_contacts
                .iter()
                .find(|r| r.common_fields().id == recovery_relationship_id)
            {
                (
                    endorsed_relationship,
                    InheritanceClaimCanceledBy::Benefactor,
                )
            } else if let Some(RecoveryRelationship::Endorsed(endorsed_relationship)) =
                relationships
                    .customers
                    .iter()
                    .find(|r| r.common_fields().id == recovery_relationship_id)
            {
                (
                    endorsed_relationship,
                    InheritanceClaimCanceledBy::Beneficiary,
                )
            } else {
                return Err(ServiceError::MismatchingRecoveryRelationship);
            };

        let pending_claim = match claim {
            InheritanceClaim::Pending(pending_claim) => pending_claim,
            InheritanceClaim::Canceled(_) => return Ok((canceled_by, claim)), // If the recovery relationship is valid, return the existing canceled claim
            _ => return Err(ServiceError::InvalidClaimStateForCancellation), // This should not happen due to the previous check
        };

        // TODO[W-9712]: Add contestation scenario for Lite Accounts

        let updated_claim = InheritanceClaim::Canceled(InheritanceClaimCanceled {
            common_fields: pending_claim.common_fields.clone(),
            canceled_by: canceled_by.to_owned(),
        });
        let claim = self
            .repository
            .persist_inheritance_claim(&updated_claim)
            .await?;

        let (benefactor_payload, beneficiary_payload) = (
            NotificationPayloadBuilder::default()
                .inheritance_claim_canceled_payload(Some(InheritanceClaimCanceledPayload {
                    inheritance_claim_id: pending_claim.common_fields.id.clone(),
                    trusted_contact_alias: endorsed_relationship
                        .common_fields
                        .trusted_contact_info
                        .alias
                        .clone(),
                    customer_alias: endorsed_relationship
                        .connection_fields
                        .customer_alias
                        .clone(),
                    acting_account_role: canceled_by.clone().into(),
                    recipient_account_role: RecoveryRelationshipRole::ProtectedCustomer,
                }))
                .build()?,
            NotificationPayloadBuilder::default()
                .inheritance_claim_canceled_payload(Some(InheritanceClaimCanceledPayload {
                    inheritance_claim_id: pending_claim.common_fields.id,
                    trusted_contact_alias: endorsed_relationship
                        .common_fields
                        .trusted_contact_info
                        .alias
                        .clone(),
                    customer_alias: endorsed_relationship
                        .connection_fields
                        .customer_alias
                        .clone(),
                    acting_account_role: canceled_by.clone().into(),
                    recipient_account_role: RecoveryRelationshipRole::TrustedContact,
                }))
                .build()?,
        );

        try_join!(
            // Send notification for the benefactor
            self.notification_service
                .send_notification(SendNotificationInput {
                    account_id: &endorsed_relationship.common_fields.customer_account_id,
                    payload: &benefactor_payload,
                    payload_type: NotificationPayloadType::InheritanceClaimCanceled,
                    only_touchpoints: None,
                }),
            // Send notification for the beneficiary
            self.notification_service
                .send_notification(SendNotificationInput {
                    account_id: &endorsed_relationship
                        .connection_fields
                        .trusted_contact_account_id,
                    payload: &beneficiary_payload,
                    payload_type: NotificationPayloadType::InheritanceClaimCanceled,
                    only_touchpoints: None,
                })
        )?;

        Ok((canceled_by, claim))
    }
}
