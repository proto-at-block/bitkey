use std::str::FromStr;

use bdk_utils::bdk::bitcoin::bip32::ExtendedPubKey;
use bdk_utils::bdk::bitcoin::key::Secp256k1;
use bdk_utils::bdk::bitcoin::{Address, OutPoint};
use bdk_utils::bdk::{
    bitcoin::psbt::PartiallySignedTransaction, database::AnyDatabase, keys::DescriptorPublicKey,
    miniscript::descriptor::DescriptorXKey, wallet::AddressInfo, FeeRate, SignOptions, Wallet,
};
use crypto::chaincode_delegation::{
    HwAccountLevelDescriptorPublicKeys, Keyset as CcdKeyset, UntweakedPsbt, XpubWithOrigin,
};
use mobile_pay::routes::SignTransactionResponse;
use types::account::{entities::FullAccount, identifiers::KeysetId, spending::SpendingKeyset};

use crate::tests::requests::axum::TestClient;
use crate::tests::TestContext;
use crate::Services;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum WalletTestProtocol {
    Legacy,
    PrivateCcd,
}

pub struct WalletFixture {
    pub protocol: WalletTestProtocol,
    pub account: FullAccount,
    pub wallet: Wallet<AnyDatabase>,
    pub signing_keyset_id: KeysetId,
}

#[derive(Clone, Debug)]
pub enum SweepDestination {
    External(Address),
    Internal {
        address: Address,
        target_keyset: CcdKeyset,
    },
}

impl SweepDestination {
    fn address(&self) -> Address {
        match self {
            SweepDestination::External(address) => address.clone(),
            SweepDestination::Internal { address, .. } => address.clone(),
        }
    }
}

pub async fn setup_fixture(
    context: &mut TestContext,
    client: &TestClient,
    services: &Services,
    protocol: WalletTestProtocol,
) -> WalletFixture {
    match protocol {
        WalletTestProtocol::Legacy => {
            let (account, wallet) =
                super::create_default_account_with_predefined_wallet(context, client, services)
                    .await;
            WalletFixture {
                protocol,
                signing_keyset_id: account.active_keyset_id.clone(),
                account,
                wallet,
            }
        }
        WalletTestProtocol::PrivateCcd => {
            let (account, wallet) =
                super::create_default_account_with_private_wallet(context, client, services).await;
            WalletFixture {
                protocol,
                signing_keyset_id: account.active_keyset_id.clone(),
                account,
                wallet,
            }
        }
    }
}

pub fn build_app_signed_psbt_for_protocol(
    fixture: &WalletFixture,
    recipient: AddressInfo,
    amount_sats: u64,
    uxtos: &[OutPoint],
) -> PartiallySignedTransaction {
    match fixture.protocol {
        WalletTestProtocol::Legacy => {
            build_transaction_with_amount(&fixture.wallet, recipient, amount_sats, uxtos)
        }
        WalletTestProtocol::PrivateCcd => {
            let (psbt, _details) = {
                let mut builder = fixture.wallet.build_tx();
                builder
                    .add_recipient(recipient.script_pubkey(), amount_sats)
                    .enable_rbf()
                    .fee_rate(FeeRate::from_sat_per_vb(5.0));
                if !uxtos.is_empty() {
                    builder.manually_selected_only().add_utxos(uxtos).unwrap();
                }
                builder.finish().expect("Failed to build transaction")
            };

            // Prepare CCD keyset components from predefined test keys and the account keyset
            let predefined = super::predefined_descriptor_public_keys();
            let hw_descriptor_public_keys = match &predefined.hardware_account_dpub {
                DescriptorPublicKey::XPub(DescriptorXKey {
                    origin: Some((fp, _path)),
                    xkey,
                    ..
                }) => HwAccountLevelDescriptorPublicKeys::new(*fp, *xkey),
                _ => panic!("Unsupported hardware descriptor public key"),
            };

            let spending_keyset = fixture
                .account
                .spending_keysets
                .get(&fixture.signing_keyset_id)
                .expect("Active keyset not found");
            let private_keyset = match spending_keyset {
                SpendingKeyset::PrivateMultiSig(k) => k,
                _ => panic!("Expected PrivateMultiSig keyset for PrivateCcd protocol"),
            };

            let app_account_xpub = match predefined.app_account_descriptor_keypair().1 {
                DescriptorPublicKey::XPub(DescriptorXKey { xkey, .. }) => xkey,
                _ => panic!("Expected XPub descriptor"),
            };

            // At this part of the fixture construction, we use code from the `core` create to
            // populate the PSBT with tweaks. This keeps us aligned with the code we expect the App
            // to run.
            let mut psbt = UntweakedPsbt::new(psbt)
                .with_source_wallet_tweaks(&CcdKeyset {
                    hw_descriptor_public_keys,
                    server_root_xpub: super::predefined_server_root_xpub(
                        private_keyset.network,
                        private_keyset.server_pub,
                    ),
                    app_account_xpub_with_origin: XpubWithOrigin {
                        fingerprint: predefined.app_root_xprv.fingerprint(&Secp256k1::new()),
                        xpub: app_account_xpub,
                    },
                })
                .expect("Failed to apply tweaks")
                .into_psbt();
            fixture
                .wallet
                .sign(
                    &mut psbt,
                    SignOptions {
                        remove_partial_sigs: false,
                        ..SignOptions::default()
                    },
                )
                .expect("Failed to sign PSBT with app key");
            psbt
        }
    }
}

pub fn sweep_destination_for_ccd(
    app_dpub: DescriptorPublicKey,
    hw_dpub: DescriptorPublicKey,
    server_root_xpub: ExtendedPubKey,
    sweep_address: Address,
) -> SweepDestination {
    let app_fingerprint = app_dpub.master_fingerprint();
    let hw_fingerprint = hw_dpub.master_fingerprint();

    let new_app_account_xpub = match app_dpub {
        DescriptorPublicKey::XPub(xkey) => xkey.xkey,
        _ => panic!("Expected XPub descriptor"),
    };
    let new_hw_account_xpub = match hw_dpub {
        DescriptorPublicKey::XPub(xkey) => xkey.xkey,
        _ => panic!("Expected XPub descriptor"),
    };

    let target_keyset = CcdKeyset {
        hw_descriptor_public_keys: HwAccountLevelDescriptorPublicKeys::new(
            hw_fingerprint,
            new_hw_account_xpub,
        ),
        server_root_xpub,
        app_account_xpub_with_origin: XpubWithOrigin {
            fingerprint: app_fingerprint,
            xpub: new_app_account_xpub,
        },
    };

    SweepDestination::Internal {
        address: sweep_address,
        target_keyset,
    }
}

pub fn build_sweep_psbt_for_protocol(
    source_fixture: &WalletFixture,
    destination: SweepDestination,
) -> PartiallySignedTransaction {
    let destination_address = destination.address();
    let (psbt, _details) = {
        let mut builder = source_fixture.wallet.build_tx();
        builder
            .drain_wallet()
            .drain_to(destination_address.script_pubkey())
            .enable_rbf()
            .fee_rate(FeeRate::from_sat_per_vb(5.0));
        builder.finish().expect("Failed to build sweep transaction")
    };

    match source_fixture.protocol {
        WalletTestProtocol::Legacy => match destination {
            SweepDestination::External(_) => {
                let mut psbt = psbt;
                source_fixture
                    .wallet
                    .sign(
                        &mut psbt,
                        SignOptions {
                            remove_partial_sigs: false,
                            ..SignOptions::default()
                        },
                    )
                    .expect("Failed to sign sweep PSBT with app key");
                psbt
            }
            SweepDestination::Internal { target_keyset, .. } => {
                let mut psbt = UntweakedPsbt::new(psbt)
                    .with_migration_sweep_prepared_tweaks(&target_keyset)
                    .expect("Failed to apply sweep tweaks")
                    .into_psbt();
                source_fixture
                    .wallet
                    .sign(
                        &mut psbt,
                        SignOptions {
                            remove_partial_sigs: false,
                            ..SignOptions::default()
                        },
                    )
                    .expect("Failed to sign sweep PSBT with app key");
                psbt
            }
        },
        WalletTestProtocol::PrivateCcd => {
            let predefined = super::predefined_descriptor_public_keys();
            let hw_descriptor_public_keys = match &predefined.hardware_account_dpub {
                DescriptorPublicKey::XPub(DescriptorXKey {
                    origin: Some((fp, _path)),
                    xkey,
                    ..
                }) => HwAccountLevelDescriptorPublicKeys::new(*fp, *xkey),
                _ => panic!("Unsupported hardware descriptor public key"),
            };

            let spending_keyset = source_fixture
                .account
                .spending_keysets
                .get(&source_fixture.signing_keyset_id)
                .expect("Signing keyset not found on source account");
            let private_keyset = match spending_keyset {
                SpendingKeyset::PrivateMultiSig(k) => k,
                _ => panic!("Expected PrivateMultiSig keyset for PrivateCcd protocol"),
            };

            let app_account_xpub = match predefined.app_account_descriptor_keypair().1 {
                DescriptorPublicKey::XPub(DescriptorXKey { xkey, .. }) => xkey,
                _ => panic!("Expected XPub descriptor"),
            };

            let ccd_origin_keyset = CcdKeyset {
                hw_descriptor_public_keys,
                server_root_xpub: super::predefined_server_root_xpub(
                    private_keyset.network,
                    private_keyset.server_pub,
                ),
                app_account_xpub_with_origin: XpubWithOrigin {
                    fingerprint: predefined.app_root_xprv.fingerprint(&Secp256k1::new()),
                    xpub: app_account_xpub,
                },
            };

            let mut psbt = match &destination {
                SweepDestination::Internal { target_keyset, .. } => UntweakedPsbt::new(psbt)
                    .with_source_wallet_tweaks(&ccd_origin_keyset)
                    .and_then(|p| p.with_sweep_prepared_tweaks(target_keyset))
                    .expect("Failed to apply sweep tweaks")
                    .into_psbt(),
                SweepDestination::External(_) => UntweakedPsbt::new(psbt)
                    .with_source_wallet_tweaks(&ccd_origin_keyset)
                    .expect("Failed to apply tweaks")
                    .into_psbt(),
            };

            source_fixture
                .wallet
                .sign(
                    &mut psbt,
                    SignOptions {
                        remove_partial_sigs: false,
                        ..SignOptions::default()
                    },
                )
                .expect("Failed to sign sweep PSBT with app key");
            psbt
        }
    }
}

pub fn check_finalized_psbt(
    response_body: Option<SignTransactionResponse>,
    wallet: &Wallet<AnyDatabase>,
) {
    let mut server_and_app_signed_psbt = match response_body {
        Some(r) => PartiallySignedTransaction::from_str(&r.tx).unwrap(),
        None => return,
    };

    let sign_options = SignOptions {
        remove_partial_sigs: false,
        ..SignOptions::default()
    };

    let is_finalized = wallet
        .finalize_psbt(&mut server_and_app_signed_psbt, sign_options)
        .expect("Failed to finalize PSBT");
    assert!(is_finalized);
}

fn build_transaction_with_amount(
    wallet: &Wallet<AnyDatabase>,
    recipient: AddressInfo,
    amt: u64,
    uxtos: &[OutPoint],
) -> PartiallySignedTransaction {
    let mut builder = wallet.build_tx();
    builder
        .add_recipient(recipient.script_pubkey(), amt)
        .fee_rate(FeeRate::from_sat_per_vb(5.0));
    if !uxtos.is_empty() {
        builder.manually_selected_only().add_utxos(uxtos).unwrap();
    }
    let (mut tx, _) = builder.finish().unwrap();
    let _ = wallet.sign(
        &mut tx,
        SignOptions {
            remove_partial_sigs: false,
            ..SignOptions::default()
        },
    );
    tx
}

fn build_sweep_transaction(
    wallet: &Wallet<AnyDatabase>,
    recipient: Address,
) -> PartiallySignedTransaction {
    let mut builder = wallet.build_tx();
    builder
        .drain_wallet()
        .drain_to(recipient.script_pubkey())
        .fee_rate(FeeRate::from_sat_per_vb(5.0));
    let (mut tx, _) = builder.finish().unwrap();
    let _ = wallet.sign(
        &mut tx,
        SignOptions {
            remove_partial_sigs: false,
            ..SignOptions::default()
        },
    );
    tx
}
