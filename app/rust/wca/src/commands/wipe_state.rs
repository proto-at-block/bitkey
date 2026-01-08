use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{
        wallet_rsp::Msg, wipe_state_rsp::WipeStateRspStatus, Status, WipeStateCmd, WipeStateRsp,
    },
    wca::decode_and_check,
};

use crate::command_interface::command;

#[derive(Debug, Clone)]
pub enum WipeStateResult {
    Success {
        value: bool,
    },
    ConfirmationPending {
        response_handle: Vec<u8>,
        confirmation_handle: Vec<u8>,
    },
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn wipe_state() -> Result<WipeStateResult, CommandError> {
    let apdu: apdu::Command = WipeStateCmd {}.try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    if Status::try_from(wallet_rsp.status) == Ok(Status::ConfirmationPending) {
        return Ok(WipeStateResult::ConfirmationPending {
            response_handle: wallet_rsp.response_handle,
            confirmation_handle: wallet_rsp.confirmation_handle,
        });
    }

    let message = wallet_rsp.msg.ok_or(CommandError::MissingMessage)?;

    if let Msg::WipeStateRsp(WipeStateRsp { rsp_status, .. }) = message {
        match WipeStateRspStatus::try_from(rsp_status) {
            Ok(WipeStateRspStatus::Unspecified) => Err(CommandError::UnspecifiedCommandError),
            Ok(WipeStateRspStatus::Success) => Ok(WipeStateResult::Success { value: true }),
            Ok(WipeStateRspStatus::Error) => Err(CommandError::GeneralCommandError),
            Ok(WipeStateRspStatus::Unauthenticated) => Err(CommandError::Unauthenticated),
            Err(_) => Err(CommandError::InvalidResponse),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(WipeState = wipe_state -> WipeStateResult);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{
            wallet_rsp::Msg, wipe_state_rsp::WipeStateRspStatus, Status, WalletRsp, WipeStateRsp,
        },
    };

    use super::{WipeState, WipeStateResult};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn wipe_state_old_firmware_success() -> Result<(), CommandError> {
        let command = WipeState::new();
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::WipeStateRsp(WipeStateRsp {
                rsp_status: WipeStateRspStatus::Success.into(),
            })),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Ok(State::Result {
                value: WipeStateResult::Success { value: true }
            })
        ));

        Ok(())
    }

    #[test]
    fn wipe_state_new_firmware_confirmation_pending() -> Result<(), CommandError> {
        let command = WipeState::new();
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
                    WipeStateResult::ConfirmationPending {
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
}
