use crate::bitcoin::{Amount, Network, Script};
use crate::descriptor::Descriptor;
use crate::esplora::EsploraClient;
use crate::store::Persister;
use crate::tx_builder::TxBuilder;
use crate::types::FullScanScriptInspector;
use crate::wallet::Wallet;

use std::sync::Arc;

struct FullScanInspector;

impl FullScanScriptInspector for FullScanInspector {
    fn inspect(&self, _: bdk_wallet::KeychainKind, _: u32, _: Arc<Script>) {}
}

#[test]
fn test_policy_path() {
    let wallet = create_and_sync_wallet();
    let address = wallet
        .next_unused_address(bdk_wallet::KeychainKind::External)
        .address;
    println!("Wallet address: {:?}", address);

    let ext_policy = wallet.policies(bdk_wallet::KeychainKind::External);
    let int_policy = wallet.policies(bdk_wallet::KeychainKind::Internal);

    if let (Ok(Some(ext_policy)), Ok(Some(int_policy))) = (ext_policy, int_policy) {
        let ext_path = vec![(ext_policy.id().clone(), vec![0, 1])]
            .into_iter()
            .collect();
        println!("External Policy path : {:?}\n", ext_path);
        let int_path = vec![(int_policy.id().clone(), vec![0, 1])]
            .into_iter()
            .collect();
        println!("Internal Policy Path: {:?}\n", int_path);

        match TxBuilder::new()
            .add_recipient(
                &(*address.script_pubkey()).to_owned(),
                Arc::new(Amount::from_sat(1000)),
            )
            .do_not_spend_change()
            .policy_path(int_path, bdk_wallet::KeychainKind::Internal)
            .policy_path(ext_path, bdk_wallet::KeychainKind::External)
            .finish(&Arc::new(wallet))
        {
            Ok(tx) => println!("Transaction serialized: {}\n", tx.serialize()),
            Err(e) => eprintln!("Error: {:?}", e),
        }
    } else {
        println!("Failed to retrieve valid policies for keychains.");
    }
}

fn create_and_sync_wallet() -> Wallet {
    let external_descriptor = format!(
        "wsh(thresh(2,pk({}/0/*),sj:and_v(v:pk({}/0/*),n:older(6)),snj:and_v(v:pk({}/0/*),after(630000))))",
        "tpubD6NzVbkrYhZ4XJBfEJ6gt9DiVdfWJijsQTCE3jtXByW3Tk6AVGQ3vL1NNxg3SjB7QkJAuutACCQjrXD8zdZSM1ZmBENszCqy49ECEHmD6rf",
        "tpubD6NzVbkrYhZ4YfAr3jCBRk4SpqB9L1Hh442y83njwfMaker7EqZd7fHMqyTWrfRYJ1e5t2ue6BYjW5i5yQnmwqbzY1a3kfqNxog1AFcD1aE",
        "tprv8ZgxMBicQKsPeitVUz3s6cfyCECovNP7t82FaKPa4UKqV1kssWcXgLkMDjzDbgG9GWoza4pL7z727QitfzkiwX99E1Has3T3a1MKHvYWmQZ"
    );
    let internal_descriptor = format!(
        "wsh(thresh(2,pk({}/1/*),sj:and_v(v:pk({}/1/*),n:older(6)),snj:and_v(v:pk({}/1/*),after(630000))))",
        "tpubD6NzVbkrYhZ4XJBfEJ6gt9DiVdfWJijsQTCE3jtXByW3Tk6AVGQ3vL1NNxg3SjB7QkJAuutACCQjrXD8zdZSM1ZmBENszCqy49ECEHmD6rf",
        "tpubD6NzVbkrYhZ4YfAr3jCBRk4SpqB9L1Hh442y83njwfMaker7EqZd7fHMqyTWrfRYJ1e5t2ue6BYjW5i5yQnmwqbzY1a3kfqNxog1AFcD1aE",
        "tprv8ZgxMBicQKsPeitVUz3s6cfyCECovNP7t82FaKPa4UKqV1kssWcXgLkMDjzDbgG9GWoza4pL7z727QitfzkiwX99E1Has3T3a1MKHvYWmQZ"
    );
    let wallet = Wallet::new(
        Arc::new(Descriptor::new(external_descriptor, Network::Signet).unwrap()),
        Arc::new(Descriptor::new(internal_descriptor, Network::Signet).unwrap()),
        Network::Signet,
        Arc::new(Persister::new_in_memory().unwrap()),
        25,
    )
    .unwrap();
    let client = EsploraClient::new("https://mutinynet.com/api/".to_string(), None);
    let full_scan_builder = wallet.start_full_scan();
    let full_scan_request = full_scan_builder
        .inspect_spks_for_all_keychains(Arc::new(FullScanInspector))
        .unwrap()
        .build()
        .unwrap();
    let update = client.full_scan(full_scan_request, 10, 10).unwrap();
    wallet.apply_update(update).unwrap();
    println!("Wallet balance: {:?}", wallet.balance().total.to_sat());
    wallet
}
