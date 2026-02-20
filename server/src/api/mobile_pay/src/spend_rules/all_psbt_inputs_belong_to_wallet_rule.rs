use bdk_utils::{AttributableWallet, ChaincodeDelegationCollaboratorWallet};

use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::Wallet;
use types::account::spending::PrivateMultiSigSpendingKeyset;

use super::Rule;
use crate::metrics;
use crate::spend_rules::errors::SpendRuleCheckError;

pub(crate) struct AllPsbtInputsBelongToWalletRule<'a> {
    wallet: &'a Wallet,
}

impl Rule for AllPsbtInputsBelongToWalletRule<'_> {
    /// Ensure all the inputs in the PSBT belong to this wallet
    fn check_transaction(&self, psbt: &Psbt) -> Result<(), SpendRuleCheckError> {
        if self
            .wallet
            .all_inputs_are_from_self(psbt)
            .map_err(|err| SpendRuleCheckError::BdkUtils(err.to_string()))?
        {
            Ok(())
        } else {
            metrics::MOBILE_PAY_INPUTS_DO_NOT_BELONG_TO_SELF.add(1, &[]);
            Err(SpendRuleCheckError::PsbtInputsDontBelongToOriginWallet)
        }
    }
}

impl<'a> AllPsbtInputsBelongToWalletRule<'a> {
    pub fn new(wallet: &'a Wallet) -> Self {
        AllPsbtInputsBelongToWalletRule { wallet }
    }
}

pub(crate) struct AllPsbtInputsBelongToWalletRuleV2<'a> {
    private_keyset: &'a PrivateMultiSigSpendingKeyset,
}

impl Rule for AllPsbtInputsBelongToWalletRuleV2<'_> {
    fn check_transaction(&self, psbt: &Psbt) -> Result<(), SpendRuleCheckError> {
        // Build on demand to avoid changing constructor signature to return Result
        let wallet = ChaincodeDelegationCollaboratorWallet::new(
            self.private_keyset.server_pub,
            self.private_keyset.app_pub,
            self.private_keyset.hardware_pub,
        );

        if wallet
            .all_inputs_are_from_self(psbt)
            .map_err(|e| SpendRuleCheckError::BdkUtils(e.to_string()))?
        {
            Ok(())
        } else {
            metrics::MOBILE_PAY_INPUTS_DO_NOT_BELONG_TO_SELF.add(1, &[]);
            Err(SpendRuleCheckError::PsbtInputsDontBelongToOriginWallet)
        }
    }
}

impl<'a> AllPsbtInputsBelongToWalletRuleV2<'a> {
    pub fn new(private_keyset: &'a PrivateMultiSigSpendingKeyset) -> Self {
        AllPsbtInputsBelongToWalletRuleV2 { private_keyset }
    }
}

#[cfg(test)]
mod tests {

    use bdk_utils::bdk::bitcoin::{Amount, FeeRate};
    use bdk_utils::bdk::KeychainKind;

    use crate::spend_rules::all_psbt_inputs_belong_to_wallet_rule::AllPsbtInputsBelongToWalletRule;
    use crate::spend_rules::test::get_funded_wallet;
    use crate::spend_rules::Rule;

    #[test]
    fn invalid_psbt_for_wallet() {
        let alice_wallet = get_funded_wallet(0);
        let mut bob_wallet = get_funded_wallet(1);
        let bob_address = bob_wallet.next_unused_address(KeychainKind::External);

        let mut builder = bob_wallet.build_tx();
        builder
            .add_recipient(bob_address.address.script_pubkey(), Amount::from_sat(1_000))
            .fee_rate(FeeRate::from_sat_per_vb_unchecked(5));
        let psbt = builder.finish().unwrap();

        let rule = AllPsbtInputsBelongToWalletRule::new(&alice_wallet);
        assert!(rule.check_transaction(&psbt).is_err());
    }

    #[test]
    fn valid_psbt_for_wallet() {
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
        let rule = AllPsbtInputsBelongToWalletRule::new(&alice_wallet);
        assert!(rule.check_transaction(&psbt).is_ok());
    }
}
