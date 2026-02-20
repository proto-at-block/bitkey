use bdk_utils::{AttributableWallet, ChaincodeDelegationCollaboratorWallet};

use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::Wallet;
use types::account::spending::PrivateMultiSigSpendingKeyset;

use super::Rule;
use crate::metrics;
use crate::spend_rules::errors::SpendRuleCheckError;

pub(crate) struct NoPsbtOutputsBelongToWalletRule<'a> {
    wallet: &'a Wallet,
}

impl Rule for NoPsbtOutputsBelongToWalletRule<'_> {
    /// Ensure no outputs in the PSBT belong to this wallet.
    /// Requires derivation path information in the PSBT.
    /// Works for an un-synced walled if checking for self-spend.
    fn check_transaction(&self, psbt: &Psbt) -> Result<(), SpendRuleCheckError> {
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
    pub fn new(wallet: &'a Wallet) -> Self {
        NoPsbtOutputsBelongToWalletRule { wallet }
    }
}

pub(crate) struct NoPsbtOutputsBelongToWalletRuleV2<'a> {
    private_keyset: &'a PrivateMultiSigSpendingKeyset,
}

impl Rule for NoPsbtOutputsBelongToWalletRuleV2<'_> {
    fn check_transaction(&self, psbt: &Psbt) -> Result<(), SpendRuleCheckError> {
        let wallet = ChaincodeDelegationCollaboratorWallet::new(
            self.private_keyset.server_pub,
            self.private_keyset.app_pub,
            self.private_keyset.hardware_pub,
        );

        if wallet
            .is_addressed_to_self(psbt)
            .map_err(|e| SpendRuleCheckError::BdkUtils(e.to_string()))?
        {
            metrics::MOBILE_PAY_OUTPUTS_BELONG_TO_SELF.add(1, &[]);
            Err(SpendRuleCheckError::PsbtOutputsBelongToOriginWallet)
        } else {
            Ok(())
        }
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

    use bdk_utils::bdk::{
        bitcoin::{Amount, FeeRate},
        KeychainKind,
    };

    use crate::spend_rules::test::get_funded_wallet;

    #[test]
    fn non_self_spend_success() {
        let mut alice_wallet = get_funded_wallet(0);
        let mut bob_wallet = get_funded_wallet(1);
        let bob_address = bob_wallet
            .next_unused_address(KeychainKind::External)
            .address;
        let mut builder = alice_wallet.build_tx();
        builder
            .add_recipient(bob_address.script_pubkey(), Amount::from_sat(1000))
            .fee_rate(FeeRate::from_sat_per_vb_unchecked(5));
        let psbt = builder.finish().unwrap();
        let rule = NoPsbtOutputsBelongToWalletRule::new(&alice_wallet);
        assert!(rule.check_transaction(&psbt).is_ok());
    }

    #[test]
    fn self_spend_fails() {
        let mut alice_wallet = get_funded_wallet(0);
        let alice_address = alice_wallet
            .next_unused_address(KeychainKind::External)
            .address;
        let mut builder = alice_wallet.build_tx();
        builder
            .add_recipient(alice_address.script_pubkey(), Amount::from_sat(1000))
            .fee_rate(FeeRate::from_sat_per_vb_unchecked(5));
        let psbt = builder.finish().unwrap();

        let rule = NoPsbtOutputsBelongToWalletRule::new(&alice_wallet);
        assert!(rule.check_transaction(&psbt).is_err());
    }
}
