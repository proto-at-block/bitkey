use bitcoin::{bip32::ExtendedPubKey, key::Secp256k1, psbt::Psbt};
use crypto::chaincode_delegation::{
    psbt_with_tweaks as crypto_psbt_with_tweaks, ChaincodeDelegationError,
    HwAccountLevelDescriptorPublicKeys, Keyset, XpubWithOrigin,
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
    let (app_account_fingerprint, app_account_xprv) = match app_account_dprv {
        DescriptorSecretKey::XPrv(DescriptorXKey {
            origin: Some((app_account_fingerprint, _)),
            xkey: app_account_xprv,
            ..
        }) => (app_account_fingerprint, app_account_xprv),
        _ => panic!("Unsupported descriptor secret key type"),
    };
    let app_account_xpub = ExtendedPubKey::from_priv(&Secp256k1::new(), &app_account_xprv);

    let hw_account_level_descriptor_public_keys = match hw_dpub {
        DescriptorPublicKey::XPub(DescriptorXKey {
            origin: Some(origin),
            xkey,
            ..
        }) => HwAccountLevelDescriptorPublicKeys::new(origin.0, xkey),
        _ => panic!("Unsupported descriptor public key type"),
    };

    let crypto_keyset = Keyset {
        hw_descriptor_public_keys: hw_account_level_descriptor_public_keys,
        server_root_xpub,
        app_account_xpub_with_origin: XpubWithOrigin {
            fingerprint: app_account_fingerprint,
            xpub: app_account_xpub,
        },
    };

    crypto_psbt_with_tweaks(psbt, &crypto_keyset)
}
