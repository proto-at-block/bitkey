use crate::entities::TransactionVerificationFeatures;
use crate::spend_rules::{errors::SpendRuleCheckError, Rule};

use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::Wallet;
use bdk_utils::get_total_outflow_for_psbt;

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
        match &self.features {
            Some(f) => {
                let spend_amount = get_total_outflow_for_psbt(self.wallet, psbt);
                Ok(spend_amount >= f.verification_sats)
            }
            None => Ok(false),
        }
    }
}
