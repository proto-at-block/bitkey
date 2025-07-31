use anyhow::{anyhow, Context, Error};
use bitcoin::hashes::{sha256, Hash, HashEngine};
use bitcoin::psbt::Input;
use bitcoin::secp256k1::Message;

pub use bitcoin::secp256k1::PublicKey;

pub const GRANT_REQUEST_SIG_PREFIX: &[u8] = b"BKGrantReq";

fn verify_inputs_only_have_one_signature(inputs: &[Input]) -> Result<(), Error> {
    for input in inputs.iter() {
        if input.partial_sigs.len() != 1 {
            return Err(anyhow!("More than one signature found in input"));
        }
    }

    Ok(())
}

/// Calculate a SHA256 hash of lexicographically sorted and concatenated PSBT input signatures
/// The returned chain is [h[0], h[1], ..., h[n]]
/// h[n] = random_bytes
/// h[i] = sha256(h[i+1] || sorted_signatures[i]) for i from n-1 down to 0
/// The commitment is h[0].
///
/// Design doc: https://docs.google.com/document/d/1vYALV79uj_DsIEK0sCz2vkkCJ-z-t7q-v4q1vIDWwVY/edit?tab=t.0#heading=h.8toyyilp9b4c
pub fn calculate_chained_sighashes(
    inputs: Vec<Input>,
    init: [u8; 32],
) -> Result<Vec<Vec<u8>>, Error> {
    // Verify that every input has exactly one signature
    // This enforces our requirement and simplifies signature extraction
    verify_inputs_only_have_one_signature(&inputs)?;

    // Extract all signature bytes (one from each input)
    let mut signatures = inputs
        .iter()
        .map(|input| {
            // We verified above that each input has exactly one signature
            let (_, signature) = input
                .partial_sigs
                .iter()
                .next()
                .ok_or_else(|| anyhow!("Input has no signatures"))?;
            Ok(signature.to_vec())
        })
        .collect::<Result<Vec<Vec<u8>>, Error>>()?;

    // Sort signatures lexicographically
    signatures.sort();

    // Compute chained sighash using SHA256
    let n = signatures.len();
    let mut hash_chain: Vec<Vec<u8>> = Vec::with_capacity(n + 1);
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

/// Generates a transaction verification message with the format:
/// "TVA1" || hw_auth_pubkey || chained_sighashes_final
pub fn generate_message(
    hw_auth_public_key: PublicKey,
    commitment: Vec<u8>,
) -> Result<Message, Error> {
    // Create a SHA256 hash engine for the final message
    let mut message_engine = sha256::Hash::engine();

    // Prefix constant for transaction verification requests
    let prefix = "TVA1";

    // Add prefix to message
    message_engine.input(prefix.as_bytes());

    // Add hardware auth public key
    message_engine.input(&hw_auth_public_key.serialize());

    // Add commitment (h[n])
    message_engine.input(&commitment);

    // Compute final hash
    let message_hash = sha256::Hash::from_engine(message_engine);

    // Create message from hash
    let message = Message::from_slice(&message_hash.to_byte_array())
        .context("Failed to create message from hash")?;

    Ok(message)
}

#[cfg(test)]
mod tests {
    use std::collections::BTreeMap;

    use bitcoin::secp256k1::Secp256k1;
    use bitcoin::{
        bip32::{ChildNumber, DerivationPath, Fingerprint},
        secp256k1::SecretKey,
    };
    use rand::random;

    use super::*;

    fn generate_random_input(
        secp: &Secp256k1<bitcoin::secp256k1::All>,
        rng: &mut rand::rngs::ThreadRng,
    ) -> (Input, SecretKey, PublicKey, Vec<u8>) {
        let (priv_key, pub_key) = secp.generate_keypair(rng);
        let msg_slice: [u8; 32] = random();
        let msg = Message::from_slice(&msg_slice).unwrap();
        let sig = bitcoin::ecdsa::Signature::sighash_all(secp.sign_ecdsa(&msg, &priv_key));

        let bdk_pk = bitcoin::PublicKey {
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

    fn create_input_with_known_sig_bytes() -> (Input, Vec<u8>) {
        let secp = Secp256k1::new();
        let mut rng = rand::thread_rng();
        let (priv_key, pub_key) = secp.generate_keypair(&mut rng);

        // Create a proper bitcoin signature
        let msg_slice: [u8; 32] = random();
        let msg = Message::from_slice(&msg_slice).unwrap();
        let sig = bitcoin::ecdsa::Signature::sighash_all(secp.sign_ecdsa(&msg, &priv_key));

        let bdk_pk = bitcoin::PublicKey {
            compressed: true,
            inner: pub_key,
        };

        let input = Input {
            partial_sigs: BTreeMap::from([(bdk_pk, sig)]),
            ..Default::default()
        };

        // Return both the input and the actual signature bytes for testing
        (input, sig.to_vec())
    }

    fn create_input_with_multiple_signatures() -> Input {
        let secp = Secp256k1::new();
        let mut rng = rand::thread_rng();

        let (priv_key1, pub_key1) = secp.generate_keypair(&mut rng);
        let (priv_key2, pub_key2) = secp.generate_keypair(&mut rng);

        let msg_slice: [u8; 32] = random();
        let msg = Message::from_slice(&msg_slice).unwrap();

        let sig1 = bitcoin::ecdsa::Signature::sighash_all(secp.sign_ecdsa(&msg, &priv_key1));
        let sig2 = bitcoin::ecdsa::Signature::sighash_all(secp.sign_ecdsa(&msg, &priv_key2));

        let bdk_pk1 = bitcoin::PublicKey {
            compressed: true,
            inner: pub_key1,
        };
        let bdk_pk2 = bitcoin::PublicKey {
            compressed: true,
            inner: pub_key2,
        };

        Input {
            partial_sigs: BTreeMap::from([(bdk_pk1, sig1), (bdk_pk2, sig2)]),
            ..Default::default()
        }
    }

    #[test]
    fn test_calculate_chained_sighashes_basic() {
        let (input, signature_bytes) = create_input_with_known_sig_bytes();
        let init = [0u8; 32];

        let result = calculate_chained_sighashes(vec![input], init);
        assert!(result.is_ok());

        let hash_chain = result.unwrap();
        assert_eq!(hash_chain.len(), 2);
        assert_eq!(&hash_chain[1], &init.to_vec());

        // Verify h[0] = sha256(h[1] || signature)
        let mut engine = sha256::Hash::engine();
        engine.input(&hash_chain[1]);
        engine.input(&signature_bytes);
        let expected = sha256::Hash::from_engine(engine).to_byte_array().to_vec();
        assert_eq!(&hash_chain[0], &expected);
    }

    #[test]
    fn test_calculate_chained_sighashes_sorting() {
        let (input1, _) = create_input_with_known_sig_bytes();
        let (input2, _) = create_input_with_known_sig_bytes();
        let init = [42u8; 32];

        // Results should be identical regardless of input order
        let result1 = calculate_chained_sighashes(vec![input1.clone(), input2.clone()], init);
        let result2 = calculate_chained_sighashes(vec![input2, input1], init);

        assert!(result1.is_ok() && result2.is_ok());
        assert_eq!(result1.unwrap(), result2.unwrap());
    }

    #[test]
    fn test_calculate_chained_sighashes_errors() {
        // Multiple signatures error
        let input_with_multiple_sigs = create_input_with_multiple_signatures();
        let result = calculate_chained_sighashes(vec![input_with_multiple_sigs], [0u8; 32]);
        assert!(result
            .unwrap_err()
            .to_string()
            .contains("More than one signature found in input"));

        // Empty inputs should work
        let result = calculate_chained_sighashes(vec![], [0u8; 32]);
        assert!(result.is_ok());
        assert_eq!(result.unwrap().len(), 1);
    }

    #[test]
    fn test_generate_message_basic() {
        let secp = Secp256k1::new();
        let mut rng = rand::thread_rng();
        let (_, hw_auth_pub_key) = secp.generate_keypair(&mut rng);
        let commitment = vec![1, 2, 3, 4, 5];

        let result = generate_message(hw_auth_pub_key, commitment);
        assert!(result.is_ok());
        assert_eq!(result.unwrap().as_ref().len(), 32);
    }

    #[test]
    fn test_generate_message_uniqueness() {
        let secp = Secp256k1::new();
        let mut rng = rand::thread_rng();
        let (_, key1) = secp.generate_keypair(&mut rng);
        let (_, key2) = secp.generate_keypair(&mut rng);
        let commitment = vec![1, 2, 3];

        // Different keys should produce different messages
        let msg1 = generate_message(key1, commitment.clone()).unwrap();
        let msg2 = generate_message(key2, commitment.clone()).unwrap();
        assert_ne!(msg1, msg2);

        // Different commitments should produce different messages
        let msg3 = generate_message(key1, vec![4, 5, 6]).unwrap();
        assert_ne!(msg1, msg3);
    }

    #[test]
    fn test_full_integration() {
        let secp = Secp256k1::new();
        let mut rng = rand::thread_rng();
        let (_, hw_auth_pub_key) = secp.generate_keypair(&mut rng);

        // Create inputs and calculate chained sighashes
        let (input1, _, _, _) = generate_random_input(&secp, &mut rng);
        let (input2, _, _, _) = generate_random_input(&secp, &mut rng);
        let init: [u8; 32] = random();

        let hash_chain = calculate_chained_sighashes(vec![input1, input2], init).unwrap();
        let commitment = hash_chain[0].clone();

        // Generate final message
        let message = generate_message(hw_auth_pub_key, commitment).unwrap();
        assert_eq!(message.as_ref().len(), 32);
    }
}
