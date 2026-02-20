use bdk_utils::{
    is_psbt_addressed_to_attributable_wallet, is_psbt_addressed_to_wallet,
    ChaincodeDelegationCollaboratorWallet,
};

use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::Wallet;
use types::account::spending::PrivateMultiSigSpendingKeyset;

use super::Rule;
use crate::metrics;
use crate::spend_rules::errors::SpendRuleCheckError;

pub(crate) struct AllPsbtOutputsBelongToWalletRule<'a> {
    wallet: &'a Wallet,
}

impl Rule for AllPsbtOutputsBelongToWalletRule<'_> {
    /// Ensure all the outputs in the PSBT belong to this wallet
    /// Requires a synced wallet
    fn check_transaction(&self, psbt: &Psbt) -> Result<(), SpendRuleCheckError> {
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
    pub fn new(wallet: &'a Wallet) -> Self {
        AllPsbtOutputsBelongToWalletRule { wallet }
    }
}

pub(crate) struct AllPsbtOutputsBelongToWalletRuleV2<'a> {
    private_keyset: &'a PrivateMultiSigSpendingKeyset,
}

impl Rule for AllPsbtOutputsBelongToWalletRuleV2<'_> {
    fn check_transaction(&self, psbt: &Psbt) -> Result<(), SpendRuleCheckError> {
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
    use bdk_utils::bdk::{
        bitcoin::{Amount, FeeRate},
        KeychainKind,
    };

    use super::*;
    use crate::spend_rules::test::get_funded_wallet;

    #[test]
    fn non_self_spend_fails() {
        let mut alice_wallet = get_funded_wallet(0);
        let mut bob_wallet = get_funded_wallet(1);
        let bob_address = bob_wallet.next_unused_address(KeychainKind::External);

        let mut builder = alice_wallet.build_tx();
        builder
            .add_recipient(bob_address.address.script_pubkey(), Amount::from_sat(1_000))
            .fee_rate(FeeRate::from_sat_per_vb_unchecked(5));
        let psbt = builder.finish().unwrap();

        let rule = AllPsbtOutputsBelongToWalletRule::new(&alice_wallet);
        assert!(rule.check_transaction(&psbt).is_err());
    }

    #[test]
    fn self_spend_succeeds() {
        let mut alice_wallet = get_funded_wallet(0);
        let alice_address = alice_wallet.next_unused_address(KeychainKind::External);
        let mut builder = alice_wallet.build_tx();
        builder
            .add_recipient(
                alice_address.address.script_pubkey(),
                Amount::from_sat(1_000),
            )
            .fee_rate(FeeRate::from_sat_per_vb_unchecked(5));
        let psbt = builder.finish().unwrap();

        let rule = AllPsbtOutputsBelongToWalletRule::new(&alice_wallet);
        assert!(rule.check_transaction(&psbt).is_ok());
    }
}
