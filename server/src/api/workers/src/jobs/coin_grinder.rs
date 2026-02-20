use std::{env, str::FromStr};

use bdk_utils::{
    bdk::{
        self,
        bitcoin::{bip32::Xpriv, Amount, FeeRate, Network},
        template::Bip84,
        KeychainKind, SignOptions, Wallet,
    },
    get_bdk_electrum_client, ElectrumRpcUris,
};
use tracing::instrument;

use crate::error::WorkerError;

const MEMPOOL_ELECTRUM_SERVER_ENDPOINT: &str =
    "ssl://bitcoin-signet.aed8yee4taeh6tugeiv6.blockstream.info:50002";
const OUTPUT_AMOUNT: u64 = 11_000;

#[instrument]
#[allow(clippy::print_stdout)]
pub async fn handler() -> Result<(), WorkerError> {
    let xprv = Xpriv::from_str(&env::var("E2E_SIGNET_TREASURY_KEY").unwrap()).unwrap();
    let (receive, change) = (
        Bip84(xprv, bdk::KeychainKind::External),
        Bip84(xprv, bdk::KeychainKind::Internal),
    );
    let mut wallet = Wallet::create(receive, change)
        .network(Network::Signet)
        .create_wallet_no_persist()
        .unwrap();
    let electrum_config = ElectrumRpcUris {
        mainnet: "".to_string(),
        testnet: "".to_string(),
        signet: MEMPOOL_ELECTRUM_SERVER_ENDPOINT.to_string(),
    };
    let client = get_bdk_electrum_client(Network::Signet, &electrum_config).unwrap();

    // Initial sync to grab all the information about this wallet on-chain (can take a while)
    let (stop_gap, batch_size) = (100, 10);
    let request = wallet.start_full_scan();
    let update = client
        .full_scan(request, stop_gap, batch_size, false)
        .expect("Error full scanning");
    wallet.apply_update(update).expect("Error applying update");
    let balance = wallet.balance();

    let grinded_address = wallet.next_unused_address(KeychainKind::External).address;
    let mut estimation_builder = wallet.build_tx();
    estimation_builder
        .drain_wallet()
        .fee_rate(FeeRate::BROADCAST_MIN);
    let total_spendable = balance.trusted_spendable().to_sat();
    let outputs_num: u64 = total_spendable / OUTPUT_AMOUNT; // Number of outputs
    for _ in 0..outputs_num {
        estimation_builder.add_recipient(
            grinded_address.script_pubkey(),
            Amount::from_sat(OUTPUT_AMOUNT),
        );
    }
    estimation_builder.add_recipient(
        grinded_address.script_pubkey(),
        Amount::from_sat(total_spendable % OUTPUT_AMOUNT),
    );
    let tx_fee = match estimation_builder.finish() {
        Ok(psbt) => psbt.fee().unwrap(),
        Err(e) => panic!("Error building transaction: {:?}", e),
    };
    assert!(
        tx_fee > Amount::ZERO,
        "Transaction fee must be greater than 0"
    );

    let mut builder = wallet.build_tx();
    builder.drain_wallet().fee_absolute(tx_fee);
    let total_spendable = balance.trusted_spendable() - tx_fee;
    let outputs_num: u64 = total_spendable.to_sat() / OUTPUT_AMOUNT; // Number of outputs
    for _ in 0..outputs_num {
        builder.add_recipient(
            grinded_address.script_pubkey(),
            Amount::from_sat(OUTPUT_AMOUNT),
        );
    }
    builder.add_recipient(
        grinded_address.script_pubkey(),
        total_spendable % OUTPUT_AMOUNT,
    );
    let mut grinded_psbt = builder.finish().unwrap();

    wallet
        .sign(&mut grinded_psbt, SignOptions::default())
        .expect("Error signing grinded PSBT");

    let grinded_transaction_to_broadcast = grinded_psbt
        .extract_tx()
        .expect("Error extracting transaction");
    println!(
        "Broadcasting grinded transaction with txid: {}",
        grinded_transaction_to_broadcast.compute_txid()
    );
    client
        .transaction_broadcast(&grinded_transaction_to_broadcast)
        .expect("Error broadcasting consolidation transaction");
    Ok(())
}
