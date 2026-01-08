use bitcoin::bip32::{DerivationPath, Fingerprint, Xpriv};
use bitcoin::hashes::{hash160, sha256, Hash};
use bitcoin::psbt::Psbt;
use bitcoin::secp256k1::{ecdsa::Signature, Message, Secp256k1};
use bitcoin::sighash::SighashCache;
use bitcoin::{
    Address, CompressedPublicKey, EcdsaSighashType, Network, OutPoint, ScriptBuf, Transaction,
    TxIn, TxOut, Witness,
};
use serde_json::{json, Value};
use std::collections::BTreeMap;
use std::fs;
use std::process::Command;
use std::str::FromStr;

// You can also do: `cat unsigned.psbt.signed | base64 | hal psbt decode`
fn psbt_to_json(psbt: &Psbt) -> Value {
    let mut inputs = vec![];
    for (idx, input) in psbt.inputs.iter().enumerate() {
        let mut input_json = json!({
            "index": idx,
        });

        if let Some(ref utxo) = input.witness_utxo {
            input_json["witness_utxo"] = json!({
                "amount": utxo.value.to_sat(),
                "script_pubkey": hex::encode(utxo.script_pubkey.as_bytes()),
            });
        }

        if !input.bip32_derivation.is_empty() {
            let mut derivations = json!({});
            for (key, (fingerprint, path)) in &input.bip32_derivation {
                derivations[hex::encode(key.serialize())] = json!({
                    "fingerprint": hex::encode(fingerprint.as_bytes()),
                    "path": path.to_string(),
                });
            }
            input_json["bip32_derivations"] = derivations;
        }

        if !input.partial_sigs.is_empty() {
            let mut sigs = json!({});
            for (key, sig) in &input.partial_sigs {
                sigs[hex::encode(key.inner.serialize())] = json!(hex::encode(sig.serialize()));
            }
            input_json["partial_sigs"] = sigs;
            input_json["signatures_count"] = json!(input.partial_sigs.len());
        }

        inputs.push(input_json);
    }

    let tx = &psbt.unsigned_tx;
    json!({
        "unsigned_tx": {
            "version": tx.version.0,
            "locktime": tx.lock_time.to_consensus_u32(),
            "inputs": tx.input.iter().map(|i| json!({
                "txid": i.previous_output.txid.to_string(),
                "vout": i.previous_output.vout,
                "sequence": i.sequence.0,
            })).collect::<Vec<_>>(),
            "outputs": tx.output.iter().map(|o| json!({
                "value": o.value.to_sat(),
                "script_pubkey": hex::encode(o.script_pubkey.as_bytes()),
            })).collect::<Vec<_>>(),
        },
        "inputs": inputs,
        "outputs": psbt.outputs.iter().map(|_| json!({})).collect::<Vec<_>>(),
    })
}

fn verify_psbt_signature(psbt: &Psbt, secp: &Secp256k1<bitcoin::secp256k1::All>) -> bool {
    println!("\n=== Verifying PSBT Signatures ===");

    let mut all_valid = true;
    let mut total_sigs = 0;
    let mut valid_sigs = 0;

    // Iterate through all inputs
    for (input_idx, input) in psbt.inputs.iter().enumerate() {
        // Skip inputs without signatures
        if input.partial_sigs.is_empty() {
            println!("Input {}: No signatures to verify", input_idx);
            continue;
        }

        // Get witness UTXO (required for segwit sighash)
        let witness_utxo = match &input.witness_utxo {
            Some(utxo) => utxo,
            None => {
                eprintln!("Input {}: ❌ No witness UTXO found, skipping", input_idx);
                continue;
            }
        };

        println!("\nInput {} ({} signature(s)):", input_idx, input.partial_sigs.len());

        // Verify each signature in this input
        for (pubkey, sig_bytes) in &input.partial_sigs {
            total_sigs += 1;

            println!("  Public key: {}", hex::encode(pubkey.inner.serialize()));

            // Extract sighash type from signature (last byte)
            let sig_bytes_vec = sig_bytes.serialize();
            if sig_bytes_vec.is_empty() {
                eprintln!("  ❌ Empty signature");
                all_valid = false;
                continue;
            }

            let sighash_byte = sig_bytes_vec[sig_bytes_vec.len() - 1];
            let sighash_type = EcdsaSighashType::from_consensus(sighash_byte as u32);
            println!("  Sighash type: {:?} (0x{:02x})", sighash_type, sighash_byte);

            // Calculate the sighash
            let mut sighash_cache = SighashCache::new(&psbt.unsigned_tx);
            let sighash = match sighash_cache.p2wpkh_signature_hash(
                input_idx,
                &witness_utxo.script_pubkey,
                witness_utxo.value,
                sighash_type,
            ) {
                Ok(hash) => hash,
                Err(e) => {
                    eprintln!("  ❌ Failed to compute sighash: {}", e);
                    all_valid = false;
                    continue;
                }
            };

            // Parse DER signature (without the last byte which is the sighash flag)
            let der_sig = &sig_bytes_vec[..sig_bytes_vec.len() - 1];
            let sig = match Signature::from_der(der_sig) {
                Ok(s) => s,
                Err(e) => {
                    eprintln!("  ❌ Failed to parse DER signature: {}", e);
                    all_valid = false;
                    continue;
                }
            };

            // Create message from sighash
            let message = Message::from_digest(*sighash.as_byte_array());

            // Verify the signature
            match secp.verify_ecdsa(&message, &sig, &pubkey.inner) {
                Ok(_) => {
                    println!("  ✅ Signature valid");
                    valid_sigs += 1;
                }
                Err(e) => {
                    eprintln!("  ❌ Signature invalid: {}", e);
                    all_valid = false;
                }
            }
        }
    }

    // Summary
    println!("\n=== Verification Summary ===");
    println!("Total signatures verified: {}", total_sigs);
    println!("Valid signatures: {}", valid_sigs);
    println!("Invalid signatures: {}", total_sigs - valid_sigs);

    if total_sigs == 0 {
        println!("⚠️  No signatures found in PSBT");
        false
    } else if all_valid {
        println!("✅ All signatures are valid!");
        true
    } else {
        println!("❌ Some signatures are invalid");
        false
    }
}

fn main() {
    // Generate a test seed (32 bytes)
    let seed = sha256::Hash::hash(b"test seed for libew demo").to_byte_array();
    let seed_hex = hex::encode(&seed);
    println!("Seed: {}", seed_hex);

    // Create secp context
    let secp = Secp256k1::new();

    // Derive master key from seed
    let master = Xpriv::new_master(Network::Bitcoin, &seed).unwrap();

    // Calculate master fingerprint (hash160 of master pubkey)
    let master_pubkey = master.to_priv().public_key(&secp);
    let master_compressed = CompressedPublicKey::try_from(master_pubkey).unwrap();
    let hash = hash160::Hash::hash(&master_compressed.to_bytes());
    let mut fingerprint_bytes = [0u8; 4];
    fingerprint_bytes.copy_from_slice(&hash[..4]);
    let fingerprint = Fingerprint::from(fingerprint_bytes);

    // Derive multiple addresses using different BIP84 paths
    let paths = vec![
        "m/84'/0'/0'/0/0",  // First receiving address
        "m/84'/0'/0'/0/1",  // Second receiving address
        "m/84'/0'/0'/1/0",  // First change address
    ];

    let mut addresses = Vec::new();
    let mut pubkeys = Vec::new();

    for path_str in &paths {
        let path = DerivationPath::from_str(path_str).unwrap();
        let derived = master.derive_priv(&secp, &path).unwrap();
        let pubkey = derived.to_priv().public_key(&secp);
        let compressed = CompressedPublicKey::try_from(pubkey).unwrap();
        let address = Address::p2wpkh(&compressed, Network::Bitcoin);

        println!("Address ({}): {}", path_str, address);
        addresses.push(address);
        pubkeys.push((pubkey, path));
    }

    // Create a transaction with multiple inputs
    let mut inputs = Vec::new();
    for i in 0..3 {
        inputs.push(TxIn {
            previous_output: OutPoint {
                txid: format!("0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}0{:01x}",
                    i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1,
                    i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1, i+1)
                    .parse()
                    .unwrap(),
                vout: 0,
            },
            script_sig: ScriptBuf::new(),
            sequence: bitcoin::Sequence::MAX,
            witness: Witness::new(),
        });
    }

    // Create outputs spending to the first address
    let tx = Transaction {
        version: bitcoin::transaction::Version::TWO,
        lock_time: bitcoin::absolute::LockTime::ZERO,
        input: inputs,
        output: vec![TxOut {
            value: bitcoin::Amount::from_sat(250000),  // Total from 3 inputs of 100k each minus fees
            script_pubkey: addresses[0].script_pubkey(),
        }],
    };

    // Create PSBT
    let mut psbt = Psbt::from_unsigned_tx(tx).unwrap();

    // Add witness UTXOs and BIP32 derivation info for each input
    for (i, ((pubkey, path), address)) in pubkeys.iter().zip(&addresses).enumerate() {
        // Add witness UTXO (required for signing)
        psbt.inputs[i].witness_utxo = Some(TxOut {
            value: bitcoin::Amount::from_sat(100000),
            script_pubkey: address.script_pubkey(),
        });

        // Add BIP32 derivation path info so ew knows which key to use
        let mut bip32_derivations = BTreeMap::new();
        bip32_derivations.insert(pubkey.inner, (fingerprint, path.clone()));
        psbt.inputs[i].bip32_derivation = bip32_derivations;
    }

    println!("\nCreated PSBT with {} inputs", psbt.inputs.len());

    // Serialize PSBT to file
    let psbt_bytes = psbt.serialize();
    let psbt_file = "unsigned.psbt";
    fs::write(psbt_file, &psbt_bytes).expect("Failed to write PSBT file");
    println!("PSBT written to: {}", psbt_file);

    // Write unsigned PSBT as JSON
    let unsigned_json = psbt_to_json(&psbt);
    let unsigned_json_file = "unsigned.psbt.json";
    fs::write(
        unsigned_json_file,
        serde_json::to_string_pretty(&unsigned_json).unwrap(),
    )
    .expect("Failed to write unsigned JSON");
    println!("\nUnsigned PSBT JSON written to: {}", unsigned_json_file);
    println!(
        "Unsigned PSBT JSON:\n{}",
        serde_json::to_string_pretty(&unsigned_json).unwrap()
    );

    // Call ew-demo to sign
    println!("\nCalling ew-demo to sign PSBT...");
    let output = Command::new("./build/ew-demo")
        .arg("sign")
        .arg(psbt_file)
        .arg(&seed_hex)
        .output()
        .expect("Failed to execute ew-demo");

    if output.status.success() {
        let stdout = String::from_utf8_lossy(&output.stdout);
        println!("Output:\n{}", stdout);

        // Read the signed PSBT file
        let signed_file = "signed.psbt";
        if let Ok(signed_bytes) = fs::read(&signed_file) {
            println!("\nRead signed PSBT from: {}", signed_file);

            if let Ok(signed_psbt) = Psbt::deserialize(&signed_bytes) {
                println!("Successfully parsed signed PSBT");

                // Check if signatures were added to all inputs
                let mut total_sigs = 0;
                for (i, input) in signed_psbt.inputs.iter().enumerate() {
                    let sigs = input.partial_sigs.len();
                    if sigs > 0 {
                        println!("✓ Input {}: {} signature(s) added", i, sigs);
                        total_sigs += sigs;
                    } else {
                        println!("✗ Input {}: No signatures", i);
                    }
                }

                if total_sigs > 0 {
                    // Verify all signatures
                    verify_psbt_signature(&signed_psbt, &secp);
                } else {
                    println!("No signatures found in any input");
                }

                // Write signed PSBT as JSON
                let signed_json = psbt_to_json(&signed_psbt);
                let signed_json_file = "signed.psbt.json";
                fs::write(
                    signed_json_file,
                    serde_json::to_string_pretty(&signed_json).unwrap(),
                )
                .expect("Failed to write signed JSON");
                println!("\nSigned PSBT JSON written to: {}", signed_json_file);
                println!(
                    "Signed PSBT JSON:\n{}",
                    serde_json::to_string_pretty(&signed_json).unwrap()
                );
            } else {
                eprintln!("Failed to parse signed PSBT");
            }
        } else {
            eprintln!("Failed to read signed PSBT file: {}", signed_file);
        }
    } else {
        eprintln!("Error: {}", String::from_utf8_lossy(&output.stderr));
    }
}
