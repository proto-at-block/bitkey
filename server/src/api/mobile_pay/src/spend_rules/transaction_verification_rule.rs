use crate::entities::TransactionVerificationFeatures;
use crate::spend_rules::{errors::SpendRuleCheckError, Rule};

use bdk_utils::bdk::bitcoin::psbt::{Input, PartiallySignedTransaction};
use bdk_utils::bdk::bitcoin::secp256k1::Secp256k1;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::Wallet;
use bdk_utils::get_total_outflow_for_psbt;
use types::transaction_verification::router::TransactionVerificationGrantView;

pub struct TransactionVerificationRule<'a> {
    wallet: &'a Wallet<AnyDatabase>,
    features: Option<TransactionVerificationFeatures>,
}

impl Rule for TransactionVerificationRule<'_> {
    fn check_transaction(
        &self,
        psbt: &PartiallySignedTransaction,
    ) -> Result<(), SpendRuleCheckError> {
        if self.requires_verification(psbt)? {
            Err(SpendRuleCheckError::TransactionVerificationRequired)
        } else {
            Ok(())
        }
    }
}

impl<'a> TransactionVerificationRule<'a> {
    pub fn new(
        wallet: &'a Wallet<AnyDatabase>,
        features: Option<TransactionVerificationFeatures>,
    ) -> Self {
        TransactionVerificationRule { wallet, features }
    }

    fn requires_verification(
        &self,
        psbt: &PartiallySignedTransaction,
    ) -> Result<bool, SpendRuleCheckError> {
        let Some(features) = &self.features else {
            return Ok(false);
        };

        let spend_amount = get_total_outflow_for_psbt(self.wallet, psbt);
        if spend_amount < features.verification_sats {
            return Ok(false);
        }

        let Some(grant) = &features.grant else {
            return Ok(true); // Needs verification but no successful verification found
        };

        // Validate the grant's hash chain and commitment to the PSBT
        Self::validate_commitment(grant, &psbt.inputs)?;

        // Ensure the validity of the signature
        let secp = Secp256k1::new();
        let message = wsm_grant::tx_verification::generate_message(
            grant.hw_auth_public_key,
            grant.commitment.to_owned(),
        )
        .map_err(|_| SpendRuleCheckError::GenerateTransactionVerificationMessage)?;
        secp.verify_ecdsa(&message, &grant.signature, &features.wik_pub_key)?;

        Ok(false)
    }

    fn validate_commitment(
        grant: &TransactionVerificationGrantView,
        psbt_inputs: &[Input],
    ) -> Result<(), SpendRuleCheckError> {
        let init: [u8; 32] = grant
            .reverse_hash_chain
            .last()
            .ok_or(SpendRuleCheckError::InvalidCommitment)?
            .clone()
            .try_into()
            .map_err(|_| SpendRuleCheckError::InvalidCommitment)?;

        let regenerated_commitment =
            wsm_grant::tx_verification::calculate_chained_sighashes(psbt_inputs.to_vec(), init)
                .map_err(|_| SpendRuleCheckError::InvalidCommitment)?
                .first()
                .ok_or(SpendRuleCheckError::InvalidCommitment)?
                .to_owned();

        if regenerated_commitment != grant.commitment {
            return Err(SpendRuleCheckError::InvalidCommitment);
        }
        Ok(())
    }
}
