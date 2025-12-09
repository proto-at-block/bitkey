use std::{collections::BTreeMap, error::Error, fmt::Display};
pub mod common;

use bitcoin::{
    bip32::{ChildNumber, DerivationPath, ExtendedPubKey, Fingerprint},
    psbt::{raw::ProprietaryKey, Psbt},
    secp256k1::{All, PublicKey, Scalar, Secp256k1},
    Network,
};
use miniscript::{
    descriptor::{DescriptorPublicKey, DescriptorXKey, Wildcard},
    Descriptor,
};

use common::{tweak_from_path, PROPRIETARY_KEY_PREFIX, PROPRIETARY_KEY_SUBTYPE};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ChaincodeDelegationError {
    InvalidPsbt {
        reason: String,
    },
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
            ChaincodeDelegationError::InvalidPsbt { reason } => {
                write!(f, "Invalid PSBT: {}", reason)
            }
        }
    }
}

impl Error for ChaincodeDelegationError {}
pub type Result<T> = core::result::Result<T, ChaincodeDelegationError>;

/// A wrapper around a PSBT that allows for adding tweaks to the PSBT's inputs and outputs.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct UntweakedPsbt(Psbt);
impl UntweakedPsbt {
    pub fn new(psbt: Psbt) -> Self {
        Self(psbt)
    }

    /// Prepares a PSBT for cosigning for non-sweep transactions.
    ///
    /// Add tweaks to the PSBT's inputs such that the App's counterparties with possession of:
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
    /// * `source_keyset` - The keyset that will be used to tweak the PSBT's inputs.
    pub fn with_source_wallet_tweaks(
        mut self,
        source_keyset: &Keyset,
    ) -> Result<WithSourceWalletTweaksPsbt> {
        let secp = Secp256k1::new();
        for input in self.0.inputs.iter_mut() {
            for (final_pk, (master_fingerprint, path_from_parent)) in input.bip32_derivation.iter()
            {
                process_psbt_entry_tweaks(
                    &secp,
                    source_keyset,
                    master_fingerprint,
                    path_from_parent,
                    final_pk,
                    &mut input.proprietary,
                )?;
            }
        }
        for output in self.0.outputs.iter_mut() {
            for (final_pk, (master_fingerprint, path_from_parent)) in output.bip32_derivation.iter()
            {
                process_psbt_entry_tweaks(
                    &secp,
                    source_keyset,
                    master_fingerprint,
                    path_from_parent,
                    final_pk,
                    &mut output.proprietary,
                )?;
            }
        }

        Ok(WithSourceWalletTweaksPsbt(self.0))
    }

    /// Prepares a PSBT for cosigning for migration sweep transactions.
    ///
    /// Add tweaks to the PSBT's inputs such that the Server with possession of the target keyset's:
    /// - Server master raw private key
    /// - App's raw public key derived up to the account level (i.e. 84h/0h/0h)
    /// - HW's raw public key derived up to the account level (i.e. 84h/0h/0h)
    /// can verify the sweep output of the PSBT.
    ///
    /// # Arguments
    /// * `target_keyset` - The keyset that will control the swept funds
    pub fn with_migration_sweep_prepared_tweaks(
        self,
        target_keyset: &Keyset,
    ) -> Result<SweepPreparedPsbt> {
        if self.0.outputs.len() != 1 || self.0.unsigned_tx.output.len() != 1 {
            return Err(ChaincodeDelegationError::InvalidPsbt {
                reason: "PSBT must have exactly one output".to_string(),
            });
        }

        let psbt = sweep_psbt_with_tweaks(self.0, target_keyset)?;
        Ok(SweepPreparedPsbt(psbt))
    }

    pub fn into_psbt(self) -> Psbt {
        self.0
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct WithSourceWalletTweaksPsbt(Psbt);
impl WithSourceWalletTweaksPsbt {
    /// Prepares a PSBT for cosigning for private wallet sweep transactions.
    ///
    /// Add tweaks to the PSBT's sweep output such that the Server with possession of the target keyset's:
    /// - Server master raw private key
    /// - App's raw public key derived up to the account level (i.e. 84h/0h/0h)
    /// - HW's raw public key derived up to the account level (i.e. 84h/0h/0h)
    /// can verify the sweep output of the PSBT.
    ///
    /// # Arguments
    /// * `target_keyset` - The keyset that will control the swept funds
    pub fn with_sweep_prepared_tweaks(self, target_keyset: &Keyset) -> Result<SweepPreparedPsbt> {
        if self.0.outputs.len() != 1 || self.0.unsigned_tx.output.len() != 1 {
            return Err(ChaincodeDelegationError::InvalidPsbt {
                reason: "PSBT must have exactly one output".to_string(),
            });
        }

        let psbt = sweep_psbt_with_tweaks(self.0, target_keyset)?;
        Ok(SweepPreparedPsbt(psbt))
    }

    pub fn into_psbt(self) -> Psbt {
        self.0
    }
}

/// A wrapped around a PSBT that represents a PSBT with input and output tweaks ready for validation
/// and signing.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct SweepPreparedPsbt(Psbt);
impl SweepPreparedPsbt {
    pub fn into_psbt(self) -> Psbt {
        self.0
    }
}

fn sweep_psbt_with_tweaks(psbt: Psbt, target_keyset: &Keyset) -> Result<Psbt> {
    let mut psbt = psbt.clone();
    // Assemble target keyset information to derive the sweep output descriptor.

    // HW and App keys are derived up to the account level, so we can use the last two child numbers
    // to derive the tweak. Server key is at the root level, so we need to use the full derivation
    // path.
    let sweep_server_path = get_account_path(target_keyset.server_root_xpub.network)
        .extend(&SWEEP_RECEIVE_ADDRESS_PATH[..]);
    let participants = [
        (
            target_keyset.app_account_xpub_with_origin.xpub,
            &SWEEP_RECEIVE_ADDRESS_PATH[..],
        ),
        (
            target_keyset.hw_descriptor_public_keys.account_xpub(),
            &SWEEP_RECEIVE_ADDRESS_PATH[..],
        ),
        (target_keyset.server_root_xpub, &sweep_server_path[..]),
    ];

    // For each participant, derive the tweak and the tweaked public key.
    let secp = Secp256k1::new();
    let sweep_keys = participants
        .iter()
        .map(|(base_xpub, path)| SweepKey::new(&secp, *base_xpub, path))
        .collect::<Result<Vec<SweepKey>>>()?;

    // Using the tweaked child public keys to build the sweep output descriptor.
    let tweaked_pubkeys: Vec<PublicKey> = sweep_keys
        .iter()
        .map(|metadata| metadata.child_pubkey())
        .collect();
    let descriptor: Descriptor<PublicKey> = Descriptor::new_wsh_sortedmulti(2, tweaked_pubkeys)
        .map_err(|_| ChaincodeDelegationError::InvalidPsbt {
            reason: "Failed to build sweep descriptor".to_string(),
        })?;

    // Enforce that the expected script pubkey in the tx generated by BDK matches the descriptor.
    let sweep_txout =
        psbt.unsigned_tx
            .output
            .get(0)
            .ok_or_else(|| ChaincodeDelegationError::InvalidPsbt {
                reason: "PSBT must have exactly one output".to_string(),
            })?;
    if sweep_txout.script_pubkey != descriptor.script_pubkey() {
        return Err(ChaincodeDelegationError::InvalidPsbt {
            reason: "Script pubkey mismatch.".to_string(),
        });
    }

    // Build the witness script, and add the proprietary tweaks for the sweep output validation.
    let witness_script =
        descriptor
            .explicit_script()
            .map_err(|_| ChaincodeDelegationError::InvalidPsbt {
                reason: "Failed to build witness script".to_string(),
            })?;

    let sweep_output =
        psbt.outputs
            .get_mut(0)
            .ok_or_else(|| ChaincodeDelegationError::InvalidPsbt {
                reason: "PSBT must have exactly one output".to_string(),
            })?;

    sweep_output.witness_script = Some(witness_script);
    for sweep_key in &sweep_keys {
        sweep_output.proprietary.insert(
            ProprietaryKey {
                prefix: PROPRIETARY_KEY_PREFIX.to_vec(),
                subtype: PROPRIETARY_KEY_SUBTYPE,
                key: sweep_key.base_pubkey().serialize().to_vec(),
            },
            sweep_key.tweak.to_be_bytes().to_vec(),
        );
    }

    Ok(psbt)
}

// Sweep paths should always use the first receive address of the target keyset.
const SWEEP_RECEIVE_ADDRESS_PATH: [ChildNumber; 2] = [
    ChildNumber::Normal { index: 0 },
    ChildNumber::Normal { index: 0 },
];

#[derive(Clone, Debug)]
struct SweepKey {
    /// Base, un-tweaked, extended public key.
    base_xpub: ExtendedPubKey,
    /// The BIP32 scalar tweak derived using [`SWEEP_RECEIVE_ADDRESS_PATH`] from the base public key.
    tweak: Scalar,
    /// The tweaked extended public key derived using the tweak.
    child_xpub: ExtendedPubKey,
}

impl SweepKey {
    fn new(secp: &Secp256k1<All>, base_xpub: ExtendedPubKey, path: &[ChildNumber]) -> Result<Self> {
        tweak_from_path(secp, base_xpub, path).map(|(tweak, child_xpub)| Self {
            base_xpub,
            tweak,
            child_xpub,
        })
    }

    fn base_pubkey(&self) -> PublicKey {
        self.base_xpub.public_key
    }

    fn child_pubkey(&self) -> PublicKey {
        self.child_xpub.public_key
    }
}

/// The HW's descriptor public keys. Derived up to the account level.
#[derive(Debug, Clone, PartialEq, Eq)]
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
            account_path: get_account_path(account_xpub.network),
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

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct XpubWithOrigin {
    pub fingerprint: Fingerprint,
    pub xpub: ExtendedPubKey,
}

/// Standardize keyset that the App has possession of.
#[derive(Debug, Clone, PartialEq, Eq)]
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

fn get_account_path(network: Network) -> DerivationPath {
    let network_index = if network == Network::Bitcoin { 0 } else { 1 };

    vec![
        ChildNumber::Normal { index: 84 },
        ChildNumber::Normal {
            index: network_index,
        },
        ChildNumber::Normal { index: 0 },
    ]
    .into()
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
        psbt = UntweakedPsbt::new(psbt)
            .with_source_wallet_tweaks(&keyset)
            .unwrap()
            .into_psbt();

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

        psbt = UntweakedPsbt::new(psbt)
            .with_source_wallet_tweaks(&keyset)
            .unwrap()
            .into_psbt();

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

        psbt = UntweakedPsbt::new(psbt)
            .with_source_wallet_tweaks(&keyset)
            .unwrap()
            .into_psbt();
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

        psbt = UntweakedPsbt::new(psbt)
            .with_source_wallet_tweaks(&keyset)
            .unwrap()
            .into_psbt();

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

        psbt = UntweakedPsbt::new(psbt)
            .with_source_wallet_tweaks(&keyset)
            .unwrap()
            .into_psbt();

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
            UntweakedPsbt::new(psbt).with_source_wallet_tweaks(&keyset),
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
            UntweakedPsbt::new(psbt).with_source_wallet_tweaks(&keyset),
            Err(ChaincodeDelegationError::UnknownKey { .. })
        ));
    }

    #[test]
    fn test_sweep_psbt_with_tweaks_no_sweep_outputs() {
        let (source_keyset, _, _) = create_test_keyset();
        let (target_keyset, _, _) = create_test_keyset();

        let mut psbt = create_default_psbt();
        psbt.outputs.clear();

        let result = UntweakedPsbt::new(psbt)
            .with_source_wallet_tweaks(&source_keyset)
            .and_then(|p| p.with_sweep_prepared_tweaks(&target_keyset));
        assert!(
            matches!(
                result,
                Err(ChaincodeDelegationError::InvalidPsbt { ref reason })
                    if reason.contains("exactly one output")
            ),
            "Expected invalid PSBT error for missing sweep output, got: {:?}",
            result
        );
    }

    #[test]
    fn test_sweep_psbt_with_tweaks_no_sweep_txouts() {
        let (source_keyset, _, _) = create_test_keyset();
        let (target_keyset, _, _) = create_test_keyset();

        let mut psbt = create_default_psbt();
        psbt.unsigned_tx.output.clear();

        let result = UntweakedPsbt::new(psbt)
            .with_source_wallet_tweaks(&source_keyset)
            .and_then(|p| p.with_sweep_prepared_tweaks(&target_keyset));
        assert!(
            matches!(
                result,
                Err(ChaincodeDelegationError::InvalidPsbt { ref reason })
                    if reason.contains("exactly one output")
            ),
            "Expected invalid PSBT error for missing sweep txout, got: {:?}",
            result
        );
    }

    #[test]
    fn test_sweep_psbt_with_tweaks_more_than_one_sweep_outputs() {
        let (source_keyset, _, _) = create_test_keyset();
        let (target_keyset, _, _) = create_test_keyset();

        let mut psbt = create_default_psbt();
        psbt.outputs.push(Default::default());

        let result = UntweakedPsbt::new(psbt)
            .with_source_wallet_tweaks(&source_keyset)
            .and_then(|p| p.with_sweep_prepared_tweaks(&target_keyset));

        assert!(
            matches!(
                result,
                Err(ChaincodeDelegationError::InvalidPsbt { ref reason })
                    if reason.contains("exactly one output")
            ),
            "Expected invalid PSBT error for multiple sweep outputs, got: {:?}",
            result
        );
    }

    #[test]
    fn test_sweep_psbt_with_tweaks_more_than_one_sweep_txouts() {
        let (source_keyset, _, _) = create_test_keyset();
        let (target_keyset, _, _) = create_test_keyset();

        let mut psbt = create_default_psbt();
        psbt.unsigned_tx.output.push(Default::default());

        let result = UntweakedPsbt::new(psbt)
            .with_source_wallet_tweaks(&source_keyset)
            .and_then(|p| p.with_sweep_prepared_tweaks(&target_keyset));
        assert!(
            matches!(
                result,
                Err(ChaincodeDelegationError::InvalidPsbt { ref reason })
                    if reason.contains("exactly one output")
            ),
            "Expected invalid PSBT error for multiple sweep outputs, got: {:?}",
            result
        );
    }

    #[test]
    fn test_sweep_psbt_with_tweaks_script_pubkey_mismatch() {
        let (source_keyset, _, _) = create_test_keyset();
        let (target_keyset, _, _) = create_test_keyset();

        let mut psbt = create_default_psbt();
        psbt.unsigned_tx.output[0].script_pubkey = bitcoin::ScriptBuf::new();

        let result = UntweakedPsbt::new(psbt)
            .with_source_wallet_tweaks(&source_keyset)
            .and_then(|p| p.with_sweep_prepared_tweaks(&target_keyset));
        assert!(
            matches!(
                result,
                Err(ChaincodeDelegationError::InvalidPsbt { ref reason })
                    if reason.contains("Script pubkey mismatch")
            ),
            "Expected script pubkey mismatch error, got: {:?}",
            result
        );
    }
}
