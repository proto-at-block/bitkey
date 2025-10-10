use std::{collections::BTreeMap, error::Error, fmt::Display, str::FromStr};
pub mod common;

use bitcoin::{
    bip32::{ChainCode, ChildNumber, DerivationPath, ExtendedPrivKey, ExtendedPubKey, Fingerprint},
    psbt::{raw::ProprietaryKey, Psbt},
    secp256k1::{All, PublicKey, Secp256k1},
    Network,
};
use miniscript::descriptor::{DescriptorPublicKey, DescriptorXKey, Wildcard};
use rand::RngCore;

use common::{tweak_from_path, PROPRIETARY_KEY_PREFIX, PROPRIETARY_KEY_SUBTYPE};

// Generates server account descriptor public key given network and server root public key
// This is used by the app during the onboarding of a private account
pub fn server_account_dpub(network: Network, server_root_pubkey: PublicKey) -> DescriptorPublicKey {
    let network_index = if network == Network::Bitcoin { 0 } else { 1 };
    let account_path: DerivationPath = vec![
        ChildNumber::Normal { index: 84 },
        ChildNumber::Normal {
            index: network_index,
        },
        ChildNumber::Normal { index: 0 },
    ]
    .into();

    let server_root_xpub = server_root_xpub(network, server_root_pubkey);
    let server_account_xpub = server_root_xpub
        .derive_pub(&Secp256k1::new(), &account_path)
        .expect("derived server xpub");
    let origin = (server_root_xpub.fingerprint(), account_path);

    DescriptorPublicKey::XPub(DescriptorXKey {
        origin: Some(origin),
        xkey: server_account_xpub,
        derivation_path: DerivationPath::default(),
        wildcard: Wildcard::Unhardened,
    })
}

// Generates server root xpub given network and server root public key
fn server_root_xpub(network: Network, server_root_pubkey: PublicKey) -> ExtendedPubKey {
    ExtendedPubKey {
        network,
        depth: 0,
        parent_fingerprint: Default::default(),
        child_number: ChildNumber::from_normal_idx(0).expect("root child number"),
        public_key: server_root_pubkey,
        chain_code: generate_server_chaincode(),
    }
}

// Generates server chaincode
fn generate_server_chaincode() -> ChainCode {
    // We piggy-back off of the code written by rust-bitcoin instead of generating the chain_code
    // as-per bip32 ourselves. We do this by using the ExtendedPrivKey generation API, but discard
    // everything else other than the chain code.
    let mut chaincode_seed = [0u8; 32];
    rand::thread_rng().fill_bytes(&mut chaincode_seed);
    let ExtendedPrivKey { chain_code, .. } =
        ExtendedPrivKey::new_master(Network::Bitcoin, &chaincode_seed)
            .expect("server chaincode seed");
    chain_code
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ChaincodeDelegationError {
    KeyDerivation {
        reason: String,
    },
    TweakComputation {
        reason: String,
    },
    UnknownKey {
        fingerprint: Fingerprint,
    },
    KeyMismatch {
        expected: PublicKey,
        actual: PublicKey,
    },
}

impl Display for ChaincodeDelegationError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ChaincodeDelegationError::KeyDerivation { reason } => {
                write!(f, "Key derivation failed: {}", reason)
            }
            ChaincodeDelegationError::TweakComputation { reason } => {
                write!(f, "Tweak computation failed: {}", reason)
            }
            ChaincodeDelegationError::UnknownKey { fingerprint } => {
                write!(f, "Unknown master fingerprint: {}", fingerprint)
            }
            ChaincodeDelegationError::KeyMismatch { expected, actual } => {
                write!(f, "Key mismatch: expected {:?}, got {:?}", expected, actual)
            }
        }
    }
}

impl Error for ChaincodeDelegationError {}
pub type Result<T> = core::result::Result<T, ChaincodeDelegationError>;

// Add tweaks to the PSBT's inputs such that the App's counterparties with possession of:
/// - Their own master raw private key
/// - The App's raw public key derived up to the account level (i.e. 84h/0h/0h)
/// - The HW's raw public key derived up to the account level (i.e. 84h/0h/0h)
/// can sign the PSBT.
///
/// It works by reading the BIP32 derivation path from the PSBT and then using the fingerprint
/// of the corresponding key to determine which counterparty to tweak the PSBT for. Then, it
/// derives the tweak from the path and adds it to the PSBT's `proprietary` field, keyed using
/// a proprietary key with the prefix `CCDT`, subtype `0`, and the public key of the counterparty.
///
/// NOTE: Here, we assume that the App has possession of the HW's XPUB with it's master
/// fingerprint. This is important for the match to work.
///
/// # Arguments
///
/// * `secp` - The secp256k1 context.
/// * `psbt` - The PSBT to add tweaks to.
pub fn psbt_with_tweaks(psbt: Psbt, keyset: &Keyset) -> Result<Psbt> {
    let secp = Secp256k1::new();
    // Clone and make mutable
    let mut psbt = psbt.clone();

    for input in psbt.inputs.iter_mut() {
        for (final_pk, (master_fingerprint, path_from_parent)) in input.bip32_derivation.iter() {
            process_psbt_entry_tweaks(
                &secp,
                keyset,
                master_fingerprint,
                path_from_parent,
                final_pk,
                &mut input.proprietary,
            )?;
        }
    }
    for output in psbt.outputs.iter_mut() {
        for (final_pk, (master_fingerprint, path_from_parent)) in output.bip32_derivation.iter() {
            process_psbt_entry_tweaks(
                &secp,
                keyset,
                master_fingerprint,
                path_from_parent,
                final_pk,
                &mut output.proprietary,
            )?;
        }
    }

    Ok(psbt)
}

/// The HW's descriptor public keys. Derived up to the account level.
pub struct HwAccountLevelDescriptorPublicKeys {
    root_fingerprint: Fingerprint,
    account_xpub: ExtendedPubKey,
    account_path: DerivationPath,
}

impl HwAccountLevelDescriptorPublicKeys {
    /// Create a new HwAccountLevelDescriptorPublicKeys struct.
    pub fn new(root_fingerprint: Fingerprint, account_xpub: ExtendedPubKey) -> Self {
        Self {
            root_fingerprint,
            account_xpub,
            account_path: DerivationPath::from_str("m/84'/0'/0'")
                .expect("Failed to create account path"),
        }
    }

    pub fn external(&self) -> DescriptorPublicKey {
        DescriptorPublicKey::XPub(DescriptorXKey {
            origin: Some((self.root_fingerprint, self.account_path.clone())),
            xkey: self.account_xpub,
            derivation_path: DerivationPath::from(
                [ChildNumber::from_normal_idx(0).unwrap()].as_ref(),
            ),
            wildcard: Wildcard::Unhardened,
        })
    }

    pub fn internal(&self) -> DescriptorPublicKey {
        DescriptorPublicKey::XPub(DescriptorXKey {
            origin: Some((self.root_fingerprint, self.account_path.clone())),
            xkey: self.account_xpub,
            derivation_path: DerivationPath::from(
                [ChildNumber::from_normal_idx(1).unwrap()].as_ref(),
            ),
            wildcard: Wildcard::Unhardened,
        })
    }

    pub fn root_fingerprint(&self) -> Fingerprint {
        self.root_fingerprint
    }

    pub fn account_xpub(&self) -> ExtendedPubKey {
        self.account_xpub
    }
}

pub struct XpubWithOrigin {
    pub fingerprint: Fingerprint,
    pub xpub: ExtendedPubKey,
}

/// Standardize keyset that the App has possession of.
pub struct Keyset {
    pub hw_descriptor_public_keys: HwAccountLevelDescriptorPublicKeys,
    pub server_root_xpub: ExtendedPubKey,
    pub app_account_xpub_with_origin: XpubWithOrigin,
}

// Helper function to process BIP32 derivation and add tweaks to a PSBT entry's proprietary map.
fn process_psbt_entry_tweaks(
    secp: &Secp256k1<All>,
    keyset: &Keyset,
    master_fingerprint: &Fingerprint,
    path_from_parent: &DerivationPath,
    final_pk: &PublicKey,
    proprietary_map: &mut BTreeMap<ProprietaryKey, Vec<u8>>,
) -> Result<()> {
    if master_fingerprint == &keyset.hw_descriptor_public_keys.root_fingerprint() {
        // Matched HW master fingerprint
        let xpub = keyset.hw_descriptor_public_keys.account_xpub();

        // The XPUB is derived up to the account level, so we can use the last two child numbers
        // to derive the tweak.
        let (tweak, final_pk_from_hw) =
            tweak_from_path(secp, xpub, &path_from_parent[path_from_parent.len() - 2..])?;

        if final_pk_from_hw.public_key != *final_pk {
            return Err(ChaincodeDelegationError::KeyMismatch {
                expected: *final_pk,
                actual: final_pk_from_hw.public_key,
            });
        }

        proprietary_map.insert(
            ProprietaryKey {
                prefix: PROPRIETARY_KEY_PREFIX.to_vec(),
                subtype: PROPRIETARY_KEY_SUBTYPE,
                key: xpub.public_key.serialize().to_vec(),
            },
            tweak.to_be_bytes().to_vec(),
        );
    } else if master_fingerprint == &keyset.server_root_xpub.fingerprint() {
        // Matched server master fingerprint

        // The XPUB is at the root level, so we can use the full derivation path
        let (tweak, final_pk_from_server) =
            tweak_from_path(secp, keyset.server_root_xpub, path_from_parent.as_ref())?;

        if final_pk_from_server.public_key != *final_pk {
            return Err(ChaincodeDelegationError::KeyMismatch {
                expected: *final_pk,
                actual: final_pk_from_server.public_key,
            });
        }

        proprietary_map.insert(
            ProprietaryKey {
                prefix: PROPRIETARY_KEY_PREFIX.to_vec(),
                subtype: PROPRIETARY_KEY_SUBTYPE,
                key: keyset.server_root_xpub.public_key.serialize().to_vec(),
            },
            tweak.to_be_bytes().to_vec(),
        );
    } else if master_fingerprint == &keyset.app_account_xpub_with_origin.fingerprint {
        // Since the XPUB is derived up to the account level, we can use the last two child numbers
        // to derive the tweak.
        let app_account_xpub = keyset.app_account_xpub_with_origin.xpub;
        let (tweak, final_pk_from_app) = tweak_from_path(
            secp,
            app_account_xpub,
            path_from_parent[path_from_parent.len() - 2..].as_ref(),
        )?;

        if final_pk_from_app.public_key != *final_pk {
            return Err(ChaincodeDelegationError::KeyMismatch {
                expected: *final_pk,
                actual: final_pk_from_app.public_key,
            });
        }

        proprietary_map.insert(
            ProprietaryKey {
                prefix: PROPRIETARY_KEY_PREFIX.to_vec(),
                subtype: PROPRIETARY_KEY_SUBTYPE,
                key: app_account_xpub.public_key.serialize().to_vec(),
            },
            tweak.to_be_bytes().to_vec(),
        );
    } else {
        return Err(ChaincodeDelegationError::UnknownKey {
            fingerprint: *master_fingerprint,
        });
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use bitcoin::{
        bip32::{ChildNumber, DerivationPath, ExtendedPrivKey, ExtendedPubKey},
        psbt::Psbt,
        secp256k1::Secp256k1,
        Network,
    };
    use std::str::FromStr;

    fn create_test_keys() -> (ExtendedPrivKey, ExtendedPrivKey, ExtendedPrivKey) {
        let hw_root_xprv = ExtendedPrivKey::new_master(Network::Testnet, &[1; 32]).unwrap();
        let server_root_xprv = ExtendedPrivKey::new_master(Network::Testnet, &[2; 32]).unwrap();
        let app_root_xprv = ExtendedPrivKey::new_master(Network::Testnet, &[3; 32]).unwrap();

        (hw_root_xprv, server_root_xprv, app_root_xprv)
    }

    fn create_test_keyset() -> (Keyset, ExtendedPrivKey, ExtendedPrivKey) {
        let secp = Secp256k1::new();
        let account_path = DerivationPath::from_str("m/84'/0'/0'").unwrap();
        let (hw_root_xprv, server_root_xprv, app_root_xprv) = create_test_keys();

        let app_account_level_xpub = ExtendedPubKey::from_priv(
            &secp,
            &app_root_xprv.derive_priv(&secp, &account_path).unwrap(),
        );

        // Create HW account level keys (m/84'/0'/0'/0 and m/84'/0'/0'/1)
        let hw_account_level_xpub = ExtendedPubKey::from_priv(
            &secp,
            &hw_root_xprv.derive_priv(&secp, &account_path).unwrap(),
        );

        let hw_descriptor_keys = HwAccountLevelDescriptorPublicKeys::new(
            hw_root_xprv.fingerprint(&secp),
            hw_account_level_xpub,
        );

        let server_root_xpub = ExtendedPubKey::from_priv(&secp, &server_root_xprv);

        let keyset = Keyset {
            hw_descriptor_public_keys: hw_descriptor_keys,
            server_root_xpub,
            app_account_xpub_with_origin: XpubWithOrigin {
                fingerprint: app_root_xprv.fingerprint(&secp),
                xpub: app_account_level_xpub,
            },
        };

        (keyset, hw_root_xprv, server_root_xprv)
    }

    #[test]
    fn test_hw_descriptor_keys_validation_success() {
        let (keyset, _, _) = create_test_keyset();
        // Should not panic - validation passed during creation
        assert_eq!(
            keyset.hw_descriptor_public_keys.root_fingerprint(),
            keyset
                .hw_descriptor_public_keys
                .internal()
                .master_fingerprint()
        );
    }

    fn create_default_psbt() -> Psbt {
        Psbt::from_unsigned_tx(bitcoin::Transaction {
            version: 2,
            lock_time: bitcoin::locktime::absolute::LockTime::ZERO,
            input: vec![bitcoin::TxIn::default()],
            output: vec![bitcoin::TxOut::default()],
        })
        .unwrap()
    }

    #[test]
    fn test_tweak_from_path() {
        let secp = Secp256k1::new();
        let (_, _, app_root_xprv) = create_test_keys();
        let app_root_xpub = ExtendedPubKey::from_priv(&secp, &app_root_xprv);

        // Tweaks "m/0/5"
        let path = [
            ChildNumber::from_normal_idx(0).unwrap(),
            ChildNumber::from_normal_idx(5).unwrap(),
        ];
        let (tweak, derived_key) = tweak_from_path(&secp, app_root_xpub, &path).unwrap();

        let expected_key = app_root_xpub.derive_pub(&secp, &path).unwrap();
        assert_eq!(derived_key.public_key, expected_key.public_key);
        assert_ne!(tweak, bitcoin::secp256k1::Scalar::ZERO);
    }

    #[test]
    fn test_psbt_with_tweaks_hw_key() {
        let secp = Secp256k1::new();
        let (keyset, hw_root_xprv, _) = create_test_keyset();

        // Create a PSBT with HW key derivation info
        let mut psbt = create_default_psbt();
        let derivation_path = DerivationPath::from_str("m/84'/0'/0'/0/5").unwrap();
        let derived_key = hw_root_xprv.derive_priv(&secp, &derivation_path).unwrap();
        let derived_pubkey = derived_key.to_priv().public_key(&secp);

        psbt.inputs[0].bip32_derivation.insert(
            derived_pubkey.inner,
            (
                keyset.hw_descriptor_public_keys.root_fingerprint(),
                derivation_path,
            ),
        );

        // Apply tweaks
        psbt = psbt_with_tweaks(psbt, &keyset).unwrap();

        // Verify that proprietary data was added
        assert!(!psbt.inputs[0].proprietary.is_empty());

        let prop_key = psbt.inputs[0].proprietary.keys().next().unwrap();
        let tweak_bytes = psbt.inputs[0].proprietary.values().next().unwrap();

        assert_eq!(prop_key.prefix, PROPRIETARY_KEY_PREFIX);
        assert_eq!(prop_key.subtype, PROPRIETARY_KEY_SUBTYPE);
        assert_eq!(tweak_bytes.len(), 32);
    }

    #[test]
    fn test_psbt_with_tweaks_server_key() {
        let secp = Secp256k1::new();
        let (keyset, _, server_root_xprv) = create_test_keyset();

        // Create a PSBT with server key derivation info
        let mut psbt = create_default_psbt();

        // Add BIP32 derivation info for server key. Server keys going forward would use unhardened
        // derivation paths.
        let derivation_path = DerivationPath::from_str("m/84/0/0/0/10").unwrap();
        let derived_key = server_root_xprv
            .derive_priv(&secp, &derivation_path)
            .unwrap();
        let derived_pubkey = derived_key.to_priv().public_key(&secp);

        psbt.inputs[0].bip32_derivation.insert(
            derived_pubkey.inner,
            (server_root_xprv.fingerprint(&secp), derivation_path),
        );

        psbt = psbt_with_tweaks(psbt, &keyset).unwrap();

        let prop_key = psbt.inputs[0].proprietary.keys().next().unwrap();
        assert!(!psbt.inputs[0].proprietary.is_empty());
        assert_eq!(
            prop_key.key,
            keyset.server_root_xpub.public_key.serialize().to_vec()
        );
    }

    #[test]
    fn test_psbt_with_tweaks_app_key() {
        let secp = Secp256k1::new();
        let (keyset, _, _) = create_test_keyset();

        // Create a PSBT with app key derivation info
        let mut psbt = create_default_psbt();

        // Add BIP32 derivation info for app key
        let derivation_path = DerivationPath::from_str("m/84'/0'/0'/1/3").unwrap();
        let derived_pubkey = keyset
            .app_account_xpub_with_origin
            .xpub
            .derive_pub(
                &secp,
                &derivation_path[derivation_path.len() - 2..].to_vec(),
            )
            .unwrap();

        psbt.inputs[0].bip32_derivation.insert(
            derived_pubkey.public_key,
            (
                keyset.app_account_xpub_with_origin.fingerprint,
                derivation_path.into(),
            ),
        );

        psbt = psbt_with_tweaks(psbt, &keyset).unwrap();
        let prop_key = psbt.inputs[0].proprietary.keys().next().unwrap();

        assert!(!psbt.inputs[0].proprietary.is_empty());
        assert_eq!(prop_key.prefix, PROPRIETARY_KEY_PREFIX);
        assert_eq!(prop_key.subtype, PROPRIETARY_KEY_SUBTYPE);
    }

    #[test]
    fn test_psbt_with_tweaks_multiple_keys() {
        let secp = Secp256k1::new();
        let (keyset, hw_root_xprv, server_root_xprv) = create_test_keyset();

        let mut psbt = create_default_psbt();

        // Add path information for both HW and Server
        let hw_path = DerivationPath::from_str("m/84'/0'/0'/0/1").unwrap();
        let hw_derived = hw_root_xprv.derive_priv(&secp, &hw_path).unwrap();
        psbt.inputs[0].bip32_derivation.insert(
            hw_derived.to_priv().public_key(&secp).inner,
            (keyset.hw_descriptor_public_keys.root_fingerprint(), hw_path),
        );

        let server_path = DerivationPath::from_str("m/84/0/0/1/2").unwrap();
        let server_derived = server_root_xprv.derive_priv(&secp, &server_path).unwrap();
        psbt.inputs[0].bip32_derivation.insert(
            server_derived.to_priv().public_key(&secp).inner,
            (server_root_xprv.fingerprint(&secp), server_path),
        );

        psbt = psbt_with_tweaks(psbt, &keyset).unwrap();

        assert_eq!(psbt.inputs[0].proprietary.len(), 2);
        for tweak_bytes in psbt.inputs[0].proprietary.values() {
            assert_eq!(tweak_bytes.len(), 32);
        }
    }

    #[test]
    fn test_psbt_outputs_also_get_tweaks() {
        let secp = Secp256k1::new();
        let (keyset, hw_root_xprv, _) = create_test_keyset();

        let mut psbt = create_default_psbt();

        // Add BIP32 derivation info to output
        let derivation_path = DerivationPath::from_str("m/84'/0'/0'/1/7").unwrap();
        let derived_key = hw_root_xprv.derive_priv(&secp, &derivation_path).unwrap();
        let derived_pubkey = derived_key.to_priv().public_key(&secp);

        psbt.outputs[0].bip32_derivation.insert(
            derived_pubkey.inner,
            (
                keyset.hw_descriptor_public_keys.root_fingerprint(),
                derivation_path,
            ),
        );

        psbt = psbt_with_tweaks(psbt, &keyset).unwrap();

        assert!(!psbt.outputs[0].proprietary.is_empty());

        let prop_key = psbt.outputs[0].proprietary.keys().next().unwrap();
        assert_eq!(prop_key.prefix, PROPRIETARY_KEY_PREFIX);
        assert_eq!(prop_key.subtype, PROPRIETARY_KEY_SUBTYPE);
    }

    #[test]
    fn test_psbt_with_tweaks_key_mismatch_error() {
        let secp = Secp256k1::new();
        let (keyset, hw_root_xprv, _) = create_test_keyset();

        // Create a sample PSBT
        let mut psbt = create_default_psbt();

        // Use the correct derivation path for the fingerprint
        let derivation_path = DerivationPath::from_str("m/84'/0'/0'/0/5").unwrap();

        // But derive a DIFFERENT key for the actual public key
        let wrong_path = DerivationPath::from_str("m/84'/0'/0'/0/10").unwrap();
        let wrong_key = hw_root_xprv.derive_priv(&secp, &wrong_path).unwrap();
        let wrong_pubkey = wrong_key.to_priv().public_key(&secp);

        // Insert with mismatched key - correct fingerprint but wrong public key
        psbt.inputs[0].bip32_derivation.insert(
            wrong_pubkey.inner,
            (
                keyset.hw_descriptor_public_keys.root_fingerprint(),
                derivation_path.clone(),
            ),
        );

        assert!(matches!(
            psbt_with_tweaks(psbt, &keyset),
            Err(ChaincodeDelegationError::KeyMismatch { .. })
        ));
    }

    #[test]
    fn test_psbt_with_tweaks_unknown_key_error() {
        let secp = Secp256k1::new();
        let (keyset, _, _) = create_test_keyset();

        // Create an unrelated key that won't match any in the keyset
        let unknown_xprv = ExtendedPrivKey::new_master(Network::Testnet, &[94; 32]).unwrap();

        let mut psbt = create_default_psbt();
        let derivation_path = DerivationPath::from_str("m/84'/0'/0'/0/1").unwrap();
        let derived_key = unknown_xprv.derive_priv(&secp, &derivation_path).unwrap();
        let derived_pubkey = derived_key.to_priv().public_key(&secp);

        // Insert with unknown fingerprint
        psbt.inputs[0].bip32_derivation.insert(
            derived_pubkey.inner,
            (unknown_xprv.fingerprint(&secp), derivation_path),
        );

        assert!(matches!(
            psbt_with_tweaks(psbt, &keyset),
            Err(ChaincodeDelegationError::UnknownKey { .. })
        ));
    }

    #[test]
    fn test_server_account_dpub() {
        let secp = Secp256k1::new();
        let server_root_xprv = ExtendedPrivKey::new_master(Network::Bitcoin, &[42; 32]).unwrap();
        let server_root_pubkey = server_root_xprv.to_priv().public_key(&secp);

        let dpub = server_account_dpub(Network::Bitcoin, server_root_pubkey.inner);

        // Verify it's a valid descriptor public key with expected structure
        match dpub {
            DescriptorPublicKey::XPub(xkey) => {
                let expected_path = DerivationPath::from_str("m/84/0/0").unwrap();
                assert_eq!(xkey.origin.as_ref().unwrap().1, expected_path);
                assert_eq!(xkey.wildcard, Wildcard::Unhardened);
                assert_eq!(xkey.derivation_path, DerivationPath::default());

                // Verify the xpub network matches
                assert_eq!(xkey.xkey.network, Network::Bitcoin);
            }
            _ => panic!("Expected XPub descriptor key"),
        }
    }

    #[test]
    fn test_server_account_dpub_deterministic_chaincode() {
        // Test that server_account_dpub generates different chaincodes each time
        // since it uses random generation
        let secp = Secp256k1::new();
        let server_root_xprv = ExtendedPrivKey::new_master(Network::Bitcoin, &[44; 32]).unwrap();
        let server_root_pubkey = server_root_xprv.to_priv().public_key(&secp);

        let dpub1 = server_account_dpub(Network::Bitcoin, server_root_pubkey.inner);
        let dpub2 = server_account_dpub(Network::Bitcoin, server_root_pubkey.inner);

        // Extract chaincodes and verify they're different (due to random generation)
        match (dpub1, dpub2) {
            (DescriptorPublicKey::XPub(xkey1), DescriptorPublicKey::XPub(xkey2)) => {
                assert_ne!(xkey1.xkey.chain_code, xkey2.xkey.chain_code);
                // Since chaincodes are different, the derived public keys will also be different
                assert_ne!(xkey1.xkey.public_key, xkey2.xkey.public_key);
            }
            _ => panic!("Expected XPub descriptor keys"),
        }
    }

    #[test]
    fn test_server_root_xpub_properties() {
        let secp = Secp256k1::new();
        let server_root_xprv = ExtendedPrivKey::new_master(Network::Bitcoin, &[45; 32]).unwrap();
        let server_root_pubkey = server_root_xprv.to_priv().public_key(&secp);

        let xpub = server_root_xpub(Network::Bitcoin, server_root_pubkey.inner);

        // Verify root xpub properties
        assert_eq!(xpub.network, Network::Bitcoin);
        assert_eq!(xpub.depth, 0);
        assert_eq!(xpub.parent_fingerprint, Fingerprint::default());
        assert_eq!(xpub.child_number, ChildNumber::from_normal_idx(0).unwrap());
        assert_eq!(xpub.public_key, server_root_pubkey.inner);
    }
}
