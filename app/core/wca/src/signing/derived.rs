use bitcoin::{
    bip32::{DerivationPath, Fingerprint},
    psbt::PartiallySignedTransaction,
    sighash::SighashCache,
};

use super::{is_finalised, sighash, DescriptorExtendedKey, Error, Signable, Signer};

pub(crate) struct DerivedKeySigner {
    spending_key: DescriptorExtendedKey,
}

impl DerivedKeySigner {
    pub(crate) fn new(spending_key: DescriptorExtendedKey) -> Self {
        Self { spending_key }
    }
}

impl Signer for DerivedKeySigner {
    fn signables_for(&self, psbt: &PartiallySignedTransaction) -> Result<Vec<Signable>, Error> {
        let mut cache: SighashCache<&bitcoin::Transaction> = SighashCache::new(&psbt.unsigned_tx);
        let mut signables = vec![];
        for (input_index, input) in psbt.inputs.iter().enumerate() {
            if is_finalised(input) {
                continue;
            }

            let sighash = sighash(&mut cache, psbt, input_index)?;

            for (target_public_key, (target_origin_fingerprint, target_derivation_path)) in
                &input.bip32_derivation
            {
                if input
                    .partial_sigs
                    .contains_key(&bitcoin::PublicKey::new(*target_public_key))
                {
                    continue;
                }

                if let Some(path) = path_to_derive(
                    &self.spending_key,
                    target_origin_fingerprint,
                    target_derivation_path,
                ) {
                    signables.push(Signable {
                        path,
                        sighash,
                        input_index,
                    });
                }
            }
        }

        Ok(signables)
    }
}

fn path_to_derive(
    xpub: &DescriptorExtendedKey,
    target_origin_fingerprint: &Fingerprint,
    target_derivation_path: &DerivationPath,
) -> Option<DerivationPath> {
    let (compare_fingerprint, path_to_derive) = if let Some((fingerprint, path)) = &xpub.origin {
        let target_origin_prefix = target_derivation_path
            .as_ref()
            .get(0..path.len())
            .unwrap_or_default();
        let target_origin_suffix = target_derivation_path
            .as_ref()
            .get(path.len()..)
            .unwrap_or_default();

        if target_origin_prefix != path.as_ref() {
            return None;
        }

        (*fingerprint, target_origin_suffix.into())
    } else {
        (xpub.xkey.fingerprint(), target_derivation_path.clone())
    };

    if compare_fingerprint != *target_origin_fingerprint {
        return None;
    }

    Some(path_to_derive)
}

#[cfg(test)]
mod tests {
    use std::str::FromStr;

    use bdk::wallet::{get_funded_wallet, AddressIndex};
    use miniscript::{Descriptor, DescriptorPublicKey};

    use crate::signing::Signer;

    use super::DerivedKeySigner;

    #[test]
    fn test_signing() {
        let psbt_dpub = DescriptorPublicKey::from_str("[96ae1927/84'/1'/0']tpubDDTqca3h8xPvEas4gMwWuqVhnaPyfBQapLj3jkr7j7M9WVBDx6PiVec5XJBbWgP4UmuLSYW9pr36Lc2iyCLJZ2KQD2ggAX2dyRcVbcM9Ygn/*").unwrap();
        let signing_dxpub = match psbt_dpub {
            DescriptorPublicKey::XPub(ref dxpub) => dxpub.clone(),
            _ => unimplemented!(),
        };

        let psbt = get_drain_psbt(psbt_dpub);
        let signables = DerivedKeySigner::new(signing_dxpub)
            .signables_for(&psbt)
            .unwrap();

        assert!(!signables.is_empty());
    }

    #[test]
    fn test_signs_deeply_derived() {
        let signing_dpub = DescriptorPublicKey::from_str("[0c5f9a1e]tpubD6NzVbkrYhZ4WaWSyoBvQwbpLkojyoTZPRsgXELWz3Popb3qkjcJyJUGLnL4qHHoQvao8ESaAstxYSnhyswJ76uZPStJRJCTKvosUCJZL5B/*").unwrap();
        let signing_dxpub = match signing_dpub {
            DescriptorPublicKey::XPub(ref dxpub) => dxpub.clone(),
            _ => unimplemented!(),
        };

        let psbt_dpub = DescriptorPublicKey::from_str("[0c5f9a1e/84'/1'/0']tpubDCxzhZZE31g2EqSv1UajMAw5Hd62htydz9r2XBkrccHgBh8uw3n62zr6Zjmj64tfTk8Tjxo6VctjUMAh5DXWTErfQPC6RmQhTdtNnXuTXTQ/*").unwrap();
        let psbt = get_drain_psbt(psbt_dpub);

        let signables = DerivedKeySigner::new(signing_dxpub)
            .signables_for(&psbt)
            .unwrap();

        assert!(!signables.is_empty());
    }

    #[test]
    fn test_signs_origin_missing() {
        let dpub = DescriptorPublicKey::from_str("tpubDDTqca3h8xPvEas4gMwWuqVhnaPyfBQapLj3jkr7j7M9WVBDx6PiVec5XJBbWgP4UmuLSYW9pr36Lc2iyCLJZ2KQD2ggAX2dyRcVbcM9Ygn/*").unwrap();
        let dxpub = match dpub {
            DescriptorPublicKey::XPub(ref dxpub) => dxpub.clone(),
            _ => unimplemented!(),
        };

        let psbt = get_drain_psbt(dpub);
        let signables = DerivedKeySigner::new(dxpub).signables_for(&psbt).unwrap();

        assert!(!signables.is_empty());
    }

    #[test]
    fn test_ignores_fingerprint_mismatch() {
        let psbt_dpub = DescriptorPublicKey::from_str("[96ae1927/84'/1'/0']tpubDDTqca3h8xPvEas4gMwWuqVhnaPyfBQapLj3jkr7j7M9WVBDx6PiVec5XJBbWgP4UmuLSYW9pr36Lc2iyCLJZ2KQD2ggAX2dyRcVbcM9Ygn/*").unwrap();
        let signing_dpub = DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/*").unwrap();
        let signing_dxpub = match signing_dpub {
            DescriptorPublicKey::XPub(ref dxpub) => dxpub.clone(),
            _ => unimplemented!(),
        };

        let psbt = get_drain_psbt(psbt_dpub);
        let signables = DerivedKeySigner::new(signing_dxpub)
            .signables_for(&psbt)
            .unwrap();

        assert!(signables.is_empty());
    }

    #[test]
    fn test_ignores_path_mismatch() {
        let psbt_dpub = DescriptorPublicKey::from_str("tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/*").unwrap();
        let signing_dpub = DescriptorPublicKey::from_str("[96ae1927/84'/1'/0']tpubDDTqca3h8xPvEas4gMwWuqVhnaPyfBQapLj3jkr7j7M9WVBDx6PiVec5XJBbWgP4UmuLSYW9pr36Lc2iyCLJZ2KQD2ggAX2dyRcVbcM9Ygn/*").unwrap();
        let signing_dxpub = match signing_dpub {
            DescriptorPublicKey::XPub(ref dxpub) => dxpub.clone(),
            _ => unimplemented!(),
        };

        let psbt = get_drain_psbt(psbt_dpub);
        let signables = DerivedKeySigner::new(signing_dxpub)
            .signables_for(&psbt)
            .unwrap();

        assert!(signables.is_empty());
    }

    #[test]
    fn test_ignores_origin_missing() {
        let psbt_dpub = DescriptorPublicKey::from_str("tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/*").unwrap();
        let signing_dpub = DescriptorPublicKey::from_str("tpubDDTqca3h8xPvEas4gMwWuqVhnaPyfBQapLj3jkr7j7M9WVBDx6PiVec5XJBbWgP4UmuLSYW9pr36Lc2iyCLJZ2KQD2ggAX2dyRcVbcM9Ygn/*").unwrap();
        let signing_dxpub = match signing_dpub {
            DescriptorPublicKey::XPub(ref dxpub) => dxpub.clone(),
            _ => unimplemented!(),
        };

        let psbt = get_drain_psbt(psbt_dpub);
        let signables = DerivedKeySigner::new(signing_dxpub)
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
