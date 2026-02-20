use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{
        sign_start_rsp::SignStartRspStatus, wallet_rsp::Msg, SignStartCmd, SignStartRsp,
        SignTransferCmd, Status,
    },
    wca::decode_and_check,
};

use crate::command_interface::command;

/// Result of the sign_start command.
///
/// For W3 hardware, this initiates the chunked PSBT transfer flow.
#[derive(Debug, Clone)]
pub enum SignStartResult {
    Success,
}

/// Result of the sign_transfer command.
///
/// For W3 hardware, returns `Success` while more chunks are expected,
/// or `ConfirmationPending` on the last chunk, requiring the user to
/// confirm on the device.
#[derive(Debug, Clone)]
pub enum SignTransferResult {
    /// Chunk received; more chunks expected.
    Success,
    /// Last chunk received, awaiting user confirmation.
    ConfirmationPending {
        response_handle: Vec<u8>,
        confirmation_handle: Vec<u8>,
    },
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn sign_start(psbt_size: u32) -> Result<SignStartResult, CommandError> {
    let apdu: apdu::Command = SignStartCmd { psbt_size }.try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    let message = wallet_rsp.msg.ok_or(CommandError::MissingMessage)?;

    if let Msg::SignStartRsp(SignStartRsp { rsp_status, .. }) = message {
        match SignStartRspStatus::try_from(rsp_status) {
            Ok(SignStartRspStatus::Unspecified) => Err(CommandError::UnspecifiedCommandError),
            Ok(SignStartRspStatus::Success) => Ok(SignStartResult::Success),
            Ok(SignStartRspStatus::Error) => Err(CommandError::GeneralCommandError),
            Ok(SignStartRspStatus::Unauthenticated) => Err(CommandError::Unauthenticated),
            Err(_) => Err(CommandError::InvalidResponse),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn sign_transfer(
    sequence_id: u32,
    chunk_data: Vec<u8>,
) -> Result<SignTransferResult, CommandError> {
    let apdu: apdu::Command = SignTransferCmd {
        sequence_id,
        chunk_data,
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    // Check for confirmation pending status (last chunk triggers user confirmation)
    if Status::try_from(wallet_rsp.status) == Ok(Status::ConfirmationPending) {
        return Ok(SignTransferResult::ConfirmationPending {
            response_handle: wallet_rsp.response_handle,
            confirmation_handle: wallet_rsp.confirmation_handle,
        });
    }

    // sign_transfer_rsp uses the global status code for response status.
    // decode_and_check handles all error cases via the global status field.
    let message = wallet_rsp.msg.ok_or(CommandError::MissingMessage)?;

    if let Msg::SignTransferRsp(_) = message {
        Ok(SignTransferResult::Success)
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(SignStart = sign_start -> SignStartResult, psbt_size: u32);
command!(SignTransfer = sign_transfer -> SignTransferResult, sequence_id: u32, chunk_data: Vec<u8>);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{
            sign_start_rsp::SignStartRspStatus, wallet_rsp::Msg, SignStartRsp, SignTransferRsp,
            Status, WalletRsp,
        },
    };

    use super::{SignStart, SignStartResult, SignTransfer, SignTransferResult};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    // ========================================================================
    // sign_start Tests
    // ========================================================================

    #[test]
    fn sign_start_success() -> Result<(), CommandError> {
        let command = SignStart::new(1000);
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::SignStartRsp(SignStartRsp {
                rsp_status: SignStartRspStatus::Success.into(),
                ..Default::default()
            })),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Ok(State::Result {
                value: SignStartResult::Success
            })
        ));

        Ok(())
    }

    #[test]
    fn sign_start_success_with_large_psbt_size() -> Result<(), CommandError> {
        let command = SignStart::new(5000);
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::SignStartRsp(SignStartRsp {
                rsp_status: SignStartRspStatus::Success.into(),
                ..Default::default()
            })),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Ok(State::Result {
                value: SignStartResult::Success
            })
        ));

        Ok(())
    }

    #[test]
    fn sign_start_unauthenticated_error() {
        let command = SignStart::new(1000);
        command.next(Vec::default()).unwrap();

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::SignStartRsp(SignStartRsp {
                rsp_status: SignStartRspStatus::Unauthenticated.into(),
                ..Default::default()
            })),
            ..Default::default()
        });

        let result = command.next(response);
        assert!(matches!(result, Err(CommandError::Unauthenticated)));
    }

    #[test]
    fn sign_start_general_error() {
        let command = SignStart::new(1000);
        command.next(Vec::default()).unwrap();

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::SignStartRsp(SignStartRsp {
                rsp_status: SignStartRspStatus::Error.into(),
                ..Default::default()
            })),
            ..Default::default()
        });

        let result = command.next(response);
        assert!(matches!(result, Err(CommandError::GeneralCommandError)));
    }

    #[test]
    fn sign_start_unspecified_error() {
        let command = SignStart::new(1000);
        command.next(Vec::default()).unwrap();

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::SignStartRsp(SignStartRsp {
                rsp_status: SignStartRspStatus::Unspecified.into(),
                ..Default::default()
            })),
            ..Default::default()
        });

        let result = command.next(response);
        assert!(matches!(result, Err(CommandError::UnspecifiedCommandError)));
    }

    #[test]
    fn sign_start_missing_message() {
        let command = SignStart::new(1000);
        command.next(Vec::default()).unwrap();

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: None,
            ..Default::default()
        });

        let result = command.next(response);
        assert!(matches!(result, Err(CommandError::MissingMessage)));
    }

    #[test]
    fn sign_start_invalid_response_status() {
        let command = SignStart::new(1000);
        command.next(Vec::default()).unwrap();

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::SignStartRsp(SignStartRsp {
                rsp_status: 999, // Invalid enum value
                ..Default::default()
            })),
            ..Default::default()
        });

        let result = command.next(response);
        assert!(matches!(result, Err(CommandError::InvalidResponse)));
    }

    // ========================================================================
    // sign_transfer Tests
    // ========================================================================

    #[test]
    fn sign_transfer_success() -> Result<(), CommandError> {
        let chunk_data = b"test chunk data".to_vec();
        let command = SignTransfer::new(0, chunk_data);
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::SignTransferRsp(SignTransferRsp::default())),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Ok(State::Result {
                value: SignTransferResult::Success
            })
        ));

        Ok(())
    }

    #[test]
    fn sign_transfer_success_with_large_chunk() -> Result<(), CommandError> {
        let chunk_data = vec![0xFF; 452]; // Max chunk size
        let command = SignTransfer::new(5, chunk_data);
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::SignTransferRsp(SignTransferRsp::default())),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Ok(State::Result {
                value: SignTransferResult::Success
            })
        ));

        Ok(())
    }

    #[test]
    fn sign_transfer_confirmation_pending() -> Result<(), CommandError> {
        let command = SignTransfer::new(5, vec![0x01, 0x02, 0x03, 0x04]);
        command.next(Vec::default())?;

        let response_handle = vec![0x01, 0x02, 0x03, 0x04];
        let confirmation_handle = vec![0x05, 0x06, 0x07, 0x08];

        let response = make_response(WalletRsp {
            status: Status::ConfirmationPending.into(),
            response_handle: response_handle.clone(),
            confirmation_handle: confirmation_handle.clone(),
            msg: Some(Msg::SignTransferRsp(SignTransferRsp {})),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value:
                    SignTransferResult::ConfirmationPending {
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
    fn sign_transfer_missing_message() {
        let command = SignTransfer::new(0, b"data".to_vec());
        command.next(Vec::default()).unwrap();

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: None,
            ..Default::default()
        });

        let result = command.next(response);
        assert!(matches!(result, Err(CommandError::MissingMessage)));
    }

    #[test]
    fn sign_transfer_empty_chunk() -> Result<(), CommandError> {
        let command = SignTransfer::new(0, Vec::new());
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::SignTransferRsp(SignTransferRsp::default())),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Ok(State::Result {
                value: SignTransferResult::Success
            })
        ));

        Ok(())
    }
}
