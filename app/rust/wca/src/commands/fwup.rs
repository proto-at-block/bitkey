use next_gen::generator;

use crate::{
    commands::McuRole,
    errors::CommandError,
    fwpb,
    fwpb::{
        fwup_finish_rsp::FwupFinishRspStatus, fwup_start_rsp::FwupStartRspStatus,
        fwup_transfer_rsp::FwupTransferRspStatus, wallet_rsp::Msg, FwupFinishCmd, FwupFinishRsp,
        FwupStartCmd, FwupStartRsp, FwupTransferCmd, FwupTransferRsp, Status,
    },
    wca::decode_and_check,
};

use crate::command_interface::command;

/// Result of the fwup_start command.
///
/// For W3 hardware, this may return `ConfirmationPending` which requires
/// the user to confirm on the device before completing the operation.
#[derive(Debug, Clone)]
pub enum FwupStartResult {
    Success {
        value: bool,
    },
    ConfirmationPending {
        response_handle: Vec<u8>,
        confirmation_handle: Vec<u8>,
    },
}

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
fn fwup_start(
    patch_size: Option<u32>,
    fwup_mode: FwupMode,
    mcu_role: McuRole,
) -> Result<FwupStartResult, CommandError> {
    let m: fwpb::FwupMode = fwup_mode.into();
    let mr: fwpb::McuRole = mcu_role.into();
    let apdu: apdu::Command = FwupStartCmd {
        mode: m as i32,
        patch_size: patch_size.unwrap_or(0),
        mcu_role: mr.into(),
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    // Check for confirmation pending status first (W3 two-tap flow)
    if Status::try_from(wallet_rsp.status) == Ok(Status::ConfirmationPending) {
        return Ok(FwupStartResult::ConfirmationPending {
            response_handle: wallet_rsp.response_handle,
            confirmation_handle: wallet_rsp.confirmation_handle,
        });
    }

    let message = wallet_rsp.msg.ok_or(CommandError::MissingMessage)?;

    if let Msg::FwupStartRsp(FwupStartRsp { rsp_status, .. }) = message {
        match FwupStartRspStatus::try_from(rsp_status) {
            Ok(FwupStartRspStatus::Unspecified) => Err(CommandError::UnspecifiedCommandError),
            Ok(FwupStartRspStatus::Success) => Ok(FwupStartResult::Success { value: true }),
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
    mcu_role: McuRole,
) -> Result<bool, CommandError> {
    let m: fwpb::FwupMode = fwup_mode.into();
    let mr: fwpb::McuRole = mcu_role.into();
    let apdu: apdu::Command = FwupTransferCmd {
        mode: m as i32,
        sequence_id,
        fwup_data,
        offset,
        mcu_role: mr.into(),
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = decode_and_check(response)?
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
    mcu_role: McuRole,
) -> Result<FwupFinishRspStatus, CommandError> {
    let m: fwpb::FwupMode = fwup_mode.into();
    let mr: fwpb::McuRole = mcu_role.into();
    let apdu: apdu::Command = FwupFinishCmd {
        mode: m as i32,
        app_properties_offset,
        signature_offset,
        bl_upgrade: false,
        mcu_role: mr.into(),
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::FwupFinishRsp(FwupFinishRsp { rsp_status, .. }) = message {
        FwupFinishRspStatus::try_from(rsp_status).map_err(|_| CommandError::InvalidResponse)
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(FwupStart = fwup_start -> FwupStartResult, patch_size: Option<u32>, fwup_mode: FwupMode, mcu_role: McuRole);
command!(FwupTransfer = fwup_transfer -> bool,
    sequence_id: u32,
    fwup_data: Vec<u8>,
    offset: u32,
    fwup_mode: FwupMode,
    mcu_role: McuRole
);
command!(FwupFinish = fwup_finish -> FwupFinishRspStatus,
    app_properties_offset: u32,
    signature_offset: u32,
    fwup_mode: FwupMode,
    mcu_role: McuRole
);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        commands::McuRole,
        errors::CommandError,
        fwpb::{
            fwup_start_rsp::FwupStartRspStatus, wallet_rsp::Msg, FwupStartRsp, Status, WalletRsp,
        },
    };

    use super::{FwupMode, FwupStart, FwupStartResult};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn fwup_start_success() -> Result<(), CommandError> {
        let command = FwupStart::new(None, FwupMode::Normal, McuRole::Core);
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::FwupStartRsp(FwupStartRsp {
                rsp_status: FwupStartRspStatus::Success.into(),
                max_chunk_size: 0,
            })),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Ok(State::Result {
                value: FwupStartResult::Success { value: true }
            })
        ));

        Ok(())
    }

    #[test]
    fn fwup_start_confirmation_pending() -> Result<(), CommandError> {
        let command = FwupStart::new(None, FwupMode::Normal, McuRole::Core);
        command.next(Vec::default())?;

        let response_handle = vec![0x01, 0x02, 0x03, 0x04];
        let confirmation_handle = vec![0x05, 0x06, 0x07, 0x08];

        let response = make_response(WalletRsp {
            status: Status::ConfirmationPending.into(),
            response_handle: response_handle.clone(),
            confirmation_handle: confirmation_handle.clone(),
            msg: None,
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value:
                    FwupStartResult::ConfirmationPending {
                        response_handle: rh,
                        confirmation_handle: ch,
                    },
            }) => {
                assert_eq!(rh, response_handle);
                assert_eq!(ch, confirmation_handle);
            }
            other => panic!("Expected ConfirmationPending, got {:?}", other),
        }

        Ok(())
    }

    #[test]
    fn fwup_start_error() {
        let command = FwupStart::new(None, FwupMode::Normal, McuRole::Core);
        command.next(Vec::default()).unwrap();

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::FwupStartRsp(FwupStartRsp {
                rsp_status: FwupStartRspStatus::Error.into(),
                max_chunk_size: 0,
            })),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Err(CommandError::GeneralCommandError)
        ));
    }
}
