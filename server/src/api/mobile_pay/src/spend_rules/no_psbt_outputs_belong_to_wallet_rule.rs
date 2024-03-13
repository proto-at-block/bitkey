use bdk_utils::AttributableWallet;

use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::Wallet;

use crate::metrics;

use super::Rule;

pub(crate) struct NoPsbtOutputsBelongToWalletRule<'a> {
    wallet: &'a Wallet<AnyDatabase>,
}

impl<'a> Rule for NoPsbtOutputsBelongToWalletRule<'a> {
    /// Ensure no outputs in the PSBT belong to this wallet
    fn check_transaction(&self, psbt: &PartiallySignedTransaction) -> Result<(), String> {
        if self
            .wallet
            .is_addressed_to_self(psbt)
            .map_err(|err| format!("Invalid PSBT for given wallet: {err}"))?
        {
            metrics::MOBILE_PAY_OUTPUTS_BELONG_TO_SELF.add(1, &[]);
            Err("Invalid Mobile Pay transaction. Contains outputs to self.".to_string())
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
