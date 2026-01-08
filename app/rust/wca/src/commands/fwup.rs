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
        mcu_role: fwpb::McuRole::Core.into(),
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::FwupStartRsp(FwupStartRsp { rsp_status, .. }) = message {
        match FwupStartRspStatus::try_from(rsp_status) {
            Ok(FwupStartRspStatus::Unspecified) => Err(CommandError::UnspecifiedCommandError),
            Ok(FwupStartRspStatus::Success) => Ok(true),
            Ok(FwupStartRspStatus::Error) => Err(CommandError::GeneralCommandError),
            Ok(FwupStartRspStatus::Unauthenticated) => Err(CommandError::Unauthenticated),
            Err(_) => Err(CommandError::InvalidResponse),
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
        mcu_role: fwpb::McuRole::Core.into(),
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::FwupTransferRsp(FwupTransferRsp { rsp_status, .. }) = message {
        match FwupTransferRspStatus::try_from(rsp_status) {
            Ok(FwupTransferRspStatus::Unspecified) => Err(CommandError::UnspecifiedCommandError),
            Ok(FwupTransferRspStatus::Success) => Ok(true),
            Ok(FwupTransferRspStatus::Error) => Err(CommandError::GeneralCommandError),
            Ok(FwupTransferRspStatus::Unauthenticated) => Err(CommandError::Unauthenticated),
            Err(_) => Err(CommandError::InvalidResponse),
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
        mcu_role: fwpb::McuRole::Core.into(),
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::FwupFinishRsp(FwupFinishRsp { rsp_status, .. }) = message {
        FwupFinishRspStatus::try_from(rsp_status).map_err(|_| CommandError::InvalidResponse)
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
