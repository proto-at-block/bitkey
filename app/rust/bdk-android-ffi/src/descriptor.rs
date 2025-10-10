use crate::Network;
use crate::{BdkError, DescriptorPublicKey, DescriptorSecretKey};
use bdk::bitcoin::bip32::Fingerprint;
use bdk::bitcoin::secp256k1::Secp256k1;
use bdk::descriptor::{ExtendedDescriptor, IntoWalletDescriptor};
use bdk::keys::{
    DescriptorPublicKey as BdkDescriptorPublicKey, DescriptorSecretKey as BdkDescriptorSecretKey,
    KeyMap,
};
use bdk::template::{
    Bip44, Bip44Public, Bip49, Bip49Public, Bip84, Bip84Public, Bip86, Bip86Public,
    DescriptorTemplate,
};
use bdk::KeychainKind;
use std::str::FromStr;
use std::sync::Arc;

#[derive(Debug)]
pub(crate) struct Descriptor {
    pub(crate) extended_descriptor: ExtendedDescriptor,
    pub(crate) key_map: KeyMap,
}

impl Descriptor {
    pub(crate) fn new(descriptor: String, network: Network) -> Result<Self, BdkError> {
        let secp = Secp256k1::new();
        let (extended_descriptor, key_map) =
            descriptor.into_wallet_descriptor(&secp, network.into())?;
        Ok(Self {
            extended_descriptor,
            key_map,
        })
    }

    pub(crate) fn new_bip44(
        secret_key: Arc<DescriptorSecretKey>,
        keychain_kind: KeychainKind,
        network: Network,
    ) -> Self {
        let derivable_key = &secret_key.inner;

        match derivable_key {
            BdkDescriptorSecretKey::XPrv(descriptor_x_key) => {
                let derivable_key = descriptor_x_key.xkey;
                let (extended_descriptor, key_map, _) = Bip44(derivable_key, keychain_kind)
                    .build(network.into())
                    .unwrap();
                Self {
                    extended_descriptor,
                    key_map,
                }
            }
            BdkDescriptorSecretKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorSecretKey::MultiXPrv(_) => {
                unreachable!()
            }
        }
    }

    pub(crate) fn new_bip44_public(
        public_key: Arc<DescriptorPublicKey>,
        fingerprint: String,
        keychain_kind: KeychainKind,
        network: Network,
    ) -> Self {
        let fingerprint = Fingerprint::from_str(fingerprint.as_str()).unwrap();
        let derivable_key = &public_key.inner;

        match derivable_key {
            BdkDescriptorPublicKey::XPub(descriptor_x_key) => {
                let derivable_key = descriptor_x_key.xkey;
                let (extended_descriptor, key_map, _) =
                    Bip44Public(derivable_key, fingerprint, keychain_kind)
                        .build(network.into())
                        .unwrap();

                Self {
                    extended_descriptor,
                    key_map,
                }
            }
            BdkDescriptorPublicKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorPublicKey::MultiXPub(_) => {
                unreachable!()
            }
        }
    }

    pub(crate) fn new_bip49(
        secret_key: Arc<DescriptorSecretKey>,
        keychain_kind: KeychainKind,
        network: Network,
    ) -> Self {
        let derivable_key = &secret_key.inner;

        match derivable_key {
            BdkDescriptorSecretKey::XPrv(descriptor_x_key) => {
                let derivable_key = descriptor_x_key.xkey;
                let (extended_descriptor, key_map, _) = Bip49(derivable_key, keychain_kind)
                    .build(network.into())
                    .unwrap();
                Self {
                    extended_descriptor,
                    key_map,
                }
            }
            BdkDescriptorSecretKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorSecretKey::MultiXPrv(_) => {
                unreachable!()
            }
        }
    }

    pub(crate) fn new_bip49_public(
        public_key: Arc<DescriptorPublicKey>,
        fingerprint: String,
        keychain_kind: KeychainKind,
        network: Network,
    ) -> Self {
        let fingerprint = Fingerprint::from_str(fingerprint.as_str()).unwrap();
        let derivable_key = &public_key.inner;

        match derivable_key {
            BdkDescriptorPublicKey::XPub(descriptor_x_key) => {
                let derivable_key = descriptor_x_key.xkey;
                let (extended_descriptor, key_map, _) =
                    Bip49Public(derivable_key, fingerprint, keychain_kind)
                        .build(network.into())
                        .unwrap();

                Self {
                    extended_descriptor,
                    key_map,
                }
            }
            BdkDescriptorPublicKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorPublicKey::MultiXPub(_) => {
                unreachable!()
            }
        }
    }

    pub(crate) fn new_bip84(
        secret_key: Arc<DescriptorSecretKey>,
        keychain_kind: KeychainKind,
        network: Network,
    ) -> Self {
        let derivable_key = &secret_key.inner;

        match derivable_key {
            BdkDescriptorSecretKey::XPrv(descriptor_x_key) => {
                let derivable_key = descriptor_x_key.xkey;
                let (extended_descriptor, key_map, _) = Bip84(derivable_key, keychain_kind)
                    .build(network.into())
                    .unwrap();
                Self {
                    extended_descriptor,
                    key_map,
                }
            }
            BdkDescriptorSecretKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorSecretKey::MultiXPrv(_) => {
                unreachable!()
            }
        }
    }

    pub(crate) fn new_bip84_public(
        public_key: Arc<DescriptorPublicKey>,
        fingerprint: String,
        keychain_kind: KeychainKind,
        network: Network,
    ) -> Self {
        let fingerprint = Fingerprint::from_str(fingerprint.as_str()).unwrap();
        let derivable_key = &public_key.inner;

        match derivable_key {
            BdkDescriptorPublicKey::XPub(descriptor_x_key) => {
                let derivable_key = descriptor_x_key.xkey;
                let (extended_descriptor, key_map, _) =
                    Bip84Public(derivable_key, fingerprint, keychain_kind)
                        .build(network.into())
                        .unwrap();

                Self {
                    extended_descriptor,
                    key_map,
                }
            }
            BdkDescriptorPublicKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorPublicKey::MultiXPub(_) => {
                unreachable!()
            }
        }
    }

    pub(crate) fn new_bip86(
        secret_key: Arc<DescriptorSecretKey>,
        keychain_kind: KeychainKind,
        network: Network,
    ) -> Self {
        let derivable_key = &secret_key.inner;

        match derivable_key {
            BdkDescriptorSecretKey::XPrv(descriptor_x_key) => {
                let derivable_key = descriptor_x_key.xkey;
                let (extended_descriptor, key_map, _) = Bip86(derivable_key, keychain_kind)
                    .build(network.into())
                    .unwrap();
                Self {
                    extended_descriptor,
                    key_map,
                }
            }
            BdkDescriptorSecretKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorSecretKey::MultiXPrv(_) => {
                unreachable!()
            }
        }
    }

    pub(crate) fn new_bip86_public(
        public_key: Arc<DescriptorPublicKey>,
        fingerprint: String,
        keychain_kind: KeychainKind,
        network: Network,
    ) -> Self {
        let fingerprint = Fingerprint::from_str(fingerprint.as_str()).unwrap();
        let derivable_key = &public_key.inner;

        match derivable_key {
            BdkDescriptorPublicKey::XPub(descriptor_x_key) => {
                let derivable_key = descriptor_x_key.xkey;
                let (extended_descriptor, key_map, _) =
                    Bip86Public(derivable_key, fingerprint, keychain_kind)
                        .build(network.into())
                        .unwrap();

                Self {
                    extended_descriptor,
                    key_map,
                }
            }
            BdkDescriptorPublicKey::Single(_) => {
                unreachable!()
            }
            BdkDescriptorPublicKey::MultiXPub(_) => {
                unreachable!()
            }
        }
    }

    pub(crate) fn as_string_private(&self) -> String {
        let descriptor = &self.extended_descriptor;
        let key_map = &self.key_map;
        descriptor.to_string_with_secret(key_map)
    }

    pub(crate) fn as_string(&self) -> String {
        self.extended_descriptor.to_string()
    }
}

// The goal of these tests to to ensure `bdk-ffi` intermediate code correctly calls `bdk` APIs.
// These tests should not be used to verify `bdk` behavior that is already tested in the `bdk`
// crate.
#[cfg(test)]
mod test {
    use crate::database::DatabaseConfig;
    use crate::*;
    use assert_matches::assert_matches;
    use bdk::descriptor::DescriptorError::Key;
    use bdk::keys::KeyError::InvalidNetwork;

    fn get_descriptor_secret_key() -> DescriptorSecretKey {
        let mnemonic = Mnemonic::from_string("chaos fabric time speed sponsor all flat solution wisdom trophy crack object robot pave observe combine where aware bench orient secret primary cable detect".to_string()).unwrap();
        DescriptorSecretKey::new(Network::Testnet, Arc::new(mnemonic), None)
    }

    #[test]
    fn test_descriptor_templates() {
        let master: Arc<DescriptorSecretKey> = Arc::new(get_descriptor_secret_key());
        println!("Master: {:?}", master.as_string());
        // tprv8ZgxMBicQKsPdWuqM1t1CDRvQtQuBPyfL6GbhQwtxDKgUAVPbxmj71pRA8raTqLrec5LyTs5TqCxdABcZr77bt2KyWA5bizJHnC4g4ysm4h
        let handmade_public_44 = master
            .derive(Arc::new(
                DerivationPath::new("m/44h/1h/0h".to_string()).unwrap(),
            ))
            .unwrap()
            .as_public();
        println!("Public 44: {}", handmade_public_44.as_string());
        // Public 44: [d1d04177/44'/1'/0']tpubDCoPjomfTqh1e7o1WgGpQtARWtkueXQAepTeNpWiitS3Sdv8RKJ1yvTrGHcwjDXp2SKyMrTEca4LoN7gEUiGCWboyWe2rz99Kf4jK4m2Zmx/*
        let handmade_public_49 = master
            .derive(Arc::new(
                DerivationPath::new("m/49h/1h/0h".to_string()).unwrap(),
            ))
            .unwrap()
            .as_public();
        println!("Public 49: {}", handmade_public_49.as_string());
        // Public 49: [d1d04177/49'/1'/0']tpubDC65ZRvk1NDddHrVAUAZrUPJ772QXzooNYmPywYF9tMyNLYKf5wpKE7ZJvK9kvfG3FV7rCsHBNXy1LVKW95jrmC7c7z4hq7a27aD2sRrAhR/*
        let handmade_public_84 = master
            .derive(Arc::new(
                DerivationPath::new("m/84h/1h/0h".to_string()).unwrap(),
            ))
            .unwrap()
            .as_public();
        println!("Public 84: {}", handmade_public_84.as_string());
        // Public 84: [d1d04177/84'/1'/0']tpubDDNxbq17egjFk2edjv8oLnzxk52zny9aAYNv9CMqTzA4mQDiQq818sEkNe9Gzmd4QU8558zftqbfoVBDQorG3E4Wq26tB2JeE4KUoahLkx6/*
        let handmade_public_86 = master
            .derive(Arc::new(
                DerivationPath::new("m/86h/1h/0h".to_string()).unwrap(),
            ))
            .unwrap()
            .as_public();
        println!("Public 86: {}", handmade_public_86.as_string());
        // Public 86: [d1d04177/86'/1'/0']tpubDCJzjbcGbdEfXMWaL6QmgVmuSfXkrue7m2YNoacWwyc7a2XjXaKojRqNEbo41CFL3PyYmKdhwg2fkGpLX4SQCbQjCGxAkWHJTw9WEeenrJb/*
        let template_private_44 =
            Descriptor::new_bip44(master.clone(), KeychainKind::External, Network::Testnet);
        let template_private_49 =
            Descriptor::new_bip49(master.clone(), KeychainKind::External, Network::Testnet);
        let template_private_84 =
            Descriptor::new_bip84(master.clone(), KeychainKind::External, Network::Testnet);
        let template_private_86 =
            Descriptor::new_bip86(master, KeychainKind::External, Network::Testnet);
        // the extended public keys are the same when creating them manually as they are with the templates
        println!("Template 49: {}", template_private_49.as_string());
        println!("Template 44: {}", template_private_44.as_string());
        println!("Template 84: {}", template_private_84.as_string());
        println!("Template 86: {}", template_private_86.as_string());
        let template_public_44 = Descriptor::new_bip44_public(
            handmade_public_44,
            "d1d04177".to_string(),
            KeychainKind::External,
            Network::Testnet,
        );
        let template_public_49 = Descriptor::new_bip49_public(
            handmade_public_49,
            "d1d04177".to_string(),
            KeychainKind::External,
            Network::Testnet,
        );
        let template_public_84 = Descriptor::new_bip84_public(
            handmade_public_84,
            "d1d04177".to_string(),
            KeychainKind::External,
            Network::Testnet,
        );
        let template_public_86 = Descriptor::new_bip86_public(
            handmade_public_86,
            "d1d04177".to_string(),
            KeychainKind::External,
            Network::Testnet,
        );
        println!("Template public 49: {}", template_public_49.as_string());
        println!("Template public 44: {}", template_public_44.as_string());
        println!("Template public 84: {}", template_public_84.as_string());
        println!("Template public 86: {}", template_public_86.as_string());
        // when using a public key, both as_string and as_string_private return the same string
        assert_eq!(
            template_public_44.as_string_private(),
            template_public_44.as_string()
        );
        assert_eq!(
            template_public_49.as_string_private(),
            template_public_49.as_string()
        );
        assert_eq!(
            template_public_84.as_string_private(),
            template_public_84.as_string()
        );
        assert_eq!(
            template_public_86.as_string_private(),
            template_public_86.as_string()
        );
        // when using as_string on a private key, we get the same result as when using it on a public key
        assert_eq!(
            template_private_44.as_string(),
            template_public_44.as_string()
        );
        assert_eq!(
            template_private_49.as_string(),
            template_public_49.as_string()
        );
        assert_eq!(
            template_private_84.as_string(),
            template_public_84.as_string()
        );
        assert_eq!(
            template_private_86.as_string(),
            template_public_86.as_string()
        );
    }
    #[test]
    fn test_descriptor_from_string() {
        let descriptor1 = Descriptor::new("wpkh(tprv8hwWMmPE4BVNxGdVt3HhEERZhondQvodUY7Ajyseyhudr4WabJqWKWLr4Wi2r26CDaNCQhhxEftEaNzz7dPGhWuKFU4VULesmhEfZYyBXdE/0/*)".to_string(), Network::Testnet);
        let descriptor2 = Descriptor::new("wpkh(tprv8hwWMmPE4BVNxGdVt3HhEERZhondQvodUY7Ajyseyhudr4WabJqWKWLr4Wi2r26CDaNCQhhxEftEaNzz7dPGhWuKFU4VULesmhEfZYyBXdE/0/*)".to_string(), Network::Bitcoin);
        // Creating a Descriptor using an extended key that doesn't match the network provided will throw and InvalidNetwork Error
        assert!(descriptor1.is_ok());
        assert_matches!(
            descriptor2.unwrap_err(),
            bdk::Error::Descriptor(Key(InvalidNetwork))
        )
    }
    #[test]
    fn test_wallet_from_descriptor() {
        let descriptor1 = Descriptor::new("wpkh(tprv8hwWMmPE4BVNxGdVt3HhEERZhondQvodUY7Ajyseyhudr4WabJqWKWLr4Wi2r26CDaNCQhhxEftEaNzz7dPGhWuKFU4VULesmhEfZYyBXdE/0/*)".to_string(), Network::Testnet).unwrap();
        let wallet1 = Wallet::new(
            Arc::new(Descriptor::new("wpkh(tprv8hwWMmPE4BVNxGdVt3HhEERZhondQvodUY7Ajyseyhudr4WabJqWKWLr4Wi2r26CDaNCQhhxEftEaNzz7dPGhWuKFU4VULesmhEfZYyBXdE/0/*)".to_string(), Network::Testnet).unwrap()),
            None,
            Network::Testnet,
            DatabaseConfig::Memory
        );
        let wallet2 = Wallet::new(
            Arc::new(descriptor1),
            None,
            Network::Bitcoin,
            DatabaseConfig::Memory,
        );
        // Creating a wallet using a Descriptor with an extended key that doesn't match the network provided in the wallet constructor will throw and InvalidNetwork Error
        assert!(wallet1.is_ok());
        assert_matches!(
            wallet2.unwrap_err(),
            bdk::Error::Descriptor(Key(InvalidNetwork))
        )
    }
}
