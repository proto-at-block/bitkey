use crate::bitcoin::{Address, Key, Network, ProprietaryKey, Psbt};
use bdk_electrum::bdk_core::bitcoin::hex::DisplayHex;

#[test]
fn test_is_valid_for_network() {
    // ====Docs tests====
    // https://docs.rs/bitcoin/0.29.2/src/bitcoin/util/address.rs.html#798-802

    let docs_address_testnet_str = "2N83imGV3gPwBzKJQvWJ7cRUY2SpUyU6A5e";
    let docs_address_testnet =
        Address::new(docs_address_testnet_str.to_string(), Network::Testnet).unwrap();
    assert!(
        docs_address_testnet.is_valid_for_network(Network::Testnet),
        "Address should be valid for Testnet"
    );
    assert!(
        docs_address_testnet.is_valid_for_network(Network::Signet),
        "Address should be valid for Signet"
    );
    assert!(
        docs_address_testnet.is_valid_for_network(Network::Regtest),
        "Address should be valid for Regtest"
    );

    let docs_address_mainnet_str = "32iVBEu4dxkUQk9dJbZUiBiQdmypcEyJRf";
    let docs_address_mainnet =
        Address::new(docs_address_mainnet_str.to_string(), Network::Bitcoin).unwrap();
    assert!(
        docs_address_mainnet.is_valid_for_network(Network::Bitcoin),
        "Address should be valid for Bitcoin"
    );

    // ====Bech32====

    //     | Network         | Prefix  | Address Type |
    //     |-----------------|---------|--------------|
    //     | Bitcoin Mainnet | `bc1`   | Bech32       |
    //     | Bitcoin Testnet | `tb1`   | Bech32       |
    //     | Bitcoin Signet  | `tb1`   | Bech32       |
    //     | Bitcoin Regtest | `bcrt1` | Bech32       |

    // Bech32 - Bitcoin
    // Valid for:
    // - Bitcoin
    // Not valid for:
    // - Testnet
    // - Signet
    // - Regtest
    let bitcoin_mainnet_bech32_address_str = "bc1qxhmdufsvnuaaaer4ynz88fspdsxq2h9e9cetdj";
    let bitcoin_mainnet_bech32_address = Address::new(
        bitcoin_mainnet_bech32_address_str.to_string(),
        Network::Bitcoin,
    )
    .unwrap();
    assert!(
        bitcoin_mainnet_bech32_address.is_valid_for_network(Network::Bitcoin),
        "Address should be valid for Bitcoin"
    );
    assert!(
        !bitcoin_mainnet_bech32_address.is_valid_for_network(Network::Testnet),
        "Address should not be valid for Testnet"
    );
    assert!(
        !bitcoin_mainnet_bech32_address.is_valid_for_network(Network::Signet),
        "Address should not be valid for Signet"
    );
    assert!(
        !bitcoin_mainnet_bech32_address.is_valid_for_network(Network::Regtest),
        "Address should not be valid for Regtest"
    );

    // Bech32 - Testnet
    // Valid for:
    // - Testnet
    // - Regtest
    // Not valid for:
    // - Bitcoin
    // - Regtest
    let bitcoin_testnet_bech32_address_str =
        "tb1p4nel7wkc34raczk8c4jwk5cf9d47u2284rxn98rsjrs4w3p2sheqvjmfdh";
    let bitcoin_testnet_bech32_address = Address::new(
        bitcoin_testnet_bech32_address_str.to_string(),
        Network::Testnet,
    )
    .unwrap();
    assert!(
        !bitcoin_testnet_bech32_address.is_valid_for_network(Network::Bitcoin),
        "Address should not be valid for Bitcoin"
    );
    assert!(
        bitcoin_testnet_bech32_address.is_valid_for_network(Network::Testnet),
        "Address should be valid for Testnet"
    );
    assert!(
        bitcoin_testnet_bech32_address.is_valid_for_network(Network::Signet),
        "Address should be valid for Signet"
    );
    assert!(
        !bitcoin_testnet_bech32_address.is_valid_for_network(Network::Regtest),
        "Address should not not be valid for Regtest"
    );

    // Bech32 - Signet
    // Valid for:
    // - Signet
    // - Testnet
    // Not valid for:
    // - Bitcoin
    // - Regtest
    let bitcoin_signet_bech32_address_str =
        "tb1pwzv7fv35yl7ypwj8w7al2t8apd6yf4568cs772qjwper74xqc99sk8x7tk";
    let bitcoin_signet_bech32_address = Address::new(
        bitcoin_signet_bech32_address_str.to_string(),
        Network::Signet,
    )
    .unwrap();
    assert!(
        !bitcoin_signet_bech32_address.is_valid_for_network(Network::Bitcoin),
        "Address should not be valid for Bitcoin"
    );
    assert!(
        bitcoin_signet_bech32_address.is_valid_for_network(Network::Testnet),
        "Address should be valid for Testnet"
    );
    assert!(
        bitcoin_signet_bech32_address.is_valid_for_network(Network::Signet),
        "Address should be valid for Signet"
    );
    assert!(
        !bitcoin_signet_bech32_address.is_valid_for_network(Network::Regtest),
        "Address should not not be valid for Regtest"
    );

    // Bech32 - Regtest
    // Valid for:
    // - Regtest
    // Not valid for:
    // - Bitcoin
    // - Testnet
    // - Signet
    let bitcoin_regtest_bech32_address_str = "bcrt1q39c0vrwpgfjkhasu5mfke9wnym45nydfwaeems";
    let bitcoin_regtest_bech32_address = Address::new(
        bitcoin_regtest_bech32_address_str.to_string(),
        Network::Regtest,
    )
    .unwrap();
    assert!(
        !bitcoin_regtest_bech32_address.is_valid_for_network(Network::Bitcoin),
        "Address should not be valid for Bitcoin"
    );
    assert!(
        !bitcoin_regtest_bech32_address.is_valid_for_network(Network::Testnet),
        "Address should not be valid for Testnet"
    );
    assert!(
        !bitcoin_regtest_bech32_address.is_valid_for_network(Network::Signet),
        "Address should not be valid for Signet"
    );
    assert!(
        bitcoin_regtest_bech32_address.is_valid_for_network(Network::Regtest),
        "Address should be valid for Regtest"
    );

    // ====P2PKH====

    //     | Network                            | Prefix for P2PKH | Prefix for P2SH |
    //     |------------------------------------|------------------|-----------------|
    //     | Bitcoin Mainnet                    | `1`              | `3`             |
    //     | Bitcoin Testnet, Regtest, Signet   | `m` or `n`       | `2`             |

    // P2PKH - Bitcoin
    // Valid for:
    // - Bitcoin
    // Not valid for:
    // - Testnet
    // - Regtest
    let bitcoin_mainnet_p2pkh_address_str = "1FfmbHfnpaZjKFvyi1okTjJJusN455paPH";
    let bitcoin_mainnet_p2pkh_address = Address::new(
        bitcoin_mainnet_p2pkh_address_str.to_string(),
        Network::Bitcoin,
    )
    .unwrap();
    assert!(
        bitcoin_mainnet_p2pkh_address.is_valid_for_network(Network::Bitcoin),
        "Address should be valid for Bitcoin"
    );
    assert!(
        !bitcoin_mainnet_p2pkh_address.is_valid_for_network(Network::Testnet),
        "Address should not be valid for Testnet"
    );
    assert!(
        !bitcoin_mainnet_p2pkh_address.is_valid_for_network(Network::Regtest),
        "Address should not be valid for Regtest"
    );

    // P2PKH - Testnet
    // Valid for:
    // - Testnet
    // - Regtest
    // Not valid for:
    // - Bitcoin
    let bitcoin_testnet_p2pkh_address_str = "mucFNhKMYoBQYUAEsrFVscQ1YaFQPekBpg";
    let bitcoin_testnet_p2pkh_address = Address::new(
        bitcoin_testnet_p2pkh_address_str.to_string(),
        Network::Testnet,
    )
    .unwrap();
    assert!(
        !bitcoin_testnet_p2pkh_address.is_valid_for_network(Network::Bitcoin),
        "Address should not be valid for Bitcoin"
    );
    assert!(
        bitcoin_testnet_p2pkh_address.is_valid_for_network(Network::Testnet),
        "Address should be valid for Testnet"
    );
    assert!(
        bitcoin_testnet_p2pkh_address.is_valid_for_network(Network::Regtest),
        "Address should be valid for Regtest"
    );

    // P2PKH - Regtest
    // Valid for:
    // - Testnet
    // - Regtest
    // Not valid for:
    // - Bitcoin
    let bitcoin_regtest_p2pkh_address_str = "msiGFK1PjCk8E6FXeoGkQPTscmcpyBdkgS";
    let bitcoin_regtest_p2pkh_address = Address::new(
        bitcoin_regtest_p2pkh_address_str.to_string(),
        Network::Regtest,
    )
    .unwrap();
    assert!(
        !bitcoin_regtest_p2pkh_address.is_valid_for_network(Network::Bitcoin),
        "Address should not be valid for Bitcoin"
    );
    assert!(
        bitcoin_regtest_p2pkh_address.is_valid_for_network(Network::Testnet),
        "Address should be valid for Testnet"
    );
    assert!(
        bitcoin_regtest_p2pkh_address.is_valid_for_network(Network::Regtest),
        "Address should be valid for Regtest"
    );

    // ====P2SH====

    //     | Network                            | Prefix for P2PKH | Prefix for P2SH |
    //     |------------------------------------|------------------|-----------------|
    //     | Bitcoin Mainnet                    | `1`              | `3`             |
    //     | Bitcoin Testnet, Regtest, Signet   | `m` or `n`       | `2`             |

    // P2SH - Bitcoin
    // Valid for:
    // - Bitcoin
    // Not valid for:
    // - Testnet
    // - Regtest
    let bitcoin_mainnet_p2sh_address_str = "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy";
    let bitcoin_mainnet_p2sh_address = Address::new(
        bitcoin_mainnet_p2sh_address_str.to_string(),
        Network::Bitcoin,
    )
    .unwrap();
    assert!(
        bitcoin_mainnet_p2sh_address.is_valid_for_network(Network::Bitcoin),
        "Address should be valid for Bitcoin"
    );
    assert!(
        !bitcoin_mainnet_p2sh_address.is_valid_for_network(Network::Testnet),
        "Address should not be valid for Testnet"
    );
    assert!(
        !bitcoin_mainnet_p2sh_address.is_valid_for_network(Network::Regtest),
        "Address should not be valid for Regtest"
    );

    // P2SH - Testnet
    // Valid for:
    // - Testnet
    // - Regtest
    // Not valid for:
    // - Bitcoin
    let bitcoin_testnet_p2sh_address_str = "2NFUBBRcTJbYc1D4HSCbJhKZp6YCV4PQFpQ";
    let bitcoin_testnet_p2sh_address = Address::new(
        bitcoin_testnet_p2sh_address_str.to_string(),
        Network::Testnet,
    )
    .unwrap();
    assert!(
        !bitcoin_testnet_p2sh_address.is_valid_for_network(Network::Bitcoin),
        "Address should not be valid for Bitcoin"
    );
    assert!(
        bitcoin_testnet_p2sh_address.is_valid_for_network(Network::Testnet),
        "Address should be valid for Testnet"
    );
    assert!(
        bitcoin_testnet_p2sh_address.is_valid_for_network(Network::Regtest),
        "Address should be valid for Regtest"
    );

    // P2SH - Regtest
    // Valid for:
    // - Testnet
    // - Regtest
    // Not valid for:
    // - Bitcoin
    let bitcoin_regtest_p2sh_address_str = "2NEb8N5B9jhPUCBchz16BB7bkJk8VCZQjf3";
    let bitcoin_regtest_p2sh_address = Address::new(
        bitcoin_regtest_p2sh_address_str.to_string(),
        Network::Regtest,
    )
    .unwrap();
    assert!(
        !bitcoin_regtest_p2sh_address.is_valid_for_network(Network::Bitcoin),
        "Address should not be valid for Bitcoin"
    );
    assert!(
        bitcoin_regtest_p2sh_address.is_valid_for_network(Network::Testnet),
        "Address should be valid for Testnet"
    );
    assert!(
        bitcoin_regtest_p2sh_address.is_valid_for_network(Network::Regtest),
        "Address should be valid for Regtest"
    );
}

#[test]
fn test_to_address_data() {
    // P2PKH address
    let p2pkh = Address::new(
        "1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2".to_string(),
        Network::Bitcoin,
    )
    .unwrap();
    let p2pkh_data = p2pkh.to_address_data();
    println!("P2PKH data: {:#?}", p2pkh_data);

    // P2SH address
    let p2sh = Address::new(
        "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy".to_string(),
        Network::Bitcoin,
    )
    .unwrap();
    let p2sh_data = p2sh.to_address_data();
    println!("P2SH data: {:#?}", p2sh_data);

    // Segwit address (P2WPKH)
    let segwit = Address::new(
        "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4".to_string(),
        Network::Bitcoin,
    )
    .unwrap();
    let segwit_data = segwit.to_address_data();
    println!("Segwit data: {:#?}", segwit_data);
}

#[test]
fn test_psbt_spend_utxo() {
    let psbt = sample_psbt();
    let psbt_utxo = psbt.spend_utxo(0);

    println!("Psbt utxo: {:?}", psbt_utxo);

    assert_eq!(
        psbt_utxo,
        r#"{"value":9536,"script_pubkey":"0020432ae79fce8bf43def0e21fde7d2896cfb9d0c773f6e9ea723d1391012d00f56"}"#,
        "Psbt utxo does not match the expected value"
    );
}

#[test]
fn test_psbt_input_length() {
    let psbt = sample_psbt2();
    let psbt_inputs = psbt.input();
    println!("Psbt Input: {:?}", psbt_inputs);

    let unknown = &psbt_inputs[0].unknown;
    println!("Unknown field in Psbt Input: {:?}", unknown);
    assert_eq!(psbt_inputs.len(), 1);
}

#[test]
fn test_psbt_input_partial_sigs() {
    let psbt = sample_psbt();
    let psbt_inputs = psbt.input();
    println!("Psbt Input: {:?}", psbt_inputs);
    assert_eq!(psbt_inputs.len(), 1);

    let psbt_input = &psbt_inputs[0];

    let partial_sigs = &psbt_input.partial_sigs;
    let signatures_count = partial_sigs.len();
    assert_eq!(signatures_count, 3, "There should be 3 partial signatures");

    let public_key = "032e15f9dfd07dfef5cf33d7c4ae05586e71fb02ff2aef77cbf2c6e69fb4ac8db1";
    let signature: Option<Vec<u8>> = partial_sigs.get(public_key).cloned();
    println!("Signature for pubkey {}: {:?}", public_key, signature);
    assert!(
        signature.is_some(),
        "Signature for the given pubkey should be present"
    );

    // Convert signature from bytes to hex string (Including last byte 0x01 for sighash type)
    let signature_hex = DisplayHex::to_lower_hex_string(&signature.unwrap());
    println!("Signature in hex: {}", signature_hex);

    let expected_signature_hex = "3045022100f5bd0e740480b343f6ba14229e337b4063f98d7fdbdf62dc4a10669f2f14d1150220179bc6f0836e97210a3a8b02138c8f2753f340c0ce891a6a51b357b5d54ec6e201";
    assert_eq!(
        signature_hex, expected_signature_hex,
        "Signature hex does not match the expected value"
    );
}

#[test]
fn test_psbt_input_bip32_derivation() {
    let psbt = sample_psbt();
    let psbt_inputs = psbt.input();
    assert_eq!(psbt_inputs.len(), 1);

    let psbt_input = &psbt_inputs[0];
    let bip32_derivations = &psbt_input.bip32_derivation;
    let derivations_count = bip32_derivations.len();
    assert_eq!(derivations_count, 3, "There should be 3 BIP32 derivations");

    let public_key = "032e15f9dfd07dfef5cf33d7c4ae05586e71fb02ff2aef77cbf2c6e69fb4ac8db1";
    let derivation = bip32_derivations.get(public_key);
    println!(
        "BIP32 Derivation for pubkey {}: {:?}",
        public_key, derivation
    );
    assert!(
        derivation.is_some(),
        "BIP32 derivation for the given pubkey should be present"
    );

    let fingerprint = &derivation.unwrap().fingerprint;
    let expected_fingerprint = "09aa4113";
    assert_eq!(
        fingerprint, &expected_fingerprint,
        "Fingerprint does not match the expected value"
    );

    let derivation_path = &derivation.unwrap().path.to_string();
    let expected_derivation_path = "84'/1'/0'/0/0";
    assert_eq!(
        derivation_path, &expected_derivation_path,
        "Derivation path does not match the expected value"
    );
}

#[test]
fn test_psbt_input_witness_script() {
    let psbt = sample_psbt();
    let psbt_inputs = psbt.input();
    assert_eq!(psbt_inputs.len(), 1);

    let psbt_input = &psbt_inputs[0];
    let witness_script = psbt_input
        .witness_script
        .as_ref()
        .expect("Witness script should be present");
    let byte_witness = witness_script.to_bytes();
    let witness_hex = DisplayHex::to_lower_hex_string(&byte_witness);
    let expected_witness_hex = "5221035b4c63ee3a120c481d4d7c6e01e752f00094090cab92dfc24a4e199f7bb0329121032e15f9dfd07dfef5cf33d7c4ae05586e71fb02ff2aef77cbf2c6e69fb4ac8db12103f9a0b32bd9dc97085b6cdeca6c854651018b599b2371aa963f193d805786f85c53ae";
    assert_eq!(
        witness_hex, expected_witness_hex,
        "Witness script hex does not match the expected value"
    );
}

#[test]
fn test_psbt_input_unknown() {
    let psbt = sample_psbt2();
    let psbt_inputs = psbt.input();
    assert_eq!(psbt_inputs.len(), 1);

    let psbt_input = &psbt_inputs[0];
    let unknown = &psbt_input.unknown;
    println!("Unknown : {:?}", unknown);

    assert_eq!(unknown.len(), 1, "There should be 1 unknown fields");

    let key = unknown.keys().next().unwrap();
    let value = unknown.get(key).unwrap();
    let expected_key = Key {
        type_value: 9,
        key: vec![0, 1],
    };
    let expected_value_hex = "030405";

    let value_hex = DisplayHex::to_lower_hex_string(value);
    assert_eq!(key, &expected_key, "Unknown key hex does not match");
    assert_eq!(
        value_hex, expected_value_hex,
        "Unknown value hex does not match"
    );
}

#[test]
fn test_psbt_input_proprietary() {
    let psbt = sample_psbt2();
    let psbt_inputs = psbt.input();
    assert_eq!(psbt_inputs.len(), 1);

    let psbt_input = &psbt_inputs[0];
    let proprietary = &psbt_input.proprietary;
    println!("Proprietary : {:?}", proprietary);
    assert_eq!(proprietary.len(), 1, "There should be 1 proprietary fields");
    let key = proprietary.keys().next().unwrap();
    let value = proprietary.get(key).unwrap();
    let expected_key = ProprietaryKey {
        prefix: "prefx".as_bytes().to_vec(),
        subtype: 42,
        key: "test_key".as_bytes().to_vec(),
    };
    let expected_value_hex = "050607";
    let value_hex = DisplayHex::to_lower_hex_string(value);
    assert_eq!(key, &expected_key, "Proprietary key does not match");
    assert_eq!(
        value_hex, expected_value_hex,
        "Proprietary value hex does not match"
    );
}

fn sample_psbt() -> Psbt {
    Psbt::new("cHNidP8BAH0CAAAAAXHl8cCbj84lm1v42e54IGI6CQru/nBXwrPE3q2fiGO4AAAAAAD9////Ar4DAAAAAAAAIgAgYw/rnGd4Bifj8s7TaMgR2tal/lq+L1jVv2Sqd1mxMbJEEQAAAAAAABYAFNVpt8vHYUPZNSF6Hu07uP1YeHts4QsAAAABALUCAAAAAAEBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD/////BAJ+CwD/////AkAlAAAAAAAAIgAgQyrnn86L9D3vDiH959KJbPudDHc/bp6nI9E5EBLQD1YAAAAAAAAAACZqJKohqe3i9hw/cdHe/T+pmd+jaVN1XGkGiXmZYrSL69g2l06M+QEgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQErQCUAAAAAAAAiACBDKuefzov0Pe8OIf3n0ols+50Mdz9unqcj0TkQEtAPViICAy4V+d/Qff71zzPXxK4FWG5x+wL/Ku93y/LG5p+0rI2xSDBFAiEA9b0OdASAs0P2uhQinjN7QGP5jX/b32LcShBmny8U0RUCIBebxvCDbpchCjqLAhOMjydT80DAzokaalGzV7XVTsbiASICA1tMY+46EgxIHU18bgHnUvAAlAkMq5LfwkpOGZ97sDKRRzBEAiBpmlZwJocNEiKLxexEX0Par6UgG8a89AklTG3/z9AHlAIgQH/ybCvfKJzr2dq0+IyueDebm7FamKIJdzBYWMXRr/wBIgID+aCzK9nclwhbbN7KbIVGUQGLWZsjcaqWPxk9gFeG+FxIMEUCIQDRPBzb0i9vaUmxCcs1yz8uq4tq1mdDAYvvYn3isKEhFAIgfmeTLLzMo0mmQ23ooMnyx6iPceE8xV5CvARuJsd88tEBAQVpUiEDW0xj7joSDEgdTXxuAedS8ACUCQyrkt/CSk4Zn3uwMpEhAy4V+d/Qff71zzPXxK4FWG5x+wL/Ku93y/LG5p+0rI2xIQP5oLMr2dyXCFts3spshUZRAYtZmyNxqpY/GT2AV4b4XFOuIgYDLhX539B9/vXPM9fErgVYbnH7Av8q73fL8sbmn7SsjbEYCapBE1QAAIABAACAAAAAgAAAAAAAAAAAIgYDW0xj7joSDEgdTXxuAedS8ACUCQyrkt/CSk4Zn3uwMpEY2bvrelQAAIABAACAAAAAgAAAAAAAAAAAIgYD+aCzK9nclwhbbN7KbIVGUQGLWZsjcaqWPxk9gFeG+FwYAKVFVFQAAIABAACAAAAAgAAAAAAAAAAAAAEBaVIhA7cr8fTHOPtE+t0zM3iWJvpfPvsNaVyQ0Sar6nIe9tQXIQMm7k7OY+q+Lsge3bVACuSa9r19Js+lNuTtEhehWkpe1iECelHmzmhzDsQTDnApIcnWRz3oFR68UX1ag8jfk/SKuopTriICAnpR5s5ocw7EEw5wKSHJ1kc96BUevFF9WoPI35P0irqKGAClRVRUAACAAQAAgAAAAIABAAAAAAAAACICAybuTs5j6r4uyB7dtUAK5Jr2vX0mz6U25O0SF6FaSl7WGAmqQRNUAACAAQAAgAAAAIABAAAAAAAAACICA7cr8fTHOPtE+t0zM3iWJvpfPvsNaVyQ0Sar6nIe9tQXGNm763pUAACAAQAAgAAAAIABAAAAAAAAAAAA".to_string())
        .unwrap()
}

fn sample_psbt2() -> Psbt {
    Psbt::new("cHNidP8BAFMBAAAAATkUkZZWjQ4TAMqaOkez2dl2+5yBsfd38qS6x8fkjesmAQAAAAD/////AXL++E4sAAAAF6kUM5cluiHv1irHU6m80GfWx6ajnQWHAAAAAE8BBIiyHgAAAAAAAAAAAIc9/4HAL1JWI/0f5RZ+rDpVoEnePTFLtC7iJ//tN9UIAzmjYBMwFZfa70H75ZOgLMUT0LVVJ+wt8QUOLo/0nIXCDN6tvu8AAACAAQAAABD8BXByZWZ4KnRlc3Rfa2V5AwUGBwMJAAEDAwQFAAEAjwEAAAAAAQGJo8ceq00g4Dcbu6TMaY+ilclGOvouOX+FM8y2L5Vn5QEAAAAXFgAUvhjRUqmwEgOdrz2n3k9TNJ7suYX/////AXL++E4sAAAAF6kUM5cluiHv1irHU6m80GfWx6ajnQWHASED0uFWdJQbrUqZY3LLh+GFbTZSYG2YVi/jnF6efkE/IQUAAAAAAQEgcv74TiwAAAAXqRQzlyW6Ie/WKsdTqbzQZ9bHpqOdBYciAgM5iA3JI5S3NV49BDn6KDwx3nWQgS6gEcQkXAZ0poXog0cwRAIgT2fir7dhQtRPrliiSV0zo0GdqibNDbjQTzRStjKJrA8CIBB2Kp+2fpTMXK2QJvbcmf9/Bw9CeNMPvH0Mhp3TjH/nAQEDBIMAAAABBAFRIgYDOYgNySOUtzVePQQ5+ig8Md51kIEuoBHEJFwGdKaF6IMM3q2+7wAAAIABAAAAAQgGAgIBAwEFFQoYn3yLGjhv/o7tkbODDHp7zR53jAIBAiELoShx/uIQ+4YZKR6uoZRYHL0lMeSyN1nSJfaAaSP2MiICAQIVDBXMSeGRy8Ug2RlEYApct3r2qjKRAgECIQ12pWrO2RXSUT3NhMLDeLLoqlzWMrW3HKLyrFsOOmSb2wIBAhD8BXByZWZ4KnRlc3Rfa2V5AwUGBwMJAAEDAwQFACICAzmIDckjlLc1Xj0EOfooPDHedZCBLqARxCRcBnSmheiDDN6tvu8AAACAAQAAABD8BXByZWZ4KnRlc3Rfa2V5AwUGBwMJAAEDAwQFAA==".to_string())
        .unwrap()
}
