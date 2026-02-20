mod helpers;

#[cfg(feature = "pcsc")]
mod recordings {

    use bdk_wallet::{KeychainKind, Wallet};
    use bitcoin::{
        bip32::ChildNumber,
        hashes::{sha256, Hash as _},
        psbt::Psbt as PartiallySignedTransaction,
        secp256k1::{Message, Secp256k1},
        Amount,
    };
    use miniscript::{descriptor::DescriptorXKey, Descriptor, DescriptorPublicKey};
    use wca::{fwpb::BtcNetwork::Signet, pcsc::Performer};

    use crate::helpers::{expectations::Expectations, pcsc::RecordingTransactor};

    #[test]
    fn test_authentication() {
        let expectations = Expectations::new("authentication");
        let rt = RecordingTransactor::new(&expectations).unwrap();

        let challenge = "0123456789abcdef".as_bytes();
        let message = Message::from_digest(sha256::Hash::hash(challenge).to_byte_array());

        let authentication_key = rt
            .perform(wca::commands::GetAuthenticationKey::new())
            .unwrap();
        let signature = rt
            .perform(wca::commands::SignChallenge::new(challenge.to_vec(), false))
            .unwrap();

        Secp256k1::new()
            .verify_ecdsa(&message, &signature, &authentication_key)
            .unwrap();
    }

    fn extend_descriptor_public_key(
        origin: &DescriptorPublicKey,
        path: &[ChildNumber],
    ) -> DescriptorPublicKey {
        match origin {
            DescriptorPublicKey::Single(_) => unimplemented!(),
            DescriptorPublicKey::MultiXPub(_) => unimplemented!(),
            DescriptorPublicKey::XPub(xpub) => DescriptorPublicKey::XPub(DescriptorXKey {
                derivation_path: xpub.derivation_path.extend(path),
                origin: xpub.origin.clone(),
                ..*xpub
            }),
        }
    }

    fn get_funded_wallet(base: &DescriptorPublicKey) -> Wallet {
        let spending: DescriptorPublicKey =
            extend_descriptor_public_key(base, &[ChildNumber::Normal { index: 0 }]);
        let change: DescriptorPublicKey =
            extend_descriptor_public_key(base, &[ChildNumber::Normal { index: 1 }]);
        let descriptor = Descriptor::<DescriptorPublicKey>::new_wpkh(spending).unwrap();
        let change_descriptor = Descriptor::<DescriptorPublicKey>::new_wpkh(change).unwrap();
        let (wallet, _) = bdk_wallet::test_utils::get_funded_wallet(
            &descriptor.to_string(),
            &change_descriptor.to_string(),
        );
        wallet
    }

    fn normal_transaction(
        from: &mut Wallet,
        to: &mut Wallet,
        amount: u64,
    ) -> PartiallySignedTransaction {
        let destination = to.reveal_next_address(KeychainKind::External);

        let mut builder = from.build_tx();
        builder
            .add_recipient(destination.script_pubkey(), Amount::from_sat(amount))
            .ordering(bdk_wallet::TxOrdering::Untouched); // A deterministic PSBT is nice for testing purposes.
        builder.finish().unwrap()
    }

    fn drain_wallet(from: &mut Wallet, to: &mut Wallet) -> PartiallySignedTransaction {
        let destination = to.reveal_next_address(KeychainKind::External);

        let mut builder = from.build_tx();
        builder.drain_wallet().drain_to(destination.script_pubkey());
        builder.finish().unwrap()
    }

    fn is_finalized(psbt: &PartiallySignedTransaction) -> bool {
        psbt.inputs
            .iter()
            .all(|input| input.final_script_sig.is_some() || input.final_script_witness.is_some())
    }

    #[ignore]
    #[test]
    fn test_spending_derive() {
        let expectations = Expectations::new("spending-derive");
        let rt = RecordingTransactor::new(&expectations).unwrap();

        let source = {
            let a = rt
                .perform(wca::commands::GetInitialSpendingKey::new(Signet))
                .unwrap();
            let b = rt
                .perform(wca::commands::GetInitialSpendingKey::new(Signet))
                .unwrap();
            assert_eq!(a, b);
            a
        };

        let destination = {
            let a = rt
                .perform(wca::commands::GetNextSpendingKey::new(
                    vec![source.clone()],
                    Signet,
                ))
                .unwrap();
            let b = rt
                .perform(wca::commands::GetNextSpendingKey::new(
                    vec![source.clone()],
                    Signet,
                ))
                .unwrap();
            assert_eq!(a, b);
            a
        };

        assert_ne!(source, destination);

        let mut source_wallet = get_funded_wallet(&source);
        let mut destination_wallet = get_funded_wallet(&destination);
        let unsigned = normal_transaction(&mut source_wallet, &mut destination_wallet, 5000);
        let mut signed = rt
            .perform(wca::commands::SignTransaction::new(
                unsigned,
                source.master_fingerprint(),
                false,
            ))
            .unwrap();
        assert!(is_finalized(&signed));
        let finalized = source_wallet
            .finalize_psbt(&mut signed, Default::default())
            .unwrap();
        assert!(finalized);
    }

    #[ignore]
    #[test]
    fn test_drain_derive() {
        let expectations = Expectations::new("drain-derive");
        let rt = RecordingTransactor::new(&expectations).unwrap();

        let source = {
            let a = rt
                .perform(wca::commands::GetInitialSpendingKey::new(Signet))
                .unwrap();
            let b = rt
                .perform(wca::commands::GetInitialSpendingKey::new(Signet))
                .unwrap();
            assert_eq!(a, b);
            a
        };

        let destination = {
            let a = rt
                .perform(wca::commands::GetNextSpendingKey::new(
                    vec![source.clone()],
                    Signet,
                ))
                .unwrap();
            let b = rt
                .perform(wca::commands::GetNextSpendingKey::new(
                    vec![source.clone()],
                    Signet,
                ))
                .unwrap();
            assert_eq!(a, b);
            a
        };

        assert_ne!(source, destination);

        let mut source_wallet = get_funded_wallet(&source);
        let mut destination_wallet = get_funded_wallet(&destination);
        let unsigned = drain_wallet(&mut source_wallet, &mut destination_wallet);
        let mut signed = rt
            .perform(wca::commands::SignTransaction::new(
                unsigned,
                source.master_fingerprint(),
                false,
            ))
            .unwrap();
        assert!(is_finalized(&signed));
        let finalized = source_wallet
            .finalize_psbt(&mut signed, Default::default())
            .unwrap();
        assert!(finalized);
    }
}
