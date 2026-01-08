use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction as Psbt;
use bdk_utils::bdk::bitcoin::{Address, Network};
use errors::ApiError;
use feature_flags::flag::{evaluate_flag_value, ContextKey};
use feature_flags::service::Service as FeatureFlagsService;
use thiserror::Error;
use tracing::warn;
use types::account::entities::Account;

/// Feature flag key to force a sanctions hit.
pub const SANCTION_TEST_FLAG_KEY: &str = "f8e-sanction-test-account";

#[derive(Debug, Error, PartialEq)]
pub enum SanctionsScreenerError {
    #[error("One or more script pub keys are invalid. Cannot check transaction.")]
    InvalidScriptPubKeys,
    #[error("One or more outputs belong to sanctioned individuals.")]
    OutputsBelongToSanctionedIndividuals,
    #[error("Error screening transaction")]
    ScreenerError(#[from] ApiError),
}

/// Trait for sanctions screening; provides a default helper over should_block_transaction.
pub trait SanctionsScreener: Send + Sync {
    fn should_block_transaction(
        &self,
        account: &Account,
        addresses: &[String],
    ) -> Result<bool, SanctionsScreenerError>;

    fn screen_psbt_outputs_for_sanctions(
        &self,
        psbt: &Psbt,
        network: Network,
        account: &Account,
        feature_flags_service: &FeatureFlagsService,
        context_key: Option<ContextKey>,
    ) -> Result<(), SanctionsScreenerError> {
        let destination_addresses = psbt
            .unsigned_tx
            .output
            .iter()
            .map(|output| {
                Address::from_script(&output.script_pubkey, network)
                    .map(|address| address.to_string())
                    .map_err(|_| SanctionsScreenerError::InvalidScriptPubKeys)
            })
            .collect::<Result<Vec<String>, SanctionsScreenerError>>()?;

        let sanction_test_account = context_key
            .as_ref()
            .and_then(|ck| {
                evaluate_flag_value(feature_flags_service, SANCTION_TEST_FLAG_KEY, ck).ok()
            })
            .unwrap_or(false);

        if sanction_test_account
            || self.should_block_transaction(account, &destination_addresses)?
        {
            warn!("One or more outputs belong to sanctioned individuals.");
            Err(SanctionsScreenerError::OutputsBelongToSanctionedIndividuals)
        } else {
            Ok(())
        }
    }
}
