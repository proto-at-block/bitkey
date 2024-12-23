use super::social::relationship::Service as RecoveryRelationshipService;
use crate::service::inheritance::error::ServiceError;
use crate::service::social::relationship::get_recovery_relationships::{
    GetRecoveryRelationshipsInput, GetRecoveryRelationshipsOutput,
};
use account::service::{FetchAccountInput, Service as AccountService};

use bdk_utils::DescriptorKeyset;
use feature_flags::service::Service as FeatureFlagsService;
use notification::service::Service as NotificationService;
use repository::recovery::inheritance::InheritanceRepository;
use screener::service::Service as ScreenerService;
use std::sync::Arc;
use tokio::join;
use tracing::{event, instrument, Level};
use types::account::entities::Account;
use types::account::identifiers::{AccountId, KeysetId};
use types::recovery::inheritance::claim::{InheritanceClaim, InheritanceClaimId};
use types::recovery::social::relationship::{RecoveryRelationship, RecoveryRelationshipId};
use types::recovery::trusted_contacts::TrustedContactRole::Beneficiary;

pub mod cancel_inheritance_claim;
pub mod create_inheritance_claim;
pub mod packages;
pub mod update_inheritance_claim_destination;

pub(crate) mod error;
pub mod get_inheritance_claims;
pub mod lock_inheritance_claim;
pub mod recreate_pending_claims_for_beneficiary;
pub mod sign_and_complete_inheritance_claim;

#[cfg(test)]
mod tests;

#[derive(Clone)]
pub struct Service {
    pub repository: InheritanceRepository,
    pub recovery_relationship_service: RecoveryRelationshipService,
    pub notification_service: NotificationService,
    pub account_service: AccountService,
    pub feature_flags_service: FeatureFlagsService,
    pub screener_service: Arc<ScreenerService>,
}

impl Service {
    #[must_use]
    pub fn new(
        repository: InheritanceRepository,
        recovery_relationship_service: RecoveryRelationshipService,
        notification_service: NotificationService,
        account_service: AccountService,
        feature_flags_service: FeatureFlagsService,
        screener_service: Arc<ScreenerService>,
    ) -> Self {
        Self {
            repository,
            recovery_relationship_service,
            notification_service,
            account_service,
            feature_flags_service,
            screener_service,
        }
    }

    #[instrument(skip(self))]
    async fn fetch_active_benefactor_descriptor_keyset(
        &self,
        recovery_relationship_id: &RecoveryRelationshipId,
    ) -> Result<(KeysetId, DescriptorKeyset), ServiceError> {
        let account = self.get_customer_account(recovery_relationship_id).await?;
        match account {
            Account::Full(account) => {
                let active_descriptor_keyset = account
                    .active_descriptor_keyset()
                    .ok_or(ServiceError::NoActiveDescriptorKeySet)?;

                let active_keyset_id = account.active_keyset_id;

                Ok((active_keyset_id, active_descriptor_keyset))
            }
            _ => Err(ServiceError::IncompatibleAccountType),
        }
    }

    #[instrument(skip(self))]
    async fn get_customer_account(
        &self,
        recovery_relationship_id: &RecoveryRelationshipId,
    ) -> Result<Account, ServiceError> {
        let relationship = self
            .recovery_relationship_service
            .repository
            .fetch_recovery_relationship(recovery_relationship_id)
            .await?;
        let customer_account_id = relationship.common_fields().customer_account_id.clone();
        let customer_account = self
            .account_service
            .fetch_account(FetchAccountInput {
                account_id: &customer_account_id,
            })
            .await
            .map_err(|err| {
                event!(Level::ERROR, "Could not fetch account: {:?}", err);
                ServiceError::MismatchingRecoveryRelationship
            })?;
        Ok(customer_account)
    }
}

/// This function fetches the recovery relationships for a given beneficiary account
/// as well as the claim, if it exists, for a given inheritance claim id
///
/// # Arguments
///
/// * `service` - The inheritance service object
/// * `beneficiary_account_id` - The beneficiary account id
/// * `inheritance_claim_id` - The inheritance claim id
///
async fn fetch_relationships_and_claim(
    service: &Service,
    beneficiary_account_id: &AccountId,
    inheritance_claim_id: &InheritanceClaimId,
) -> Result<(GetRecoveryRelationshipsOutput, InheritanceClaim), ServiceError> {
    let (relationships_result, claim_result) = join!(
        service
            .recovery_relationship_service
            .get_recovery_relationships(GetRecoveryRelationshipsInput {
                account_id: beneficiary_account_id,
                trusted_contact_role_filter: Some(Beneficiary),
            }),
        service
            .repository
            .fetch_inheritance_claim(inheritance_claim_id)
    );
    Ok((relationships_result?, claim_result?))
}

/// This function fetches all pending inheritance claims for a given beneficiary account
///
/// # Arguments
///
/// * `service` - The inheritance service object
/// * `beneficiary_account_id` - The beneficiary account id
///
async fn fetch_pending_claims_as_beneficiary(
    service: &Service,
    beneficiary_account_id: &AccountId,
) -> Result<Vec<InheritanceClaim>, ServiceError> {
    let endorsed_relationship_ids = service
        .recovery_relationship_service
        .get_recovery_relationships(GetRecoveryRelationshipsInput {
            account_id: beneficiary_account_id,
            trusted_contact_role_filter: Some(Beneficiary),
        })
        .await?
        .customers
        .into_iter()
        .filter_map(|r| match r {
            RecoveryRelationship::Endorsed(r) => Some(r.common_fields.id.clone()),
            _ => None,
        })
        .collect();
    let claims = service
        .repository
        .fetch_pending_claims_for_recovery_relationship_ids(endorsed_relationship_ids)
        .await?;
    Ok(claims)
}

fn filter_endorsed_relationship(
    customers: Vec<RecoveryRelationship>,
    recovery_relationship_id: &RecoveryRelationshipId,
) -> Result<RecoveryRelationship, ServiceError> {
    // Ensure relationship still exists between benefactor and beneficiary
    let relationship = customers
        .into_iter()
        .find(|r| &r.common_fields().id == recovery_relationship_id)
        .ok_or(ServiceError::MismatchingRecoveryRelationship)?;

    // Ensure the relationship is endorsed by the customer
    if !matches!(relationship, RecoveryRelationship::Endorsed(_)) {
        return Err(ServiceError::MismatchingRecoveryRelationship);
    }
    Ok(relationship)
}
