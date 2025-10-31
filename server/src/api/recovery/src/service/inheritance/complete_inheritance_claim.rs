use super::{
    error::ServiceError, fetch_relationships_and_claim, filter_endorsed_relationship, Service,
};

use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::ElectrumRpcUris;
use feature_flags::flag::ContextKey;
use mobile_pay::signing_processor::{SigningMethod, SigningValidator};
use mobile_pay::signing_strategies::{RecoverySweepSigningStrategy, SigningStrategy};
use time::OffsetDateTime;
use tracing::instrument;
use types::account::entities::FullAccount;
use types::account::spending::SpendingKeyset;
use types::recovery::inheritance::claim::{
    InheritanceClaim, InheritanceClaimCompleted, InheritanceClaimId, InheritanceCompletionMethod,
};

#[derive(Debug, Clone)]
pub(crate) struct SignAndCompleteInheritanceClaimInput<'a, T> {
    pub signing_processor: T,
    pub rpc_uris: ElectrumRpcUris,
    pub inheritance_claim_id: InheritanceClaimId,
    pub beneficiary_account: &'a FullAccount,
    pub psbt: Psbt,
    pub context_key: Option<ContextKey>,
}

#[derive(Debug, Clone)]
pub(crate) struct CompleteWithoutPsbtInput<'a> {
    pub inheritance_claim_id: InheritanceClaimId,
    pub beneficiary_account: &'a FullAccount,
}

impl Service {
    /// This function completes an inheritance claim for a valid benefactor and beneficiary.
    /// It accepts a PSBT, signs it with the benefactors server key and broadcasts it.
    /// There must be a locked claim between the benefactor and beneficiary.
    /// The passed PSBT must not have a sanctioned destination address.
    /// # Arguments
    ///
    /// * `input`
    ///   * `inheritance_claim_id` - id for the locked inheritance claim
    ///   * `beneficiary_account` - The beneficiary account
    ///   * `psbt` - The 1 of 3 signed PSBT
    ///
    /// # Returns
    ///
    /// * The completed inheritance claim including the signed PSBT
    ///     
    #[instrument(skip(self, input))]
    pub async fn sign_and_complete<'a, T>(
        &self,
        input: SignAndCompleteInheritanceClaimInput<'a, T>,
    ) -> Result<InheritanceClaimCompleted, ServiceError>
    where
        T: SigningValidator,
        <T as SigningValidator>::SigningProcessor: Send + Sync,
    {
        let (relationships, claim) = fetch_relationships_and_claim(
            self,
            &input.beneficiary_account.id,
            &input.inheritance_claim_id,
        )
        .await?;

        if !matches!(
            claim,
            InheritanceClaim::Completed(_) | InheritanceClaim::Locked(_)
        ) {
            return Err(ServiceError::LockedClaimNotFound);
        }

        filter_endorsed_relationship(
            relationships.customers,
            &claim.common_fields().recovery_relationship_id,
        )?;

        let (benefactor_keyset_id, benefactor_keyset) = self
            .fetch_active_benefactor_spending_keyset(
                &claim.common_fields().recovery_relationship_id,
            )
            .await?;

        let signing_method = match &benefactor_keyset {
            SpendingKeyset::LegacyMultiSig(legacy_benefactor_source) => {
                let beneficiary_keyset = input
                    .beneficiary_account
                    .active_spending_keyset()
                    .ok_or(ServiceError::NoActiveSpendingKeyset)?;

                match beneficiary_keyset {
                    SpendingKeyset::LegacyMultiSig(legacy_beneficiary_dest) => {
                        SigningMethod::LegacySweep {
                            source_descriptor: legacy_benefactor_source.clone().into(),
                            active_descriptor: legacy_beneficiary_dest.clone().into(),
                        }
                    }
                    SpendingKeyset::PrivateMultiSig(private_beneficiary_dest) => {
                        SigningMethod::MigrationSweep {
                            source_descriptor: legacy_benefactor_source.clone().into(),
                            active_keyset: private_beneficiary_dest.clone(),
                        }
                    }
                }
            }
            SpendingKeyset::PrivateMultiSig(private_benefactor_source) => {
                let beneficiary_keyset = input
                    .beneficiary_account
                    .active_spending_keyset()
                    .ok_or(ServiceError::NoActiveSpendingKeyset)?;

                match beneficiary_keyset {
                    SpendingKeyset::LegacyMultiSig(legacy_beneficiary_dest) => {
                        SigningMethod::InheritanceDowngradeSweep {
                            source_keyset: private_benefactor_source.clone(),
                            active_descriptor: legacy_beneficiary_dest.clone().into(),
                        }
                    }
                    SpendingKeyset::PrivateMultiSig(private_beneficiary_dest) => {
                        SigningMethod::PrivateSweep {
                            source_keyset: private_benefactor_source.clone(),
                            active_keyset: private_beneficiary_dest.clone(),
                        }
                    }
                }
            }
        };

        let signing_strategy: Box<dyn SigningStrategy> =
            Box::new(RecoverySweepSigningStrategy::new(
                &input.beneficiary_account.clone().into(),
                input.signing_processor,
                &input.psbt,
                signing_method,
                benefactor_keyset.network().into(),
                benefactor_keyset_id,
                &input.rpc_uris,
                self.screener_service.clone(),
                self.feature_flags_service.clone(),
                input.context_key,
            )?);

        let signed_psbt = signing_strategy.execute().await?;

        // If the following fails, the transaction would have been broadcast
        // but the claim would remain locked.
        // A retry would rebroadcast the transaction and complete the claim.
        self.mark_completed(
            claim,
            InheritanceCompletionMethod::WithPsbt {
                txid: signed_psbt.unsigned_tx.txid(),
            },
        )
        .await
    }

    async fn mark_completed(
        &self,
        claim: InheritanceClaim,
        completion_method: InheritanceCompletionMethod,
    ) -> Result<InheritanceClaimCompleted, ServiceError> {
        if matches!(&claim, InheritanceClaim::Completed(c) if matches!(c.completion_method, InheritanceCompletionMethod::WithPsbt{ ..}))
            && matches!(completion_method, InheritanceCompletionMethod::EmptyBalance)
        {
            return Err(ServiceError::AlreadyCompletedWithPsbt);
        }

        let completed_at = match &claim {
            InheritanceClaim::Locked(_) => OffsetDateTime::now_utc(),
            InheritanceClaim::Completed(completed_claim) => completed_claim.completed_at,
            _ => return Err(ServiceError::ClaimCompleteFailed),
        };

        let completed_claim = InheritanceClaimCompleted {
            common_fields: claim.common_fields().to_owned(),
            completion_method,
            completed_at,
        };

        let claim = self
            .repository
            .persist_inheritance_claim(&InheritanceClaim::Completed(completed_claim))
            .await?;

        if let InheritanceClaim::Completed(completed_claim) = claim {
            Ok(completed_claim)
        } else {
            Err(ServiceError::ClaimCompleteFailed)
        }
    }

    /// This function completes an inheritance claim for a valid benefactor and beneficiary.
    /// This is used when the beneficiary has no funds to claim and we want to mark the claim as completed.
    /// There must be a locked claim between the benefactor and beneficiary.
    ///
    /// # Arguments
    ///
    /// * `input`
    ///   * `inheritance_claim_id` - id for the locked inheritance claim
    ///   * `beneficiary_account` - The beneficiary account
    ///
    /// # Returns
    ///
    /// * The completed inheritance claim
    ///     
    #[instrument(skip(self, input))]
    pub async fn complete_without_psbt<'a>(
        &self,
        input: CompleteWithoutPsbtInput<'a>,
    ) -> Result<InheritanceClaimCompleted, ServiceError> {
        let (relationships, claim) = fetch_relationships_and_claim(
            self,
            &input.beneficiary_account.id,
            &input.inheritance_claim_id,
        )
        .await?;

        if !matches!(
            claim,
            InheritanceClaim::Completed(_) | InheritanceClaim::Locked(_)
        ) {
            return Err(ServiceError::LockedClaimNotFound);
        }

        filter_endorsed_relationship(
            relationships.customers,
            &claim.common_fields().recovery_relationship_id,
        )?;

        self.mark_completed(claim, InheritanceCompletionMethod::EmptyBalance)
            .await
    }
}
