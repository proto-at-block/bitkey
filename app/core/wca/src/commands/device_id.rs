use next_gen::generator;
use teltra::TelemetryIdentifiers;

use crate::{
    commands::metadata::FirmwareSlot,
    errors::CommandError,
    fwpb,
    fwpb::device_info_rsp::DeviceInfoRspStatus,
    fwpb::telemetry_id_get_rsp::TelemetryIdGetRspStatus,
    fwpb::{
        wallet_rsp::Msg, DeviceIdCmd, DeviceIdRsp, DeviceInfoCmd, DeviceInfoRsp, TelemetryIdGetCmd,
        TelemetryIdGetRsp,
    },
    wca,
};

use crate::command_interface::command;

pub struct DeviceIdentifiers {
    pub mlb_serial: String,
    pub assy_serial: String,
}

#[derive(Debug)]
pub enum SecureBootConfig {
    Dev,
    Prod,
}

pub struct DeviceInfo {
    pub version: String,
    pub serial: String,
    pub sw_type: String,
    pub hw_revision: String,
    pub active_slot: FirmwareSlot,
    pub battery_charge: f32,
    pub vcell: u32,
    pub avg_current_ma: i32,
    pub battery_cycles: u32,
    pub secure_boot_config: Option<SecureBootConfig>,
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn device_id() -> Result<DeviceIdentifiers, CommandError> {
    let apdu: apdu::Command = DeviceIdCmd {}.try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::DeviceIdRsp(DeviceIdRsp {
        mlb_serial,
        mlb_serial_valid,
        assy_serial,
        assy_serial_valid,
    }) = message
    {
        if mlb_serial_valid && assy_serial_valid {
            Ok(DeviceIdentifiers {
                mlb_serial,
                assy_serial,
            })
        } else {
            Err(CommandError::InvalidResponse)
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn telemetry_id() -> Result<TelemetryIdentifiers, CommandError> {
    let apdu: apdu::Command = TelemetryIdGetCmd {}.try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::TelemetryIdGetRsp(TelemetryIdGetRsp {
        rsp_status,
        serial,
        version,
        sw_type,
        hw_revision,
    }) = message
    {
        match TelemetryIdGetRspStatus::from_i32(rsp_status) {
            Some(TelemetryIdGetRspStatus::Unspecified) => {
                return Err(CommandError::UnspecifiedCommandError)
            }
            Some(TelemetryIdGetRspStatus::Error) => return Err(CommandError::GeneralCommandError),
            Some(TelemetryIdGetRspStatus::Success) => (),
            _ => return Err(CommandError::InvalidResponse),
        };

        let version = match version {
            Some(v) => {
                format!("{}.{}.{}", v.major, v.minor, v.patch)
            }
            _ => return Err(CommandError::InvalidResponse),
        };

        Ok(TelemetryIdentifiers {
            serial,
            version,
            sw_type,
            hw_revision,
        })
    } else {
        Err(CommandError::MissingMessage)
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn device_info() -> Result<DeviceInfo, CommandError> {
    let apdu: apdu::Command = DeviceInfoCmd {}.try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::DeviceInfoRsp(DeviceInfoRsp {
        rsp_status,
        version,
        serial,
        sw_type,
        hw_revision,
        active_slot,
        battery_charge,
        vcell,
        avg_current_ma,
        battery_cycles,
        secure_boot_config,
    }) = message
    {
        match DeviceInfoRspStatus::from_i32(rsp_status) {
            Some(DeviceInfoRspStatus::Unspecified) => {
                return Err(CommandError::UnspecifiedCommandError)
            }
            Some(DeviceInfoRspStatus::Error) => return Err(CommandError::GeneralCommandError),
            Some(DeviceInfoRspStatus::MetadataError) => return Err(CommandError::MetadataError),
            Some(DeviceInfoRspStatus::BatteryError) => return Err(CommandError::BatteryError),
            Some(DeviceInfoRspStatus::SerialError) => return Err(CommandError::SerialError),
            Some(DeviceInfoRspStatus::Success) => (),
            _ => return Err(CommandError::InvalidResponse),
        };

        let version = match version {
            Some(v) => {
                format!("{}.{}.{}", v.major, v.minor, v.patch)
            }
            _ => return Err(CommandError::InvalidResponse),
        };

        let slot = match fwpb::FirmwareSlot::from_i32(active_slot) {
            Some(s) => match s {
                fwpb::FirmwareSlot::SlotA => FirmwareSlot::A,
                fwpb::FirmwareSlot::SlotB => FirmwareSlot::B,
                _ => return Err(CommandError::MetadataError),
            },
            _ => return Err(CommandError::MetadataError),
        };

        let secure_boot_config =
            fwpb::SecureBootConfig::from_i32(secure_boot_config).and_then(|s| match s {
                fwpb::SecureBootConfig::Unspecified => None,
                fwpb::SecureBootConfig::Dev => Some(SecureBootConfig::Dev),
                fwpb::SecureBootConfig::Prod => Some(SecureBootConfig::Prod),
            });

        let battery_percent = (battery_charge / 1000) as f32;

        Ok(DeviceInfo {
            version,
            serial,
            sw_type,
            hw_revision,
            active_slot: slot,
            battery_charge: battery_percent,
            vcell,
            avg_current_ma,
            battery_cycles,
            secure_boot_config,
        })
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(GetDeviceIdentifiers = device_id -> DeviceIdentifiers);
command!(GetTelemetryIdentifiers = telemetry_id -> TelemetryIdentifiers);
command!(GetDeviceInfo = device_info -> DeviceInfo);
