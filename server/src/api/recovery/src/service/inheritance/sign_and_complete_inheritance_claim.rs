use super::{
    error::ServiceError, fetch_relationships_and_claim, filter_endorsed_relationship, Service,
};

use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::Wallet;
use bdk_utils::ElectrumRpcUris;
use mobile_pay::signing_processor::{SignerBroadcaster, SigningValidator};
use mobile_pay::spend_rules::SpendRuleSet;
use time::OffsetDateTime;
use tracing::instrument;
use types::account::entities::Account;
use types::recovery::inheritance::claim::{
    InheritanceClaim, InheritanceClaimCompleted, InheritanceClaimId,
};

#[derive(Debug, Clone)]
pub(crate) struct SignAndCompleteInheritanceClaimInput<T: SigningValidator> {
    pub signing_processor: T,
    pub rpc_uris: ElectrumRpcUris,
    pub inheritance_claim_id: InheritanceClaimId,
    pub beneficiary_account: Account,
    pub psbt: Psbt,
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
    pub async fn sign_and_complete<T>(
        &self,
        input: SignAndCompleteInheritanceClaimInput<T>,
    ) -> Result<InheritanceClaimCompleted, ServiceError>
    where
        T: SigningValidator,
    {
        let (relationships, claim) = fetch_relationships_and_claim(
            self,
            input.beneficiary_account.get_id(),
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

        let benefactor_wallet = descriptor.generate_wallet(false, &input.rpc_uris)?;
        let beneficiary_wallet =
            generate_beneficiary_wallet(&input.beneficiary_account, &input.rpc_uris)?;

        let rules = SpendRuleSet::sweep(
            &benefactor_wallet,
            &beneficiary_wallet,
            self.screener_service.clone(),
        );

        let signed_psbt = input
            .signing_processor
            .validate(&input.psbt, rules)?
            .sign_and_broadcast_transaction(&descriptor, &keyset_id)
            .await?;

        // If the following fails, the transaction would have been broadcast
        // but the claim would remain locked.
        // A retry would rebroadcast the transaction and complete the claim.
        self.mark_completed(claim, &signed_psbt).await
    }

    async fn mark_completed(
        &self,
        claim: InheritanceClaim,
        signed_psbt: &Psbt,
    ) -> Result<InheritanceClaimCompleted, ServiceError> {
        let completed_at = match &claim {
            InheritanceClaim::Locked(_) => OffsetDateTime::now_utc(),
            InheritanceClaim::Completed(completed_claim) => completed_claim.completed_at,
            _ => return Err(ServiceError::ClaimCompleteFailed),
        };

        let completed_claim = InheritanceClaimCompleted {
            common_fields: claim.common_fields().to_owned(),
            psbt: signed_psbt.to_owned(),
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
}

fn generate_beneficiary_wallet(
    beneficiary_account: &Account,
    rpc_uris: &ElectrumRpcUris,
) -> Result<Wallet<AnyDatabase>, ServiceError> {
    if let Account::Full(full_account) = beneficiary_account {
        let keyset = full_account
            .active_descriptor_keyset()
            .ok_or(ServiceError::IncompatibleAccountType)?;

        // W-9888: Syncing the wallet because the AllPsbtOutputsBelongToWalletRule requires it
        Ok(keyset.generate_wallet(true, rpc_uris)?)
    } else {
        Err(ServiceError::IncompatibleAccountType)
    }
}
