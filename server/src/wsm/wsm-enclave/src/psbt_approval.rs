use crate::psbt_verification::verify_inputs_only_have_one_signature;
use anyhow::Result;
use bdk::bitcoin::psbt::Input;
use bdk::bitcoin::{
    hashes::{sha256, Hash, HashEngine},
    psbt::PartiallySignedTransaction,
    secp256k1::{Message, Secp256k1, SecretKey},
};
use rand::random;
use wsm_common::bitcoin::secp256k1::PublicKey;
use wsm_common::messages::api::TransactionVerificationGrant;

/// Calculate a SHA256 hash of lexicographically sorted and concatenated PSBT input signatures
/// The returned chain is [h[0], h[1], ..., h[n]]
/// h[n] = random_bytes
/// h[i] = sha256(h[i+1] || sorted_signatures[i]) for i from n-1 down to 0
/// The commitment is h[0].
///
/// Design doc: https://docs.google.com/document/d/1vYALV79uj_DsIEK0sCz2vkkCJ-z-t7q-v4q1vIDWwVY/edit?tab=t.0#heading=h.8toyyilp9b4c
fn calculate_chained_sighashes(inputs: Vec<Input>) -> Result<Vec<Vec<u8>>> {
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
    let n = signatures.len();
    let mut hash_chain: Vec<Vec<u8>> = Vec::with_capacity(n + 1);
    let init: [u8; 32] = random();
    let mut hnext = init.to_vec();
    hash_chain.push(hnext.clone());
    for i in 0..n {
        let mut engine = sha256::Hash::engine();
        engine.input(&hnext);
        engine.input(&signatures[n - 1 - i]);
        hnext = sha256::Hash::from_engine(engine).to_byte_array().to_vec();
        hash_chain.push(hnext.clone());
    }
    hash_chain.reverse();

    Ok(hash_chain)
}

/// Signs a transaction verification message with the format:
/// "TVA1" || hw_auth_pubkey || chained_sighashes_final
pub(crate) fn approve_psbt(
    wik_private_key: SecretKey,
    hw_auth_public_key: PublicKey,
    psbt: PartiallySignedTransaction,
) -> Result<TransactionVerificationGrant> {
    approve_inputs(wik_private_key, hw_auth_public_key, psbt.inputs)
}

/// Signs a transaction verification message with the format:
/// "TVA1" || hw_auth_pubkey || chained_sighashes_final
fn approve_inputs(
    wik_private_key: SecretKey,
    hw_auth_public_key: PublicKey,
    inputs: Vec<Input>,
) -> Result<TransactionVerificationGrant> {
    // Calculate the chained sighashes from lexicographically sorted inputs
    let reverse_hash_chain = calculate_chained_sighashes(inputs)?;

    // Create a SHA256 hash engine for the final message
    let mut message_engine = sha256::Hash::engine();

    // Prefix constant for transaction verification requests
    let prefix = "TVA1";

    // Add prefix to message
    message_engine.input(prefix.as_bytes());

    // Add hardware auth public key
    message_engine.input(&hw_auth_public_key.serialize());

    // Add commitment (h[n])
    let commitment = reverse_hash_chain
        .first()
        .expect("hash chain cannot be empty");
    message_engine.input(commitment);

    // Compute final hash
    let message_hash = sha256::Hash::from_engine(message_engine);

    // Create message from hash
    let message = Message::from_slice(&message_hash.to_byte_array())?;

    // Create secp context
    let secp = Secp256k1::new();

    // Sign the message
    let signature = secp.sign_ecdsa(&message, &wik_private_key);

    Ok(TransactionVerificationGrant {
        version: 0,
        hw_auth_public_key,
        commitment: commitment.to_vec(),
        reverse_hash_chain,
        signature,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use bdk::bitcoin::secp256k1::{rand, Secp256k1};
    use std::collections::BTreeMap;
    use wsm_common::bitcoin::bip32::{ChildNumber, DerivationPath, Fingerprint};

    fn generate_random_input(
        secp: &Secp256k1<bdk::bitcoin::secp256k1::All>,
        rng: &mut rand::rngs::ThreadRng,
    ) -> (Input, SecretKey, PublicKey, Vec<u8>) {
        let (priv_key, pub_key) = secp.generate_keypair(rng);
        let msg_slice: [u8; 32] = random();
        let msg = Message::from_slice(&msg_slice).unwrap();
        let sig = bdk::bitcoin::ecdsa::Signature::sighash_all(secp.sign_ecdsa(&msg, &priv_key));

        let bdk_pk = bdk::bitcoin::PublicKey {
            compressed: true,
            inner: pub_key,
        };

        let bk_derivation_path: DerivationPath = vec![
            ChildNumber::from_hardened_idx(84).unwrap(),
            ChildNumber::from_hardened_idx(1).unwrap(),
            ChildNumber::from_hardened_idx(0).unwrap(),
        ]
        .into();

        let input = Input {
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
        };
        (input, priv_key, pub_key, sig.to_vec())
    }

    #[test]
    fn test_approve_psbt_with_client_verification() {
        use bdk::bitcoin::hashes::sha256;

        // Create a secp context
        let secp = Secp256k1::new();
        let mut rng = rand::thread_rng();

        // Generate WIK key (not used for signing inputs, but for the final approval)
        let (wik_priv_key, wik_pub_key) = secp.generate_keypair(&mut rng);
        // Generate HW Auth Pubkey
        let (_, hw_auth_pub_key) = secp.generate_keypair(&mut rng);

        // Create two inputs
        let (input1, _priv1, _pub1, sig1_bytes) = generate_random_input(&secp, &mut rng);
        let (input2, _priv2, _pub2, sig2_bytes) = generate_random_input(&secp, &mut rng);

        let inputs_vec = vec![input1, input2];

        // Get sorted signatures (these are the "sighashes" for client verification)
        let mut sorted_signatures = [sig1_bytes, sig2_bytes];
        sorted_signatures.sort();
        let s0 = &sorted_signatures[0];
        let s1 = &sorted_signatures[1];

        // Call the function we're testing
        let approval_result = approve_inputs(wik_priv_key, hw_auth_pub_key, inputs_vec);

        // Verify that the function returns a valid approval
        assert!(approval_result.is_ok());
        let approval = approval_result.unwrap();

        // Client-side verification
        // reverse_hash_chain is [h[0], h[1], h[2]] where n=2 (number of inputs)
        // h[2] is random_seed
        // h[1] = sha256(h[2] || s1)  (s1 because signatures were sorted, and we process from n-1 down to 0)
        // h[0] = sha256(h[1] || s0)
        // commitment = h[0]

        assert_eq!(
            approval.reverse_hash_chain.len(),
            3,
            "Hash chain should have n+1 elements (2 inputs + 1 random seed)"
        );

        let h0_commitment = &approval.commitment; // This is h[0]
        let h0_from_chain = &approval.reverse_hash_chain[0];
        let h1_from_chain = &approval.reverse_hash_chain[1];
        let h2_from_chain = &approval.reverse_hash_chain[2]; // This is the initial random seed

        assert_eq!(
            h0_commitment, h0_from_chain,
            "Commitment field should be h[0] from the chain"
        );

        // Verification Step 1 (verifying s0, using h1 as proof)
        // current_state_0 = commitment (h[0])
        // proof_0 = h[1]
        // We expect sha256(proof_0 || s0) == current_state_0
        let client_current_state_0 = h0_commitment; // h[0]
        let proof_0 = h1_from_chain; // h[1]

        let mut engine_verify_s0 = sha256::Hash::engine();
        engine_verify_s0.input(proof_0);
        engine_verify_s0.input(s0);
        let expected_current_state_0 = sha256::Hash::from_engine(engine_verify_s0)
            .to_byte_array()
            .to_vec();

        assert_eq!(
            &expected_current_state_0, client_current_state_0,
            "Client verification for s0 failed"
        );

        // Update client state for next step
        // client_current_state_1 = proof_0 (which is h[1])
        let client_current_state_1 = proof_0; // h[1]

        // Verification Step 2 (verifying s1, using h2 as proof)
        // proof_1 = h[2] (the random seed)
        // We expect sha256(proof_1 || s1) == client_current_state_1
        let proof_1 = h2_from_chain; // h[2]

        let mut engine_verify_s1 = sha256::Hash::engine();
        engine_verify_s1.input(proof_1);
        engine_verify_s1.input(s1);
        let expected_current_state_1 = sha256::Hash::from_engine(engine_verify_s1)
            .to_byte_array()
            .to_vec();

        assert_eq!(
            &expected_current_state_1, client_current_state_1,
            "Client verification for s1 failed"
        );

        // Final client state is proof_1 (h[2]), which was the initial random data.
        // This implies the chain was correctly consumed.
    }
}
