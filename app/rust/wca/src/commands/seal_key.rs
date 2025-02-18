use next_gen::generator;
use prost::Message;

use crate::{
    errors::CommandError,
    fwpb::{seal_csek_rsp::SealCsekRspStatus, wallet_rsp::Msg, SealCsekCmd, SealCsekRsp},
    wca,
};

use crate::command_interface::command;

use super::{SealedKey, UnsealedKey};

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn seal_key(key: UnsealedKey) -> Result<SealedKey, CommandError> {
    let apdu: apdu::Command = SealCsekCmd {
        unsealed_csek: key.to_vec(),
        csek: None, // TODO(W-5088)
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::SealCsekRsp(SealCsekRsp {
        rsp_status,
        sealed_csek,
    }) = message
    {
        match SealCsekRspStatus::try_from(rsp_status) {
            Ok(SealCsekRspStatus::Unspecified) => Err(CommandError::UnspecifiedCommandError),
            Ok(SealCsekRspStatus::Success) => sealed_csek
                .ok_or(CommandError::GeneralCommandError)
                .map(|s| s.encode_to_vec()),
            Ok(SealCsekRspStatus::Error) => Err(CommandError::GeneralCommandError),
            Ok(SealCsekRspStatus::SealError) => Err(CommandError::SealCsekResponseSealError),
            Err(_) => Err(CommandError::InvalidResponse),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(SealKey = seal_key -> SealedKey,
    unsealed_csek: UnsealedKey
);
