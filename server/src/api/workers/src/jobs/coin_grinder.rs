use std::{env, str::FromStr};

use bdk_utils::bdk::{
    self,
    bitcoin::{bip32::ExtendedPrivKey, Network},
    blockchain::{
        Blockchain, ConfigurableBlockchain, ElectrumBlockchain, ElectrumBlockchainConfig,
    },
    database::MemoryDatabase,
    template::Bip84,
    wallet::AddressIndex,
    FeeRate, SignOptions, SyncOptions, Wallet,
};
use tracing::instrument;

use crate::error::WorkerError;

const MEMPOOL_ELECTRUM_SERVER_ENDPOINT: &str =
    "ssl://bitcoin-signet.aed8yee4taeh6tugeiv6.blockstream.info:50002";
const OUTPUT_AMOUNT: u64 = 11_000;

#[instrument]
#[allow(clippy::print_stdout)]
pub async fn handler() -> Result<(), WorkerError> {
    let xprv = ExtendedPrivKey::from_str(&env::var("E2E_SIGNET_TREASURY_KEY").unwrap()).unwrap();

    let db = MemoryDatabase::default();
    let wallet = Wallet::new(
        Bip84(xprv, bdk::KeychainKind::External),
        Some(Bip84(xprv, bdk::KeychainKind::Internal)),
        Network::Signet,
        db,
    )
    .unwrap();
    let blockchain = ElectrumBlockchain::from_config(&ElectrumBlockchainConfig {
        url: MEMPOOL_ELECTRUM_SERVER_ENDPOINT.to_string(),
        socks5: None,
        retry: 0,
        timeout: None,
        stop_gap: 10,
        validate_domain: true,
    })
    .unwrap();

    // Initial sync to grab all the information about this wallet on-chain (can take a while)
    wallet.sync(&blockchain, SyncOptions::default()).unwrap();
    let balance = wallet.get_balance().unwrap();

    let grinded_address = wallet.get_address(AddressIndex::New).unwrap().address;
    let mut estimation_builder = wallet.build_tx();
    estimation_builder
        .drain_wallet()
        .fee_rate(FeeRate::default_min_relay_fee());
    let total_spendable = balance.get_spendable();
    let outputs_num: u64 = total_spendable / OUTPUT_AMOUNT; // Number of outputs
    for _ in 0..outputs_num {
        estimation_builder.add_recipient(grinded_address.script_pubkey(), OUTPUT_AMOUNT);
    }
    estimation_builder.add_recipient(
        grinded_address.script_pubkey(),
        total_spendable % OUTPUT_AMOUNT,
    );
    let tx_fee = match estimation_builder.finish() {
        Ok((_, details)) => details.fee.unwrap(),
        Err(bdk_utils::bdk::Error::InsufficientFunds { needed, available }) => needed - available,
        Err(e) => panic!("Error building transaction: {:?}", e),
    };
    assert!(tx_fee > 0, "Transaction fee must be greater than 0");

    let mut builder = wallet.build_tx();
    builder.drain_wallet().fee_absolute(tx_fee);
    let total_spendable = balance.get_spendable() - tx_fee;
    let outputs_num: u64 = total_spendable / OUTPUT_AMOUNT; // Number of outputs
    for _ in 0..outputs_num {
        builder.add_recipient(grinded_address.script_pubkey(), OUTPUT_AMOUNT);
    }
    builder.add_recipient(
        grinded_address.script_pubkey(),
        total_spendable % OUTPUT_AMOUNT,
    );
    let (mut grinded_psbt, _) = builder.finish().unwrap();

    wallet
        .sign(&mut grinded_psbt, SignOptions::default())
        .expect("Error signing grinded PSBT");

    let grinded_transaction_to_broadcast = grinded_psbt.extract_tx();
    println!(
        "Broadcasting grinded transaction with txid: {}",
        grinded_transaction_to_broadcast.txid()
    );
    blockchain
        .broadcast(&grinded_transaction_to_broadcast)
        .expect("Error broadcasting consolidation transaction");
    Ok(())
}
