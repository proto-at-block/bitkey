use bitcoin::{
    bip32::{ExtendedPrivKey, ExtendedPubKey},
    psbt::Psbt,
};
use crypto::chaincode_delegation::{
    psbt_with_tweaks as crypto_psbt_with_tweaks, ChaincodeDelegationError,
    HwAccountLevelDescriptorPublicKeys, Keyset,
};
use miniscript::{descriptor::DescriptorXKey, DescriptorPublicKey};

pub fn psbt_with_tweaks(
    psbt: Psbt,
    app_root_xprv: ExtendedPrivKey,
    server_root_xpub: ExtendedPubKey,
    hw_dpub: DescriptorPublicKey,
) -> Result<Psbt, ChaincodeDelegationError> {
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
        app_root_xprv,
    };

    crypto_psbt_with_tweaks(psbt, &crypto_keyset)
}
