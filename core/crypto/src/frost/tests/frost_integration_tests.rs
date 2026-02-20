use std::env;
use std::str::FromStr;

use bdk_bitcoind_rpc::bitcoincore_rpc::{Auth, Client, RpcApi};
use bdk_bitcoind_rpc::{Emitter, NO_EXPECTED_MEMPOOL_TXS};
use bdk_wallet::{KeychainKind, Wallet};
use bitcoin::bip32::DerivationPath;
use bitcoin::{secp256k1, Address, Amount, Network};
use miniscript::descriptor::{DescriptorXKey, Wildcard};
use miniscript::psbt::PsbtExt;
use miniscript::{Descriptor, DescriptorPublicKey};
use secp256k1_zkp::{
    self as zkp,
    frost::{FrostPublicKey, VerificationShare},
};

use crate::frost::Participant::{self, App, Server};
use crate::frost::{compute_frost_master_xpub, ZkpPublicKey};
use crate::frost::{
    dkg::{aggregate_shares, generate_share_packages},
    signing::Signer,
};

#[test]
fn test_dkg_and_receive_funds() {
    let app_share_packages = generate_share_packages().unwrap();
    let server_share_packages = generate_share_packages().unwrap();

    let app_share_packages_to_agg = vec![
        app_share_packages.first().unwrap(),
        server_share_packages.first().unwrap(),
    ];

    let server_share_packages_to_agg = vec![
        server_share_packages.get(1).unwrap(),
        app_share_packages.get(1).unwrap(),
    ];

    let app_share_details = aggregate_shares(App, &app_share_packages_to_agg).unwrap();
    let server_share_details = aggregate_shares(Server, &server_share_packages_to_agg).unwrap();

    // We manually compute the aggregate public key from the verification shares and assert that the one in the share details is correct.
    let app_verification_share = VerificationShare::new(
        zkp::SECP256K1,
        &app_share_details
            .key_commitments
            .aggregate_coefficient_commitment(),
        &Participant::App.into(),
        2,
    )
    .unwrap();
    let server_verification_share = VerificationShare::new(
        zkp::SECP256K1,
        &server_share_details
            .key_commitments
            .aggregate_coefficient_commitment(),
        &Participant::Server.into(),
        2,
    )
    .unwrap();

    let aggregate_pubkey = FrostPublicKey::from_verification_shares(
        zkp::SECP256K1,
        &[&app_verification_share, &server_verification_share],
        &[&Participant::App.into(), &Participant::Server.into()],
    );

    assert_eq!(
        app_share_details.key_commitments.aggregate_public_key,
        ZkpPublicKey(aggregate_pubkey.public_key(zkp::SECP256K1)).into()
    );

    let (mut wallet, rpc_client) =
        get_wallet(app_share_details.key_commitments.aggregate_public_key).unwrap();
    let address = wallet.next_unused_address(KeychainKind::External);
    treasury_fund_address(&address);

    sync_wallet_mempool(&mut wallet, &rpc_client);

    assert_eq!(wallet.balance().untrusted_pending, Amount::from_sat(50_000));
}

#[test]
fn test_sign_psbt() {
    let app_share_packages = generate_share_packages().unwrap();
    let server_share_packages = generate_share_packages().unwrap();

    let app_share_packages_to_agg = vec![
        app_share_packages.first().unwrap(),
        server_share_packages.first().unwrap(),
    ];
    let app_share_details = aggregate_shares(App, &app_share_packages_to_agg).unwrap();
    let server_share_packages_to_agg = vec![
        server_share_packages.get(1).unwrap(),
        app_share_packages.get(1).unwrap(),
    ];
    let server_share_details = aggregate_shares(Server, &server_share_packages_to_agg).unwrap();

    let app_verification_share = VerificationShare::new(
        zkp::SECP256K1,
        &app_share_details
            .key_commitments
            .aggregate_coefficient_commitment(),
        &Participant::App.into(),
        2,
    )
    .unwrap();

    let server_verification_share = VerificationShare::new(
        zkp::SECP256K1,
        &server_share_details
            .key_commitments
            .aggregate_coefficient_commitment(),
        &Participant::Server.into(),
        2,
    )
    .unwrap();

    let aggregate_pubkey = FrostPublicKey::from_verification_shares(
        zkp::SECP256K1,
        &[&app_verification_share, &server_verification_share],
        &[&Participant::App.into(), &Participant::Server.into()],
    );

    let (mut wallet, rpc_client) =
        get_wallet(ZkpPublicKey(aggregate_pubkey.public_key(zkp::SECP256K1)).into()).unwrap();
    let funding_address = wallet.next_unused_address(KeychainKind::External);
    treasury_fund_address(&funding_address);
    sync_wallet_mempool(&mut wallet, &rpc_client);

    let psbt = {
        let recipient_spk = wallet
            .peek_address(KeychainKind::External, 1337)
            .address
            .script_pubkey();
        let mut builder = wallet.build_tx();
        builder.add_recipient(recipient_spk, Amount::from_sat(20_000));
        builder.finish().unwrap()
    };

    let mut app_signer = Signer::new(App, psbt.clone(), app_share_details).unwrap();
    let app_signing_commitments = app_signer.public_signing_commitments();

    let mut server_signer = Signer::new(Server, psbt, server_share_details).unwrap();
    let server_signing_commitments = server_signer.public_signing_commitments();
    let server_partial_signatures = server_signer
        .generate_partial_signatures(app_signing_commitments)
        .unwrap();
    let app_partial_signatures = app_signer
        .generate_partial_signatures(server_signing_commitments.clone())
        .unwrap();

    // App receives the server's public signing nonces and partial signatures
    let mut psbt = app_signer
        .sign_psbt(app_partial_signatures, server_partial_signatures)
        .unwrap();

    psbt.finalize_mut(&secp256k1::Secp256k1::new())
        .map_err(|e| {
            println!("Error finalizing PSBT: {:?}", e);
        })
        .unwrap();
    let signed_tx = psbt.extract_tx().unwrap();
    rpc_client.send_raw_transaction(&signed_tx).unwrap();

    println!("Successfully broadcast {}", signed_tx.compute_txid());
}

fn treasury_fund_address(address: &Address) {
    let wallet_name = env::var("BITCOIND_RPC_WALLET_NAME").unwrap_or("testwallet".to_string());
    let treasury_rpc_client = generate_rpc_client(Some(wallet_name));
    treasury_rpc_client
        .send_to_address(
            address,
            Amount::from_sat(50_000),
            None,
            None,
            None,
            None,
            None,
            None,
        )
        .unwrap();
}

fn get_wallet(aggregate_public_key: secp256k1::PublicKey) -> anyhow::Result<(Wallet, Client)> {
    let external_descriptor = compute_wallet_descriptor(aggregate_public_key, false)?;
    let internal_descriptor = compute_wallet_descriptor(aggregate_public_key, true)?;

    let wallet = Wallet::create(external_descriptor.clone(), internal_descriptor.clone())
        .network(Network::Regtest)
        .create_wallet_no_persist()?;

    Ok((wallet, generate_rpc_client(None)))
}

fn compute_wallet_descriptor(
    aggregate_public_key: secp256k1::PublicKey,
    is_internal: bool,
) -> Result<Descriptor<DescriptorPublicKey>, miniscript::Error> {
    let master_xpub = compute_frost_master_xpub(aggregate_public_key, Network::Regtest);
    let secp = secp256k1::Secp256k1::new();

    let path = if is_internal {
        DerivationPath::from_str("m/86/1/0/1").expect("Derivation path must be valid")
    } else {
        DerivationPath::from_str("m/86/1/0/0").expect("Derivation path must be valid")
    };

    let xpub = master_xpub
        .derive_pub(&secp, &path)
        .expect("Derivation must be valid");

    Descriptor::new_tr(
        DescriptorPublicKey::XPub(DescriptorXKey {
            origin: Some((master_xpub.fingerprint(), path)),
            xkey: xpub,
            derivation_path: DerivationPath::default(),
            wildcard: Wildcard::Unhardened,
        }),
        None,
    )
}

fn sync_wallet_mempool(wallet: &mut Wallet, rpc_client: &Client) {
    let wallet_tip = wallet.latest_checkpoint();
    let mut emitter = Emitter::new(rpc_client, wallet_tip, 0, NO_EXPECTED_MEMPOOL_TXS);
    let mempool_txs = emitter.mempool().unwrap().update;
    wallet.apply_unconfirmed_txs(mempool_txs);
}

fn generate_rpc_client(wallet_name: Option<String>) -> Client {
    let url = if let Some(wallet_name) = wallet_name {
        format!(
            "{}/wallet/{}",
            env::var("REGTEST_BITCOIND_SERVER_URI").unwrap_or("127.0.0.1:18443".to_string()),
            wallet_name
        )
    } else {
        env::var("REGTEST_BITCOIND_SERVER_URI").unwrap_or("127.0.0.1:18443".to_string())
    };

    Client::new(
        &url,
        Auth::UserPass(
            env::var("BITCOIND_RPC_USER").unwrap_or("test".to_string()),
            env::var("BITCOIND_RPC_PASSWORD").unwrap_or("test".to_string()),
        ),
    )
    .expect("Failed to load bitcoind RPC client")
}
