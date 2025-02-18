use next_gen::generator;

use crate::command_interface::command;
use crate::errors::CommandError;
use crate::fwpb;
use crate::fwpb::meta_rsp::MetaRspStatus;
use crate::fwpb::wallet_rsp::Msg;
use crate::fwpb::{MetaCmd, MetaRsp};
use crate::wca;

#[derive(Debug)]
pub enum FirmwareSlot {
    A,
    B,
}

#[derive(Debug)]
pub struct FirmwareMetadata {
    pub active_slot: FirmwareSlot,
    pub git_id: String,
    pub git_branch: String,
    pub version: String,
    pub build: String,
    pub timestamp: u64,
    pub hash: Vec<u8>,
    pub hw_revision: String,
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn metadata() -> Result<FirmwareMetadata, CommandError> {
    let apdu: apdu::Command = MetaCmd {}.try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::MetaRsp(MetaRsp {
        rsp_status,
        meta_bl: _,
        meta_slot_a,
        meta_slot_b,
        active_slot,
    }) = message
    {
        match MetaRspStatus::try_from(rsp_status) {
            Ok(MetaRspStatus::Unspecified) => return Err(CommandError::UnspecifiedCommandError),
            Ok(MetaRspStatus::Error) => return Err(CommandError::GeneralCommandError),
            Ok(MetaRspStatus::Success) => (),
            Err(_) => return Err(CommandError::InvalidResponse),
        };

        // Translate from protobuf types.

        let output_slot: FirmwareSlot;
        let meta = match fwpb::FirmwareSlot::try_from(active_slot) {
            Ok(fwpb::FirmwareSlot::SlotA) => match meta_slot_a {
                Some(meta_slot_a) => {
                    output_slot = FirmwareSlot::A;
                    meta_slot_a
                }
                _ => return Err(CommandError::InvalidResponse),
            },
            Ok(fwpb::FirmwareSlot::SlotB) => match meta_slot_b {
                Some(meta_slot_b) => {
                    output_slot = FirmwareSlot::B;
                    meta_slot_b
                }
                _ => return Err(CommandError::InvalidResponse),
            },
            _ => return Err(CommandError::InvalidResponse),
        };

        let version = match meta.version {
            Some(v) => {
                format!("{}.{}.{}", v.major, v.minor, v.patch)
            }
            _ => return Err(CommandError::InvalidResponse),
        };

        Ok(FirmwareMetadata {
            active_slot: output_slot,
            git_id: meta.git_id,
            git_branch: meta.git_branch,
            version,
            build: meta.build,
            timestamp: meta.timestamp,
            hash: meta.hash,
            hw_revision: meta.hw_revision,
        })
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(GetFirmwareMetadata = metadata -> FirmwareMetadata);
