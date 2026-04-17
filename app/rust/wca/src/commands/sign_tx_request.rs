use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{self, wallet_cmd, SignTxInput, SignTxOutput, SignTxRequestCmd, Status},
    wca::{decode_and_check, encode_proto_cmd},
};

use crate::command_interface::command;

/// Per-input signature produced by the hardware.
/// Mirrors the `input_signature` proto message.
#[derive(Debug, Clone)]
pub struct InputSignatureTuple {
    /// Zero-based index into the original inputs array.
    pub input_index: u32,
    /// Compressed public key that produced this signature (33 bytes).
    pub public_key: Vec<u8>,
    /// DER-encoded ECDSA signature + sighash type byte (max 73 bytes).
    pub signature: Vec<u8>,
}

/// Result of the sign_tx_request command.
///
/// Transaction signing always requires user confirmation on the device,
/// so this only has a `ConfirmationPending` variant.
#[derive(Debug, Clone)]
pub enum SignTxRequestResult {
    ConfirmationPending {
        response_handle: Vec<u8>,
        confirmation_handle: Vec<u8>,
    },
}

/// Per-input data for the sign_tx_request_cmd.
/// Used to construct the command from app-side types.
#[derive(Debug, Clone)]
pub struct SignTxInputData {
    pub prev_txid: Vec<u8>,
    pub prev_index: u32,
    pub sequence: u32,
    pub amount: u64,
    pub derivation_path: Vec<u32>,
}

/// Per-output data for the sign_tx_request_cmd.
/// Used to construct the command from app-side types.
#[derive(Debug, Clone)]
pub struct SignTxOutputData {
    pub amount: u64,
    pub destination_spk: Vec<u8>,
    pub derivation_path: Vec<u32>,
    pub has_derivation_path: bool,
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn sign_tx_request(
    version: u32,
    lock_time: u32,
    inputs: Vec<SignTxInputData>,
    outputs: Vec<SignTxOutputData>,
    btc_display_unit: fwpb::BtcDisplayUnit,
) -> Result<SignTxRequestResult, CommandError> {
    // Preflight validation matching firmware nanopb constraints (wallet.proto).
    const MAX_SIGN_TX_ENTRIES: usize = 5;
    const TXID_LEN: usize = 32;
    const MAX_DERIVATION_PATH: usize = 5;
    const MAX_SPK_LEN: usize = 35;

    if inputs.is_empty() || inputs.len() > MAX_SIGN_TX_ENTRIES {
        return Err(CommandError::InvalidArguments);
    }
    if outputs.is_empty() || outputs.len() > MAX_SIGN_TX_ENTRIES {
        return Err(CommandError::InvalidArguments);
    }
    for input in &inputs {
        if input.prev_txid.len() != TXID_LEN {
            return Err(CommandError::InvalidArguments);
        }
        if input.derivation_path.len() > MAX_DERIVATION_PATH {
            return Err(CommandError::InvalidArguments);
        }
    }
    for output in &outputs {
        if output.destination_spk.len() > MAX_SPK_LEN {
            return Err(CommandError::InvalidArguments);
        }
        if output.derivation_path.len() > MAX_DERIVATION_PATH {
            return Err(CommandError::InvalidArguments);
        }
    }

    let msg = wallet_cmd::Msg::SignTxRequestCmd(SignTxRequestCmd {
        version,
        lock_time,
        inputs: inputs
            .into_iter()
            .map(|i| SignTxInput {
                prev_txid: i.prev_txid,
                prev_index: i.prev_index,
                sequence: i.sequence,
                amount: i.amount,
                derivation_path: i.derivation_path,
            })
            .collect(),
        outputs: outputs
            .into_iter()
            .map(|o| SignTxOutput {
                amount: o.amount,
                destination_spk: o.destination_spk,
                derivation_path: o.derivation_path,
                has_derivation_path: o.has_derivation_path,
            })
            .collect(),
        btc_display_unit: btc_display_unit.into(),
        ..Default::default()
    });

    // Use proto continuation to support payloads > MAX_PROTO_SIZE (505 bytes).
    // With 5 inputs + 5 outputs the encoded proto can reach ~870 bytes, exceeding
    // the per-APDU MAX_PROTO_SIZE limit.
    let apdus = encode_proto_cmd(msg)?;
    let mut data = Vec::new();
    for apdu in apdus {
        data = yield_!(apdu.into());
    }

    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    // sign_tx_request_cmd always requires user confirmation on device.
    if Status::try_from(wallet_rsp.status) == Ok(Status::ConfirmationPending) {
        return Ok(SignTxRequestResult::ConfirmationPending {
            response_handle: wallet_rsp.response_handle,
            confirmation_handle: wallet_rsp.confirmation_handle,
        });
    }

    // If we get a direct response (shouldn't happen for tx signing), treat it as an error.
    Err(CommandError::InvalidResponse)
}

command!(SignTxRequest = sign_tx_request -> SignTxRequestResult,
    version: u32,
    lock_time: u32,
    inputs: Vec<SignTxInputData>,
    outputs: Vec<SignTxOutputData>,
    btc_display_unit: fwpb::BtcDisplayUnit
);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{Status, WalletRsp},
    };

    use crate::fwpb::BtcDisplayUnit;

    use super::{SignTxInputData, SignTxOutputData, SignTxRequest, SignTxRequestResult};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn sign_tx_request_confirmation_pending() -> Result<(), CommandError> {
        let inputs = vec![SignTxInputData {
            prev_txid: vec![0u8; 32],
            prev_index: 0,
            sequence: 0xFFFFFFFD,
            amount: 100_000,
            derivation_path: vec![84 | (1 << 31), 0 | (1 << 31), 0 | (1 << 31), 0, 7],
        }];
        let outputs = vec![SignTxOutputData {
            amount: 90_000,
            destination_spk: vec![0u8; 34],
            derivation_path: vec![],
            has_derivation_path: false,
        }];

        let command = SignTxRequest::new(2, 0, inputs, outputs, BtcDisplayUnit::Satoshi);
        command.next(Vec::default())?;

        let response_handle = vec![0x01, 0x02, 0x03, 0x04];
        let confirmation_handle = vec![0x05, 0x06, 0x07, 0x08];

        let response = make_response(WalletRsp {
            status: Status::ConfirmationPending.into(),
            response_handle: response_handle.clone(),
            confirmation_handle: confirmation_handle.clone(),
            msg: None,
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value:
                    SignTxRequestResult::ConfirmationPending {
                        response_handle: rh,
                        confirmation_handle: ch,
                    },
            }) => {
                assert_eq!(rh, response_handle);
                assert_eq!(ch, confirmation_handle);
            }
            other => panic!("Expected ConfirmationPending, got {:?}", other),
        }

        Ok(())
    }

    /// With 5 inputs + 5 outputs, the proto exceeds MAX_PROTO_SIZE (505 bytes)
    /// and must be sent via proto continuation (multiple APDUs).
    #[test]
    fn sign_tx_request_uses_multiple_fragments_for_large_payload() {
        let inputs: Vec<SignTxInputData> = (0..5)
            .map(|i| SignTxInputData {
                prev_txid: vec![i as u8; 32],
                prev_index: i,
                sequence: 0xFFFFFFFD,
                amount: 100_000 * (i as u64 + 1),
                derivation_path: vec![84 | (1 << 31), 0 | (1 << 31), 0 | (1 << 31), 0, i],
            })
            .collect();
        let outputs: Vec<SignTxOutputData> = (0..5)
            .map(|i| SignTxOutputData {
                amount: 50_000 * (i as u64 + 1),
                destination_spk: vec![0u8; 22],
                derivation_path: vec![],
                has_derivation_path: false,
            })
            .collect();

        let command = SignTxRequest::new(2, 800_000, inputs, outputs, BtcDisplayUnit::Satoshi);
        let ack = vec![0x90, 0x00];

        // Count how many APDU fragments the generator yields.
        // Feed ACKs for each; the final ACK will cause a decode error when the
        // generator tries to parse it as a WalletRsp, which is expected.
        let mut fragment_count = 0;
        let mut data: Vec<u8> = Vec::default();

        loop {
            match command.next(data.clone()) {
                Ok(State::Data { .. }) => {
                    fragment_count += 1;
                    data = ack.clone();
                }
                _ => break, // Result or error after last fragment
            }
        }

        assert!(
            fragment_count > 1,
            "Expected multiple APDU fragments for 5-input/5-output tx, got {}",
            fragment_count
        );
    }

    #[test]
    fn sign_tx_request_rejects_too_many_inputs() {
        let inputs: Vec<SignTxInputData> = (0..6)
            .map(|i| SignTxInputData {
                prev_txid: vec![0u8; 32],
                prev_index: i,
                sequence: 0xFFFFFFFD,
                amount: 100_000,
                derivation_path: vec![84 | (1 << 31), 0 | (1 << 31), 0 | (1 << 31), 0, i],
            })
            .collect();
        let outputs = vec![SignTxOutputData {
            amount: 90_000,
            destination_spk: vec![0u8; 22],
            derivation_path: vec![],
            has_derivation_path: false,
        }];

        let command = SignTxRequest::new(2, 0, inputs, outputs, BtcDisplayUnit::Satoshi);
        assert!(matches!(
            command.next(Vec::default()),
            Err(CommandError::InvalidArguments)
        ));
    }

    #[test]
    fn sign_tx_request_rejects_too_many_outputs() {
        let inputs = vec![SignTxInputData {
            prev_txid: vec![0u8; 32],
            prev_index: 0,
            sequence: 0xFFFFFFFD,
            amount: 100_000,
            derivation_path: vec![84 | (1 << 31), 0 | (1 << 31), 0 | (1 << 31), 0, 0],
        }];
        let outputs: Vec<SignTxOutputData> = (0..6)
            .map(|i| SignTxOutputData {
                amount: 50_000 * (i as u64 + 1),
                destination_spk: vec![0u8; 22],
                derivation_path: vec![],
                has_derivation_path: false,
            })
            .collect();

        let command = SignTxRequest::new(2, 0, inputs, outputs, BtcDisplayUnit::Satoshi);
        assert!(matches!(
            command.next(Vec::default()),
            Err(CommandError::InvalidArguments)
        ));
    }

    #[test]
    fn sign_tx_request_rejects_empty_inputs() {
        let outputs = vec![SignTxOutputData {
            amount: 90_000,
            destination_spk: vec![0u8; 22],
            derivation_path: vec![],
            has_derivation_path: false,
        }];

        let command = SignTxRequest::new(2, 0, vec![], outputs, BtcDisplayUnit::Satoshi);
        assert!(matches!(
            command.next(Vec::default()),
            Err(CommandError::InvalidArguments)
        ));
    }
}
