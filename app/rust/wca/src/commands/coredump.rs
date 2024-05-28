use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{
        coredump_get_cmd::CoredumpGetType, coredump_get_rsp::CoredumpGetRspStatus, wallet_rsp::Msg,
        CoredumpGetCmd, CoredumpGetRsp,
    },
    wca,
};

use crate::command_interface::command;

pub struct CoredumpFragment {
    pub data: Vec<u8>,
    pub offset: i32,
    pub complete: bool,
    pub coredumps_remaining: i32,
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_coredump_fragment(offset: u32) -> Result<CoredumpFragment, CommandError> {
    let apdu: apdu::Command = CoredumpGetCmd {
        r#type: CoredumpGetType::Coredump as i32,
        offset,
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::CoredumpGetRsp(CoredumpGetRsp {
        rsp_status,
        coredump_fragment,
        coredump_count: _,
    }) = message
    {
        match CoredumpGetRspStatus::from_i32(rsp_status) {
            Some(CoredumpGetRspStatus::Unspecified) => {
                return Err(CommandError::UnspecifiedCommandError)
            }
            Some(CoredumpGetRspStatus::Success) => {}
            Some(CoredumpGetRspStatus::Error) => return Err(CommandError::GeneralCommandError),
            None => return Err(CommandError::InvalidResponse),
        };

        match coredump_fragment {
            Some(fragment) => Ok(CoredumpFragment {
                data: fragment.data,
                offset: fragment.offset,
                complete: fragment.complete,
                coredumps_remaining: fragment.coredumps_remaining,
            }),
            None => Err(CommandError::InvalidResponse),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_coredump_count() -> Result<u16, CommandError> {
    let apdu: apdu::Command = CoredumpGetCmd {
        r#type: CoredumpGetType::Count as i32,
        offset: 0,
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::CoredumpGetRsp(CoredumpGetRsp {
        rsp_status,
        coredump_fragment: _,
        coredump_count,
    }) = message
    {
        match CoredumpGetRspStatus::from_i32(rsp_status) {
            Some(CoredumpGetRspStatus::Unspecified) => {
                return Err(CommandError::UnspecifiedCommandError)
            }
            Some(CoredumpGetRspStatus::Success) => {}
            Some(CoredumpGetRspStatus::Error) => return Err(CommandError::GeneralCommandError),
            None => return Err(CommandError::InvalidResponse),
        };

        Ok(coredump_count as u16)
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(GetCoredumpFragment = get_coredump_fragment -> CoredumpFragment,
    offset: u32);
command!(GetCoredumpCount = get_coredump_count -> u16);
