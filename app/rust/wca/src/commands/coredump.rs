use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{
        self, coredump_get_cmd::CoredumpGetType, coredump_get_rsp::CoredumpGetRspStatus,
        wallet_rsp::Msg, CoredumpGetCmd, CoredumpGetRsp,
    },
    wca,
};

use super::metadata::{McuName, McuRole};

use crate::command_interface::command;

pub struct CoredumpFragment {
    pub data: Vec<u8>,
    pub offset: i32,
    pub complete: bool,
    pub coredumps_remaining: i32,
    pub mcu_name: Option<McuName>,
    pub mcu_role: Option<McuRole>,
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_coredump_fragment(offset: u32, mcu_role: McuRole) -> Result<CoredumpFragment, CommandError> {
    let mr: fwpb::McuRole = mcu_role.into();
    let apdu: apdu::Command = CoredumpGetCmd {
        r#type: CoredumpGetType::Coredump as i32,
        offset,
        mcu_role: mr as i32,
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
        mcu_role,
        mcu_name,
    }) = message
    {
        match CoredumpGetRspStatus::try_from(rsp_status) {
            Ok(CoredumpGetRspStatus::Unspecified) => {
                return Err(CommandError::UnspecifiedCommandError)
            }
            Ok(CoredumpGetRspStatus::Success) => {}
            Ok(CoredumpGetRspStatus::Error) => return Err(CommandError::GeneralCommandError),
            Err(_) => return Err(CommandError::InvalidResponse),
        };

        let mcu_name = match fwpb::McuName::try_from(mcu_name) {
            Ok(fwpb::McuName::Efr32) => Some(McuName::Efr32),
            Ok(fwpb::McuName::Stm32u5) => Some(McuName::Stm32u5),
            _ => None,
        };

        let mcu_role = match fwpb::McuRole::try_from(mcu_role) {
            Ok(fwpb::McuRole::Core) => Some(McuRole::Core),
            Ok(fwpb::McuRole::Uxc) => Some(McuRole::Uxc),
            _ => None,
        };

        match coredump_fragment {
            Some(fragment) => Ok(CoredumpFragment {
                data: fragment.data,
                offset: fragment.offset,
                complete: fragment.complete,
                coredumps_remaining: fragment.coredumps_remaining,
                mcu_role,
                mcu_name,
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
        mcu_role: fwpb::McuRole::Core.into(),
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
        mcu_role: _,
        mcu_name: _,
    }) = message
    {
        match CoredumpGetRspStatus::try_from(rsp_status) {
            Ok(CoredumpGetRspStatus::Unspecified) => {
                return Err(CommandError::UnspecifiedCommandError)
            }
            Ok(CoredumpGetRspStatus::Success) => {}
            Ok(CoredumpGetRspStatus::Error) => return Err(CommandError::GeneralCommandError),
            Err(_) => return Err(CommandError::InvalidResponse),
        };

        Ok(coredump_count as u16)
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(GetCoredumpFragment = get_coredump_fragment -> CoredumpFragment,
    offset: u32, mcu_role: McuRole);
command!(GetCoredumpCount = get_coredump_count -> u16);
