use crate::psbt_verification::verify_inputs_only_have_one_signature;
use anyhow::Result;
use bdk::bitcoin::psbt::Input;
use bdk::bitcoin::{
    hashes::{sha256, Hash, HashEngine},
    psbt::PartiallySignedTransaction,
    secp256k1::{Message, Secp256k1, SecretKey},
};
use wsm_common::bitcoin::secp256k1::PublicKey;
use wsm_common::messages::api::TransactionVerificationApproval;

/// Calculate a SHA256 hash of lexicographically sorted and concatenated PSBT input signatures
fn calculate_chained_sighashes(inputs: Vec<Input>) -> Result<sha256::Hash> {
    // Verify that every input has exactly one signature
    // This enforces our requirement and simplifies signature extraction
    verify_inputs_only_have_one_signature(&inputs)?;

    // Extract all signature bytes (one from each input)
    let mut signatures: Vec<Vec<u8>> = inputs
        .iter()
        .map(|input| {
            // We verified above that each input has exactly one signature
            let (_, signature) = input
                .partial_sigs
                .iter()
                .next()
                .expect("input should have one signature");
            signature.to_vec()
        })
        .collect();

    // Sort signatures lexicographically
    signatures.sort();

    // Compute chained sighash using SHA256
    let mut engine = sha256::Hash::engine();
    for signature in signatures {
        engine.input(&signature);
    }

    Ok(sha256::Hash::from_engine(engine))
}

/// Signs a transaction verification message with the format:
/// "TVA1" || hw_auth_pubkey || chained_sighashes_final
pub(crate) fn approve_psbt(
    wik_private_key: SecretKey,
    hw_auth_public_key: PublicKey,
    psbt: PartiallySignedTransaction,
) -> Result<TransactionVerificationApproval> {
    approve_inputs(wik_private_key, hw_auth_public_key, psbt.inputs)
}

/// Signs a transaction verification message with the format:
/// "TVA1" || hw_auth_pubkey || chained_sighashes_final
fn approve_inputs(
    wik_private_key: SecretKey,
    hw_auth_public_key: PublicKey,
    inputs: Vec<Input>,
) -> Result<TransactionVerificationApproval> {
    // Calculate the chained sighashes from lexicographically sorted inputs
    let chained_sighashes_final = calculate_chained_sighashes(inputs)?;

    // Create a SHA256 hash engine for the final message
    let mut message_engine = sha256::Hash::engine();

    // Prefix constant for transaction verification requests
    let prefix = "TVA1";

    // Add prefix to message
    message_engine.input(prefix.as_bytes());

    // Add hardware auth public key
    message_engine.input(&hw_auth_public_key.serialize());

    // Add chained sighashes
    message_engine.input(&chained_sighashes_final[..]);

    // Compute final hash
    let message_hash = sha256::Hash::from_engine(message_engine);

    // Create message from hash
    let message = Message::from_slice(&message_hash.to_byte_array())?;

    // Create secp context
    let secp = Secp256k1::new();

    // Sign the message
    let signature = secp.sign_ecdsa(&message, &wik_private_key);

    Ok(TransactionVerificationApproval {
        version: 0,
        hw_auth_public_key,
        allowed_hash: message_hash.to_byte_array().to_vec(),
        signature,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use bdk::bitcoin::secp256k1::{rand, Secp256k1};
    use std::collections::BTreeMap;
    use wsm_common::bitcoin::bip32::{ChildNumber, DerivationPath, Fingerprint};

    #[test]
    fn test_approve_psbt() {
        use bdk::bitcoin::{ecdsa, secp256k1::Message, PublicKey as BdkPublicKey};

        // Create a secp context
        let secp = Secp256k1::new();
        let mut rng = rand::thread_rng();

        let bk_derivation_path: DerivationPath = vec![
            ChildNumber::from_hardened_idx(84).unwrap(),
            ChildNumber::from_hardened_idx(1).unwrap(),
            ChildNumber::from_hardened_idx(0).unwrap(),
        ]
        .into();

        // Generate a random private key for testing
        let (priv_key, pub_key) = secp.generate_keypair(&mut rng);

        // Create a signature
        let msg = Message::from_slice(&[0; 32]).unwrap();
        let sig = ecdsa::Signature::sighash_all(secp.sign_ecdsa(&msg, &priv_key));

        // Create a BdkPublicKey from our PublicKey
        let bdk_pk = BdkPublicKey {
            compressed: true,
            inner: pub_key,
        };

        let inputs = vec![Input {
            partial_sigs: BTreeMap::from([(bdk_pk, sig)]),
            bip32_derivation: BTreeMap::from([(
                pub_key,
                (
                    Fingerprint::default(),
                    bk_derivation_path.extend([
                        ChildNumber::Normal { index: 0 },
                        ChildNumber::Normal { index: 0 },
                    ]),
                ),
            )]),
            ..Default::default()
        }];

        // Call the function we're testing
        let approval = approve_inputs(priv_key, pub_key, inputs);

        // Verify that the function returns a valid approval
        assert!(approval.is_ok());
    }
}
