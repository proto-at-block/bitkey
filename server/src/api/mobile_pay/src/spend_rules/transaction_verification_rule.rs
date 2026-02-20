use crate::entities::TransactionVerificationFeatures;
use crate::spend_rules::{errors::SpendRuleCheckError, Rule};

use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use bdk_utils::bdk::bitcoin::secp256k1::Secp256k1;
use bdk_utils::bdk::Wallet;
use bdk_utils::{
    get_total_outflow_for_psbt, ChaincodeDelegationCollaboratorWallet, ChaincodeDelegationPsbt,
};
use types::account::spending::PrivateMultiSigSpendingKeyset;
use types::transaction_verification::router::TransactionVerificationGrantView;
use wsm_compat::{wsm_inputs_from_bdk_psbt, wsm_pubkey_from_bdk, wsm_signature_from_bdk};

pub struct TransactionVerificationRule<'a> {
    wallet: &'a Wallet,
    features: Option<TransactionVerificationFeatures>,
}

impl Rule for TransactionVerificationRule<'_> {
    fn check_transaction(&self, psbt: &Psbt) -> Result<(), SpendRuleCheckError> {
        if self.requires_verification(psbt)? {
            Err(SpendRuleCheckError::TransactionVerificationRequired)
        } else {
            Ok(())
        }
    }
}

impl<'a> TransactionVerificationRule<'a> {
    pub fn new(wallet: &'a Wallet, features: Option<TransactionVerificationFeatures>) -> Self {
        TransactionVerificationRule { wallet, features }
    }

    fn requires_verification(&self, psbt: &Psbt) -> Result<bool, SpendRuleCheckError> {
        let Some(features) = self.features.as_ref() else {
            return Ok(false);
        };

        let spend_amount = get_total_outflow_for_psbt(self.wallet, psbt);
        features.requires_verification_for(spend_amount, psbt)
    }
}

pub(crate) struct TransactionVerificationRuleV2<'a> {
    private_keyset: &'a PrivateMultiSigSpendingKeyset,
    features: Option<TransactionVerificationFeatures>,
}

impl Rule for TransactionVerificationRuleV2<'_> {
    fn check_transaction(&self, psbt: &Psbt) -> Result<(), SpendRuleCheckError> {
        if self.requires_verification(psbt)? {
            Err(SpendRuleCheckError::TransactionVerificationRequired)
        } else {
            Ok(())
        }
    }
}

impl<'a> TransactionVerificationRuleV2<'a> {
    pub fn new(
        private_keyset: &'a PrivateMultiSigSpendingKeyset,
        features: Option<TransactionVerificationFeatures>,
    ) -> Self {
        TransactionVerificationRuleV2 {
            private_keyset,
            features,
        }
    }

    fn requires_verification(&self, psbt: &Psbt) -> Result<bool, SpendRuleCheckError> {
        let Some(features) = self.features.as_ref() else {
            return Ok(false);
        };

        let spend_amount = self.total_spend_for_psbt(psbt)?;

        features.requires_verification_for(spend_amount, psbt)
    }

    fn total_spend_for_psbt(&self, psbt: &Psbt) -> Result<u64, SpendRuleCheckError> {
        let chaincode_delegation_psbt = ChaincodeDelegationPsbt::new(
            psbt,
            vec![
                self.private_keyset.server_pub,
                self.private_keyset.app_pub,
                self.private_keyset.hardware_pub,
            ],
        )
        .map_err(|err| SpendRuleCheckError::InvalidChaincodeDelegationPsbt(err.to_string()))?;

        ChaincodeDelegationCollaboratorWallet::new(
            self.private_keyset.server_pub,
            self.private_keyset.app_pub,
            self.private_keyset.hardware_pub,
        )
        .get_outflow_for_psbt(&chaincode_delegation_psbt)
        .map_err(|err| SpendRuleCheckError::CouldNotFetchSpendAmount(err.to_string()))
    }
}

trait TransactionVerificationFeaturesExt {
    fn requires_verification_for(
        &self,
        spend_amount: u64,
        psbt: &Psbt,
    ) -> Result<bool, SpendRuleCheckError>;
}

impl TransactionVerificationFeaturesExt for TransactionVerificationFeatures {
    fn requires_verification_for(
        &self,
        spend_amount: u64,
        psbt: &Psbt,
    ) -> Result<bool, SpendRuleCheckError> {
        if spend_amount < self.verification_sats {
            return Ok(false);
        }

        let Some(grant) = &self.grant else {
            return Ok(true); // Needs verification but no successful verification found
        };

        validate_commitment(grant, psbt)?;
        verify_grant_signature(grant, &self.wik_pub_key)?;

        Ok(false)
    }
}

fn verify_grant_signature(
    grant: &TransactionVerificationGrantView,
    wik_pub_key: &PublicKey,
) -> Result<(), SpendRuleCheckError> {
    let secp = Secp256k1::verification_only();
    let hw_auth_public_key_wsm = wsm_pubkey_from_bdk(&grant.hw_auth_public_key)
        .map_err(|_| SpendRuleCheckError::InvalidTransactionVerificationGrant)?;
    let message = wsm_grant::tx_verification::generate_message(
        grant.hw_auth_public_key,
        grant.commitment.clone(),
    )
    .map_err(|_| SpendRuleCheckError::GenerateTransactionVerificationMessage)?;

    let signature_wsm = wsm_signature_from_bdk(&grant.signature)
        .map_err(|_| SpendRuleCheckError::InvalidTransactionVerificationGrant)?;
    let wik_pub_key_wsm = wsm_pubkey_from_bdk(wik_pub_key)
        .map_err(|_| SpendRuleCheckError::InvalidTransactionVerificationGrant)?;
    secp.verify_ecdsa(&message, &signature_wsm, &wik_pub_key_wsm)
        .map_err(|_| SpendRuleCheckError::InvalidCommitment)?;

    Ok(())
}

fn validate_commitment(
    grant: &TransactionVerificationGrantView,
    psbt: &Psbt,
) -> Result<(), SpendRuleCheckError> {
    let init: [u8; 32] = grant
        .reverse_hash_chain
        .last()
        .ok_or(SpendRuleCheckError::InvalidCommitment)?
        .clone()
        .try_into()
        .map_err(|_| SpendRuleCheckError::InvalidCommitment)?;

    let psbt_inputs_wsm =
        wsm_inputs_from_bdk_psbt(psbt).map_err(|_| SpendRuleCheckError::InvalidCommitment)?;

    let regenerated_commitment =
        wsm_grant::tx_verification::calculate_chained_sighashes(psbt_inputs_wsm, init)
            .map_err(|_| SpendRuleCheckError::InvalidCommitment)?
            .first()
            .ok_or(SpendRuleCheckError::InvalidCommitment)?
            .to_owned();

    if regenerated_commitment != grant.commitment {
        return Err(SpendRuleCheckError::InvalidCommitment);
    }
    Ok(())
}
