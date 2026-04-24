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
/// on the corresponding PSBT input. Then attempts to finalize the PSBT
/// (converting `partial_sigs` → `final_script_witness`) so the transaction
/// can be extracted and broadcast.
///
/// # Finalization behavior
///
/// Whether finalization failure is surfaced depends on `allow_unfinalized`:
///
/// - `allow_unfinalized = false` (regular sends): the caller has already
///   co-signed via BDK, so HW's signatures should complete the 2-of-3
///   multisig. A finalization error here indicates a real problem
///   (malformed signature, keypath mismatch, witness-script disagreement)
///   and is surfaced to the caller so the user doesn't silently end up
///   with a half-signed PSBT that fails at broadcast time.
/// - `allow_unfinalized = true` (sweep flows): HW signs first, then the
///   app + server apply their signatures later (see
///   `SweepDataStateMachineImpl`). With only HW's sig present, finalization
///   is expected to fail; the partial_sigs are preserved for downstream
///   combiners.
///
/// # Arguments
/// * `psbt_base64` - Base64-encoded PSBT string
/// * `signatures` - Per-input signatures from the hardware (`sign_tx_response`)
/// * `allow_unfinalized` - See finalization behavior above
///
/// # Errors
/// Returns `CommandError::InvalidArguments` if the PSBT cannot be parsed,
/// a signature is malformed, or an input_index is out of bounds.
/// Returns `CommandError::InvalidResponse` when `allow_unfinalized` is
/// false and finalization fails.
pub fn assemble_psbt_signatures(
    psbt_base64: String,
    signatures: Vec<InputSignatureTuple>,
    allow_unfinalized: bool,
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

    let finalize_result = psbt.finalize_mut(&Secp256k1::verification_only());
    if !allow_unfinalized {
        finalize_result.map_err(|_| CommandError::InvalidResponse)?;
    }

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

        let result = assemble_psbt_signatures(base64, signatures, false).unwrap();

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

        let result = assemble_psbt_signatures(base64, signatures, false);
        assert!(result.is_err());
    }

    #[test]
    fn assemble_rejects_invalid_psbt() {
        let result = assemble_psbt_signatures("not-a-psbt".to_string(), vec![], false);
        assert!(result.is_err());
    }

    #[test]
    fn assemble_surfaces_finalization_failure_on_regular_sends() {
        // Regular-send path (`allow_unfinalized = false`) must surface a
        // finalization failure rather than silently returning a half-signed
        // PSBT. With no HW signatures supplied, finalize_mut fails and the
        // caller should see the error.
        let (base64, _, _) = make_signable_psbt();
        let result = assemble_psbt_signatures(base64, vec![], false);
        assert!(matches!(result, Err(CommandError::InvalidResponse)));
    }

    #[test]
    fn assemble_swallows_finalization_failure_on_sweeps() {
        // Sweep path (`allow_unfinalized = true`): HW signs first, then the
        // app + server apply their sigs later. The PSBT must round-trip with
        // partial_sigs preserved so downstream combiners can finalize.
        let (base64, _, _) = make_signable_psbt();
        let result = assemble_psbt_signatures(base64, vec![], true).unwrap();
        let psbt: Psbt = result.parse().unwrap();
        assert!(psbt.inputs[0].final_script_witness.is_none());
        assert!(psbt.inputs[0].partial_sigs.is_empty());
    }
}
