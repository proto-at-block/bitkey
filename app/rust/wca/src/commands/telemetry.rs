use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{events_get_rsp::EventsGetRspStatus, wallet_rsp::Msg, EventsGetCmd, EventsGetRsp},
    wca,
};

use crate::command_interface::command;

pub struct EventFragment {
    pub fragment: Vec<u8>,
    pub remaining_size: i32,
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_events() -> Result<EventFragment, CommandError> {
    let apdu: apdu::Command = EventsGetCmd {}.try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::EventsGetRsp(EventsGetRsp {
        rsp_status,
        version,
        fragment,
    }) = message
    {
        match EventsGetRspStatus::from_i32(rsp_status) {
            Some(EventsGetRspStatus::Unspecified) => {
                return Err(CommandError::UnspecifiedCommandError)
            }
            Some(EventsGetRspStatus::Success) => {}
            Some(EventsGetRspStatus::Error) => return Err(CommandError::GeneralCommandError),
            None => return Err(CommandError::InvalidResponse),
        };

        if version != 1 {
            return Err(CommandError::VersionInvalid);
        }

        match fragment {
            Some(fragment) => Ok(EventFragment {
                fragment: fragment.data,
                remaining_size: fragment.remaining_size,
            }),
            None => Err(CommandError::InvalidResponse),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(GetEvents = get_events -> EventFragment);
