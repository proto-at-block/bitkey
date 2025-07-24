use std::fmt::{Display, Formatter, Result};
use std::mem::discriminant;
use thiserror::Error;

#[derive(Clone, Debug, Error, PartialEq)]
pub enum SpendRuleCheckError {
    #[error("Invalid PSBT for given wallet: {0}")]
    BdkUtils(String),
    #[error("Invalid Sweep transaction. Contains output not addressed to destination wallet.")]
    OutputsDontBelongToDestinationWallet,
    #[error("Transaction spend total of {0} with existing spend of {1} for the day exceeds limit of {2}")]
    SpendLimitExceeded(u64, u64, u64),
    #[error("Spending limit inactive")]
    SpendLimitInactive,
    #[error("Invalid sweep transaction. Contains outputs to origin wallet.")]
    PsbtOutputsBelongToOriginWallet,
    #[error("Invalid transaction. All inputs don't belong to origin wallet.")]
    PsbtInputsDontBelongToOriginWallet,
    #[error("One or more script pub keys are invalid. Cannot check transaction.")]
    InvalidScriptPubKeys,
    #[error("One or more outputs belong to sanctioned individuals.")]
    OutputsBelongToSanctionedIndividuals,
    #[error("Error fetching spend amount: {0}")]
    CouldNotFetchSpendAmount(String),
    #[error("Transaction requires verification")]
    TransactionVerificationRequired,
}

#[derive(Error, Debug)]
pub struct SpendRuleCheckErrors(pub Vec<SpendRuleCheckError>);

impl Display for SpendRuleCheckErrors {
    fn fmt(&self, f: &mut Formatter<'_>) -> Result {
        for error in &self.0 {
            write!(f, "{} ", error)?;
        }
        Ok(())
    }
}

impl From<Vec<SpendRuleCheckError>> for SpendRuleCheckErrors {
    fn from(errors: Vec<SpendRuleCheckError>) -> Self {
        SpendRuleCheckErrors(errors)
    }
}

impl SpendRuleCheckErrors {
    pub fn has_error(&self, error: &SpendRuleCheckError) -> bool {
        self.0
            .iter()
            .any(|e| discriminant(e) == discriminant(error))
    }

    pub fn is_transaction_verification_required(&self) -> bool {
        matches!(
            self.0.as_slice(),
            [SpendRuleCheckError::TransactionVerificationRequired]
        )
    }
}
