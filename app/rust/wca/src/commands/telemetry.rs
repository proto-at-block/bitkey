use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{self, events_get_rsp::EventsGetRspStatus, wallet_rsp::Msg, EventsGetCmd, EventsGetRsp},
    wca,
};

use super::metadata::McuRole;

use crate::command_interface::command;

pub struct EventFragment {
    pub fragment: Vec<u8>,
    pub remaining_size: i32,
    pub mcu_role: Option<McuRole>,
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_events(mcu_role: McuRole) -> Result<EventFragment, CommandError> {
    let mr: fwpb::McuRole = mcu_role.into();
    let apdu: apdu::Command = EventsGetCmd {
        mcu_role: mr as i32,
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::EventsGetRsp(EventsGetRsp {
        rsp_status,
        version,
        fragment,
        mcu_role,
    }) = message
    {
        match EventsGetRspStatus::try_from(rsp_status) {
            Ok(EventsGetRspStatus::Unspecified) => {
                return Err(CommandError::UnspecifiedCommandError)
            }
            Ok(EventsGetRspStatus::Success) => {}
            Ok(EventsGetRspStatus::Error) => return Err(CommandError::GeneralCommandError),
            Err(_) => return Err(CommandError::InvalidResponse),
        };

        if version != 1 {
            return Err(CommandError::VersionInvalid);
        }

        let mcu_role = match fwpb::McuRole::try_from(mcu_role) {
            Ok(fwpb::McuRole::Core) => Some(McuRole::Core),
            Ok(fwpb::McuRole::Uxc) => Some(McuRole::Uxc),
            _ => None,
        };

        match fragment {
            Some(fragment) => Ok(EventFragment {
                fragment: fragment.data,
                remaining_size: fragment.remaining_size,
                mcu_role,
            }),
            None => Err(CommandError::InvalidResponse),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(GetEvents = get_events -> EventFragment, mcu_role: McuRole);
