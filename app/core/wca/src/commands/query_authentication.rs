use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{
        query_authentication_rsp::QueryAuthenticationRspStatus, wallet_rsp::Msg,
        QueryAuthenticationCmd, QueryAuthenticationRsp,
    },
    wca,
};

use crate::command_interface::command;

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn query_authentication() -> Result<bool, CommandError> {
    let apdu: apdu::Command = QueryAuthenticationCmd {}.try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::QueryAuthenticationRsp(QueryAuthenticationRsp { rsp_status, .. }) = message {
        match QueryAuthenticationRspStatus::from_i32(rsp_status) {
            Some(QueryAuthenticationRspStatus::Unspecified) => {
                Err(CommandError::UnspecifiedCommandError)
            }
            Some(QueryAuthenticationRspStatus::Authenticated) => Ok(true),
            Some(QueryAuthenticationRspStatus::Unauthenticated) => Ok(false),
            _ => Err(CommandError::InvalidResponse),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(QueryAuthentication = query_authentication -> bool);
