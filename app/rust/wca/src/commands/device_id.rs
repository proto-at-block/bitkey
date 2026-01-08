use next_gen::generator;
use teltra::TelemetryIdentifiers;

use crate::{
    commands::metadata::FirmwareSlot,
    commands::metadata::McuName,
    commands::metadata::McuRole,
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

#[derive(Debug)]
pub struct TemplateMatchStats {
    pub pass_count: u32,
    pub firmware_version: String,
}

#[derive(Debug)]
pub struct BioMatchStats {
    pub pass_counts: Vec<TemplateMatchStats>,
    pub fail_count: u32,
}

#[derive(Debug)]
pub struct McuInfo {
    pub name: McuName,
    pub role: McuRole,
    pub firmware_version: String,
}

#[derive(Debug)]
pub struct DeviceInfoMcu {
    pub mcus: Vec<McuInfo>,
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
    pub bio_match_stats: Option<BioMatchStats>,
    pub device_info_mcus: Option<DeviceInfoMcu>,
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
        match TelemetryIdGetRspStatus::try_from(rsp_status) {
            Ok(TelemetryIdGetRspStatus::Unspecified) => {
                return Err(CommandError::UnspecifiedCommandError)
            }
            Ok(TelemetryIdGetRspStatus::Error) => return Err(CommandError::GeneralCommandError),
            Ok(TelemetryIdGetRspStatus::Success) => (),
            Err(_) => return Err(CommandError::InvalidResponse),
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
        bio_match_stats,
        device_info_mcus,
    }) = message
    {
        match DeviceInfoRspStatus::try_from(rsp_status) {
            Ok(DeviceInfoRspStatus::Unspecified) => {
                return Err(CommandError::UnspecifiedCommandError)
            }
            Ok(DeviceInfoRspStatus::Error) => return Err(CommandError::GeneralCommandError),
            Ok(DeviceInfoRspStatus::MetadataError) => return Err(CommandError::MetadataError),
            Ok(DeviceInfoRspStatus::BatteryError) => return Err(CommandError::BatteryError),
            Ok(DeviceInfoRspStatus::SerialError) => return Err(CommandError::SerialError),
            Ok(DeviceInfoRspStatus::Success) => (),
            _ => return Err(CommandError::InvalidResponse),
        };

        let version = match version {
            Some(v) => {
                format!("{}.{}.{}", v.major, v.minor, v.patch)
            }
            _ => return Err(CommandError::InvalidResponse),
        };

        let slot = match fwpb::FirmwareSlot::try_from(active_slot) {
            Ok(s) => match s {
                fwpb::FirmwareSlot::SlotA => FirmwareSlot::A,
                fwpb::FirmwareSlot::SlotB => FirmwareSlot::B,
                _ => return Err(CommandError::MetadataError),
            },
            Err(_) => return Err(CommandError::MetadataError),
        };

        let secure_boot_config = match fwpb::SecureBootConfig::try_from(secure_boot_config) {
            Ok(s) => match s {
                fwpb::SecureBootConfig::Unspecified => None,
                fwpb::SecureBootConfig::Dev => Some(SecureBootConfig::Dev),
                fwpb::SecureBootConfig::Prod => Some(SecureBootConfig::Prod),
            },
            Err(_) => None,
        };

        let battery_percent = (battery_charge / 1000) as f32;

        let bio_match_stats = match bio_match_stats {
            Some(stats) => {
                let mut pass_counts = Vec::new();
                for pass_count in stats.pass_counts {
                    if let Some(firmware_version) = pass_count.firmware_version {
                        let version_string = format!(
                            "{}.{}.{}",
                            firmware_version.major, firmware_version.minor, firmware_version.patch
                        );
                        pass_counts.push(TemplateMatchStats {
                            pass_count: pass_count.pass_count,
                            firmware_version: version_string,
                        });
                    }
                }
                let fail_count = stats.fail_count;
                Some(BioMatchStats {
                    pass_counts,
                    fail_count,
                })
            }
            None => None,
        };

        let mcus = match device_info_mcus.as_slice() {
            [] => None,
            _ => {
                let mut mcu_vect = Vec::new();
                for mcu in device_info_mcus {
                    if let Some(firmware_version) = mcu.version {
                        let version_string = format!(
                            "{}.{}.{}",
                            firmware_version.major, firmware_version.minor, firmware_version.patch
                        );
                        mcu_vect.push(McuInfo {
                            name: match fwpb::McuName::try_from(mcu.mcu_name) {
                                Ok(fwpb::McuName::Efr32) => McuName::Efr32,
                                Ok(fwpb::McuName::Stm32u5) => McuName::Stm32u5,
                                _ => McuName::Efr32,
                            },
                            role: match fwpb::McuRole::try_from(mcu.mcu_role) {
                                Ok(fwpb::McuRole::Core) => McuRole::Core,
                                Ok(fwpb::McuRole::Uxc) => McuRole::Uxc,
                                _ => McuRole::Core,
                            },
                            firmware_version: version_string,
                        });
                    }
                }
                Some(DeviceInfoMcu { mcus: mcu_vect })
            }
        };

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
            bio_match_stats,
            device_info_mcus: mcus,
        })
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(GetDeviceIdentifiers = device_id -> DeviceIdentifiers);
command!(GetTelemetryIdentifiers = telemetry_id -> TelemetryIdentifiers);
command!(GetDeviceInfo = device_info -> DeviceInfo);
