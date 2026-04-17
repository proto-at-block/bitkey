//! Canonical binary serializer for the streaming transaction signing protocol.
//!
//! Encodes the same fields as `sign_tx_input` / `sign_tx_output` proto messages
//! into a flat binary format suitable for streaming in 452-byte NFC chunks.
//!
//! ## Wire Format
//!
//! ```text
//! Header (16 bytes):
//!   version     : u32 LE
//!   lock_time   : u32 LE
//!   num_inputs  : u32 LE
//!   num_outputs : u32 LE
//!
//! Per-input (69 bytes each):
//!   prev_txid   : [u8; 32]
//!   prev_index  : u32 LE
//!   sequence    : u32 LE
//!   amount      : u64 LE
//!   path_len    : u8        (0..=5)
//!   path        : [u32 LE; 5]  (always 20 bytes, zero-padded)
//!
//! Per-output (66 bytes each):
//!   amount      : u64 LE
//!   spk_len     : u8        (0..=35)
//!   spk         : [u8; 35]  (always 35 bytes, zero-padded)
//!   has_path    : u8        (0 or 1)
//!   path_len    : u8        (0..=5)
//!   path        : [u32 LE; 5]  (always 20 bytes, zero-padded)
//! ```
//!
//! Fixed-size records eliminate length-field manipulation attacks and make
//! firmware parsing trivial (no variable-length decoding, just pointer arithmetic).

use super::sign_tx_request::{SignTxInputData, SignTxOutputData};
use crate::errors::CommandError;
use sha2::{Digest, Sha256};

/// Size of the fixed header.
pub const HEADER_SIZE: usize = 16;
/// Size of each input record in the canonical encoding.
pub const INPUT_RECORD_SIZE: usize = 69;
/// Size of each output record in the canonical encoding.
pub const OUTPUT_RECORD_SIZE: usize = 66;
/// Maximum chunk size matching the NFC APDU limit (fwup_data max_size).
pub const CHUNK_SIZE: usize = 452;

/// Serializes transaction data into the canonical binary format for streaming.
///
/// Returns the complete payload bytes. The caller is responsible for chunking
/// this into 452-byte pieces for `sign_stream_transfer_cmd`.
pub fn serialize_stream_payload(
    version: u32,
    lock_time: u32,
    inputs: &[SignTxInputData],
    outputs: &[SignTxOutputData],
) -> Result<Vec<u8>, CommandError> {
    let total_size =
        HEADER_SIZE + inputs.len() * INPUT_RECORD_SIZE + outputs.len() * OUTPUT_RECORD_SIZE;
    let mut buf = Vec::with_capacity(total_size);

    // Header
    buf.extend_from_slice(&version.to_le_bytes());
    buf.extend_from_slice(&lock_time.to_le_bytes());
    buf.extend_from_slice(&(inputs.len() as u32).to_le_bytes());
    buf.extend_from_slice(&(outputs.len() as u32).to_le_bytes());

    // Inputs
    for input in inputs {
        // prev_txid: must be exactly 32 bytes
        if input.prev_txid.len() != 32 {
            return Err(CommandError::InvalidArguments);
        }
        buf.extend_from_slice(&input.prev_txid);
        // prev_index: u32 LE
        buf.extend_from_slice(&input.prev_index.to_le_bytes());
        // sequence: u32 LE
        buf.extend_from_slice(&input.sequence.to_le_bytes());
        // amount: u64 LE
        buf.extend_from_slice(&input.amount.to_le_bytes());
        // path_len: u8 (must be ≤ 5 to fit the fixed-size record)
        if input.derivation_path.len() > 5 {
            return Err(CommandError::InvalidArguments);
        }
        let path_len = input.derivation_path.len() as u8;
        buf.push(path_len);
        // path: always 5 × u32 LE = 20 bytes, zero-padded
        for i in 0..5 {
            let val = input.derivation_path.get(i).copied().unwrap_or(0);
            buf.extend_from_slice(&val.to_le_bytes());
        }
    }

    // Outputs
    for output in outputs {
        // amount: u64 LE
        buf.extend_from_slice(&output.amount.to_le_bytes());
        // spk_len: u8 (must be ≤ 35 to fit the fixed-size record)
        if output.destination_spk.len() > 35 {
            return Err(CommandError::InvalidArguments);
        }
        let spk_len = output.destination_spk.len() as u8;
        buf.push(spk_len);
        // spk: always 35 bytes, zero-padded
        let mut spk_padded = [0u8; 35];
        spk_padded[..output.destination_spk.len()].copy_from_slice(&output.destination_spk);
        buf.extend_from_slice(&spk_padded);
        // has_path: u8
        buf.push(output.has_derivation_path as u8);
        // path_len: u8 (must be ≤ 5 to fit the fixed-size record)
        if output.derivation_path.len() > 5 {
            return Err(CommandError::InvalidArguments);
        }
        let path_len = output.derivation_path.len() as u8;
        buf.push(path_len);
        // path: always 5 × u32 LE = 20 bytes, zero-padded
        for i in 0..5 {
            let val = output.derivation_path.get(i).copied().unwrap_or(0);
            buf.extend_from_slice(&val.to_le_bytes());
        }
    }

    debug_assert_eq!(buf.len(), total_size);
    Ok(buf)
}

/// Computes the SHA256 commitment hash over the canonical payload.
///
/// This is the value sent in `sign_stream_finalize_cmd.commitment_hash`.
/// Firmware independently computes the same hash during streaming and
/// verifies they match.
pub fn compute_commitment_hash(payload: &[u8]) -> Vec<u8> {
    let mut hasher = Sha256::new();
    hasher.update(payload);
    hasher.finalize().to_vec()
}

/// Splits a payload into chunks of at most `CHUNK_SIZE` bytes.
pub fn chunk_payload(payload: &[u8]) -> Vec<Vec<u8>> {
    payload.chunks(CHUNK_SIZE).map(|c| c.to_vec()).collect()
}

/// Computes the total payload size for given input/output counts.
pub fn payload_size(num_inputs: usize, num_outputs: usize) -> usize {
    HEADER_SIZE + num_inputs * INPUT_RECORD_SIZE + num_outputs * OUTPUT_RECORD_SIZE
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::commands::sign_tx_request::{SignTxInputData, SignTxOutputData};

    fn make_test_input(index: u32) -> SignTxInputData {
        SignTxInputData {
            prev_txid: vec![index as u8; 32],
            prev_index: index,
            sequence: 0xFFFFFFFD,
            amount: 100_000 * (index as u64 + 1),
            derivation_path: vec![84 | (1 << 31), 0 | (1 << 31), 0 | (1 << 31), 0, index],
        }
    }

    fn make_test_output(amount: u64, has_path: bool) -> SignTxOutputData {
        SignTxOutputData {
            amount,
            destination_spk: vec![
                0x00, 0x14, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
            ],
            derivation_path: if has_path {
                vec![84 | (1 << 31), 0 | (1 << 31), 0 | (1 << 31), 1, 0]
            } else {
                vec![]
            },
            has_derivation_path: has_path,
        }
    }

    #[test]
    fn serialize_correct_size() {
        let inputs: Vec<SignTxInputData> = (0..3).map(make_test_input).collect();
        let outputs = vec![
            make_test_output(90_000, false),
            make_test_output(9_000, true),
        ];

        let payload = serialize_stream_payload(2, 800_000, &inputs, &outputs).unwrap();
        let expected = HEADER_SIZE + 3 * INPUT_RECORD_SIZE + 2 * OUTPUT_RECORD_SIZE;
        assert_eq!(payload.len(), expected);
    }

    #[test]
    fn serialize_header_fields() {
        let inputs = vec![make_test_input(0)];
        let outputs = vec![make_test_output(90_000, false)];

        let payload = serialize_stream_payload(2, 800_000, &inputs, &outputs).unwrap();

        // version
        assert_eq!(u32::from_le_bytes(payload[0..4].try_into().unwrap()), 2);
        // lock_time
        assert_eq!(
            u32::from_le_bytes(payload[4..8].try_into().unwrap()),
            800_000
        );
        // num_inputs
        assert_eq!(u32::from_le_bytes(payload[8..12].try_into().unwrap()), 1);
        // num_outputs
        assert_eq!(u32::from_le_bytes(payload[12..16].try_into().unwrap()), 1);
    }

    #[test]
    fn serialize_input_record() {
        let inputs = vec![make_test_input(7)];
        let outputs = vec![make_test_output(90_000, false)];

        let payload = serialize_stream_payload(2, 0, &inputs, &outputs).unwrap();
        let record = &payload[HEADER_SIZE..HEADER_SIZE + INPUT_RECORD_SIZE];

        // prev_txid
        assert_eq!(&record[0..32], &[7u8; 32]);
        // prev_index
        assert_eq!(u32::from_le_bytes(record[32..36].try_into().unwrap()), 7);
        // sequence
        assert_eq!(
            u32::from_le_bytes(record[36..40].try_into().unwrap()),
            0xFFFFFFFD
        );
        // amount
        assert_eq!(
            u64::from_le_bytes(record[40..48].try_into().unwrap()),
            800_000
        );
        // path_len
        assert_eq!(record[48], 5);
        // first path element: 84' = 84 | 0x80000000
        assert_eq!(
            u32::from_le_bytes(record[49..53].try_into().unwrap()),
            84 | (1 << 31)
        );
    }

    #[test]
    fn commitment_hash_deterministic() {
        let inputs = vec![make_test_input(0)];
        let outputs = vec![make_test_output(90_000, false)];

        let payload = serialize_stream_payload(2, 0, &inputs, &outputs).unwrap();
        let hash1 = compute_commitment_hash(&payload);
        let hash2 = compute_commitment_hash(&payload);

        assert_eq!(hash1.len(), 32);
        assert_eq!(hash1, hash2);
    }

    #[test]
    fn chunk_payload_correct_count() {
        let inputs: Vec<SignTxInputData> = (0..200).map(make_test_input).collect();
        let outputs = vec![
            make_test_output(90_000, false),
            make_test_output(9_000, true),
        ];

        let payload = serialize_stream_payload(2, 0, &inputs, &outputs).unwrap();
        let chunks = chunk_payload(&payload);

        // 200 inputs × 69 + 2 outputs × 66 + 16 header = 13_948 bytes
        // ceil(13_948 / 452) = 31 chunks
        let expected_chunks = (payload.len() + CHUNK_SIZE - 1) / CHUNK_SIZE;
        assert_eq!(chunks.len(), expected_chunks);

        // All chunks except the last should be exactly CHUNK_SIZE
        for chunk in &chunks[..chunks.len() - 1] {
            assert_eq!(chunk.len(), CHUNK_SIZE);
        }
        // Last chunk should be <= CHUNK_SIZE
        assert!(chunks.last().unwrap().len() <= CHUNK_SIZE);
    }

    #[test]
    fn payload_size_matches_serialized() {
        let inputs: Vec<SignTxInputData> = (0..10).map(make_test_input).collect();
        let outputs = vec![make_test_output(90_000, false)];

        let predicted = payload_size(10, 1);
        let actual = serialize_stream_payload(2, 0, &inputs, &outputs)
            .unwrap()
            .len();
        assert_eq!(predicted, actual);
    }

    #[test]
    fn rejects_input_path_too_long() {
        let input = SignTxInputData {
            prev_txid: vec![0u8; 32],
            prev_index: 0,
            sequence: 0xFFFFFFFD,
            amount: 100_000,
            derivation_path: vec![0x80000054, 0x80000000, 0x80000000, 0, 0, 99],
        };
        let outputs = vec![make_test_output(90_000, false)];
        let result = serialize_stream_payload(2, 0, &[input], &outputs);
        assert!(result.is_err());
    }

    #[test]
    fn rejects_output_spk_too_long() {
        let inputs = vec![make_test_input(0)];
        let output = SignTxOutputData {
            amount: 90_000,
            destination_spk: vec![0xaa; 36], // 36 > 35
            derivation_path: vec![],
            has_derivation_path: false,
        };
        let result = serialize_stream_payload(2, 0, &inputs, &[output]);
        assert!(result.is_err());
    }

    #[test]
    fn rejects_output_path_too_long() {
        let inputs = vec![make_test_input(0)];
        let output = SignTxOutputData {
            amount: 90_000,
            destination_spk: vec![0x00, 0x14, 0xaa, 0xaa, 0xaa, 0xaa],
            derivation_path: vec![0x80000054, 0x80000000, 0x80000000, 1, 0, 42],
            has_derivation_path: true,
        };
        let result = serialize_stream_payload(2, 0, &inputs, &[output]);
        assert!(result.is_err());
    }
}
