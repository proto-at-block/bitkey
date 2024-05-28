use next_gen::generator;
use prost::Message;

use crate::{
    errors::CommandError,
    fwpb::{
        unseal_csek_rsp::UnsealCsekRspStatus, wallet_rsp::Msg, SealedData, UnsealCsekCmd,
        UnsealCsekRsp,
    },
    wca,
};

use super::{SealedKey, UnsealedKey};

use crate::command_interface::command;

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn unseal_key(sealed_key: SealedKey) -> Result<UnsealedKey, CommandError> {
    let apdu: apdu::Command = UnsealCsekCmd {
        sealed_csek: Some(SealedData::decode(&*sealed_key)?),
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::UnsealCsekRsp(UnsealCsekRsp {
        rsp_status,
        unsealed_csek,
    }) = message
    {
        match UnsealCsekRspStatus::from_i32(rsp_status) {
            Some(UnsealCsekRspStatus::Unspecified) => Err(CommandError::UnspecifiedCommandError),
            Some(UnsealCsekRspStatus::Success) => {
                unsealed_csek.try_into().map_err(CommandError::KeySizeError)
            }
            Some(UnsealCsekRspStatus::Error) => Err(CommandError::GeneralCommandError),
            Some(UnsealCsekRspStatus::UnsealError) => {
                Err(CommandError::SealCsekResponseUnsealError)
            }
            Some(UnsealCsekRspStatus::Unauthenticated) => {
                Err(CommandError::SealCsekResponseUnauthenticatedError)
            }
            None => Err(CommandError::InvalidResponse),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(UnsealKey = unseal_key -> UnsealedKey,
    sealed_key: SealedKey
);
