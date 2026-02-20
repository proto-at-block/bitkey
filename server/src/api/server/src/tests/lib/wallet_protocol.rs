use std::str::FromStr;

use bdk_utils::bdk::bitcoin::bip32::Xpub as ExtendedPubKey;
use bdk_utils::bdk::bitcoin::key::Secp256k1;
use bdk_utils::bdk::bitcoin::{Address, Amount, OutPoint};
use bdk_utils::bdk::{
    bitcoin::psbt::Psbt as PartiallySignedTransaction, bitcoin::FeeRate, keys::DescriptorPublicKey,
    miniscript::descriptor::DescriptorXKey, AddressInfo, SignOptions, Wallet,
};
use crypto::chaincode_delegation::{
    HwAccountLevelDescriptorPublicKeys, Keyset as CcdKeyset, UntweakedPsbt, XpubWithOrigin,
};
use mobile_pay::routes::SignTransactionResponse;
use types::account::{entities::FullAccount, identifiers::KeysetId, spending::SpendingKeyset};
use wsm_compat::{
    fingerprint_0_32_to_0_30, psbt_0_30_to_0_32, psbt_0_32_to_0_30, xpub_0_32_to_0_30,
};

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
    pub wallet: Wallet,
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
    fixture: &mut WalletFixture,
    recipient: AddressInfo,
    amount_sats: u64,
    uxtos: &[OutPoint],
) -> PartiallySignedTransaction {
    match fixture.protocol {
        WalletTestProtocol::Legacy => {
            build_transaction_with_amount(&mut fixture.wallet, recipient, amount_sats, uxtos)
        }
        WalletTestProtocol::PrivateCcd => {
            let psbt = {
                let mut builder = fixture.wallet.build_tx();
                builder
                    .add_recipient(recipient.script_pubkey(), Amount::from_sat(amount_sats))
                    .fee_rate(FeeRate::from_sat_per_vb(5).expect("Invalid feerate"));
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
                }) => HwAccountLevelDescriptorPublicKeys::new(
                    fingerprint_0_32_to_0_30(*fp).expect("Unable to convert fingerprint to 0.30"),
                    xpub_0_32_to_0_30(*xkey).expect("Unable to convert XPub to 0.30"),
                ),
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
            let psbt = UntweakedPsbt::new(
                psbt_0_32_to_0_30(&psbt).expect("Unable to convert PSBT to 0.30"),
            )
            .with_source_wallet_tweaks(&CcdKeyset {
                hw_descriptor_public_keys,
                server_root_xpub: xpub_0_32_to_0_30(super::predefined_server_root_xpub(
                    private_keyset.network,
                    private_keyset.server_pub,
                ))
                .expect("Unable to convert XPub to 0.30"),
                app_account_xpub_with_origin: XpubWithOrigin {
                    fingerprint: fingerprint_0_32_to_0_30(
                        predefined.app_root_xprv.fingerprint(&Secp256k1::new()),
                    )
                    .expect("Unable to convert fingerprint to 0.30"),
                    xpub: xpub_0_32_to_0_30(app_account_xpub)
                        .expect("Unable to convert XPub to 0.30"),
                },
            })
            .expect("Failed to apply tweaks")
            .into_psbt();
            let mut psbt = psbt_0_30_to_0_32(&psbt).expect("Unable to convert PSBT to 0.32");
            fixture
                .wallet
                .sign(&mut psbt, SignOptions::default())
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
            fingerprint_0_32_to_0_30(hw_fingerprint)
                .expect("Unable to convert fingerprint to 0.30"),
            xpub_0_32_to_0_30(new_hw_account_xpub).expect("Unable to convert XPub to 0.30"),
        ),
        server_root_xpub: xpub_0_32_to_0_30(server_root_xpub)
            .expect("Unable to convert XPub to 0.30"),
        app_account_xpub_with_origin: XpubWithOrigin {
            fingerprint: fingerprint_0_32_to_0_30(app_fingerprint)
                .expect("Unable to convert fingerprint to 0.30"),
            xpub: xpub_0_32_to_0_30(new_app_account_xpub).expect("Unable to convert XPub to 0.30"),
        },
    };

    SweepDestination::Internal {
        address: sweep_address,
        target_keyset,
    }
}

pub fn build_sweep_psbt_for_protocol(
    source_fixture: &mut WalletFixture,
    destination: SweepDestination,
) -> PartiallySignedTransaction {
    let destination_address = destination.address();
    let psbt = {
        let mut builder = source_fixture.wallet.build_tx();
        builder
            .drain_wallet()
            .drain_to(destination_address.script_pubkey())
            .fee_rate(FeeRate::from_sat_per_vb(5).expect("Invalid feerate"));
        builder.finish().expect("Failed to build sweep transaction")
    };

    match source_fixture.protocol {
        WalletTestProtocol::Legacy => match destination {
            SweepDestination::External(_) => {
                let mut psbt = psbt;
                source_fixture
                    .wallet
                    .sign(&mut psbt, SignOptions::default())
                    .expect("Failed to sign sweep PSBT with app key");
                psbt
            }
            SweepDestination::Internal { target_keyset, .. } => {
                let psbt = UntweakedPsbt::new(
                    psbt_0_32_to_0_30(&psbt).expect("Unable to convert PSBT to 0.30"),
                )
                .with_migration_sweep_prepared_tweaks(&target_keyset)
                .expect("Failed to apply sweep tweaks")
                .into_psbt();

                let mut psbt = psbt_0_30_to_0_32(&psbt).expect("Unable to convert PSBT to 0.32");
                source_fixture
                    .wallet
                    .sign(&mut psbt, SignOptions::default())
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
                }) => HwAccountLevelDescriptorPublicKeys::new(
                    fingerprint_0_32_to_0_30(*fp).expect("Unable to convert fingerprint to 0.30"),
                    xpub_0_32_to_0_30(*xkey).expect("Unable to convert XPub to 0.30"),
                ),
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
                server_root_xpub: xpub_0_32_to_0_30(super::predefined_server_root_xpub(
                    private_keyset.network,
                    private_keyset.server_pub,
                ))
                .expect("Unable to convert XPub to 0.30"),
                app_account_xpub_with_origin: XpubWithOrigin {
                    fingerprint: fingerprint_0_32_to_0_30(
                        predefined.app_root_xprv.fingerprint(&Secp256k1::new()),
                    )
                    .expect("Unable to convert fingerprint to 0.30"),
                    xpub: xpub_0_32_to_0_30(app_account_xpub)
                        .expect("Unable to convert XPub to 0.30"),
                },
            };

            let psbt = match &destination {
                SweepDestination::Internal { target_keyset, .. } => UntweakedPsbt::new(
                    psbt_0_32_to_0_30(&psbt).expect("Unable to convert PSBT to 0.30"),
                )
                .with_source_wallet_tweaks(&ccd_origin_keyset)
                .and_then(|p| p.with_sweep_prepared_tweaks(target_keyset))
                .expect("Failed to apply sweep tweaks")
                .into_psbt(),
                SweepDestination::External(_) => UntweakedPsbt::new(
                    psbt_0_32_to_0_30(&psbt).expect("Unable to convert PSBT to 0.30"),
                )
                .with_source_wallet_tweaks(&ccd_origin_keyset)
                .expect("Failed to apply tweaks")
                .into_psbt(),
            };

            let mut psbt = psbt_0_30_to_0_32(&psbt).expect("Unable to convert PSBT to 0.32");
            source_fixture
                .wallet
                .sign(&mut psbt, SignOptions::default())
                .expect("Failed to sign sweep PSBT with app key");
            psbt
        }
    }
}

pub fn check_finalized_psbt(response_body: Option<SignTransactionResponse>, wallet: &Wallet) {
    let mut server_and_app_signed_psbt = match response_body {
        Some(r) => PartiallySignedTransaction::from_str(&r.tx).unwrap(),
        None => return,
    };

    let is_finalized = wallet
        .finalize_psbt(&mut server_and_app_signed_psbt, SignOptions::default())
        .expect("Failed to finalize PSBT");
    assert!(is_finalized);
}

fn build_transaction_with_amount(
    wallet: &mut Wallet,
    recipient: AddressInfo,
    amt: u64,
    uxtos: &[OutPoint],
) -> PartiallySignedTransaction {
    let mut builder = wallet.build_tx();
    builder
        .add_recipient(recipient.script_pubkey(), Amount::from_sat(amt))
        .fee_rate(FeeRate::from_sat_per_vb(5).expect("Invalid feerate"));
    if !uxtos.is_empty() {
        builder.manually_selected_only().add_utxos(uxtos).unwrap();
    }
    let mut psbt = builder.finish().unwrap();
    let _ = wallet.sign(&mut psbt, SignOptions::default());
    psbt
}

fn build_sweep_transaction(wallet: &mut Wallet, recipient: Address) -> PartiallySignedTransaction {
    let mut builder = wallet.build_tx();
    builder
        .drain_wallet()
        .drain_to(recipient.script_pubkey())
        .fee_rate(FeeRate::from_sat_per_vb(5).expect("Invalid feerate"));
    let mut psbt = builder.finish().unwrap();
    let _ = wallet.sign(&mut psbt, SignOptions::default());
    psbt
}
