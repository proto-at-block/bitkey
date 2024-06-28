use anyhow::{bail, Context};

use bdk::bitcoin::psbt::Input;
use bdk::bitcoin::secp256k1::{All, Secp256k1};

use bdk::bitcoin::{Network, PublicKey};
use bdk::descriptor::IntoWalletDescriptor;
use bdk::keys::DescriptorPublicKey;
use bdk::miniscript::descriptor::Descriptor;

pub(crate) fn verify_inputs_only_have_one_signature(inputs: &[Input]) -> anyhow::Result<()> {
    for input in inputs.iter() {
        if input.partial_sigs.len() != 1 {
            bail!("Input does not only have one signature")
        }
    }

    Ok(())
}

pub(crate) fn verify_inputs_pubkey_belongs_to_wallet(
    wallet_descriptor: &WalletDescriptors,
    inputs: &[Input],
    secp: &Secp256k1<All>,
) -> anyhow::Result<()> {
    for input in inputs.iter() {
        verify_input_belongs_to_wallet(wallet_descriptor, input, secp)?;
    }

    Ok(())
}

fn verify_input_belongs_to_wallet(
    wallet_descriptor: &WalletDescriptors,
    input: &Input,
    secp: &Secp256k1<All>,
) -> anyhow::Result<()> {
    for partial_sig_public_key in input.partial_sigs.keys() {
        let derivation_path = input
            .bip32_derivation
            .get(&partial_sig_public_key.inner)
            .context("Invalid PSBT")?;
        let path_vec: Vec<u32> = derivation_path.1.into_iter().map(|&el| el.into()).collect();
        let last_index = path_vec.last().context("Invalid derivation path")?;

        let derived_descriptor = wallet_descriptor.to_definite_dpub(*last_index, secp)?;

        if !derived_descriptor.contains(partial_sig_public_key)? {
            return Err(anyhow::anyhow!("Unrecognized public key"));
        }
    }

    Ok(())
}

pub(crate) struct WalletDescriptors {
    external: Descriptor<DescriptorPublicKey>,
    change: Descriptor<DescriptorPublicKey>,
}

impl WalletDescriptors {
    pub fn new(
        descriptor: &String,
        change_descriptor: &String,
        secp: &Secp256k1<All>,
        network: Network,
    ) -> anyhow::Result<Self> {
        let wallet_extended_descriptor = descriptor
            .into_wallet_descriptor(secp, network)
            .or_else(|_| bail!("Unable to derive wallet descriptor"))?;
        let wallet_extended_change_descriptor = change_descriptor
            .into_wallet_descriptor(secp, network)
            .or_else(|_| bail!("Unable to derive wallet change descriptor"))?;

        Ok(Self {
            external: wallet_extended_descriptor.0,
            change: wallet_extended_change_descriptor.0,
        })
    }

    fn to_definite_dpub(
        &self,
        index: u32,
        secp: &Secp256k1<All>,
    ) -> anyhow::Result<DefiniteWalletDescriptor> {
        let definite_descriptor_key = self
            .external
            .at_derivation_index(index.clone())
            .or_else(|_| bail!("Unable to derive definite descriptor key"))?;

        let definite_change_descriptor_key = self
            .change
            .at_derivation_index(index.clone())
            .or_else(|_| bail!("Unable to derive definite change descriptor key"))?;

        let derived_descriptor = definite_descriptor_key
            .derived_descriptor(&secp)
            .or_else(|_| bail!("Unable to derive descriptor"))?;
        let derived_change_descriptor = definite_change_descriptor_key
            .derived_descriptor(&secp)
            .or_else(|_| bail!("Unable to derive change descriptor"))?;

        Ok(DefiniteWalletDescriptor::new(
            derived_descriptor,
            derived_change_descriptor,
        ))
    }
}

struct DefiniteWalletDescriptor {
    external: Descriptor<PublicKey>,
    change: Descriptor<PublicKey>,
}

impl DefiniteWalletDescriptor {
    fn new(external: Descriptor<PublicKey>, change: Descriptor<PublicKey>) -> Self {
        Self { external, change }
    }

    fn contains(self, public_key: &PublicKey) -> anyhow::Result<bool> {
        let descriptor_public_keys = match self.external {
            Descriptor::Wsh(public_key) => match public_key.into_inner() {
                bdk::miniscript::descriptor::WshInner::SortedMulti(vec) => vec.pks,
                _ => bail!("We do not use Wsh Miniscript"),
            },
            _ => bail!("Attempted to parse descriptor with unsupported script type."),
        };

        let change_descriptor_public_keys = match self.change {
            Descriptor::Wsh(public_key) => match public_key.into_inner() {
                bdk::miniscript::descriptor::WshInner::SortedMulti(vec) => vec.pks,
                _ => bail!("We do not use Wsh Miniscript"),
            },
            _ => bail!("Attempted to parse descriptor with unsupported script type."),
        };

        Ok(descriptor_public_keys.contains(public_key)
            || change_descriptor_public_keys.contains(public_key))
    }
}

#[cfg(test)]
mod tests {
    use bdk::{
        bitcoin::{
            bip32::{ChildNumber, DerivationPath, ExtendedPrivKey, ExtendedPubKey, Fingerprint},
            ecdsa,
            key::Secp256k1,
            psbt::Input,
            secp256k1::{All, Message},
            Network, PublicKey as BdkPublicKey,
        },
        keys::DescriptorPublicKey,
        miniscript::{
            descriptor::{DescriptorXKey, Wildcard},
            Descriptor,
        },
    };
    use std::collections::BTreeMap;

    use super::{
        verify_input_belongs_to_wallet, verify_inputs_only_have_one_signature, WalletDescriptors,
    };

    #[test]
    fn test_verify_inputs_only_have_one_signature() {
        // Setup
        let secp = Secp256k1::new();
        let (sk_1, pk_1) = secp.generate_keypair(&mut rand::thread_rng());
        let (sk_2, pk_2) = secp.generate_keypair(&mut rand::thread_rng());

        let sig1 = ecdsa::Signature::sighash_all(
            secp.sign_ecdsa(&Message::from_slice(&[0; 32]).unwrap(), &sk_1),
        );
        let sig2 = ecdsa::Signature::sighash_all(
            secp.sign_ecdsa(&Message::from_slice(&[0; 32]).unwrap(), &sk_2),
        );

        let bdk_pk1 = BdkPublicKey {
            compressed: false,
            inner: pk_1,
        };
        let bdk_pk2 = BdkPublicKey {
            compressed: false,
            inner: pk_2,
        };

        // All inputs only have one signature
        let input_1 = Input {
            partial_sigs: BTreeMap::from([(bdk_pk1, sig1)]),
            ..Default::default()
        };
        let input_2 = Input {
            partial_sigs: BTreeMap::from([(bdk_pk2, sig2)]),
            ..Default::default()
        };
        assert!(
            verify_inputs_only_have_one_signature(&vec![input_1.clone(), input_2.clone()]).is_ok()
        );

        // At least one input has no signatures
        assert!(verify_inputs_only_have_one_signature(&vec![input_1, Input::default()]).is_err());

        // No signatures
        assert!(verify_inputs_only_have_one_signature(&vec![Input::default()]).is_err());

        // More than one signature
        let input_with_more_than_one_partial_sig = Input {
            partial_sigs: BTreeMap::from([(bdk_pk1, sig1), (bdk_pk2, sig2)]),
            ..Default::default()
        };
        assert_eq!(
            verify_inputs_only_have_one_signature(&vec![input_with_more_than_one_partial_sig])
                .err()
                .unwrap()
                .to_string(),
            "Input does not only have one signature"
        )
    }

    fn generate_xprv(seed: &[u8; 32]) -> ExtendedPrivKey {
        ExtendedPrivKey::new_master(Network::Bitcoin, seed).unwrap()
    }

    fn derive_child_xprv(
        xprv: &ExtendedPrivKey,
        derivation_path: &DerivationPath,
        child: ChildNumber,
        secp: &Secp256k1<All>,
    ) -> ExtendedPrivKey {
        xprv.derive_priv(&secp, &derivation_path.child(child))
            .unwrap()
    }

    fn derive_xpub(
        xprv: &ExtendedPrivKey,
        derivation_path: &DerivationPath,
        child_number: ChildNumber,
        secp: &Secp256k1<All>,
    ) -> DescriptorPublicKey {
        let derived_xpub = ExtendedPubKey::from_priv(&secp, &xprv);
        DescriptorPublicKey::XPub(DescriptorXKey {
            origin: Some((
                xprv.fingerprint(secp),
                derivation_path.extend(&[child_number]).clone(),
            )),
            xkey: derived_xpub,
            derivation_path: DerivationPath::default(),
            wildcard: Wildcard::Unhardened,
        })
    }

    fn generate_descriptors(
        secp: &Secp256k1<All>,
        derivation_path: &DerivationPath,
        n: usize,
    ) -> (
        Vec<ExtendedPrivKey>,
        Vec<ExtendedPrivKey>,
        WalletDescriptors,
    ) {
        let mut external_xprvs: Vec<ExtendedPrivKey> = vec![];
        let mut change_xprvs: Vec<ExtendedPrivKey> = vec![];
        for i in 0..n {
            let xprv = generate_xprv(&[i as u8; 32]);

            external_xprvs.push(derive_child_xprv(
                &xprv,
                derivation_path,
                ChildNumber::Normal { index: 0 },
                &secp,
            ));
            change_xprvs.push(derive_child_xprv(
                &xprv,
                derivation_path,
                ChildNumber::Normal { index: 1 },
                &secp,
            ))
        }

        let descriptors = WalletDescriptors {
            external: Descriptor::<DescriptorPublicKey>::new_wsh_sortedmulti(
                2,
                external_xprvs
                    .iter()
                    .map(|xprv| {
                        derive_xpub(
                            xprv,
                            derivation_path,
                            ChildNumber::Normal { index: 0 },
                            &secp,
                        )
                    })
                    .collect(),
            )
            .unwrap(),
            change: Descriptor::<DescriptorPublicKey>::new_wsh_sortedmulti(
                2,
                change_xprvs
                    .iter()
                    .map(|xprv| {
                        derive_xpub(
                            xprv,
                            derivation_path,
                            ChildNumber::Normal { index: 1 },
                            &secp,
                        )
                    })
                    .collect(),
            )
            .unwrap(),
        };

        (external_xprvs, change_xprvs, descriptors)
    }

    #[test]
    fn test_verify_input_pubkey_belong_to_wallet() {
        let secp = Secp256k1::new();

        // Setup "Wallet"
        let bk_derivation_path: DerivationPath = vec![
            ChildNumber::from_hardened_idx(84).unwrap(),
            ChildNumber::from_hardened_idx(1).unwrap(),
            ChildNumber::from_hardened_idx(0).unwrap(),
        ]
        .into();

        let (external_xprvs, change_xprvs, wallet_descriptor) =
            generate_descriptors(&secp, &bk_derivation_path, 3);

        // Input signed with key not in either descriptor - nOK
        let (sk_1, pk_1) = secp.generate_keypair(&mut rand::thread_rng());
        let sig1 = ecdsa::Signature::sighash_all(
            secp.sign_ecdsa(&Message::from_slice(&[0; 32]).unwrap(), &sk_1),
        );
        let bdk_pk1 = BdkPublicKey {
            compressed: true,
            inner: pk_1,
        };
        let input_1 = Input {
            partial_sigs: BTreeMap::from([(bdk_pk1, sig1)]),
            bip32_derivation: BTreeMap::from([(
                pk_1,
                (
                    Fingerprint::default(),
                    bk_derivation_path.extend(&[
                        ChildNumber::Normal { index: 0 },
                        ChildNumber::Normal { index: 0 },
                    ]),
                ),
            )]),
            ..Default::default()
        };

        assert_eq!(
            verify_input_belongs_to_wallet(&wallet_descriptor, &input_1, &secp)
                .err()
                .unwrap()
                .to_string(),
            "Unrecognized public key"
        );

        // Input signed with key from external descriptor – OK
        for xprv in external_xprvs {
            let derived_xprv = xprv
                .derive_priv(&secp, &[ChildNumber::Normal { index: 1 }])
                .unwrap();
            let pk = derived_xprv.private_key.public_key(&secp);
            let bdk_pk = BdkPublicKey {
                compressed: true,
                inner: pk,
            };
            let sig = ecdsa::Signature::sighash_all(secp.sign_ecdsa(
                &Message::from_slice(&[0; 32]).unwrap(),
                &derived_xprv.private_key,
            ));
            let input = Input {
                partial_sigs: BTreeMap::from([(bdk_pk, sig)]),
                bip32_derivation: BTreeMap::from([(
                    pk,
                    (
                        Fingerprint::default(),
                        DerivationPath::from(vec![ChildNumber::Normal { index: 1 }]),
                    ),
                )]),
                ..Default::default()
            };
            verify_input_belongs_to_wallet(&wallet_descriptor, &input, &secp).unwrap();
        }

        // Input signed with key from change descriptor – OK
        for xprv in change_xprvs {
            let derived_xprv = xprv
                .derive_priv(&secp, &[ChildNumber::Normal { index: 1 }])
                .unwrap();
            let pk = derived_xprv.private_key.public_key(&secp);
            let bdk_pk = BdkPublicKey {
                compressed: true,
                inner: pk,
            };
            let sig = ecdsa::Signature::sighash_all(secp.sign_ecdsa(
                &Message::from_slice(&[0; 32]).unwrap(),
                &derived_xprv.private_key,
            ));
            let input = Input {
                partial_sigs: BTreeMap::from([(bdk_pk, sig)]),
                bip32_derivation: BTreeMap::from([(
                    pk,
                    (
                        Fingerprint::default(),
                        DerivationPath::from(vec![ChildNumber::Normal { index: 1 }]),
                    ),
                )]),
                ..Default::default()
            };
            verify_input_belongs_to_wallet(&wallet_descriptor, &input, &secp).unwrap();
        }
    }

    #[test]
    fn test_verify_input_integrity_when_checking_if_input_pubkey_belongs_to_wallet() {
        let secp = Secp256k1::new();

        // Setup "Wallet"
        let bk_derivation_path: DerivationPath = vec![
            ChildNumber::from_hardened_idx(84).unwrap(),
            ChildNumber::from_hardened_idx(1).unwrap(),
            ChildNumber::from_hardened_idx(0).unwrap(),
        ]
        .into();

        let (external_xprvs, _change_xprvs, wallet_descriptor) =
            generate_descriptors(&secp, &bk_derivation_path, 3);

        // Input signed with key from external descriptor, but wrong derivation path is used – nOK

        // We test this by deriving the public key with the right derivation path, but define the
        // bip32_derivation field with a different path.
        let good_path = [ChildNumber::Normal { index: 1 }];
        let bad_path = [ChildNumber::Normal { index: 2 }];
        for xprv in external_xprvs {
            let derived_xprv = xprv.derive_priv(&secp, &good_path).unwrap();
            let pk = derived_xprv.private_key.public_key(&secp);
            let bdk_pk = BdkPublicKey {
                compressed: true,
                inner: pk,
            };
            let sig = ecdsa::Signature::sighash_all(secp.sign_ecdsa(
                &Message::from_slice(&[0; 32]).unwrap(),
                &derived_xprv.private_key,
            ));
            let input = Input {
                partial_sigs: BTreeMap::from([(bdk_pk, sig)]),
                bip32_derivation: BTreeMap::from([(
                    pk,
                    (
                        Fingerprint::default(),
                        DerivationPath::from(bad_path.to_vec()),
                    ),
                )]),
                ..Default::default()
            };

            assert_eq!(
                verify_input_belongs_to_wallet(&wallet_descriptor, &input, &secp)
                    .err()
                    .unwrap()
                    .to_string(),
                "Unrecognized public key"
            );
        }

        // Input signed with key, but wrong public key was used for `bip32_derivation` – nOK
        let (sk_1, pk_1) = secp.generate_keypair(&mut rand::thread_rng());
        let (_, wrong_pk) = secp.generate_keypair(&mut rand::thread_rng());

        let sig1 = ecdsa::Signature::sighash_all(
            secp.sign_ecdsa(&Message::from_slice(&[0; 32]).unwrap(), &sk_1),
        );
        let bdk_pk1 = BdkPublicKey {
            compressed: true,
            inner: pk_1,
        };
        let input_1 = Input {
            partial_sigs: BTreeMap::from([(bdk_pk1, sig1)]),
            bip32_derivation: BTreeMap::from([(
                wrong_pk,
                (Fingerprint::default(), DerivationPath::default()),
            )]),
            ..Default::default()
        };

        assert_eq!(
            verify_input_belongs_to_wallet(&wallet_descriptor, &input_1, &secp)
                .err()
                .unwrap()
                .to_string(),
            "Invalid PSBT"
        );

        // Input signed with key, but invalid derivation path was used. – nOK
        let (sk_1, pk_1) = secp.generate_keypair(&mut rand::thread_rng());
        let sig1 = ecdsa::Signature::sighash_all(
            secp.sign_ecdsa(&Message::from_slice(&[0; 32]).unwrap(), &sk_1),
        );
        let bdk_pk1 = BdkPublicKey {
            compressed: true,
            inner: pk_1,
        };
        let input_1 = Input {
            partial_sigs: BTreeMap::from([(bdk_pk1, sig1)]),
            bip32_derivation: BTreeMap::from([(
                pk_1,
                (Fingerprint::default(), DerivationPath::default()),
            )]),
            ..Default::default()
        };

        assert_eq!(
            verify_input_belongs_to_wallet(&wallet_descriptor, &input_1, &secp)
                .err()
                .unwrap()
                .to_string(),
            "Invalid derivation path"
        )
    }
}
