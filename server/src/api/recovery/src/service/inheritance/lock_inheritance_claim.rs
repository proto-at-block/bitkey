use super::{
    error::ServiceError, fetch_relationships_and_claim, filter_endorsed_relationship, Service,
};

use crate::helpers::validate_signatures;
use crate::helpers::SignatureType::LockInheritance;
use time::OffsetDateTime;
use tracing::instrument;
use types::account::entities::FullAccount;
use types::recovery::inheritance::claim::{
    InheritanceClaim, InheritanceClaimAuthKeys, InheritanceClaimId, InheritanceClaimLocked,
    InheritanceClaimPending,
};
use types::recovery::inheritance::package::Package;
use types::recovery::social::relationship::RecoveryRelationshipId;

#[derive(Debug, Clone)]
pub(crate) struct LockInheritanceClaimInput {
    pub inheritance_claim_id: InheritanceClaimId,
    pub beneficiary_account: FullAccount,
    pub challenge: String,
    pub app_signature: String,
}

impl Service {
    /// This function locks an inheritance claim for a valid benefactor and beneficiary.
    /// There must be a pending claim between the benefactor and beneficiary.
    /// It must be after delay_end_time of the pending claim.
    /// The beneficiary must provide a valid challenge with app and hw signatures.
    /// There must also be a valid & endorsed recovery relationship between the benefactor and beneficiary.
    ///
    /// # Arguments
    ///
    /// * `input` - Contains the beneficiary account, inheritance claim id, challenge string and app & hw signatures
    ///
    /// # Returns
    ///
    /// * The locked inheritance claim
    ///     
    #[instrument(skip(self, input))]
    pub async fn lock(
        &self,
        input: LockInheritanceClaimInput,
    ) -> Result<InheritanceClaimLocked, ServiceError> {
        let (relationships, claim) = fetch_relationships_and_claim(
            self,
            &input.beneficiary_account.id,
            &input.inheritance_claim_id,
        )
        .await?;

        filter_endorsed_relationship(
            relationships.customers,
            &claim.common_fields().recovery_relationship_id,
        )?;

        self.validate_challenge(&claim, &input.challenge, &input.app_signature)?;

        let locked_claim = match claim {
            InheritanceClaim::Locked(locked_claim) => return Ok(locked_claim),
            InheritanceClaim::Pending(pending_claim) => {
                self.lock_pending_claim(
                    &input.beneficiary_account,
                    &pending_claim.common_fields.recovery_relationship_id,
                    &pending_claim,
                )
                .await?
            }
            _ => return Err(ServiceError::PendingClaimNotFound),
        };

        Ok(locked_claim)
    }

    async fn lock_pending_claim(
        &self,
        beneficiary_account: &FullAccount,
        recovery_relationship_id: &RecoveryRelationshipId,
        pending_claim: &InheritanceClaimPending,
    ) -> Result<InheritanceClaimLocked, ServiceError> {
        if !pending_claim.is_delay_complete() {
            return Err(ServiceError::ClaimDelayNotComplete);
        }

        let (inheritance_package, benefactor_descriptor_keyset) = tokio::try_join!(
            self.get_inheritance_package(recovery_relationship_id),
            self.fetch_active_benefactor_descriptor_keyset(recovery_relationship_id)
        )?;

        let (_, benefactor_descriptor) = benefactor_descriptor_keyset;
        let benefactor_descriptor = benefactor_descriptor
            .into_multisig_descriptor()
            .map_err(ServiceError::BdkUtils)?;

        let locked_claim = InheritanceClaimLocked {
            common_fields: pending_claim.common_fields.to_owned(),
            sealed_dek: inheritance_package.sealed_dek,
            sealed_mobile_key: inheritance_package.sealed_mobile_key,
            benefactor_descriptor_keyset: benefactor_descriptor,
            locked_at: OffsetDateTime::now_utc(),
        };

        // Rotate here if we ever support Lite Accounts

        let claim = self
            .repository
            .persist_inheritance_claim(&InheritanceClaim::Locked(locked_claim.clone()))
            .await?;

        let InheritanceClaim::Locked(locked_claim) = claim else {
            return Err(ServiceError::ClaimLockFailed);
        };

        Ok(locked_claim)
    }

    #[instrument(skip(self))]
    async fn fetch_active_claim(
        &self,
        recovery_relationship_id: &RecoveryRelationshipId,
    ) -> Result<InheritanceClaim, ServiceError> {
        let claim = self
            .repository
            .fetch_claims_for_recovery_relationship_id(recovery_relationship_id)
            .await?;

        let pending_claim = claim
            .into_iter()
            .find(|claim| !matches!(claim, InheritanceClaim::Canceled(_)))
            .ok_or(ServiceError::PendingClaimNotFound)?;

        Ok(pending_claim)
    }

    #[instrument(skip(self))]
    async fn get_inheritance_package(
        &self,
        recovery_relationship_id: &RecoveryRelationshipId,
    ) -> Result<Package, ServiceError> {
        self.get_packages_by_relationship_id(&vec![recovery_relationship_id.to_owned()])
            .await?
            .last()
            .cloned()
            .ok_or(ServiceError::NoInheritancePackage)
    }

    #[instrument(skip(self, claim, challenge, app_signature))]
    fn validate_challenge(
        &self,
        claim: &InheritanceClaim,
        challenge: &str,
        app_signature: &str,
    ) -> Result<(), ServiceError> {
        let claim_auth_keys = claim.common_fields().auth_keys;

        match claim_auth_keys {
            InheritanceClaimAuthKeys::FullAccount(claim_auth_keys) => validate_signatures(
                &LockInheritance,
                claim_auth_keys.app_pubkey,
                claim_auth_keys.hardware_pubkey,
                claim_auth_keys.recovery_pubkey,
                challenge,
                app_signature,
                None,
            )
            .map_err(|_| ServiceError::InvalidChallengeSignature),
            _ => Err(ServiceError::IncompatibleAccountType),
        }
    }
}
