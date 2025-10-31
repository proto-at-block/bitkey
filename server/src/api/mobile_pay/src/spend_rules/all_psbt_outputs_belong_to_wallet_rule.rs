use bdk_utils::{
    is_psbt_addressed_to_attributable_wallet, is_psbt_addressed_to_wallet,
    ChaincodeDelegationCollaboratorWallet,
};

use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::Wallet;
use types::account::spending::PrivateMultiSigSpendingKeyset;

use super::Rule;
use crate::metrics;
use crate::spend_rules::errors::SpendRuleCheckError;

pub(crate) struct AllPsbtOutputsBelongToWalletRule<'a> {
    wallet: &'a Wallet<AnyDatabase>,
}

impl Rule for AllPsbtOutputsBelongToWalletRule<'_> {
    /// Ensure all the outputs in the PSBT belong to this wallet
    /// Requires a synced wallet
    fn check_transaction(
        &self,
        psbt: &PartiallySignedTransaction,
    ) -> Result<(), SpendRuleCheckError> {
        if !is_psbt_addressed_to_wallet(self.wallet, psbt)
            .map_err(|err| SpendRuleCheckError::BdkUtils(err.to_string()))?
        {
            metrics::SWEEP_OUTPUTS_DONT_BELONG_TO_ACTIVE_KEYSET.add(1, &[]);
            Err(SpendRuleCheckError::OutputsDontBelongToDestinationWallet)
        } else {
            Ok(())
        }
    }
}

impl<'a> AllPsbtOutputsBelongToWalletRule<'a> {
    pub fn new(wallet: &'a Wallet<AnyDatabase>) -> Self {
        AllPsbtOutputsBelongToWalletRule { wallet }
    }
}

pub(crate) struct AllPsbtOutputsBelongToWalletRuleV2<'a> {
    private_keyset: &'a PrivateMultiSigSpendingKeyset,
}

impl Rule for AllPsbtOutputsBelongToWalletRuleV2<'_> {
    fn check_transaction(
        &self,
        psbt: &PartiallySignedTransaction,
    ) -> Result<(), SpendRuleCheckError> {
        let wallet = ChaincodeDelegationCollaboratorWallet::new(
            self.private_keyset.server_pub,
            self.private_keyset.app_pub,
            self.private_keyset.hardware_pub,
        );

        if is_psbt_addressed_to_attributable_wallet(&wallet, psbt)
            .map_err(|e| SpendRuleCheckError::BdkUtils(e.to_string()))?
        {
            Ok(())
        } else {
            metrics::SWEEP_OUTPUTS_DONT_BELONG_TO_ACTIVE_KEYSET.add(1, &[]);
            Err(SpendRuleCheckError::OutputsDontBelongToDestinationWallet)
        }
    }
}

impl<'a> AllPsbtOutputsBelongToWalletRuleV2<'a> {
    pub fn new(private_keyset: &'a PrivateMultiSigSpendingKeyset) -> Self {
        AllPsbtOutputsBelongToWalletRuleV2 { private_keyset }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    use bdk_utils::bdk::wallet::{get_funded_wallet, AddressIndex};
    use bdk_utils::bdk::FeeRate;

    #[test]
    fn non_self_spend_fails() {
        let alice_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
        let bob_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/1/*)").0;
        let bob_address = bob_wallet.get_address(AddressIndex::New).unwrap();
        let mut builder = alice_wallet.build_tx();
        builder
            .add_recipient(bob_address.script_pubkey(), 1_000)
            .fee_rate(FeeRate::from_sat_per_vb(5.0));
        let (psbt, _) = builder.finish().unwrap();
        let rule = AllPsbtOutputsBelongToWalletRule::new(&alice_wallet);
        assert!(rule.check_transaction(&psbt).is_err());
    }

    #[test]
    fn self_spend_succeeds() {
        let alice_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
        let alice_address = alice_wallet.get_address(AddressIndex::New).unwrap();
        let mut builder = alice_wallet.build_tx();
        builder
            .add_recipient(alice_address.script_pubkey(), 1_000)
            .fee_rate(FeeRate::from_sat_per_vb(5.0));
        let (psbt, _) = builder.finish().unwrap();
        let rule = AllPsbtOutputsBelongToWalletRule::new(&alice_wallet);
        assert!(rule.check_transaction(&psbt).is_ok());
    }
}
