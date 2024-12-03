use std::env;
use std::str::FromStr;

use bdk::bitcoincore_rpc::RpcApi;
use bdk::blockchain::rpc::Auth;
use bdk::blockchain::{Blockchain, ConfigurableBlockchain, RpcBlockchain, RpcConfig};
use bdk::database::MemoryDatabase;
use bdk::wallet::{wallet_name_from_descriptor, AddressIndex};
use bdk::Wallet;
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

    let app_share_details = aggregate_shares(App, &app_share_packages_to_agg).unwrap();

    let (wallet, rpc_config) =
        get_wallet(app_share_details.key_commitments.aggregate_public_key).unwrap();
    let address = wallet.get_address(bdk::wallet::AddressIndex::New).unwrap();
    treasury_fund_address(&address);

    let blockchain = RpcBlockchain::from_config(&rpc_config).unwrap();
    wallet.sync(&blockchain, Default::default()).unwrap();

    assert_eq!(wallet.get_balance().unwrap().untrusted_pending, 50_000);
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

    let (wallet, rpc_config) =
        get_wallet(ZkpPublicKey(aggregate_pubkey.public_key(zkp::SECP256K1)).into()).unwrap();
    treasury_fund_address(&wallet.get_address(bdk::wallet::AddressIndex::New).unwrap());

    let blockchain = RpcBlockchain::from_config(&rpc_config).unwrap();
    wallet.sync(&blockchain, Default::default()).unwrap();

    let (psbt, _) = {
        let mut builder = wallet.build_tx();
        builder.add_recipient(
            wallet
                .get_address(AddressIndex::Peek(1337))
                .unwrap()
                .script_pubkey(),
            20_000,
        );
        match builder.finish() {
            Ok(res) => res,
            Err(bdk::Error::InsufficientFunds { .. }) => panic!(
                "Insufficient funds, send more to {}",
                wallet.get_address(AddressIndex::New).unwrap()
            ),
            Err(e) => panic!("{}", e),
        }
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
    let signed_tx = psbt.extract_tx();
    blockchain.broadcast(&signed_tx).unwrap();

    println!("Successfully broadcast {}", signed_tx.txid());
}

fn treasury_fund_address(address: &Address) {
    let treasury_rpc_config = RpcConfig {
        url: env::var("REGTEST_ELECTRUM_SERVER_URI").unwrap_or("127.0.0.1:18443".to_string()),
        auth: Auth::UserPass {
            username: env::var("BITCOIND_RPC_USER").unwrap_or("test".to_string()),
            password: env::var("BITCOIND_RPC_PASSWORD").unwrap_or("test".to_string()),
        },
        network: Network::Regtest,
        wallet_name: env::var("BITCOIND_RPC_WALLET_NAME").unwrap_or("testwallet".to_string()),
        sync_params: None,
    };
    let treasury_blockchain = RpcBlockchain::from_config(&treasury_rpc_config).unwrap();
    treasury_blockchain
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

fn get_wallet(
    aggregate_public_key: bitcoin::secp256k1::PublicKey,
) -> anyhow::Result<(Wallet<MemoryDatabase>, RpcConfig)> {
    let external_descriptor = compute_wallet_descriptor(aggregate_public_key, false)?;
    let internal_descriptor = compute_wallet_descriptor(aggregate_public_key, true)?;

    let wallet = Wallet::new(
        external_descriptor.clone(),
        Some(internal_descriptor.clone()),
        bitcoin::Network::Regtest,
        MemoryDatabase::default(),
    )?;

    let rpc_config = RpcConfig {
        url: env::var("REGTEST_ELECTRUM_SERVER_URI")
            .unwrap_or("127.0.0.1:18443".to_string())
            .to_string(),
        auth: Auth::UserPass {
            username: env::var("BITCOIND_RPC_USER").unwrap_or("test".to_string()),
            password: env::var("BITCOIND_RPC_PASSWORD").unwrap_or("test".to_string()),
        },
        network: Network::Regtest,
        wallet_name: wallet_name_from_descriptor(
            external_descriptor,
            Some(internal_descriptor),
            bitcoin::Network::Regtest,
            &secp256k1::Secp256k1::new(),
        )?,
        sync_params: None,
    };

    Ok((wallet, rpc_config))
}

fn compute_wallet_descriptor(
    aggregate_public_key: bitcoin::secp256k1::PublicKey,
    is_internal: bool,
) -> Result<Descriptor<DescriptorPublicKey>, miniscript::Error> {
    let master_xpub = compute_frost_master_xpub(aggregate_public_key, bitcoin::Network::Regtest);
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
