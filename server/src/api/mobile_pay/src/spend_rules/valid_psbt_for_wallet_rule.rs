use bdk_utils::AttributableWallet;
use time::OffsetDateTime;

use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction;
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::Wallet;

use crate::daily_spend_record::entities::SpendingEntry;
use crate::entities::Features;
use crate::metrics;

use super::Rule;

pub(crate) struct ValidPsbtForWalletRule;

impl Rule for ValidPsbtForWalletRule {
    /// Ensure all the inputs in the PSBT belong to this wallet
    fn check_transaction(
        &self,
        wallet: &Wallet<AnyDatabase>,
        psbt: &PartiallySignedTransaction,
        _: &Features,
        _: &Vec<&SpendingEntry>,
        _: OffsetDateTime,
    ) -> Result<(), String> {
        if wallet
            .all_inputs_are_from_self(psbt)
            .map_err(|err| format!("Invalid PSBT for given wallet: {err}"))?
        {
            Ok(())
        } else {
            metrics::MOBILE_PAY_INPUTS_DO_NOT_BELONG_TO_SELF.add(1, &[]);
            Err("Invalid PSBT for given Wallet".to_string())
        }
    }
}

#[cfg(test)]
mod tests {
    use time::OffsetDateTime;

    use bdk_utils::bdk::wallet::{get_funded_wallet, AddressIndex};
    use bdk_utils::bdk::FeeRate;

    use crate::entities::Features;
    use crate::spend_rules::valid_psbt_for_wallet_rule::ValidPsbtForWalletRule;
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
        let rule = ValidPsbtForWalletRule {};
        assert!(rule
            .check_transaction(
                &alice_wallet,
                &psbt,
                &Features::default(),
                &Vec::new(),
                OffsetDateTime::now_utc()
            )
            .is_err());
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
        let rule = ValidPsbtForWalletRule {};
        assert!(rule
            .check_transaction(
                &alice_wallet,
                &psbt,
                &Features::default(),
                &Vec::new(),
                OffsetDateTime::now_utc()
            )
            .is_ok());
    }
}
