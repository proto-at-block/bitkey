use std::collections::HashMap;
use std::env;
use std::path::PathBuf;
use std::str::FromStr;

use anyhow::{anyhow, Result};
use base64::prelude::{Engine as _, BASE64_STANDARD, BASE64_STANDARD_NO_PAD};
use bdk::blockchain::{ConfigurableBlockchain, ElectrumBlockchain, ElectrumBlockchainConfig};
use bdk::electrum_client::ElectrumApi;
use bdk::keys::DescriptorPublicKey;
use bdk::miniscript::Descriptor;
use bdk::wallet::AddressIndex;
use bdk::SyncOptions;
use crypto::p256_box::P256Box;
use rustyline::error::ReadlineError;
use rustyline::{Config, Editor};

use super::completion::DebugCompleter;
use super::state::{DebugState, MGet, ToM, ToS};
use super::utils::{create_spinner, format_items_yaml, get_table_name, query_dynamo};

/// Get the path to the debug history file in the user's home directory.
/// Falls back to USERPROFILE on Windows if HOME is not set.
fn get_history_file_path() -> Option<PathBuf> {
    env::var("HOME")
        .or_else(|_| env::var("USERPROFILE"))
        .ok()
        .map(|home| PathBuf::from(home).join(".bitkey_debug_history"))
}

pub async fn debug_repl(environment: String) -> Result<()> {
    println!("🐛 Bitkey Debug REPL");
    println!("📍 Environment: {}", environment);
    println!("🚀 Use 'load-account <account_id>' to begin debugging");
    println!("💡 Type 'help' for available commands or 'exit' to quit");
    println!("⌨️ Press TAB for command completion");
    println!();

    let mut state = DebugState::new(environment);

    // Configure rustyline
    let config = Config::builder()
        .completion_type(rustyline::CompletionType::List)
        .build();

    let mut rl: Editor<DebugCompleter, _> = Editor::with_config(config)?;
    rl.set_helper(Some(DebugCompleter));

    // Load history if it exists
    if let Some(history_path) = get_history_file_path() {
        let _ = rl.load_history(&history_path);
    }

    loop {
        match rl.readline("debug> ") {
            Ok(line) => {
                let line = line.trim();
                if line.is_empty() {
                    continue;
                }

                // Add to history
                rl.add_history_entry(line)?;

                match handle_command(line, &mut state).await {
                    Ok(should_exit) => {
                        if should_exit {
                            break;
                        }
                    }
                    Err(e) => {
                        eprintln!("Error: {}", e);
                    }
                }
            }
            Err(ReadlineError::Interrupted) => {
                println!("^C");
                break;
            }
            Err(ReadlineError::Eof) => {
                break;
            }
            Err(err) => {
                eprintln!("Error reading input: {}", err);
                break;
            }
        }
    }

    // Save history
    if let Some(history_path) = get_history_file_path() {
        let _ = rl.save_history(&history_path);
    }

    println!("👋 Goodbye!");
    Ok(())
}

async fn handle_command(input: &str, state: &mut DebugState) -> Result<bool> {
    let args: Vec<&str> = input.split_whitespace().collect();
    if args.is_empty() {
        return Ok(false);
    }

    match args[0] {
        "help" | "h" => {
            show_help();
        }
        "exit" | "quit" | "q" => {
            return Ok(true);
        }
        "clear" => {
            let environment = state.environment.clone();
            *state = DebugState::new(environment);
            println!("🧹 Debug state cleared (environment preserved)");
        }
        "load-account" => {
            handle_load_account(&args[1..], state).await?;
        }
        "load-descriptors" => {
            handle_load_descriptors(&args[1..], state).await?;
        }
        "list-keysets" => {
            handle_list_keysets(state).await?;
        }
        "list-addresses" => {
            handle_list_addresses(&args[1..], state).await?;
        }
        "list-recoveries" => {
            handle_list_recoveries(state).await?;
        }
        "list-relationships" => {
            handle_list_relationships(state).await?;
        }
        "list-challenges" => {
            handle_list_challenges(state).await?;
        }
        "list-claims" => {
            handle_list_claims(state).await?;
        }
        "set-gap-limit" => {
            handle_set_gap_limit(&args[1..], state).await?;
        }
        "set-electrum-server" => {
            handle_set_electrum_server(&args[1..], state).await?;
        }
        _ => {
            println!(
                "❓ Unknown command: '{}'. Type 'help' for available commands.",
                args[0]
            );
        }
    }

    Ok(false)
}

fn show_help() {
    println!("🔧 Debug REPL Commands:");
    println!("  help, h                              - Show this help");
    println!("  exit, quit, q                        - Exit the REPL");
    println!("  clear                                - Clear debug state (preserves environment)");
    println!();
    println!("👤 Account management:");
    println!("  load-account <account_id>            - Load account by account ID");
    println!();
    println!("📊 Data loading:");
    println!(
        "  load-descriptors [attachment_id]     - Load descriptors from account or by encrypted attachment ID"
    );
    println!();
    println!("⚙️ Configuration:");
    println!("  set-gap-limit <number>               - Set the gap limit for address derivation (default: 20)");
    println!("  set-electrum-server <url>            - Set the Electrum server URL (default: ssl://bitkey.mempool.space:50002)");
    println!();
    println!("📋 Listing commands (require account to be loaded):");
    println!("  list-keysets                         - List keysets (shows balance if descriptors loaded)");
    println!("  list-addresses <keyset_id>           - Derive addresses for keyset with balances (requires descriptors)");
    println!("  list-recoveries                      - List delay and notify recoveries");
    println!("  list-relationships                   - List recovery relationships");
    println!("  list-challenges                      - List social recovery challenges");
    println!("  list-claims                          - List inheritance claims");
}

async fn handle_load_account(args: &[&str], state: &mut DebugState) -> Result<()> {
    match args.first() {
        Some(&account_id) => {
            if state.account.is_some() {
                println!("⚠️ Account already loaded. Use 'clear' to reset state first");
                return Ok(());
            }

            let table_name = get_table_name(&state.environment, "fromagerie.accounts");
            let ro_config = state.get_ro_config().await?;

            let spinner = create_spinner(&format!("Loading account {}...", account_id));

            let ddb = aws_sdk_dynamodb::Client::new(ro_config);
            let items = query_dynamo(
                &ddb,
                &table_name,
                account_id,
                None,
                "partition_key = :partition_key",
                None,
                None,
                None,
            )
            .await?;

            if items.is_empty() {
                spinner.finish();
                println!("❌ No account found with ID: {}", account_id);
                return Ok(());
            }

            state.account = items.into_iter().next();

            spinner.finish();
            println!("✅ Account {} loaded successfully", account_id);
        }
        None => {
            println!("📝 Usage: load-account <account_id>");
        }
    }
    Ok(())
}

async fn handle_load_descriptors(args: &[&str], state: &mut DebugState) -> Result<()> {
    match args.first() {
        Some(&attachment_id) => {
            let table_name = get_table_name(&state.environment, "fromagerie.encrypted_attachment");
            let ear_config = state.get_ear_config().await?;

            let spinner = create_spinner(&format!(
                "Decrypting and loading descriptors {}...",
                attachment_id
            ));

            let ddb = aws_sdk_dynamodb::Client::new(ear_config);
            let items = query_dynamo(
                &ddb,
                &table_name,
                attachment_id,
                None,
                "partition_key = :partition_key",
                None,
                None,
                None,
            )
            .await?;

            let item = items
                .into_iter()
                .next()
                .ok_or_else(|| anyhow!("No encrypted attachment found for ID {}", attachment_id))?;

            let kms_key_id = item.m_get("kms_key_id")?.to_s()?;
            let private_key_ciphertext = BASE64_STANDARD
                .decode(item.m_get("private_key_ciphertext")?.to_s()?)
                .map_err(|e| {
                    anyhow!(
                        "❌ Failed to decode private key ciphertext for attachment {}: {:?}",
                        attachment_id,
                        e
                    )
                })?;
            let sealed_attachment = item.m_get("sealed_attachment")?.to_s()?;

            let parts: Vec<_> = sealed_attachment.split('.').collect();
            let [_, ciphertext_str, nonce_str, public_key_str] = parts.as_slice() else {
                return Err(anyhow!(
                    "Invalid sealed attachment format for {}",
                    attachment_id
                ));
            };

            let kms = aws_sdk_kms::Client::new(ear_config);
            let private_key = kms
                .decrypt()
                .key_id(kms_key_id)
                .ciphertext_blob(private_key_ciphertext.into())
                .encryption_context("encryptedAttachmentId", attachment_id)
                .send()
                .await?
                .plaintext
                .ok_or_else(|| {
                    anyhow!(
                        "Failed to decrypt private key for attachment {}",
                        attachment_id
                    )
                })?
                .into_inner();

            let public_key = BASE64_STANDARD_NO_PAD.decode(public_key_str).map_err(|e| {
                anyhow!(
                    "❌ Failed to decode public key for attachment {}: {:?}",
                    attachment_id,
                    e
                )
            })?;
            let ciphertext = BASE64_STANDARD_NO_PAD.decode(ciphertext_str).map_err(|e| {
                anyhow!(
                    "❌ Failed to decode ciphertext for attachment {}: {:?}",
                    attachment_id,
                    e
                )
            })?;
            let nonce = BASE64_STANDARD_NO_PAD.decode(nonce_str).map_err(|e| {
                anyhow!(
                    "❌ Failed to decode nonce for attachment {}: {:?}",
                    attachment_id,
                    e
                )
            })?;

            let p256_box = P256Box::new(&public_key, &private_key).map_err(|e| {
                anyhow!(
                    "❌ Failed to create P256Box for attachment {}: {:?}",
                    attachment_id,
                    e
                )
            })?;
            let decrypted_data = p256_box.decrypt(&ciphertext, &nonce).map_err(|e| {
                anyhow!("❌ Failed to decrypt attachment {}: {:?}", attachment_id, e)
            })?;
            let descriptors_json = String::from_utf8(decrypted_data).map_err(|e| {
                anyhow!(
                    "❌ Decrypted data is not valid UTF-8 for attachment {}: {:?}",
                    attachment_id,
                    e
                )
            })?;

            state.descriptors = Some(
                serde_json::from_str::<HashMap<String, String>>(&descriptors_json).map_err(
                    |e| {
                        anyhow!(
                            "❌ Failed to parse descriptors JSON for attachment {}: {:?}",
                            attachment_id,
                            e
                        )
                    },
                )?,
            );

            spinner.finish();
            println!("✅ Descriptors loaded successfully from {}", attachment_id);
        }
        None => {
            // Try to load descriptors from account object, massaging into the format we expect encrypted descriptors to take
            let Some(account) = state.account.as_ref() else {
                println!("⚠️ No account loaded. Use 'load-account <account_id>' first, or provide an attachment ID.");
                return Ok(());
            };

            let spinner = create_spinner("Loading descriptors from account...");

            let mut descriptors = HashMap::new();

            let keysets = account.m_get("spending_keysets")?.to_m()?;
            for (keyset_id, keyset) in keysets {
                let keyset_map = keyset.to_m()?;

                let app_dpub = keyset_map.m_get("app_dpub")?.to_s()?;
                let hw_dpub = keyset_map.m_get("hardware_dpub")?.to_s()?;
                let server_dpub = keyset_map.m_get("server_dpub")?.to_s()?;

                let dpks = [app_dpub, hw_dpub, server_dpub]
                    .iter()
                    .enumerate()
                    .map(|(i, s)| {
                        let key_type = ["app", "hardware", "server"][i];
                        DescriptorPublicKey::from_str(s).map_err(|e| {
                            anyhow!(
                                "❌ Failed to parse {} descriptor public key for keyset {}: {:?}",
                                key_type,
                                keyset_id,
                                e
                            )
                        })
                    })
                    .collect::<Result<Vec<_>>>()?;

                let descriptor = Descriptor::new_wsh_sortedmulti(2, dpks).map_err(|e| {
                    anyhow!(
                        "❌ Failed to create WSH descriptor for keyset {}: {:?}",
                        keyset_id,
                        e
                    )
                })?;

                descriptors.insert(keyset_id.clone(), descriptor.to_string());
            }

            state.descriptors = Some(descriptors);

            spinner.finish();
            println!("✅ Descriptors loaded successfully from account");
        }
    }
    Ok(())
}

async fn handle_list_keysets(state: &mut DebugState) -> Result<()> {
    let Some(account) = state.account.as_ref() else {
        println!("⚠️ No account loaded. Use 'load-account <account_id>' first.");
        return Ok(());
    };

    let active_keyset_id = account.m_get("active_keyset_id")?.to_s()?.clone();

    // Collect keysets to avoid borrowing conflicts
    let keysets = account.m_get("spending_keysets")?.to_m()?.clone();

    let spinner = create_spinner("Loading keysets...");

    let mut balances = Vec::new();

    for (keyset_id, keyset) in keysets {
        if state.descriptors.is_some() {
            // Extract the values we need before the mutable borrow
            let electrum_url = state.electrum_server_url.clone();
            let gap_limit = state.gap_limit;

            let wallet = state.get_wallet(&keyset_id, &keyset).map_err(|e| {
                anyhow!("❌ Failed to get wallet for keyset {}: {:?}", keyset_id, e)
            })?;
            let blockchain = ElectrumBlockchain::from_config(&ElectrumBlockchainConfig {
                url: electrum_url.clone(),
                socks5: None,
                retry: 0,
                timeout: None,
                stop_gap: gap_limit,
                validate_domain: true,
            })
            .map_err(|e| {
                anyhow!(
                    "❌ Failed to configure Electrum blockchain with URL {}: {:?}",
                    electrum_url,
                    e
                )
            })?;
            wallet
                .sync(&blockchain, SyncOptions::default())
                .map_err(|e| {
                    anyhow!("❌ Failed to sync wallet for keyset {}: {:?}", keyset_id, e)
                })?;

            let balance = wallet.get_balance().map_err(|e| {
                anyhow!("❌ Failed to get balance for keyset {}: {:?}", keyset_id, e)
            })?;
            balances.push((
                keyset_id.clone(),
                (active_keyset_id == keyset_id, Some(balance.confirmed)),
            ));
        } else {
            balances.push((keyset_id.clone(), (active_keyset_id == keyset_id, None)));
        }
    }

    spinner.finish();
    println!("💰 Keysets:");

    for (keyset_id, (is_active, balance)) in balances {
        if let Some(balance) = balance {
            println!(
                "  {} {} (balance: {}.{:0>8} BTC)",
                if is_active { "🔑" } else { "🗝️" },
                keyset_id,
                balance / 100_000_000,
                balance % 100_000_000 // Convert satoshis to BTC
            );
        } else {
            println!("  {} {}", if is_active { "🔑" } else { "🗝️" }, keyset_id);
        }
    }

    Ok(())
}

async fn handle_list_addresses(args: &[&str], state: &mut DebugState) -> Result<()> {
    let Some(account) = state.account.as_ref() else {
        println!("⚠️ No account loaded. Use 'load-account <account_id>' first.");
        return Ok(());
    };

    if state.descriptors.is_none() {
        println!("⚠️ Descriptors not loaded. Use 'load-descriptors <attachment_id>' first.");
        return Ok(());
    }

    match args.first() {
        Some(&keyset_id) => {
            let spinner = create_spinner(&format!(
                "Deriving addresses for keyset {} (gap: {})...",
                keyset_id, state.gap_limit
            ));

            let keyset = account
                .m_get("spending_keysets")?
                .to_m()?
                .get(keyset_id)
                .ok_or_else(|| anyhow!("Keyset ID '{}' not found in account", keyset_id))?
                .clone();

            // Extract the values we need before the mutable borrow
            let electrum_url = state.electrum_server_url.clone();
            let gap_limit = state.gap_limit;

            let wallet = state
                .get_wallet(&keyset_id.to_string(), &keyset)
                .map_err(|e| {
                    anyhow!("❌ Failed to get wallet for keyset {}: {:?}", keyset_id, e)
                })?;
            let blockchain = ElectrumBlockchain::from_config(&ElectrumBlockchainConfig {
                url: electrum_url.clone(),
                socks5: None,
                retry: 0,
                timeout: None,
                stop_gap: gap_limit,
                validate_domain: true,
            })
            .map_err(|e| {
                anyhow!(
                    "❌ Failed to configure Electrum blockchain with URL {}: {:?}",
                    electrum_url,
                    e
                )
            })?;
            wallet
                .sync(&blockchain, SyncOptions::default())
                .map_err(|e| {
                    anyhow!("❌ Failed to sync wallet for keyset {}: {:?}", keyset_id, e)
                })?;

            let mut receive_balances = Vec::new();
            let mut change_balances = Vec::new();

            for i in 0..gap_limit {
                let addr = wallet
                    .get_address(AddressIndex::Reset(i as u32))
                    .map_err(|e| {
                        anyhow!(
                            "❌ Failed to derive receiving address {} for keyset {}: {:?}",
                            i,
                            keyset_id,
                            e
                        )
                    })?;
                let bal = blockchain
                    .script_get_balance(&addr.address.script_pubkey())
                    .map_err(|e| {
                        anyhow!(
                            "❌ Failed to get balance for receiving address {} in keyset {}: {:?}",
                            i,
                            keyset_id,
                            e
                        )
                    })?;
                receive_balances.push((addr.address.to_string(), bal.confirmed));
            }
            for i in 0..gap_limit {
                let addr = wallet
                    .get_internal_address(AddressIndex::Reset(i as u32))
                    .map_err(|e| {
                        anyhow!(
                            "❌ Failed to derive change address {} for keyset {}: {:?}",
                            i,
                            keyset_id,
                            e
                        )
                    })?;
                let bal = blockchain
                    .script_get_balance(&addr.address.script_pubkey())
                    .map_err(|e| {
                        anyhow!(
                            "❌ Failed to get balance for change address {} in keyset {}: {:?}",
                            i,
                            keyset_id,
                            e
                        )
                    })?;
                change_balances.push((addr.address.to_string(), bal.confirmed));
            }

            spinner.finish();
            println!(
                "🏠 Addresses for keyset {} (gap limit: {}):",
                keyset_id, gap_limit
            );

            println!("  📥 Receiving Addresses:");
            for (addr, balance) in receive_balances {
                println!(
                    "   {} (balance: {}.{:0>8} BTC)",
                    addr,
                    balance / 100_000_000,
                    balance % 100_000_000 // Convert satoshis to BTC
                );
            }

            println!();
            println!("  📤 Change Addresses:");
            for (addr, balance) in change_balances {
                println!(
                    "   {} (balance: {}.{:0>8} BTC)",
                    addr,
                    balance / 100_000_000,
                    balance % 100_000_000 // Convert satoshis to BTC
                );
            }
        }
        _ => {
            println!("📝 Usage: list-addresses <keyset_id>");
        }
    }

    Ok(())
}

// Commands that need DynamoDB access with credential retrieval
async fn handle_list_recoveries(state: &mut DebugState) -> Result<()> {
    let account_id = match state
        .account
        .as_ref()
        .and_then(|a| a.get("partition_key"))
        .and_then(|a| a.as_s().ok())
    {
        Some(id) => id.clone(),
        None => {
            println!("⚠️ No account loaded. Use 'load-account <account_id>' first.");
            return Ok(());
        }
    };

    let table_name = get_table_name(&state.environment, "fromagerie.account_recovery");
    let ro_config = state.get_ro_config().await?;

    let spinner =
        create_spinner("Connecting to DynamoDB and querying delay & notify recoveries...");

    let ddb = aws_sdk_dynamodb::Client::new(ro_config);
    let items = query_dynamo(
        &ddb,
        &table_name,
        &account_id,
        None,
        "account_id = :partition_key",
        None,
        None,
        None,
    )
    .await?;

    spinner.finish();
    println!("⏰ Delay & Notify Recoveries:");

    format_items_yaml(None, &items);

    Ok(())
}

async fn handle_list_relationships(state: &mut DebugState) -> Result<()> {
    let account_id = match state
        .account
        .as_ref()
        .and_then(|a| a.get("partition_key"))
        .and_then(|a| a.as_s().ok())
    {
        Some(id) => id.clone(),
        None => {
            println!("⚠️ No account loaded. Use 'load-account <account_id>' first.");
            return Ok(());
        }
    };

    let table_name = get_table_name(&state.environment, "fromagerie.social_recovery");
    let ro_config = state.get_ro_config().await?;

    let spinner = create_spinner("Connecting to DynamoDB and querying recovery relationships...");

    let ddb = aws_sdk_dynamodb::Client::new(ro_config);

    let customer_items = query_dynamo(
        &ddb,
        &table_name,
        &account_id,
        Some("customer_account_id_to_created_at"),
        "customer_account_id = :partition_key",
        Some("#row_type = :row_type"),
        Some(("#row_type", "_SocialRecoveryRow_type")),
        Some((":row_type", "Relationship")),
    )
    .await?;

    let contact_items = query_dynamo(
        &ddb,
        &table_name,
        &account_id,
        Some("trusted_contact_account_id_to_customer_account_id"),
        "trusted_contact_account_id = :partition_key",
        Some("#row_type = :row_type"),
        Some(("#row_type", "_SocialRecoveryRow_type")),
        Some((":row_type", "Relationship")),
    )
    .await?;

    spinner.finish();
    println!("🤝 Recovery Relationships:");

    format_items_yaml(Some("As Customer/Benefactor"), &customer_items);
    format_items_yaml(Some("As Contact/Beneficiary"), &contact_items);

    Ok(())
}

async fn handle_list_challenges(state: &mut DebugState) -> Result<()> {
    let account_id = match state
        .account
        .as_ref()
        .and_then(|a| a.get("partition_key"))
        .and_then(|a| a.as_s().ok())
    {
        Some(id) => id.clone(),
        None => {
            println!("⚠️ No account loaded. Use 'load-account <account_id>' first.");
            return Ok(());
        }
    };

    let table_name = get_table_name(&state.environment, "fromagerie.social_recovery");
    let ro_config = state.get_ro_config().await?;

    let spinner =
        create_spinner("Connecting to DynamoDB and querying social recovery challenges...");

    let ddb = aws_sdk_dynamodb::Client::new(ro_config);
    let items = query_dynamo(
        &ddb,
        &table_name,
        &account_id,
        Some("customer_account_id_to_created_at"),
        "customer_account_id = :partition_key",
        Some("#row_type = :row_type"),
        Some(("#row_type", "_SocialRecoveryRow_type")),
        Some((":row_type", "Challenge")),
    )
    .await?;

    spinner.finish();
    println!("👨‍👩‍👧‍👦 Social Recovery Challenges:");

    format_items_yaml(None, &items);

    Ok(())
}

async fn handle_list_claims(state: &mut DebugState) -> Result<()> {
    let account_id = match state
        .account
        .as_ref()
        .and_then(|a| a.get("partition_key"))
        .and_then(|a| a.as_s().ok())
    {
        Some(id) => id.clone(),
        None => {
            println!("⚠️ No account loaded. Use 'load-account <account_id>' first.");
            return Ok(());
        }
    };

    let table_name = get_table_name(&state.environment, "fromagerie.inheritance");
    let ro_config = state.get_ro_config().await?;

    let spinner = create_spinner("Connecting to DynamoDB and querying inheritance claims...");

    let ddb = aws_sdk_dynamodb::Client::new(ro_config);

    let benefactor_items = query_dynamo(
        &ddb,
        &table_name,
        &account_id,
        Some("by_benefactor_account_id_to_created_at"),
        "benefactor_account_id = :partition_key",
        Some("#row_type = :row_type"),
        Some(("#row_type", "_InheritanceRow_type")),
        Some((":row_type", "Claim")),
    )
    .await?;

    let beneficiary_items = query_dynamo(
        &ddb,
        &table_name,
        &account_id,
        Some("by_beneficiary_account_id_to_created_at"),
        "beneficiary_account_id = :partition_key",
        Some("#row_type = :row_type"),
        Some(("#row_type", "_InheritanceRow_type")),
        Some((":row_type", "Claim")),
    )
    .await?;

    spinner.finish();
    println!("🏛️ Inheritance Claims:");

    format_items_yaml(Some("As Benefactor"), &benefactor_items);
    format_items_yaml(Some("As Beneficiary"), &beneficiary_items);

    Ok(())
}

async fn handle_set_gap_limit(args: &[&str], state: &mut DebugState) -> Result<()> {
    match args.first() {
        Some(&gap_limit_str) => match gap_limit_str.parse::<usize>() {
            Ok(gap_limit) => {
                if gap_limit == 0 {
                    return Err(anyhow!(
                        "❌ Gap limit must be greater than 0, got: {}",
                        gap_limit
                    ));
                }
                state.gap_limit = gap_limit;
                println!("✅ Gap limit set to {}", gap_limit);
            }
            Err(e) => {
                return Err(anyhow!(
                    "❌ Invalid gap limit '{}': {:?}. Must be a positive integer.",
                    gap_limit_str,
                    e
                ));
            }
        },
        None => {
            println!("📝 Usage: set-gap-limit <number>");
            println!("📊 Current gap limit: {}", state.gap_limit);
        }
    }
    Ok(())
}

async fn handle_set_electrum_server(args: &[&str], state: &mut DebugState) -> Result<()> {
    match args.first() {
        Some(&server_url) => {
            // Basic validation - check if it looks like a URL
            if !server_url.contains("://") {
                return Err(anyhow!(
                    "❌ Invalid Electrum server URL: '{}'. Must include protocol (e.g., ssl://)",
                    server_url
                ));
            }
            state.electrum_server_url = server_url.to_string();
            println!("✅ Electrum server set to {}", server_url);
        }
        None => {
            println!("📝 Usage: set-electrum-server <url>");
            println!("🌐 Current Electrum server: {}", state.electrum_server_url);
            println!("💡 Example: set-electrum-server ssl://electrum.blockstream.info:50002");
        }
    }
    Ok(())
}
