use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{
        get_confirmation_result_rsp::Result as ConfirmationResult, wallet_rsp::Msg,
        GetConfirmationResultCmd, GetConfirmationResultRsp,
    },
    wca::decode_and_check,
};

use crate::command_interface::command;

#[derive(Debug, Clone)]
pub enum ConfirmedCommandResult {
    WipeState { success: bool },
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_confirmation_result(
    response_handle: Vec<u8>,
    confirmation_handle: Vec<u8>,
) -> Result<ConfirmedCommandResult, CommandError> {
    let apdu: apdu::Command = GetConfirmationResultCmd {
        response_handle,
        confirmation_handle,
    }
    .try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::GetConfirmationResultRsp(GetConfirmationResultRsp { result }) = message {
        match result {
            Some(ConfirmationResult::WipeStateResult(wipe_rsp)) => {
                use crate::fwpb::wipe_state_rsp::WipeStateRspStatus;
                match WipeStateRspStatus::try_from(wipe_rsp.rsp_status) {
                    Ok(WipeStateRspStatus::Unspecified) => {
                        Err(CommandError::UnspecifiedCommandError)
                    }
                    Ok(WipeStateRspStatus::Success) => {
                        Ok(ConfirmedCommandResult::WipeState { success: true })
                    }
                    Ok(WipeStateRspStatus::Error) => Err(CommandError::GeneralCommandError),
                    Ok(WipeStateRspStatus::Unauthenticated) => Err(CommandError::Unauthenticated),
                    Err(_) => Err(CommandError::InvalidResponse),
                }
            }
            None => Err(CommandError::MissingMessage),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(GetConfirmationResult = get_confirmation_result -> ConfirmedCommandResult, response_handle: Vec<u8>, confirmation_handle: Vec<u8>);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{
            get_confirmation_result_rsp::Result as ConfirmationResult, wallet_rsp::Msg,
            wipe_state_rsp::WipeStateRspStatus, GetConfirmationResultRsp, Status, WalletRsp,
            WipeStateRsp,
        },
    };

    use super::{ConfirmedCommandResult, GetConfirmationResult};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn get_confirmation_result_wipe_state_success() -> Result<(), CommandError> {
        let command =
            GetConfirmationResult::new(vec![0x01, 0x02, 0x03, 0x04], vec![0x05, 0x06, 0x07, 0x08]);
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetConfirmationResultRsp(GetConfirmationResultRsp {
                result: Some(ConfirmationResult::WipeStateResult(WipeStateRsp {
                    rsp_status: WipeStateRspStatus::Success.into(),
                })),
            })),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Ok(State::Result {
                value: ConfirmedCommandResult::WipeState { success: true }
            })
        ));

        Ok(())
    }

    #[test]
    fn get_confirmation_result_missing_result() -> Result<(), CommandError> {
        let command =
            GetConfirmationResult::new(vec![0x01, 0x02, 0x03, 0x04], vec![0x05, 0x06, 0x07, 0x08]);
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetConfirmationResultRsp(GetConfirmationResultRsp {
                result: None,
            })),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Err(CommandError::MissingMessage)
        ));

        Ok(())
    }
}
