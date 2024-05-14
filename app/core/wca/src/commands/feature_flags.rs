use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{
        feature_flags_get_rsp::FeatureFlagsGetRspStatus,
        feature_flags_set_rsp::FeatureFlagsSetRspStatus, wallet_rsp::Msg, FeatureFlag,
        FeatureFlagCfg, FeatureFlagsGetCmd, FeatureFlagsGetRsp, FeatureFlagsSetCmd,
        FeatureFlagsSetRsp,
    },
    wca,
};

use crate::command_interface::command;

#[derive(Clone)]
pub enum FirmwareFeatureFlag {
    Telemetry,
    DeviceInfoFlag,
    RateLimitTemplateUpdate,
    Unlock,
    MultipleFingerprints,
}

macro_rules! convert_enum {
    ($src:ident, $dst:ident, $($variant:ident),*) => {
        impl From<$src> for $dst {
            fn from(src: $src) -> Self {
                match src {
                    $($src::$variant => $dst::$variant,)*
                }
            }
        }

        impl FirmwareFeatureFlag {
            pub fn to_i32(&self) -> i32 {
                match self {
                    $($dst::$variant => FeatureFlag::$variant as i32,)*
                }
            }
        }
    };
}

convert_enum!(
    FeatureFlag,
    FirmwareFeatureFlag,
    Telemetry,
    DeviceInfoFlag,
    RateLimitTemplateUpdate,
    Unlock,
    MultipleFingerprints
);

impl FirmwareFeatureFlag {
    pub fn from_i32(i: i32) -> Option<FirmwareFeatureFlag> {
        FeatureFlag::from_i32(i).map(FirmwareFeatureFlag::from)
    }
}

pub struct FirmwareFeatureFlagCfg {
    pub flag: FirmwareFeatureFlag,
    pub enabled: bool,
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn feature_flags_get() -> Result<Vec<FirmwareFeatureFlagCfg>, CommandError> {
    let apdu: apdu::Command = FeatureFlagsGetCmd {}.try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::FeatureFlagsGetRsp(FeatureFlagsGetRsp { rsp_status, flags }) = message {
        if rsp_status != FeatureFlagsGetRspStatus::Success as i32 {
            return Err(CommandError::InvalidResponse);
        }

        let mut feature_flags = Vec::new();

        for flag in flags {
            // If we don't understand the flag, just ignore it
            let f = match FirmwareFeatureFlag::from_i32(flag.flag) {
                Some(f) => f,
                None => continue,
            };

            feature_flags.push(FirmwareFeatureFlagCfg {
                flag: f,
                enabled: flag.enabled,
            });
        }

        Ok(feature_flags)
    } else {
        Err(CommandError::MissingMessage)
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn feature_flags_set(flags: Vec<FirmwareFeatureFlag>, enabled: bool) -> Result<bool, CommandError> {
    let apdu: apdu::Command = FeatureFlagsSetCmd {
        flags: flags
            .into_iter()
            .map(|flag| FeatureFlagCfg {
                flag: flag.to_i32(),
                enabled,
            })
            .collect(),
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::FeatureFlagsSetRsp(FeatureFlagsSetRsp { rsp_status }) = message {
        match FeatureFlagsSetRspStatus::from_i32(rsp_status) {
            Some(FeatureFlagsSetRspStatus::Success) => Ok(true),
            _ => Err(CommandError::InvalidResponse),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(GetFirmwareFeatureFlags = feature_flags_get -> Vec<FirmwareFeatureFlagCfg>);
command!(SetFirmwareFeatureFlags = feature_flags_set -> bool,
    flags: Vec<FirmwareFeatureFlag>,
    enabled: bool
);
