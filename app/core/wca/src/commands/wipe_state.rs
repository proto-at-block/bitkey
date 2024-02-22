use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{wallet_rsp::Msg, wipe_state_rsp::WipeStateRspStatus, WipeStateCmd, WipeStateRsp},
    wca::decode_and_check,
};

use crate::command_interface::command;

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn wipe_state() -> Result<bool, CommandError> {
    let apdu: apdu::Command = WipeStateCmd {}.try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::WipeStateRsp(WipeStateRsp { rsp_status, .. }) = message {
        match WipeStateRspStatus::from_i32(rsp_status) {
            Some(WipeStateRspStatus::Unspecified) => Err(CommandError::UnspecifiedCommandError),
            Some(WipeStateRspStatus::Success) => Ok(true),
            Some(WipeStateRspStatus::Error) => Err(CommandError::GeneralCommandError),
            Some(WipeStateRspStatus::Unauthenticated) => Err(CommandError::Unauthenticated),
            None => Err(CommandError::InvalidResponse),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(WipeState = wipe_state -> bool);
