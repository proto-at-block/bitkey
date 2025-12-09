use bitcoin::{
    bip32::{ChainCode, ChildNumber, DerivationPath, ExtendedPrivKey, ExtendedPubKey},
    key::Secp256k1,
    psbt::Psbt,
    secp256k1::{
        rand::{self, RngCore},
        All, PublicKey,
    },
    Network,
};
use crypto::chaincode_delegation::{
    ChaincodeDelegationError, HwAccountLevelDescriptorPublicKeys, Keyset, UntweakedPsbt,
    XpubWithOrigin,
};
use miniscript::{
    descriptor::{DescriptorSecretKey, DescriptorXKey, Wildcard},
    DescriptorPublicKey,
};

pub fn psbt_with_tweaks(
    psbt: Psbt,
    app_account_dprv: DescriptorSecretKey,
    server_root_xpub: ExtendedPubKey,
    hw_dpub: DescriptorPublicKey,
) -> Result<Psbt, ChaincodeDelegationError> {
    let keyset = KeysetComponents::from_dprv(
        &Secp256k1::new(),
        app_account_dprv,
        server_root_xpub,
        hw_dpub,
    )?;

    UntweakedPsbt::new(psbt)
        .with_source_wallet_tweaks(&keyset.into())
        .map(|p| p.into_psbt())
}

pub fn sweep_psbt_with_tweaks(
    psbt: Psbt,
    source_app_account_dpub: DescriptorPublicKey,
    source_server_root_xpub: ExtendedPubKey,
    source_hw_dpub: DescriptorPublicKey,
    target_app_account_dprv: DescriptorSecretKey,
    target_server_root_xpub: ExtendedPubKey,
    target_hw_dpub: DescriptorPublicKey,
) -> Result<Psbt, ChaincodeDelegationError> {
    let source_keyset = KeysetComponents::from_dpub(
        source_app_account_dpub,
        source_server_root_xpub,
        source_hw_dpub,
    );
    let target_keyset = KeysetComponents::from_dprv(
        &Secp256k1::new(),
        target_app_account_dprv,
        target_server_root_xpub,
        target_hw_dpub,
    )?;

    UntweakedPsbt::new(psbt)
        .with_source_wallet_tweaks(&source_keyset.into())
        .and_then(|p| p.with_sweep_prepared_tweaks(&target_keyset.into()))
        .map(|p| p.into_psbt())
}

pub fn migration_sweep_psbt_with_tweaks(
    psbt: Psbt,
    target_app_account_dprv: DescriptorSecretKey,
    target_server_root_xpub: ExtendedPubKey,
    target_hw_dpub: DescriptorPublicKey,
) -> Result<Psbt, ChaincodeDelegationError> {
    let target_keyset = KeysetComponents::from_dprv(
        &Secp256k1::new(),
        target_app_account_dprv,
        target_server_root_xpub,
        target_hw_dpub,
    )?;

    UntweakedPsbt::new(psbt)
        .with_migration_sweep_prepared_tweaks(&target_keyset.into())
        .map(|p| p.into_psbt())
}

/// Generates server account descriptor public key given network and server root xpub
/// This is used by the app during the onboarding of a private account
pub fn server_account_dpub(
    network: Network,
    server_root_xpub: ExtendedPubKey,
) -> DescriptorPublicKey {
    let account_path = get_account_path(network);

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

/// Generates server root xpub given network and server root public key
pub fn server_root_xpub(network: Network, server_root_pubkey: PublicKey) -> ExtendedPubKey {
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

struct KeysetComponents {
    app_account_dpub: DescriptorPublicKey,
    server_root_xpub: ExtendedPubKey,
    hw_dpub: DescriptorPublicKey,
}

impl KeysetComponents {
    fn from_dpub(
        app_account_dpub: DescriptorPublicKey,
        server_root_xpub: ExtendedPubKey,
        hw_dpub: DescriptorPublicKey,
    ) -> Self {
        Self {
            app_account_dpub,
            server_root_xpub,
            hw_dpub,
        }
    }

    fn from_dprv(
        secp: &Secp256k1<All>,
        app_account_dprv: DescriptorSecretKey,
        server_root_xpub: ExtendedPubKey,
        hw_dpub: DescriptorPublicKey,
    ) -> Result<Self, ChaincodeDelegationError> {
        let app_account_dpub = app_account_dprv.to_public(secp).map_err(|e| {
            ChaincodeDelegationError::KeyDerivation {
                reason: e.to_string(),
            }
        })?;

        Ok(Self {
            app_account_dpub,
            server_root_xpub,
            hw_dpub,
        })
    }
}

impl From<KeysetComponents> for Keyset {
    fn from(components: KeysetComponents) -> Self {
        let hw_descriptor_public_keys = match components.hw_dpub {
            DescriptorPublicKey::XPub(DescriptorXKey {
                origin: Some(origin),
                xkey,
                ..
            }) => HwAccountLevelDescriptorPublicKeys::new(origin.0, xkey),
            _ => panic!("Unsupported descriptor public key type"),
        };
        let app_account_xpub_with_origin = match components.app_account_dpub {
            DescriptorPublicKey::XPub(DescriptorXKey {
                origin: Some((app_account_fingerprint, _)),
                xkey: app_account_xpub,
                ..
            }) => XpubWithOrigin {
                fingerprint: app_account_fingerprint,
                xpub: app_account_xpub,
            },
            _ => panic!("Unsupported descriptor public key type"),
        };

        Self {
            hw_descriptor_public_keys,
            app_account_xpub_with_origin,
            server_root_xpub: components.server_root_xpub,
        }
    }
}

#[cfg(test)]
mod tests {
    use std::str::FromStr;

    use bitcoin::{
        bip32::{ChildNumber, DerivationPath, ExtendedPrivKey, ExtendedPubKey, Fingerprint},
        secp256k1::Secp256k1,
        Network,
    };
    use miniscript::{descriptor::Wildcard, DescriptorPublicKey};

    use crate::{server_account_dpub, server_root_xpub};

    #[test]
    fn test_server_account_dpub() {
        let secp = Secp256k1::new();
        let server_root_xprv = ExtendedPrivKey::new_master(Network::Bitcoin, &[42; 32]).unwrap();
        let server_root_xpub = ExtendedPubKey::from_priv(&secp, &server_root_xprv);

        let dpub = server_account_dpub(Network::Bitcoin, server_root_xpub);

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

        let server_root_xpub1 = server_root_xpub(Network::Bitcoin, server_root_pubkey.inner);
        let server_root_xpub2 = server_root_xpub(Network::Bitcoin, server_root_pubkey.inner);

        let dpub1 = server_account_dpub(Network::Bitcoin, server_root_xpub1);
        let dpub2 = server_account_dpub(Network::Bitcoin, server_root_xpub2);

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
