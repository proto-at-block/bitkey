use super::{
    error::ServiceError, fetch_relationships_and_claim, filter_endorsed_relationship, Service,
};

use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::Wallet;
use bdk_utils::ElectrumRpcUris;
use feature_flags::flag::ContextKey;
use mobile_pay::signing_processor::{Broadcaster, Signer, SigningMethod, SigningValidator};
use mobile_pay::spend_rules::SpendRuleSet;
use time::OffsetDateTime;
use tracing::instrument;
use types::account::entities::FullAccount;
use types::recovery::inheritance::claim::{
    InheritanceClaim, InheritanceClaimCompleted, InheritanceClaimId, InheritanceCompletionMethod,
};

#[derive(Debug, Clone)]
pub(crate) struct SignAndCompleteInheritanceClaimInput<'a, T: SigningValidator> {
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

        let (keyset_id, descriptor) = self
            .fetch_active_benefactor_descriptor_keyset(
                &claim.common_fields().recovery_relationship_id,
            )
            .await?;

        let beneficiary_descriptor = input
            .beneficiary_account
            .active_descriptor_keyset()
            .ok_or(ServiceError::IncompatibleAccountType)?
            .clone();

        let benefactor_wallet = descriptor.generate_wallet(false, &input.rpc_uris)?;
        let beneficiary_wallet =
            generate_beneficiary_wallet(input.beneficiary_account, &input.rpc_uris)?;

        let rules = SpendRuleSet::sweep(
            &benefactor_wallet,
            &beneficiary_wallet,
            self.screener_service.clone(),
            self.feature_flags_service.clone(),
            input.context_key,
        );

        let mut broadcaster = input
            .signing_processor
            .validate(&input.psbt, rules)?
            .sign_transaction(
                &input.rpc_uris,
                &SigningMethod::LegacySweep {
                    source_descriptor: descriptor,
                    active_descriptor: beneficiary_descriptor,
                },
                &keyset_id,
            )
            .await?;

        let signed_psbt = broadcaster.finalized_psbt();

        broadcaster.broadcast_transaction(&input.rpc_uris, benefactor_wallet.network())?;

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

fn generate_beneficiary_wallet(
    beneficiary_account: &FullAccount,
    rpc_uris: &ElectrumRpcUris,
) -> Result<Wallet<AnyDatabase>, ServiceError> {
    let keyset = beneficiary_account
        .active_descriptor_keyset()
        .ok_or(ServiceError::IncompatibleAccountType)?;

    // W-9888: Syncing the wallet because the AllPsbtOutputsBelongToWalletRule requires it
    Ok(keyset.generate_wallet(true, rpc_uris)?)
}
