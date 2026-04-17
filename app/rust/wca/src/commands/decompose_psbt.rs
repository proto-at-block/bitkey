use bitcoin::{
    bip32::Fingerprint, consensus::encode::serialize, psbt::Psbt as PartiallySignedTransaction,
};

use super::sign_tx_request::{SignTxInputData, SignTxOutputData};
use crate::errors::CommandError;

/// Decomposed PSBT data ready for the non-PSBT signing protocol.
///
/// Contains all fields needed by `sign_tx_request_cmd`, extracted from a
/// standard PSBT. The hardware uses these to reconstruct the transaction,
/// display it for user confirmation, and compute sighashes.
#[derive(Debug, Clone)]
pub struct DecomposedPsbt {
    pub version: u32,
    pub lock_time: u32,
    pub inputs: Vec<SignTxInputData>,
    pub outputs: Vec<SignTxOutputData>,
}

/// Decomposes a base64-encoded PSBT into the raw transaction fields needed
/// by `sign_tx_request_cmd`.
///
/// For each input, extracts:
/// - Previous outpoint (txid bytes + vout index)
/// - Sequence number
/// - UTXO amount from witness_utxo (required for segwit sighash computation)
/// - BIP32 derivation path matching the hardware fingerprint
///
/// For each output, extracts:
/// - Amount
/// - scriptPubKey bytes
/// - BIP32 derivation path matching the hardware fingerprint (for change outputs)
///
/// # Arguments
/// * `psbt_base64` - Base64-encoded PSBT string
/// * `origin_fingerprint` - 4-byte hardware key origin fingerprint (hex string, e.g. "96ae1927")
///
/// # Errors
/// Returns `CommandError::InvalidArguments` if the PSBT cannot be parsed or
/// is missing required data (e.g., witness_utxo for an input).
pub fn decompose_psbt(
    psbt_base64: String,
    origin_fingerprint: String,
) -> Result<DecomposedPsbt, CommandError> {
    let psbt: PartiallySignedTransaction = psbt_base64
        .parse()
        .map_err(|_| CommandError::InvalidArguments)?;

    let fingerprint: Fingerprint = origin_fingerprint
        .parse()
        .map_err(|_| CommandError::InvalidArguments)?;

    let version = psbt.unsigned_tx.version.0 as u32;
    let lock_time = psbt.unsigned_tx.lock_time.to_consensus_u32();

    let mut inputs = Vec::with_capacity(psbt.unsigned_tx.input.len());
    for (i, tx_in) in psbt.unsigned_tx.input.iter().enumerate() {
        let psbt_input = psbt.inputs.get(i).ok_or(CommandError::InvalidArguments)?;

        // Get UTXO amount from witness_utxo (required for segwit sighash).
        let amount = psbt_input
            .witness_utxo
            .as_ref()
            .ok_or(CommandError::InvalidArguments)?
            .value
            .to_sat();

        // Find the derivation path matching the hardware fingerprint.
        // Each input must have a matching path so the firmware can derive the
        // signing key; reject the PSBT if it's missing or mismatched.
        // The firmware proto caps derivation_path at 5 elements (BIP-84/86).
        let derivation_path = psbt_input
            .bip32_derivation
            .iter()
            .find(|(_, (fp, _))| *fp == fingerprint)
            .map(|(_, (_, path))| {
                path.into_iter()
                    .map(|child| u32::from(*child))
                    .collect::<Vec<u32>>()
            })
            .ok_or(CommandError::InvalidArguments)?;

        if derivation_path.len() > 5 {
            return Err(CommandError::InvalidArguments);
        }

        // Txid bytes in consensus serialization order (as they appear in
        // serialized transactions, needed for BIP143 sighash computation).
        let prev_txid = serialize(&tx_in.previous_output.txid);

        inputs.push(SignTxInputData {
            prev_txid,
            prev_index: tx_in.previous_output.vout,
            sequence: tx_in.sequence.0,
            amount,
            derivation_path,
        });
    }

    let mut outputs = Vec::with_capacity(psbt.unsigned_tx.output.len());
    for (i, tx_out) in psbt.unsigned_tx.output.iter().enumerate() {
        // Check if this output has a derivation path matching our HW fingerprint
        // (i.e., it's a change output back to our wallet).
        let derivation_path = psbt
            .outputs
            .get(i)
            .and_then(|psbt_output| {
                psbt_output
                    .bip32_derivation
                    .iter()
                    .find(|(_, (fp, _))| *fp == fingerprint)
                    .map(|(_, (_, path))| {
                        path.into_iter()
                            .map(|child| u32::from(*child))
                            .collect::<Vec<u32>>()
                    })
            })
            .unwrap_or_default();

        let has_derivation_path = !derivation_path.is_empty();

        // Enforce firmware proto limits: destination_spk ≤ 35 bytes,
        // derivation_path ≤ 5 elements.
        let destination_spk = tx_out.script_pubkey.to_bytes();
        if destination_spk.len() > 35 {
            return Err(CommandError::InvalidArguments);
        }
        if derivation_path.len() > 5 {
            return Err(CommandError::InvalidArguments);
        }

        outputs.push(SignTxOutputData {
            amount: tx_out.value.to_sat(),
            destination_spk,
            derivation_path,
            has_derivation_path,
        });
    }

    Ok(DecomposedPsbt {
        version,
        lock_time,
        inputs,
        outputs,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use bitcoin::{
        bip32::{ChildNumber, DerivationPath, Fingerprint},
        hashes::Hash,
        psbt::Input as PsbtInput,
        psbt::Output as PsbtOutput,
        psbt::Psbt,
        secp256k1::{PublicKey, Secp256k1, SecretKey},
        Amount, ScriptBuf, Sequence, Transaction, TxIn, TxOut, Txid, Witness,
    };
    use std::collections::BTreeMap;

    fn make_test_psbt() -> (String, String) {
        let secp = Secp256k1::new();
        let secret_key = SecretKey::from_slice(&[0x01; 32]).expect("32 bytes, within curve order");
        let public_key = PublicKey::from_secret_key(&secp, &secret_key);

        let fingerprint = Fingerprint::from([0x96, 0xae, 0x19, 0x27]);
        let derivation_path = DerivationPath::from(vec![
            ChildNumber::from_hardened_idx(84).unwrap(),
            ChildNumber::from_hardened_idx(0).unwrap(),
            ChildNumber::from_hardened_idx(0).unwrap(),
            ChildNumber::from_normal_idx(0).unwrap(),
            ChildNumber::from_normal_idx(7).unwrap(),
        ]);

        let prev_txid = Txid::from_slice(&[0xab; 32]).unwrap();
        let unsigned_tx = Transaction {
            version: bitcoin::transaction::Version::TWO,
            lock_time: bitcoin::blockdata::locktime::absolute::LockTime::from_consensus(800_000),
            input: vec![TxIn {
                previous_output: bitcoin::OutPoint {
                    txid: prev_txid,
                    vout: 1,
                },
                script_sig: ScriptBuf::new(),
                sequence: Sequence(0xFFFFFFFD),
                witness: Witness::default(),
            }],
            output: vec![
                // Destination output (no derivation path)
                TxOut {
                    value: Amount::from_sat(90_000),
                    script_pubkey: ScriptBuf::from(vec![
                        0x00, 0x14, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
                        0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
                    ]),
                },
                // Change output (with derivation path)
                TxOut {
                    value: Amount::from_sat(9_000),
                    script_pubkey: ScriptBuf::from(vec![
                        0x00, 0x14, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb,
                        0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb,
                    ]),
                },
            ],
        };

        let mut input_bip32 = BTreeMap::new();
        input_bip32.insert(public_key, (fingerprint, derivation_path));

        let change_path = DerivationPath::from(vec![
            ChildNumber::from_hardened_idx(84).unwrap(),
            ChildNumber::from_hardened_idx(0).unwrap(),
            ChildNumber::from_hardened_idx(0).unwrap(),
            ChildNumber::from_normal_idx(1).unwrap(),
            ChildNumber::from_normal_idx(0).unwrap(),
        ]);
        let mut output_bip32 = BTreeMap::new();
        output_bip32.insert(public_key, (fingerprint, change_path));

        let psbt = Psbt {
            unsigned_tx,
            version: 0,
            xpub: Default::default(),
            proprietary: Default::default(),
            unknown: Default::default(),
            inputs: vec![PsbtInput {
                witness_utxo: Some(TxOut {
                    value: Amount::from_sat(100_000),
                    script_pubkey: ScriptBuf::from(vec![
                        0x00, 0x14, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc,
                        0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc,
                    ]),
                }),
                bip32_derivation: input_bip32,
                ..Default::default()
            }],
            outputs: vec![
                PsbtOutput::default(),
                PsbtOutput {
                    bip32_derivation: output_bip32,
                    ..Default::default()
                },
            ],
        };

        let base64 = psbt.to_string();
        let fp_hex = "96ae1927".to_string();
        (base64, fp_hex)
    }

    #[test]
    fn decompose_psbt_extracts_lock_time() {
        let (base64, fp) = make_test_psbt();
        let result = decompose_psbt(base64, fp).unwrap();
        assert_eq!(result.lock_time, 800_000);
    }

    #[test]
    fn decompose_psbt_extracts_input_fields() {
        let (base64, fp) = make_test_psbt();
        let result = decompose_psbt(base64, fp).unwrap();
        assert_eq!(result.inputs.len(), 1);

        let input = &result.inputs[0];
        assert_eq!(input.prev_txid.len(), 32);
        assert_eq!(input.prev_index, 1);
        assert_eq!(input.sequence, 0xFFFFFFFD);
        assert_eq!(input.amount, 100_000);
        assert_eq!(input.derivation_path.len(), 5);
        // 84' = 84 | 0x80000000
        assert_eq!(input.derivation_path[0], 84 | (1 << 31));
    }

    #[test]
    fn decompose_psbt_extracts_output_fields() {
        let (base64, fp) = make_test_psbt();
        let result = decompose_psbt(base64, fp).unwrap();
        assert_eq!(result.outputs.len(), 2);

        // Destination output: no derivation path
        let dest = &result.outputs[0];
        assert_eq!(dest.amount, 90_000);
        assert!(!dest.has_derivation_path);
        assert!(dest.derivation_path.is_empty());

        // Change output: has derivation path
        let change = &result.outputs[1];
        assert_eq!(change.amount, 9_000);
        assert!(change.has_derivation_path);
        assert_eq!(change.derivation_path.len(), 5);
    }

    #[test]
    fn decompose_psbt_rejects_invalid_base64() {
        let result = decompose_psbt("not-valid-base64".to_string(), "96ae1927".to_string());
        assert!(result.is_err());
    }

    #[test]
    fn decompose_psbt_rejects_invalid_fingerprint() {
        let (base64, _) = make_test_psbt();
        let result = decompose_psbt(base64, "not-hex".to_string());
        assert!(result.is_err());
    }

    #[test]
    fn decompose_psbt_rejects_missing_witness_utxo() {
        let secp = Secp256k1::new();
        let secret_key = SecretKey::from_slice(&[0x01; 32]).expect("32 bytes, within curve order");
        let public_key = PublicKey::from_secret_key(&secp, &secret_key);

        let fingerprint = Fingerprint::from([0x96, 0xae, 0x19, 0x27]);
        let derivation_path = DerivationPath::from(vec![
            ChildNumber::from_hardened_idx(84).unwrap(),
            ChildNumber::from_hardened_idx(0).unwrap(),
            ChildNumber::from_hardened_idx(0).unwrap(),
            ChildNumber::from_normal_idx(0).unwrap(),
            ChildNumber::from_normal_idx(0).unwrap(),
        ]);

        let prev_txid = Txid::from_slice(&[0xab; 32]).unwrap();
        let unsigned_tx = Transaction {
            version: bitcoin::transaction::Version::TWO,
            lock_time: bitcoin::blockdata::locktime::absolute::LockTime::ZERO,
            input: vec![TxIn {
                previous_output: bitcoin::OutPoint {
                    txid: prev_txid,
                    vout: 0,
                },
                script_sig: ScriptBuf::new(),
                sequence: Sequence(0xFFFFFFFD),
                witness: Witness::default(),
            }],
            output: vec![TxOut {
                value: Amount::from_sat(90_000),
                script_pubkey: ScriptBuf::from(vec![
                    0x00, 0x14, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
                    0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
                ]),
            }],
        };

        let mut input_bip32 = BTreeMap::new();
        input_bip32.insert(public_key, (fingerprint, derivation_path));

        let psbt = Psbt {
            unsigned_tx,
            version: 0,
            xpub: Default::default(),
            proprietary: Default::default(),
            unknown: Default::default(),
            inputs: vec![PsbtInput {
                witness_utxo: None, // Missing witness_utxo
                bip32_derivation: input_bip32,
                ..Default::default()
            }],
            outputs: vec![PsbtOutput::default()],
        };

        let result = decompose_psbt(psbt.to_string(), "96ae1927".to_string());
        assert!(result.is_err());
    }

    #[test]
    fn decompose_psbt_rejects_missing_input_derivation_path() {
        let (base64, _) = make_test_psbt();
        // Use a fingerprint that doesn't match any derivation in the PSBT.
        let result = decompose_psbt(base64, "deadbeef".to_string());
        assert!(result.is_err());
    }
}
