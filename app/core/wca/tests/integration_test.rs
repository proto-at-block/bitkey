mod helpers;

#[cfg(feature = "pcsc")]
mod recordings {

    use bdk::{database::AnyDatabase, wallet::AddressIndex, Wallet};
    use bitcoin::{
        hashes::sha256,
        psbt::PartiallySignedTransaction,
        secp256k1::{Message, Secp256k1},
        util::bip32::ChildNumber,
    };
    use miniscript::{descriptor::DescriptorXKey, Descriptor, DescriptorPublicKey};
    use wca::{fwpb::BtcNetwork::Signet, pcsc::Performer};

    use crate::helpers::{expectations::Expectations, pcsc::RecordingTransactor};

    #[test]
    fn test_authentication() {
        let expectations = Expectations::new("authentication");
        let rt = RecordingTransactor::new(&expectations).unwrap();

        let challenge = "0123456789abcdef".as_bytes();
        let message = Message::from_hashed_data::<sha256::Hash>(challenge);

        let authentication_key = rt
            .perform(wca::commands::GetAuthenticationKey::new())
            .unwrap();
        let signature = rt
            .perform(wca::commands::SignChallenge::new(challenge.to_vec()))
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
            DescriptorPublicKey::XPub(xpub) => DescriptorPublicKey::XPub(DescriptorXKey {
                derivation_path: xpub.derivation_path.extend(path),
                origin: xpub.origin.clone(),
                ..*xpub
            }),
        }
    }

    fn get_funded_wallet(base: &DescriptorPublicKey) -> Wallet<AnyDatabase> {
        let spending: DescriptorPublicKey =
            extend_descriptor_public_key(base, &[ChildNumber::Normal { index: 0 }]);
        let descriptor = Descriptor::<DescriptorPublicKey>::new_wpkh(spending).unwrap();
        let (wallet, _, _) = bdk::wallet::get_funded_wallet(&descriptor.to_string());
        wallet
    }

    fn normal_transaction(
        from: &Wallet<AnyDatabase>,
        to: &Wallet<AnyDatabase>,
        amount: u64,
    ) -> bitcoin::psbt::PartiallySignedTransaction {
        let destination = to.get_address(AddressIndex::New).unwrap();

        let mut builder = from.build_tx();
        builder
            .add_recipient(destination.script_pubkey(), amount)
            .ordering(bdk::wallet::tx_builder::TxOrdering::Bip69Lexicographic); // A deterministic PSBT is nice for testing purposes.
        let (unsigned, _) = builder.finish().unwrap();
        unsigned
    }

    fn drain_wallet(
        from: &Wallet<AnyDatabase>,
        to: &Wallet<AnyDatabase>,
    ) -> bitcoin::psbt::PartiallySignedTransaction {
        let destination = to.get_address(AddressIndex::New).unwrap();

        let mut builder = from.build_tx();
        builder.drain_wallet().drain_to(destination.script_pubkey());
        let (unsigned, _) = builder.finish().unwrap();
        unsigned
    }

    fn is_finalized(psbt: &PartiallySignedTransaction) -> bool {
        psbt.inputs
            .iter()
            .all(|input| input.final_script_sig.is_some() || input.final_script_witness.is_some())
    }

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

        let source_wallet = get_funded_wallet(&source);
        let destination_wallet = get_funded_wallet(&destination);
        let unsigned = normal_transaction(&source_wallet, &destination_wallet, 5000);
        let mut signed = rt
            .perform(wca::commands::SignTransaction::new(unsigned))
            .unwrap();
        assert!(is_finalized(&signed));
        let finalized = source_wallet
            .finalize_psbt(&mut signed, Default::default())
            .unwrap();
        assert!(finalized);
    }

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

        let source_wallet = get_funded_wallet(&source);
        let destination_wallet = get_funded_wallet(&destination);
        let unsigned = drain_wallet(&source_wallet, &destination_wallet);
        let mut signed = rt
            .perform(wca::commands::SignTransaction::new(unsigned))
            .unwrap();
        assert!(is_finalized(&signed));
        let finalized = source_wallet
            .finalize_psbt(&mut signed, Default::default())
            .unwrap();
        assert!(finalized);
    }
}
