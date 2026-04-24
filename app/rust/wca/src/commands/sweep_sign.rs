//! W3 sweep signing commands.
//!
//! Sweep signing produces HW signatures for inputs at a non-current (OLD)
//! account index whose outputs spend into the current account's fresh receive
//! address (index 0). Used by lost-app recovery sweeps where the HW is retained
//! across an account bump and must sign legacy UTXOs using the old account's
//! keys.
//!
//! The regular `sign_tx_request` / `sign_stream_*` paths strictly reject any
//! input whose derivation_path[2] differs from the on-device
//! keyset.account_index. Sweeps must go through these dedicated commands.
//!
//! Security rationale:
//! - `keyset.account_index` is immutable device-side ground truth, so a
//!   compromised app cannot lie about the current account.
//! - Firmware validates that every input references the provided
//!   `old_account_index` and that the tx has exactly one output whose
//!   scriptPubKey matches the firmware-derived P2WSH at the current
//!   keyset's fresh receive m/84'/coin'/current'/0/0.
//! - The HW only signs with its own key derived from master; if the app
//!   supplies wrong sweep xpubs, the resulting witness script is wrong and the
//!   signature fails on-chain — no funds can be stolen.

use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{
        self, sweep_sign_stream_start_rsp::SweepSignStreamStartRspStatus, wallet_cmd, wallet_rsp,
        SignTxInput, SignTxOutput, Status, SweepSignCmd, SweepSignStreamStartCmd,
        SweepSignStreamStartRsp,
    },
    wca::{decode_and_check, encode_proto_cmd},
};

use crate::command_interface::command;

use super::sign_tx_request::{SignTxInputData, SignTxOutputData, SignTxRequestResult};

/// BIP32 xpub material (pubkey + chaincode) at account depth 3.
#[derive(Debug, Clone)]
pub struct SweepXpub {
    /// 33-byte compressed pubkey.
    pub pubkey: Vec<u8>,
    /// 32-byte chaincode.
    pub chaincode: Vec<u8>,
}

const XPUB_PUBKEY_LEN: usize = 33;
const XPUB_CHAINCODE_LEN: usize = 32;
const MAX_SIGN_TX_ENTRIES: usize = 5;
const TXID_LEN: usize = 32;
const MAX_DERIVATION_PATH: usize = 5;
const MAX_SPK_LEN: usize = 35;

fn validate_xpub(xpub: &SweepXpub) -> Result<(), CommandError> {
    if xpub.pubkey.len() != XPUB_PUBKEY_LEN || xpub.chaincode.len() != XPUB_CHAINCODE_LEN {
        return Err(CommandError::InvalidArguments);
    }
    Ok(())
}

// ============================================================================
// sweep_sign (one-shot, ≤5 inputs/outputs)
// ============================================================================

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn sweep_sign(
    old_account_index: u32,
    app_xpub: SweepXpub,
    server_xpub: SweepXpub,
    version: u32,
    lock_time: u32,
    inputs: Vec<SignTxInputData>,
    outputs: Vec<SignTxOutputData>,
    btc_display_unit: fwpb::BtcDisplayUnit,
) -> Result<SignTxRequestResult, CommandError> {
    validate_xpub(&app_xpub)?;
    validate_xpub(&server_xpub)?;

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

    let msg = wallet_cmd::Msg::SweepSignCmd(SweepSignCmd {
        sweep_app_xpub_pubkey: app_xpub.pubkey,
        sweep_app_xpub_chaincode: app_xpub.chaincode,
        sweep_server_xpub_pubkey: server_xpub.pubkey,
        sweep_server_xpub_chaincode: server_xpub.chaincode,
        old_account_index,
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
    });

    let apdus = encode_proto_cmd(msg)?;
    let mut data = Vec::new();
    for apdu in apdus {
        data = yield_!(apdu.into());
    }

    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    if Status::try_from(wallet_rsp.status) == Ok(Status::ConfirmationPending) {
        return Ok(SignTxRequestResult::ConfirmationPending {
            response_handle: wallet_rsp.response_handle,
            confirmation_handle: wallet_rsp.confirmation_handle,
        });
    }

    Err(CommandError::InvalidResponse)
}

command!(SweepSignRequest = sweep_sign -> SignTxRequestResult,
    old_account_index: u32,
    app_xpub: SweepXpub,
    server_xpub: SweepXpub,
    version: u32,
    lock_time: u32,
    inputs: Vec<SignTxInputData>,
    outputs: Vec<SignTxOutputData>,
    btc_display_unit: fwpb::BtcDisplayUnit
);

// ============================================================================
// sweep_sign_stream_start (streaming)
// ============================================================================

/// Result of the sweep_sign_stream_start command. Mirrors
/// `SignStreamStartResult` — subsequent `sign_stream_transfer` and
/// `sign_stream_finalize` commands are reused for the streaming payload.
#[derive(Debug, Clone)]
pub enum SweepSignStreamStartResult {
    Success,
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn sweep_sign_stream_start(
    old_account_index: u32,
    app_xpub: SweepXpub,
    server_xpub: SweepXpub,
    num_inputs: u32,
    num_outputs: u32,
    version: u32,
    lock_time: u32,
    payload_size: u32,
    btc_display_unit: fwpb::BtcDisplayUnit,
) -> Result<SweepSignStreamStartResult, CommandError> {
    validate_xpub(&app_xpub)?;
    validate_xpub(&server_xpub)?;

    let apdu: apdu::Command = SweepSignStreamStartCmd {
        sweep_app_xpub_pubkey: app_xpub.pubkey,
        sweep_app_xpub_chaincode: app_xpub.chaincode,
        sweep_server_xpub_pubkey: server_xpub.pubkey,
        sweep_server_xpub_chaincode: server_xpub.chaincode,
        old_account_index,
        num_inputs,
        num_outputs,
        version,
        lock_time,
        payload_size,
        btc_display_unit: btc_display_unit.into(),
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    let message = wallet_rsp.msg.ok_or(CommandError::MissingMessage)?;

    if let wallet_rsp::Msg::SweepSignStreamStartRsp(SweepSignStreamStartRsp {
        rsp_status, ..
    }) = message
    {
        match SweepSignStreamStartRspStatus::try_from(rsp_status) {
            Ok(SweepSignStreamStartRspStatus::Unspecified) => {
                Err(CommandError::UnspecifiedCommandError)
            }
            Ok(SweepSignStreamStartRspStatus::Success) => Ok(SweepSignStreamStartResult::Success),
            Ok(SweepSignStreamStartRspStatus::Error) => Err(CommandError::SignTransactionFailed),
            Ok(SweepSignStreamStartRspStatus::Unauthenticated) => {
                Err(CommandError::Unauthenticated)
            }
            Err(_) => Err(CommandError::InvalidResponse),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(SweepSignStreamStart = sweep_sign_stream_start -> SweepSignStreamStartResult,
    old_account_index: u32,
    app_xpub: SweepXpub,
    server_xpub: SweepXpub,
    num_inputs: u32,
    num_outputs: u32,
    version: u32,
    lock_time: u32,
    payload_size: u32,
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

    use super::{
        SignTxInputData, SignTxOutputData, SignTxRequestResult, SweepSignRequest, SweepXpub,
    };

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    fn ok_xpub() -> SweepXpub {
        SweepXpub {
            pubkey: vec![0x02; 33],
            chaincode: vec![0xab; 32],
        }
    }

    #[test]
    fn sweep_sign_confirmation_pending() -> Result<(), CommandError> {
        let inputs = vec![SignTxInputData {
            prev_txid: vec![0u8; 32],
            prev_index: 0,
            sequence: 0xFFFFFFFD,
            amount: 100_000,
            // account index 3 (old), spending from [84'/0'/3'/0/7]
            derivation_path: vec![84 | (1 << 31), 0 | (1 << 31), 3 | (1 << 31), 0, 7],
        }];
        let outputs = vec![SignTxOutputData {
            amount: 90_000,
            destination_spk: vec![0u8; 34],
            // output to current account (e.g. 4) at address index 0
            derivation_path: vec![84 | (1 << 31), 0 | (1 << 31), 4 | (1 << 31), 0, 0],
            has_derivation_path: true,
        }];

        let command = SweepSignRequest::new(
            3,
            ok_xpub(),
            ok_xpub(),
            2,
            0,
            inputs,
            outputs,
            BtcDisplayUnit::Satoshi,
        );
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

    #[test]
    fn sweep_sign_rejects_bad_app_xpub_size() {
        let inputs = vec![SignTxInputData {
            prev_txid: vec![0u8; 32],
            prev_index: 0,
            sequence: 0xFFFFFFFD,
            amount: 100_000,
            derivation_path: vec![84 | (1 << 31), 0 | (1 << 31), 3 | (1 << 31), 0, 7],
        }];
        let outputs = vec![SignTxOutputData {
            amount: 90_000,
            destination_spk: vec![0u8; 22],
            derivation_path: vec![84 | (1 << 31), 0 | (1 << 31), 4 | (1 << 31), 0, 0],
            has_derivation_path: true,
        }];
        let bad_xpub = SweepXpub {
            pubkey: vec![0x02; 32], // wrong length
            chaincode: vec![0xab; 32],
        };

        let command = SweepSignRequest::new(
            3,
            bad_xpub,
            ok_xpub(),
            2,
            0,
            inputs,
            outputs,
            BtcDisplayUnit::Satoshi,
        );
        assert!(matches!(
            command.next(Vec::default()),
            Err(CommandError::InvalidArguments)
        ));
    }
}
