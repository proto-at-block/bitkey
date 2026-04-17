//! Streaming transaction signing commands for the W3 hardware.
//!
//! These commands implement the streaming protocol for transactions with more
//! inputs/outputs than `sign_tx_request_cmd` can handle (limited to 5).
//! The protocol streams the same compact BIP143-friendly fields in 452-byte
//! NFC chunks, exactly like FWUP streams firmware.
//!
//! ## Protocol Flow
//!
//! ```text
//! sign_stream_start_cmd  → sign_stream_start_rsp(SUCCESS)
//! sign_stream_transfer_cmd (×N chunks) → sign_stream_transfer_rsp
//! sign_stream_finalize_cmd → CONFIRMATION_PENDING (global status)
//! get_confirmation_result_cmd → sign_stream_signatures_ready
//! get_tx_signature_cmd (×M inputs) → get_tx_signature_rsp(pubkey, signature)
//! ```

use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{
        self, sign_stream_start_rsp::SignStreamStartRspStatus, wallet_rsp::Msg, GetTxSignatureCmd,
        GetTxSignaturesBatchCmd, SignStreamFinalizeCmd, SignStreamStartCmd, SignStreamStartRsp,
        SignStreamTransferCmd, Status,
    },
    wca::decode_and_check,
};

use crate::command_interface::command;

/// Result of the sign_stream_start command.
#[derive(Debug, Clone)]
pub enum SignStreamStartResult {
    Success,
}

/// Result of the sign_stream_transfer command.
#[derive(Debug, Clone)]
pub enum SignStreamTransferResult {
    /// Chunk received; more chunks expected.
    Success,
}

/// Result of the sign_stream_finalize command.
///
/// Finalization always requires user confirmation, so this only has a
/// `ConfirmationPending` variant.
#[derive(Debug, Clone)]
pub enum SignStreamFinalizeResult {
    ConfirmationPending {
        response_handle: Vec<u8>,
        confirmation_handle: Vec<u8>,
    },
}

/// A single input signature retrieved from the hardware after streaming signing.
#[derive(Debug, Clone)]
pub struct TxSignature {
    /// Compressed public key that produced this signature (33 bytes).
    pub pubkey: Vec<u8>,
    /// DER-encoded ECDSA signature + sighash type byte (max 73 bytes).
    pub signature: Vec<u8>,
}

// ============================================================================
// sign_stream_start
// ============================================================================

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn sign_stream_start(
    num_inputs: u32,
    num_outputs: u32,
    version: u32,
    lock_time: u32,
    payload_size: u32,
    btc_display_unit: fwpb::BtcDisplayUnit,
) -> Result<SignStreamStartResult, CommandError> {
    let apdu: apdu::Command = SignStreamStartCmd {
        num_inputs,
        num_outputs,
        version,
        lock_time,
        payload_size,
        btc_display_unit: btc_display_unit.into(),
        ..Default::default()
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    let message = wallet_rsp.msg.ok_or(CommandError::MissingMessage)?;

    if let Msg::SignStreamStartRsp(SignStreamStartRsp { rsp_status, .. }) = message {
        match SignStreamStartRspStatus::try_from(rsp_status) {
            Ok(SignStreamStartRspStatus::Unspecified) => Err(CommandError::UnspecifiedCommandError),
            Ok(SignStreamStartRspStatus::Success) => Ok(SignStreamStartResult::Success),
            Ok(SignStreamStartRspStatus::Error) => Err(CommandError::SignTransactionFailed),
            Ok(SignStreamStartRspStatus::Unauthenticated) => Err(CommandError::Unauthenticated),
            Err(_) => Err(CommandError::InvalidResponse),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

// ============================================================================
// sign_stream_transfer
// ============================================================================

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn sign_stream_transfer(
    sequence_id: u32,
    chunk_data: Vec<u8>,
) -> Result<SignStreamTransferResult, CommandError> {
    let apdu: apdu::Command = SignStreamTransferCmd {
        sequence_id,
        chunk_data,
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    // sign_stream_transfer_rsp uses global status code.
    let message = wallet_rsp.msg.ok_or(CommandError::MissingMessage)?;

    if let Msg::SignStreamTransferRsp(_) = message {
        Ok(SignStreamTransferResult::Success)
    } else {
        Err(CommandError::MissingMessage)
    }
}

// ============================================================================
// sign_stream_finalize
// ============================================================================

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn sign_stream_finalize(
    commitment_hash: Vec<u8>,
) -> Result<SignStreamFinalizeResult, CommandError> {
    let apdu: apdu::Command = SignStreamFinalizeCmd { commitment_hash }.try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    // Finalization always returns CONFIRMATION_PENDING.
    if Status::try_from(wallet_rsp.status) == Ok(Status::ConfirmationPending) {
        return Ok(SignStreamFinalizeResult::ConfirmationPending {
            response_handle: wallet_rsp.response_handle,
            confirmation_handle: wallet_rsp.confirmation_handle,
        });
    }

    Err(CommandError::InvalidResponse)
}

// ============================================================================
// get_tx_signature
// ============================================================================

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_tx_signature(input_index: u32) -> Result<TxSignature, CommandError> {
    let apdu: apdu::Command = GetTxSignatureCmd { input_index }.try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    let message = wallet_rsp.msg.ok_or(CommandError::MissingMessage)?;

    if let Msg::GetTxSignatureRsp(rsp) = message {
        Ok(TxSignature {
            pubkey: rsp.pubkey,
            signature: rsp.signature,
        })
    } else {
        Err(CommandError::MissingMessage)
    }
}

// ============================================================================
// get_tx_signatures_batch
// ============================================================================

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_tx_signatures_batch(start_index: u32, count: u32) -> Result<Vec<TxSignature>, CommandError> {
    let apdu: apdu::Command = GetTxSignaturesBatchCmd { start_index, count }.try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    let message = wallet_rsp.msg.ok_or(CommandError::MissingMessage)?;

    if let Msg::GetTxSignaturesBatchRsp(rsp) = message {
        Ok(rsp
            .signatures
            .into_iter()
            .map(|entry| TxSignature {
                pubkey: entry.pubkey,
                signature: entry.signature,
            })
            .collect())
    } else {
        Err(CommandError::MissingMessage)
    }
}

// ============================================================================
// Command macro invocations
// ============================================================================

command!(SignStreamStart = sign_stream_start -> SignStreamStartResult,
    num_inputs: u32,
    num_outputs: u32,
    version: u32,
    lock_time: u32,
    payload_size: u32,
    btc_display_unit: fwpb::BtcDisplayUnit
);

command!(SignStreamTransfer = sign_stream_transfer -> SignStreamTransferResult,
    sequence_id: u32,
    chunk_data: Vec<u8>
);

command!(SignStreamFinalize = sign_stream_finalize -> SignStreamFinalizeResult,
    commitment_hash: Vec<u8>
);

command!(GetTxSignature = get_tx_signature -> TxSignature,
    input_index: u32
);

command!(GetTxSignaturesBatch = get_tx_signatures_batch -> Vec<TxSignature>,
    start_index: u32,
    count: u32
);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{
            sign_stream_start_rsp::SignStreamStartRspStatus, wallet_rsp::Msg, BtcDisplayUnit,
            GetTxSignatureRsp, GetTxSignaturesBatchRsp, SignStreamStartRsp, SignStreamTransferRsp,
            Status, TxSignatureEntry, WalletRsp,
        },
    };

    use super::{
        GetTxSignature, GetTxSignaturesBatch, SignStreamFinalize, SignStreamFinalizeResult,
        SignStreamStart, SignStreamStartResult, SignStreamTransfer, SignStreamTransferResult,
        TxSignature,
    };

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    // ========================================================================
    // sign_stream_start Tests
    // ========================================================================

    #[test]
    fn sign_stream_start_success() -> Result<(), CommandError> {
        let command = SignStreamStart::new(200, 2, 2, 800_000, 13_948, BtcDisplayUnit::Satoshi);
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::SignStreamStartRsp(SignStreamStartRsp {
                rsp_status: SignStreamStartRspStatus::Success.into(),
            })),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Ok(State::Result {
                value: SignStreamStartResult::Success
            })
        ));

        Ok(())
    }

    #[test]
    fn sign_stream_start_unauthenticated() {
        let command = SignStreamStart::new(10, 2, 2, 0, 1000, BtcDisplayUnit::Satoshi);
        command.next(Vec::default()).unwrap();

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::SignStreamStartRsp(SignStreamStartRsp {
                rsp_status: SignStreamStartRspStatus::Unauthenticated.into(),
            })),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Err(CommandError::Unauthenticated)
        ));
    }

    // ========================================================================
    // sign_stream_transfer Tests
    // ========================================================================

    #[test]
    fn sign_stream_transfer_success() -> Result<(), CommandError> {
        let chunk = vec![0xFF; 452];
        let command = SignStreamTransfer::new(0, chunk);
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::SignStreamTransferRsp(SignStreamTransferRsp {})),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Ok(State::Result {
                value: SignStreamTransferResult::Success
            })
        ));

        Ok(())
    }

    // ========================================================================
    // sign_stream_finalize Tests
    // ========================================================================

    #[test]
    fn sign_stream_finalize_confirmation_pending() -> Result<(), CommandError> {
        let hash = vec![0xAB; 32];
        let command = SignStreamFinalize::new(hash);
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
                    SignStreamFinalizeResult::ConfirmationPending {
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

    // ========================================================================
    // get_tx_signature Tests
    // ========================================================================

    #[test]
    fn get_tx_signature_success() -> Result<(), CommandError> {
        let command = GetTxSignature::new(0);
        command.next(Vec::default())?;

        let pubkey = vec![0x02; 33];
        let signature = vec![0x30; 72];

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetTxSignatureRsp(GetTxSignatureRsp {
                pubkey: pubkey.clone(),
                signature: signature.clone(),
            })),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value:
                    TxSignature {
                        pubkey: pk,
                        signature: sig,
                    },
            }) => {
                assert_eq!(pk, pubkey);
                assert_eq!(sig, signature);
            }
            other => panic!("Expected TxSignature, got {:?}", other),
        }

        Ok(())
    }

    // ========================================================================
    // get_tx_signatures_batch Tests
    // ========================================================================

    #[test]
    fn get_tx_signatures_batch_success() -> Result<(), CommandError> {
        let command = GetTxSignaturesBatch::new(0, 3);
        command.next(Vec::default())?;

        let entries = vec![
            TxSignatureEntry {
                pubkey: vec![0x02; 33],
                signature: vec![0x30; 72],
            },
            TxSignatureEntry {
                pubkey: vec![0x03; 33],
                signature: vec![0x31; 71],
            },
            TxSignatureEntry {
                pubkey: vec![0x04; 33],
                signature: vec![0x32; 70],
            },
        ];

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetTxSignaturesBatchRsp(GetTxSignaturesBatchRsp {
                signatures: entries.clone(),
            })),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result { value: sigs }) => {
                assert_eq!(sigs.len(), 3);
                assert_eq!(sigs[0].pubkey, vec![0x02; 33]);
                assert_eq!(sigs[1].pubkey, vec![0x03; 33]);
                assert_eq!(sigs[2].signature, vec![0x32; 70]);
            }
            other => panic!("Expected Vec<TxSignature>, got {:?}", other),
        }

        Ok(())
    }
}
