use bitcoin::{
    bip32::Fingerprint, psbt::PartiallySignedTransaction, secp256k1::PublicKey,
    sighash::SighashCache,
};

use super::{is_finalised, sighash, Error, Signable, Signer};

pub(crate) struct DerivedKeySigner {
    origin_fingerprint: Fingerprint,
}

impl DerivedKeySigner {
    pub(crate) fn new(origin_fingerprint: Fingerprint) -> Self {
        Self { origin_fingerprint }
    }
}

impl Signer for DerivedKeySigner {
    fn signables_for(
        &self,
        psbt: &PartiallySignedTransaction,
    ) -> Result<Vec<(PublicKey, Signable)>, Error> {
        let mut cache: SighashCache<&bitcoin::Transaction> = SighashCache::new(&psbt.unsigned_tx);
        let mut signables = vec![];
        for (input_index, input) in psbt.inputs.iter().enumerate() {
            if is_finalised(input) {
                continue;
            }

            let sighash = sighash(&mut cache, psbt, input_index)?;

            for (public_key, (fingerprint, derivation_path)) in &input.bip32_derivation {
                if *fingerprint != self.origin_fingerprint {
                    continue;
                }
                if input
                    .partial_sigs
                    .contains_key(&bitcoin::PublicKey::new(*public_key))
                {
                    continue;
                }

                signables.push((
                    *public_key,
                    Signable {
                        path: derivation_path.clone(),
                        sighash,
                        input_index,
                    },
                ));
            }
        }

        Ok(signables)
    }
}

#[cfg(test)]
mod tests {
    use std::str::FromStr;

    use bdk::wallet::{get_funded_wallet, AddressIndex};
    use bitcoin::bip32::Fingerprint;
    use miniscript::{Descriptor, DescriptorPublicKey};

    use crate::signing::Signer;

    use super::DerivedKeySigner;

    #[test]
    fn test_signing() {
        let psbt_dpub = DescriptorPublicKey::from_str("[96ae1927/84'/1'/0']tpubDDTqca3h8xPvEas4gMwWuqVhnaPyfBQapLj3jkr7j7M9WVBDx6PiVec5XJBbWgP4UmuLSYW9pr36Lc2iyCLJZ2KQD2ggAX2dyRcVbcM9Ygn/*").unwrap();
        let fingerprint = Fingerprint::from_str("96ae1927").unwrap();
        let psbt = get_drain_psbt(psbt_dpub);
        let signables = DerivedKeySigner::new(fingerprint)
            .signables_for(&psbt)
            .unwrap();

        assert!(!signables.is_empty());
    }

    #[test]
    fn test_signs_deeply_derived() {
        let _signing_dpub = DescriptorPublicKey::from_str("[0c5f9a1e]tpubD6NzVbkrYhZ4WaWSyoBvQwbpLkojyoTZPRsgXELWz3Popb3qkjcJyJUGLnL4qHHoQvao8ESaAstxYSnhyswJ76uZPStJRJCTKvosUCJZL5B/*").unwrap();
        let psbt_dpub = DescriptorPublicKey::from_str("[0c5f9a1e/84'/1'/0']tpubDCxzhZZE31g2EqSv1UajMAw5Hd62htydz9r2XBkrccHgBh8uw3n62zr6Zjmj64tfTk8Tjxo6VctjUMAh5DXWTErfQPC6RmQhTdtNnXuTXTQ/*").unwrap();
        let psbt = get_drain_psbt(psbt_dpub);

        let signing_fingerprint = Fingerprint::from_str("0c5f9a1e").unwrap();
        let signables = DerivedKeySigner::new(signing_fingerprint)
            .signables_for(&psbt)
            .unwrap();

        assert!(!signables.is_empty());
    }

    #[test]
    fn test_signs_origin_missing() {
        let dpub = DescriptorPublicKey::from_str("tpubDDTqca3h8xPvEas4gMwWuqVhnaPyfBQapLj3jkr7j7M9WVBDx6PiVec5XJBbWgP4UmuLSYW9pr36Lc2iyCLJZ2KQD2ggAX2dyRcVbcM9Ygn/*").unwrap();
        let fingerprint = Fingerprint::from_str("96ae1927").unwrap();
        let psbt = get_drain_psbt(dpub);
        let signables = DerivedKeySigner::new(fingerprint)
            .signables_for(&psbt)
            .unwrap();
        // BDK should always include the fingerprint in the
        // [`bitcoin::psbt::Input::bip32_derivation`] field as long as we provided it initially
        // during wallet initialization.
        assert!(signables.is_empty());
    }

    #[test]
    fn test_ignores_fingerprint_mismatch() {
        let psbt_dpub = DescriptorPublicKey::from_str("[96ae1927/84'/1'/0']tpubDDTqca3h8xPvEas4gMwWuqVhnaPyfBQapLj3jkr7j7M9WVBDx6PiVec5XJBbWgP4UmuLSYW9pr36Lc2iyCLJZ2KQD2ggAX2dyRcVbcM9Ygn/*").unwrap();
        let _signing_dpub = DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/*").unwrap();
        let signing_fingerprint = Fingerprint::from_str("74ce1142").unwrap();
        let psbt = get_drain_psbt(psbt_dpub);
        let signables = DerivedKeySigner::new(signing_fingerprint)
            .signables_for(&psbt)
            .unwrap();

        assert!(signables.is_empty());
    }

    #[test]
    fn test_ignores_path_mismatch() {
        let psbt_dpub = DescriptorPublicKey::from_str("tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/*").unwrap();
        let _signing_dpub = DescriptorPublicKey::from_str("[96ae1927/84'/1'/0']tpubDDTqca3h8xPvEas4gMwWuqVhnaPyfBQapLj3jkr7j7M9WVBDx6PiVec5XJBbWgP4UmuLSYW9pr36Lc2iyCLJZ2KQD2ggAX2dyRcVbcM9Ygn/*").unwrap();
        let signing_fingerprint = Fingerprint::from_str("96ae1927").unwrap();
        let psbt = get_drain_psbt(psbt_dpub);
        let signables = DerivedKeySigner::new(signing_fingerprint)
            .signables_for(&psbt)
            .unwrap();

        assert!(signables.is_empty());
    }

    #[test]
    fn test_ignores_origin_missing() {
        let psbt_dpub = DescriptorPublicKey::from_str("tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/*").unwrap();
        let _signing_dpub = DescriptorPublicKey::from_str("tpubDDTqca3h8xPvEas4gMwWuqVhnaPyfBQapLj3jkr7j7M9WVBDx6PiVec5XJBbWgP4UmuLSYW9pr36Lc2iyCLJZ2KQD2ggAX2dyRcVbcM9Ygn/*").unwrap();
        let signing_fingerprint = Fingerprint::from_str("96ae1927").unwrap();
        let psbt = get_drain_psbt(psbt_dpub);
        let signables = DerivedKeySigner::new(signing_fingerprint)
            .signables_for(&psbt)
            .unwrap();

        assert!(signables.is_empty());
    }

    fn get_drain_psbt(dpub: DescriptorPublicKey) -> bitcoin::psbt::PartiallySignedTransaction {
        let descriptor = Descriptor::<DescriptorPublicKey>::new_wpkh(dpub).unwrap();
        let (wallet, _, _) = get_funded_wallet(&descriptor.to_string());
        let mut builder = wallet.build_tx();
        builder.drain_wallet().drain_to(
            wallet
                .get_address(AddressIndex::New)
                .unwrap()
                .script_pubkey(),
        );
        let (psbt, _) = builder.finish().unwrap();
        psbt
    }
}
