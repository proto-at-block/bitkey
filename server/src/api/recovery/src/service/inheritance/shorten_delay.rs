use account::service::FetchAccountInput;
use time::Duration;
use tokio::try_join;
use tracing::instrument;
use types::account::entities::Account;
use types::account::identifiers::AccountId;
use types::recovery::inheritance::claim::InheritanceClaimId;
use types::recovery::inheritance::claim::{InheritanceClaim, InheritanceClaimPending};

use super::{
    error::ServiceError, fetch_relationships_and_claim, filter_endorsed_relationship, Service,
};

#[derive(Debug)]
pub struct ShortenDelayForBeneficiaryInput {
    pub beneficiary_account_id: AccountId,
    pub inheritance_claim_id: InheritanceClaimId,
    pub delay_period_seconds: i64,
}

impl Service {
    /// This function shortens the delay end time for a claim given the claim is pending with both the beneficiary and benefactor are test accounts.
    ///
    /// # Arguments
    ///
    /// * `input` - Contains the beneficiary account id, inheritance claim id, and the delay period in seconds
    ///
    /// # Returns
    ///
    /// * The shortened inheritance claim
    ///     
    #[instrument(skip(self, input))]
    pub async fn shorten_delay_for_beneficiary(
        &self,
        input: ShortenDelayForBeneficiaryInput,
    ) -> Result<InheritanceClaim, ServiceError> {
        let (relationships, claim) = fetch_relationships_and_claim(
            self,
            &input.beneficiary_account_id,
            &input.inheritance_claim_id,
        )
        .await?;

        // Ensure the claim belongs to the calling beneficiary before mutating it, and derive the
        // benefactor from the claim's own relationship rather than from an unrelated one.
        let recovery_relationship = filter_endorsed_relationship(
            relationships.customers,
            &claim.common_fields().recovery_relationship_id,
        )?;

        let (beneficiary_account, benefactor_account) = try_join!(
            self.fetch_account(&input.beneficiary_account_id),
            self.fetch_account(&recovery_relationship.common_fields().customer_account_id),
        )?;

        let are_test_accounts = beneficiary_account
            .get_common_fields()
            .properties
            .is_test_account
            && benefactor_account
                .get_common_fields()
                .properties
                .is_test_account;
        if !are_test_accounts {
            return Err(ServiceError::ShortenDelayForNonTestAccount);
        }

        let InheritanceClaim::Pending(pending_claim) = claim else {
            return Err(ServiceError::InvalidClaimStateForShortening);
        };

        let updated_claim = InheritanceClaim::Pending(InheritanceClaimPending {
            delay_end_time: pending_claim.common_fields.created_at
                + Duration::seconds(input.delay_period_seconds),
            ..pending_claim
        });

        let claim = self
            .repository
            .persist_inheritance_claim(&updated_claim)
            .await?;
        Ok(claim)
    }

    /// This function fetches the account given an account id
    ///
    /// # Arguments
    ///
    /// * `account_id` - The account id
    ///
    /// # Returns
    ///
    /// * The beneficiary account
    ///
    async fn fetch_account(&self, account_id: &AccountId) -> Result<Account, ServiceError> {
        self.account_service
            .fetch_account(FetchAccountInput { account_id })
            .await
            .map_err(ServiceError::from)
    }
}
