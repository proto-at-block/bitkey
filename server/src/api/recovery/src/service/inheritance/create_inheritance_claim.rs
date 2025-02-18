use account::service::FetchAccountInput;
use notification::payloads::inheritance_claim_period_almost_over::InheritanceClaimPeriodAlmostOverPayload;
use notification::payloads::inheritance_claim_period_completed::InheritanceClaimPeriodCompletedPayload;
use notification::payloads::inheritance_claim_period_initiated::InheritanceClaimPeriodInitiatedPayload;
use notification::schedule::ScheduleNotificationType;
use notification::service::ScheduleNotificationsInput;
use notification::{NotificationPayload, NotificationPayloadBuilder};
use tokio::{join, try_join};
use tracing::instrument;
use types::account::entities::FullAccount;
use types::account::identifiers::AccountId;
use types::recovery::inheritance::claim::{InheritanceClaim, InheritanceClaimPending};
use types::recovery::trusted_contacts::TrustedContactRole::Beneficiary;
use types::recovery::{
    inheritance::claim::{InheritanceClaimAuthKeys, InheritanceClaimId},
    social::relationship::{
        RecoveryRelationship, RecoveryRelationshipId, RecoveryRelationshipRole,
    },
};

use super::{error::ServiceError, filter_endorsed_relationship, Service};
use crate::service::social::relationship::get_recovery_relationships::{
    GetRecoveryRelationshipsInput, GetRecoveryRelationshipsOutput,
};

pub struct CreateInheritanceClaimInput<'a> {
    pub beneficiary_account: &'a FullAccount,
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub auth_keys: InheritanceClaimAuthKeys,
}

impl Service {
    /// This function starts an inheritance claim for a valid benefactor and beneficiary.
    /// There must be no pending claims between the benefactor and beneficiary.
    /// There must also be a valid recovery relationship between the benefactor and beneficiary.
    ///
    /// # Arguments
    ///
    /// * `input` - Contains the beneficiary account, recovery relationship id, and auth keys
    ///
    /// # Returns
    ///
    /// * The newly created inheritance claim
    ///     
    #[instrument(skip(self, input))]
    pub async fn create_claim(
        &self,
        input: CreateInheritanceClaimInput<'_>,
    ) -> Result<InheritanceClaim, ServiceError> {
        // Check to see if the given Recovery Relationships are valid
        let (relationships, all_claims) = fetch_relationships_and_claims(
            self,
            &input.beneficiary_account.id,
            &input.recovery_relationship_id,
        )
        .await?;

        let relationship =
            filter_endorsed_relationship(relationships.customers, &input.recovery_relationship_id)?;
        let benefactor_account_id = relationship.common_fields().customer_account_id.to_owned();
        let beneficiary_account_id = input.beneficiary_account.id.to_owned();

        // TODO: Ensure that there is a package for this relationship after W-9369

        // Ensure no pending claims for the given relationship
        check_claims_status(&all_claims)?;

        // TODO: Add contestation scenario for Lite Accounts

        let benefactor = self
            .account_service
            .fetch_full_account(FetchAccountInput {
                account_id: &relationship.common_fields().customer_account_id,
            })
            .await?;

        let id = InheritanceClaimId::gen()?;
        let use_test_delay_end_time =
            should_use_shortened_delay(&benefactor, input.beneficiary_account);
        let pending_claim = InheritanceClaimPending::new(
            id.clone(),
            input.recovery_relationship_id,
            benefactor_account_id,
            beneficiary_account_id,
            input.auth_keys,
            use_test_delay_end_time,
        );

        let claim = self
            .repository
            .persist_inheritance_claim(&InheritanceClaim::Pending(pending_claim.clone()))
            .await?;

        self.schedule_notifications_for_pending_claim(
            &pending_claim,
            &input.beneficiary_account.id,
            &relationship,
            use_test_delay_end_time,
        )
        .await?;
        Ok(claim)
    }

    pub(crate) async fn schedule_notifications_for_pending_claim(
        &self,
        pending_claim: &InheritanceClaimPending,
        beneficiary_account_id: &AccountId,
        relationship: &RecoveryRelationship,
        has_shortened_delay: bool,
    ) -> Result<(), ServiceError> {
        let (benefactor_payload, beneficiary_payload) = generate_notification_payloads(
            &pending_claim.common_fields.id,
            relationship,
            pending_claim,
        )?;

        try_join!(
            // Schedule notifications for the benefactor
            self.notification_service
                .schedule_notifications(ScheduleNotificationsInput {
                    account_id: relationship.common_fields().customer_account_id.clone(),
                    notification_type: ScheduleNotificationType::InheritanceClaimPeriodInitiated(
                        pending_claim.delay_end_time,
                        RecoveryRelationshipRole::ProtectedCustomer,
                        has_shortened_delay,
                    ),
                    payload: benefactor_payload,
                }),
            // Schedule notifications for the beneficiary
            self.notification_service
                .schedule_notifications(ScheduleNotificationsInput {
                    account_id: beneficiary_account_id.clone(),
                    notification_type: ScheduleNotificationType::InheritanceClaimPeriodInitiated(
                        pending_claim.delay_end_time,
                        RecoveryRelationshipRole::TrustedContact,
                        has_shortened_delay,
                    ),
                    payload: beneficiary_payload,
                }),
        )?;
        Ok(())
    }
}

/// This function fetches the recovery relationships and inheritance claims
/// for a given beneficiary account and recovery relationship id
///
/// # Arguments
///
/// * `service` - The inheritance service object
/// * `beneficiary_account_id` - The beneficiary account id
/// * `recovery_relationship_id` - The recovery relationship id
///
async fn fetch_relationships_and_claims(
    service: &Service,
    beneficiary_account_id: &AccountId,
    recovery_relationship_id: &RecoveryRelationshipId,
) -> Result<(GetRecoveryRelationshipsOutput, Vec<InheritanceClaim>), ServiceError> {
    let (relationships_result, all_claims_result) = join!(
        service
            .recovery_relationship_service
            .get_recovery_relationships(GetRecoveryRelationshipsInput {
                account_id: beneficiary_account_id,
                trusted_contact_role_filter: Some(Beneficiary),
            }),
        service
            .repository
            .fetch_claims_for_recovery_relationship_id(recovery_relationship_id)
    );
    Ok((relationships_result?, all_claims_result?))
}

/// This function checks if there are any pending claims for the given relationship
///
/// # Arguments
///
/// * `claims` - A list of inheritance claims
///
/// # Errors
///
/// * If there are any pending claims
///
fn check_claims_status(claims: &[InheritanceClaim]) -> Result<(), ServiceError> {
    if claims
        .iter()
        .any(|claim| matches!(claim, InheritanceClaim::Pending(_)))
    {
        return Err(ServiceError::PendingClaimExists);
    }

    Ok(())
}

/// This function generates the notification payloads for the benefactor and beneficiary
///
/// # Arguments
///
/// * `id` - The inheritance claim id
/// * `relationship` - The recovery relationship
/// * `pending_claim` - The pending inheritance claim
///
/// # Errors
///
/// * Propagation only
///
fn generate_notification_payloads(
    id: &InheritanceClaimId,
    relationship: &RecoveryRelationship,
    pending_claim: &InheritanceClaimPending,
) -> Result<(NotificationPayload, NotificationPayload), ServiceError> {
    Ok((
        NotificationPayloadBuilder::default()
            .inheritance_claim_period_initiated_payload(Some(
                InheritanceClaimPeriodInitiatedPayload {
                    inheritance_claim_id: id.clone(),
                    trusted_contact_alias: relationship
                        .common_fields()
                        .trusted_contact_info
                        .alias
                        .clone(),
                    recipient_account_role: RecoveryRelationshipRole::ProtectedCustomer,
                    delay_end_time: pending_claim.delay_end_time,
                },
            ))
            .inheritance_claim_period_completed_payload(Some(
                InheritanceClaimPeriodCompletedPayload {
                    inheritance_claim_id: id.clone(),
                    trusted_contact_alias: relationship
                        .common_fields()
                        .trusted_contact_info
                        .alias
                        .clone(),
                    recipient_account_role: RecoveryRelationshipRole::ProtectedCustomer,
                },
            ))
            .inheritance_claim_period_almost_over_payload(Some(
                InheritanceClaimPeriodAlmostOverPayload {
                    inheritance_claim_id: id.clone(),
                    trusted_contact_alias: relationship
                        .common_fields()
                        .trusted_contact_info
                        .alias
                        .clone(),
                    recipient_account_role: RecoveryRelationshipRole::ProtectedCustomer,
                    delay_end_time: pending_claim.delay_end_time,
                },
            ))
            .build()?,
        NotificationPayloadBuilder::default()
            .inheritance_claim_period_initiated_payload(Some(
                InheritanceClaimPeriodInitiatedPayload {
                    inheritance_claim_id: id.clone(),
                    trusted_contact_alias: relationship
                        .common_fields()
                        .trusted_contact_info
                        .alias
                        .clone(),
                    recipient_account_role: RecoveryRelationshipRole::TrustedContact,
                    delay_end_time: pending_claim.delay_end_time,
                },
            ))
            .inheritance_claim_period_completed_payload(Some(
                InheritanceClaimPeriodCompletedPayload {
                    inheritance_claim_id: id.clone(),
                    trusted_contact_alias: relationship
                        .common_fields()
                        .trusted_contact_info
                        .alias
                        .clone(),
                    recipient_account_role: RecoveryRelationshipRole::TrustedContact,
                },
            ))
            .inheritance_claim_period_almost_over_payload(Some(
                InheritanceClaimPeriodAlmostOverPayload {
                    inheritance_claim_id: id.clone(),
                    trusted_contact_alias: relationship
                        .common_fields()
                        .trusted_contact_info
                        .alias
                        .clone(),
                    recipient_account_role: RecoveryRelationshipRole::TrustedContact,
                    delay_end_time: pending_claim.delay_end_time,
                },
            ))
            .build()?,
    ))
}

pub(crate) fn should_use_shortened_delay(
    benefactor: &FullAccount,
    beneficiary: &FullAccount,
) -> bool {
    benefactor.common_fields.properties.is_test_account
        && beneficiary.common_fields.properties.is_test_account
}
