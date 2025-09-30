use bdk_utils::AttributableWallet;

use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::Wallet;
use types::account::spending::PrivateMultiSigSpendingKeyset;

use super::Rule;
use crate::metrics;
use crate::spend_rules::errors::SpendRuleCheckError;

pub(crate) struct NoPsbtOutputsBelongToWalletRule<'a> {
    wallet: &'a Wallet<AnyDatabase>,
}

impl Rule for NoPsbtOutputsBelongToWalletRule<'_> {
    /// Ensure no outputs in the PSBT belong to this wallet.
    /// Requires derivation path information in the PSBT.
    /// Works for an un-synced walled if checking for self-spend.
    fn check_transaction(
        &self,
        psbt: &PartiallySignedTransaction,
    ) -> Result<(), SpendRuleCheckError> {
        if self
            .wallet
            .is_addressed_to_self(psbt)
            .map_err(|err| SpendRuleCheckError::BdkUtils(err.to_string()))?
        {
            metrics::MOBILE_PAY_OUTPUTS_BELONG_TO_SELF.add(1, &[]);
            Err(SpendRuleCheckError::PsbtOutputsBelongToOriginWallet)
        } else {
            Ok(())
        }
    }
}

impl<'a> NoPsbtOutputsBelongToWalletRule<'a> {
    pub fn new(wallet: &'a Wallet<AnyDatabase>) -> Self {
        NoPsbtOutputsBelongToWalletRule { wallet }
    }
}

pub(crate) struct NoPsbtOutputsBelongToWalletRuleV2<'a> {
    private_keyset: &'a PrivateMultiSigSpendingKeyset,
}

impl Rule for NoPsbtOutputsBelongToWalletRuleV2<'_> {
    fn check_transaction(
        &self,
        psbt: &PartiallySignedTransaction,
    ) -> Result<(), SpendRuleCheckError> {
        Err(SpendRuleCheckError::PsbtOutputsBelongToOriginWallet)
    }
}

impl<'a> NoPsbtOutputsBelongToWalletRuleV2<'a> {
    pub fn new(private_keyset: &'a PrivateMultiSigSpendingKeyset) -> Self {
        NoPsbtOutputsBelongToWalletRuleV2 { private_keyset }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    use bdk_utils::bdk::wallet::{get_funded_wallet, AddressIndex};
    use bdk_utils::bdk::FeeRate;

    #[test]
    fn non_self_spend_success() {
        let alice_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
        let bob_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/1/*)").0;
        let bob_address = bob_wallet.get_address(AddressIndex::New).unwrap();
        let mut builder = alice_wallet.build_tx();
        builder
            .add_recipient(bob_address.script_pubkey(), 1_000)
            .fee_rate(FeeRate::from_sat_per_vb(5.0));
        let (psbt, _) = builder.finish().unwrap();
        let rule = NoPsbtOutputsBelongToWalletRule::new(&alice_wallet);
        assert!(rule.check_transaction(&psbt).is_ok());
    }

    #[test]
    fn self_spend_fails() {
        let alice_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
        let alice_address = alice_wallet.get_address(AddressIndex::New).unwrap();
        let mut builder = alice_wallet.build_tx();
        builder
            .add_recipient(alice_address.script_pubkey(), 1_000)
            .fee_rate(FeeRate::from_sat_per_vb(5.0));
        let (psbt, _) = builder.finish().unwrap();
        let rule = NoPsbtOutputsBelongToWalletRule::new(&alice_wallet);
        assert!(rule.check_transaction(&psbt).is_err());
    }
}
