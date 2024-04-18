use std::str::FromStr;

use anyhow::Result;
use aws_sdk_dynamodb::types::AttributeValue;
use bdk::bitcoin::bip32::ChildNumber;
use bdk::bitcoin::Network;
use bdk::blockchain::{ConfigurableBlockchain, ElectrumBlockchain, ElectrumBlockchainConfig};
use bdk::database::MemoryDatabase;
use bdk::electrum_client::ElectrumApi;
use bdk::miniscript::descriptor::{DescriptorPublicKey, DescriptorXKey};
use bdk::miniscript::Descriptor;
use bdk::wallet::AddressIndex;
use bdk::{SyncOptions, Wallet};

const RECEIVING_PATH: [ChildNumber; 1] = [ChildNumber::Normal { index: 0 }];
const CHANGE_PATH: [ChildNumber; 1] = [ChildNumber::Normal { index: 1 }];

pub fn debug(
    electrum_server_url: String,
    account_table: String,
    recovery_table: String,
    social_recovery_table: String,
    gap_limit: u32,
    address_balance: bool,
    account_id: String,
) -> Result<()> {
    let rt = tokio::runtime::Runtime::new()?;

    let item = rt.block_on(async {
        let ddb_client = aws_sdk_dynamodb::Client::new(&aws_config::load_from_env().await);
        ddb_client
            .get_item()
            .table_name(account_table)
            .key("partition_key", AttributeValue::S(account_id.clone()))
            .send()
            .await
    })?;

    let active_keyset = item
        .item()
        .unwrap()
        .get("active_keyset_id")
        .unwrap()
        .as_s()
        .unwrap();
    println!("******************************************************");
    println!("Active keyset: {}", active_keyset);
    println!("******************************************************");

    let created_at = item
        .item()
        .unwrap()
        .get("created_at")
        .unwrap()
        .as_s()
        .unwrap();
    println!("Created At: {created_at}");
    println!("******************************************************");

    let auth_keys = item
        .item()
        .unwrap()
        .get("auth_keys")
        .unwrap()
        .as_m()
        .unwrap();
    println!("auth_keyset: {:?}", auth_keys);

    let keysets = item
        .item()
        .unwrap()
        .get("spending_keysets")
        .unwrap()
        .as_m()
        .unwrap();
    println!("keysets in ddb: {:?}", keysets);
    println!("******************************************************");
    println!("Electrum Server: {}", electrum_server_url);
    for (keyset_id, keyset) in keysets {
        println!("Looking at keyset {keyset_id}");
        let spend_keyset = keyset.as_m().unwrap();
        println!("spend_keyset: {:?}", spend_keyset);

        let app = spend_keyset.get("app_dpub").unwrap().as_s().unwrap();
        let hardware = spend_keyset.get("hardware_dpub").unwrap().as_s().unwrap();
        let server = spend_keyset.get("server_dpub").unwrap().as_s().unwrap();
        let network = match spend_keyset
            .get("network")
            .unwrap()
            .as_s()
            .unwrap()
            .as_str()
        {
            "bitcoin-main" => Network::Bitcoin,
            "bitcoin-signet" => Network::Signet,
            "bitcoin-testnet" => Network::Testnet,
            _ => Network::Bitcoin,
        };

        let descriptor_strs = [app, hardware, server];
        let dpks = descriptor_strs
            .iter()
            .map(|s| DescriptorPublicKey::from_str(s).unwrap())
            .collect::<Vec<_>>();

        let descriptors = [RECEIVING_PATH, CHANGE_PATH]
            .iter()
            .map(|path| {
                dpks.iter()
                    .map(|dpk| match dpk {
                        DescriptorPublicKey::Single(_) => unimplemented!(),
                        DescriptorPublicKey::MultiXPub(_) => unimplemented!(),
                        DescriptorPublicKey::XPub(xpub) => {
                            DescriptorPublicKey::XPub(DescriptorXKey {
                                derivation_path: xpub.derivation_path.extend(path),
                                origin: xpub.origin.clone(),
                                ..*xpub
                            })
                        }
                    })
                    .collect::<Vec<_>>()
            })
            .collect::<Vec<_>>();

        let receive_desc =
            Descriptor::new_wsh_sortedmulti(2, descriptors.first().unwrap().clone()).unwrap();
        let change_desc =
            Descriptor::new_wsh_sortedmulti(2, descriptors.get(1).unwrap().clone()).unwrap();

        let wallet = Wallet::new(
            receive_desc,
            Some(change_desc),
            network,
            MemoryDatabase::new(),
        )
        .unwrap();
        let blockchain = ElectrumBlockchain::from_config(&ElectrumBlockchainConfig {
            url: electrum_server_url.to_string(),
            socks5: None,
            retry: 0,
            timeout: None,
            stop_gap: gap_limit as usize,
            validate_domain: true,
        })
        .expect("Could not build electrum client");
        wallet
            .sync(&blockchain, SyncOptions::default())
            .map_err(|e| {
                println!("Error syncing wallet: {}", e);
            })
            .unwrap();
        let balance = wallet.get_balance().unwrap();
        println!("Wallet balance: {}", balance);

        for i in 0..gap_limit {
            let addr = wallet.get_address(AddressIndex::Reset(i)).unwrap();
            if address_balance {
                let bal = blockchain
                    .script_get_balance(&addr.address.script_pubkey())
                    .unwrap();
                println!("receive address {i}: {addr} with confirmed balance: {} and pending balance: {}", bal.confirmed, bal.unconfirmed);
            } else {
                println!("receive address {i}: {addr}");
            }
        }
        for i in 0..gap_limit {
            let change_addr = wallet.get_internal_address(AddressIndex::Reset(i)).unwrap();
            if address_balance {
                let bal = blockchain
                    .script_get_balance(&change_addr.address.script_pubkey())
                    .unwrap();
                println!("change address {i}: {change_addr} with confirmed balance: {} and pending balance: {}", bal.confirmed, bal.unconfirmed);
            } else {
                println!("change address {i}: {change_addr}");
            }
        }
        println!("******************************************************");
    }

    println!("Fetching recovery records from table {}", recovery_table);
    let recovery_query_result = rt.block_on(async {
        let ddb_client = aws_sdk_dynamodb::Client::new(&aws_config::load_from_env().await);
        ddb_client
            .query()
            .table_name(recovery_table)
            .key_condition_expression("account_id = :account_id")
            .expression_attribute_values(":account_id", AttributeValue::S(account_id.clone()))
            .send()
            .await
    })?;

    if let Some(items) = recovery_query_result.items() {
        if items.is_empty() {
            println!("******************************************************");
            println!("No recovery records found");
            println!("******************************************************");
        } else {
            for item in items {
                println!("******************************************************");
                println!("Recovery record: {:?}", item);
                println!("******************************************************");
            }
        }
    }

    println!(
        "Fetching social recovery relationships from table {}",
        social_recovery_table
    );
    let social_recovery_relationship_query_result = rt.block_on(async {
        let ddb_client = aws_sdk_dynamodb::Client::new(&aws_config::load_from_env().await);
        ddb_client
            .query()
            .table_name(social_recovery_table.clone())
            .index_name("customer_account_id_to_created_at")
            .key_condition_expression("customer_account_id = :customer_account_id")
            .expression_attribute_values(
                ":customer_account_id",
                AttributeValue::S(account_id.clone()),
            )
            .filter_expression("begins_with(partition_key, :partition_key)")
            .expression_attribute_values(
                ":partition_key",
                AttributeValue::S("urn:wallet-recovery-relationship".to_owned()),
            )
            .send()
            .await
    })?;

    if let Some(items) = social_recovery_relationship_query_result.items() {
        if items.is_empty() {
            println!("******************************************************");
            println!("No social recovery relationships found");
            println!("******************************************************");
        } else {
            for item in items {
                println!("******************************************************");
                println!("Social Recovery Relationship record: {:?}", item);
                println!("******************************************************");
            }
        }
    }

    println!(
        "Fetching social recovery challenges from table {}",
        social_recovery_table
    );
    let social_recovery_challenge_query_result = rt.block_on(async {
        let ddb_client = aws_sdk_dynamodb::Client::new(&aws_config::load_from_env().await);
        ddb_client
            .query()
            .table_name(social_recovery_table)
            .index_name("customer_account_id_to_created_at")
            .key_condition_expression("customer_account_id = :customer_account_id")
            .expression_attribute_values(
                ":customer_account_id",
                AttributeValue::S(account_id.clone()),
            )
            .filter_expression("begins_with(partition_key, :partition_key)")
            .expression_attribute_values(
                ":partition_key",
                AttributeValue::S("urn:wallet-social-challenge".to_owned()),
            )
            .send()
            .await
    })?;

    if let Some(items) = social_recovery_challenge_query_result.items() {
        if items.is_empty() {
            println!("******************************************************");
            println!("No challenges found");
            println!("******************************************************");
        } else {
            for item in items {
                println!("******************************************************");
                println!("Social Recovery Challenge: {:?}", item);
                println!("******************************************************");
            }
        }
    }
    Ok(())
}
