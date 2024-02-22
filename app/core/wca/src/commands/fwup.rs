use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb,
    fwpb::{
        fwup_finish_rsp::FwupFinishRspStatus, fwup_start_rsp::FwupStartRspStatus,
        fwup_transfer_rsp::FwupTransferRspStatus, wallet_rsp::Msg, FwupFinishCmd, FwupFinishRsp,
        FwupStartCmd, FwupStartRsp, FwupTransferCmd, FwupTransferRsp,
    },
    wca,
};

use crate::command_interface::command;

#[derive(Clone, Debug)]
pub enum FwupMode {
    Normal,
    Delta,
}
impl From<FwupMode> for fwpb::FwupMode {
    fn from(val: FwupMode) -> Self {
        match val {
            FwupMode::Normal => fwpb::FwupMode::Normal,
            FwupMode::Delta => fwpb::FwupMode::DeltaOneshot,
        }
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn fwup_start(patch_size: Option<u32>, fwup_mode: FwupMode) -> Result<bool, CommandError> {
    let m: fwpb::FwupMode = fwup_mode.into();
    let apdu: apdu::Command = FwupStartCmd {
        mode: m as i32,
        patch_size: patch_size.unwrap_or(0),
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::FwupStartRsp(FwupStartRsp { rsp_status, .. }) = message {
        match FwupStartRspStatus::from_i32(rsp_status) {
            Some(FwupStartRspStatus::Unspecified) => Err(CommandError::UnspecifiedCommandError),
            Some(FwupStartRspStatus::Success) => Ok(true),
            Some(FwupStartRspStatus::Error) => Err(CommandError::GeneralCommandError),
            Some(FwupStartRspStatus::Unauthenticated) => Err(CommandError::Unauthenticated),
            None => Err(CommandError::InvalidResponse),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn fwup_transfer(
    sequence_id: u32,
    fwup_data: Vec<u8>,
    offset: u32,
    fwup_mode: FwupMode,
) -> Result<bool, CommandError> {
    let m: fwpb::FwupMode = fwup_mode.into();
    let apdu: apdu::Command = FwupTransferCmd {
        mode: m as i32,
        sequence_id,
        fwup_data,
        offset,
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::FwupTransferRsp(FwupTransferRsp { rsp_status, .. }) = message {
        match FwupTransferRspStatus::from_i32(rsp_status) {
            Some(FwupTransferRspStatus::Unspecified) => Err(CommandError::UnspecifiedCommandError),
            Some(FwupTransferRspStatus::Success) => Ok(true),
            Some(FwupTransferRspStatus::Error) => Err(CommandError::GeneralCommandError),
            Some(FwupTransferRspStatus::Unauthenticated) => Err(CommandError::Unauthenticated),
            None => Err(CommandError::InvalidResponse),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn fwup_finish(
    app_properties_offset: u32,
    signature_offset: u32,
    fwup_mode: FwupMode,
) -> Result<FwupFinishRspStatus, CommandError> {
    let m: fwpb::FwupMode = fwup_mode.into();
    let apdu: apdu::Command = FwupFinishCmd {
        mode: m as i32,
        app_properties_offset,
        signature_offset,
        bl_upgrade: false,
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::FwupFinishRsp(FwupFinishRsp { rsp_status, .. }) = message {
        FwupFinishRspStatus::from_i32(rsp_status).ok_or(CommandError::InvalidResponse)
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(FwupStart = fwup_start -> bool, patch_size: Option<u32>, fwup_mode: FwupMode);
command!(FwupTransfer = fwup_transfer -> bool,
    sequence_id: u32,
    fwup_data: Vec<u8>,
    offset: u32,
    fwup_mode: FwupMode
);
command!(FwupFinish = fwup_finish -> FwupFinishRspStatus,
    app_properties_offset: u32,
    signature_offset: u32,
    fwup_mode: FwupMode
);
