use bitcoin::{bip32::ExtendedPubKey, key::Secp256k1, psbt::Psbt, secp256k1::All};
use crypto::chaincode_delegation::{
    ChaincodeDelegationError, HwAccountLevelDescriptorPublicKeys, Keyset, UntweakedPsbt,
    XpubWithOrigin,
};
use miniscript::{
    descriptor::DescriptorSecretKey, descriptor::DescriptorXKey, DescriptorPublicKey,
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
