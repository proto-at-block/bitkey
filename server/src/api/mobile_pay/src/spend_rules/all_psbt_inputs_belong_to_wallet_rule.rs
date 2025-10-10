use bdk_utils::{AttributableWallet, ChaincodeDelegationCollaboratorWallet};

use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::Wallet;
use types::account::spending::PrivateMultiSigSpendingKeyset;

use super::Rule;
use crate::metrics;
use crate::spend_rules::errors::SpendRuleCheckError;

pub(crate) struct AllPsbtInputsBelongToWalletRule<'a> {
    wallet: &'a Wallet<AnyDatabase>,
}

impl Rule for AllPsbtInputsBelongToWalletRule<'_> {
    /// Ensure all the inputs in the PSBT belong to this wallet
    fn check_transaction(
        &self,
        psbt: &PartiallySignedTransaction,
    ) -> Result<(), SpendRuleCheckError> {
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
    pub fn new(wallet: &'a Wallet<AnyDatabase>) -> Self {
        AllPsbtInputsBelongToWalletRule { wallet }
    }
}

pub(crate) struct AllPsbtInputsBelongToWalletRuleV2<'a> {
    private_keyset: &'a PrivateMultiSigSpendingKeyset,
}

impl Rule for AllPsbtInputsBelongToWalletRuleV2<'_> {
    fn check_transaction(
        &self,
        psbt: &PartiallySignedTransaction,
    ) -> Result<(), SpendRuleCheckError> {
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

    use bdk_utils::bdk::wallet::{get_funded_wallet, AddressIndex};
    use bdk_utils::bdk::FeeRate;

    use crate::spend_rules::all_psbt_inputs_belong_to_wallet_rule::AllPsbtInputsBelongToWalletRule;
    use crate::spend_rules::Rule;

    #[test]
    fn invalid_psbt_for_wallet() {
        let alice_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
        let bob_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/1/*)").0;
        let bob_address = bob_wallet.get_address(AddressIndex::New).unwrap();
        let mut builder = bob_wallet.build_tx();
        builder
            .add_recipient(bob_address.script_pubkey(), 1_000)
            .fee_rate(FeeRate::from_sat_per_vb(5.0));
        let (psbt, _) = builder.finish().unwrap();
        let rule = AllPsbtInputsBelongToWalletRule::new(&alice_wallet);
        assert!(rule.check_transaction(&psbt).is_err());
    }

    #[test]
    fn valid_psbt_for_wallet() {
        let alice_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
        let alice_address = alice_wallet.get_address(AddressIndex::New).unwrap();
        let mut builder = alice_wallet.build_tx();
        builder
            .add_recipient(alice_address.script_pubkey(), 1_000)
            .fee_rate(FeeRate::from_sat_per_vb(5.0));
        let (psbt, _) = builder.finish().unwrap();
        let rule = AllPsbtInputsBelongToWalletRule::new(&alice_wallet);
        assert!(rule.check_transaction(&psbt).is_ok());
    }
}
