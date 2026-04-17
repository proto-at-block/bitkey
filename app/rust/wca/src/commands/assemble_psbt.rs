use bitcoin::{
    ecdsa::Signature as EcdsaSig,
    psbt::Psbt as PartiallySignedTransaction,
    secp256k1::{PublicKey, Secp256k1},
};
use miniscript::psbt::PsbtExt;

use super::sign_tx_request::InputSignatureTuple;
use crate::errors::CommandError;

/// Inserts hardware-produced signatures into a PSBT and finalizes it.
///
/// For each `InputSignatureTuple`, inserts the signature as a `partial_sig`
/// on the corresponding PSBT input. Then finalizes the PSBT (converting
/// `partial_sigs` → `final_script_witness`) so the transaction can be
/// extracted and broadcast.
///
/// The PSBT passed in should already contain the app key's `partial_sig`
/// (from BDK `wallet.sign()`). After inserting the hardware key's signatures,
/// finalization is attempted via `miniscript::psbt::PsbtExt::finalize_mut`,
/// which succeeds when the script's satisfaction policy is met (e.g. 2-of-3
/// multisig in Bitkey's descriptor).
///
/// # Arguments
/// * `psbt_base64` - Base64-encoded PSBT string (the app-signed PSBT)
/// * `signatures` - Per-input signatures from the hardware (`sign_tx_response`)
///
/// # Returns
/// The finalized PSBT as a base64 string, ready for `extractTx()` + broadcast.
///
/// # Errors
/// Returns `CommandError::InvalidArguments` if the PSBT cannot be parsed,
/// a signature is malformed, or an input_index is out of bounds.
/// Returns `CommandError::InvalidResponse` if PSBT finalization fails
/// (e.g. insufficient signatures).
pub fn assemble_psbt_signatures(
    psbt_base64: String,
    signatures: Vec<InputSignatureTuple>,
) -> Result<String, CommandError> {
    let mut psbt: PartiallySignedTransaction = psbt_base64
        .parse()
        .map_err(|_| CommandError::InvalidArguments)?;

    for sig_tuple in &signatures {
        let input_index = sig_tuple.input_index as usize;
        if input_index >= psbt.inputs.len() {
            return Err(CommandError::InvalidArguments);
        }

        let public_key = PublicKey::from_slice(&sig_tuple.public_key)
            .map_err(|_| CommandError::InvalidArguments)?;

        // The hardware returns DER-encoded signature + sighash type byte.
        let ecdsa_sig = EcdsaSig::from_slice(&sig_tuple.signature)
            .map_err(|_| CommandError::InvalidArguments)?;

        let input = &mut psbt.inputs[input_index];
        input
            .partial_sigs
            .insert(bitcoin::PublicKey::new(public_key), ecdsa_sig);
    }

    // Finalize the PSBT: convert partial_sigs into final_script_witness.
    // This requires 2-of-3 partial_sigs to be present (app key + hardware key).
    psbt.finalize_mut(&Secp256k1::verification_only())
        .map_err(|_| CommandError::InvalidResponse)?;

    Ok(psbt.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    use bitcoin::{
        bip32::{ChildNumber, DerivationPath, Fingerprint},
        hashes::Hash,
        psbt::Input as PsbtInput,
        psbt::Psbt,
        secp256k1::{Secp256k1, SecretKey},
        sighash::{EcdsaSighashType, SighashCache},
        Amount, ScriptBuf, Sequence, Transaction, TxIn, TxOut, Txid, Witness,
    };
    use std::collections::BTreeMap;

    fn make_signable_psbt() -> (String, PublicKey, Vec<u8>) {
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
        let witness_utxo_script =
            ScriptBuf::new_p2wpkh(&bitcoin::PublicKey::new(public_key).wpubkey_hash().unwrap());

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
                script_pubkey: witness_utxo_script.clone(),
            }],
        };

        let mut input_bip32 = BTreeMap::new();
        input_bip32.insert(public_key, (fingerprint, derivation_path));

        let psbt = Psbt {
            unsigned_tx: unsigned_tx.clone(),
            version: 0,
            xpub: Default::default(),
            proprietary: Default::default(),
            unknown: Default::default(),
            inputs: vec![PsbtInput {
                witness_utxo: Some(TxOut {
                    value: Amount::from_sat(100_000),
                    script_pubkey: witness_utxo_script,
                }),
                bip32_derivation: input_bip32,
                ..Default::default()
            }],
            outputs: vec![Default::default()],
        };

        // Compute sighash and sign it to get a valid DER signature
        let mut cache = SighashCache::new(&unsigned_tx);
        let sighash = cache
            .p2wpkh_signature_hash(
                0,
                &psbt.inputs[0].witness_utxo.as_ref().unwrap().script_pubkey,
                psbt.inputs[0].witness_utxo.as_ref().unwrap().value,
                EcdsaSighashType::All,
            )
            .unwrap();

        let msg = bitcoin::secp256k1::Message::from_digest(sighash.to_byte_array());
        let sig = secp.sign_ecdsa(&msg, &secret_key);

        // Build DER + sighash type byte
        let mut der_sig = sig.serialize_der().to_vec();
        der_sig.push(EcdsaSighashType::All.to_u32() as u8);

        let base64 = psbt.to_string();
        (base64, public_key, der_sig)
    }

    #[test]
    fn assemble_inserts_signature_into_psbt() {
        let (base64, public_key, der_sig) = make_signable_psbt();

        let signatures = vec![InputSignatureTuple {
            input_index: 0,
            public_key: public_key.serialize().to_vec(),
            signature: der_sig,
        }];

        let result = assemble_psbt_signatures(base64, signatures).unwrap();

        // After finalization, partial_sigs are cleared and final_script_witness is set.
        let psbt: Psbt = result.parse().unwrap();
        assert!(psbt.inputs[0].final_script_witness.is_some());
    }

    #[test]
    fn assemble_rejects_invalid_input_index() {
        let (base64, public_key, der_sig) = make_signable_psbt();

        let signatures = vec![InputSignatureTuple {
            input_index: 99, // out of bounds
            public_key: public_key.serialize().to_vec(),
            signature: der_sig,
        }];

        let result = assemble_psbt_signatures(base64, signatures);
        assert!(result.is_err());
    }

    #[test]
    fn assemble_rejects_invalid_psbt() {
        let result = assemble_psbt_signatures("not-a-psbt".to_string(), vec![]);
        assert!(result.is_err());
    }

    #[test]
    fn assemble_with_empty_signatures_fails_finalization() {
        let (base64, _, _) = make_signable_psbt();
        // Finalization requires signatures, so empty list should fail.
        let result = assemble_psbt_signatures(base64, vec![]);
        assert!(result.is_err());
    }
}
