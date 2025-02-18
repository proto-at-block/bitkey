use next_gen::generator;
use prost::Message;
use std::result::Result;

use crate::fwpb::get_fingerprint_enrollment_status_rsp::GetFingerprintEnrollmentStatusRspStatus;
use crate::fwpb::GetEnrolledFingerprintsCmd;
use crate::{command_interface::command, errors::CommandError};

use crate::{
    fwpb::{
        get_fingerprint_enrollment_status_rsp::FingerprintEnrollmentStatus,
        start_fingerprint_enrollment_rsp::StartFingerprintEnrollmentRspStatus, wallet_rsp::Msg,
        CancelFingerprintEnrollmentCmd, DeleteFingerprintCmd, FingerprintHandle,
        GetEnrolledFingerprintsRsp, GetFingerprintEnrollmentStatusCmd,
        GetFingerprintEnrollmentStatusRsp, SetFingerprintLabelCmd, StartFingerprintEnrollmentCmd,
        StartFingerprintEnrollmentRsp, WalletRsp,
    },
    wca,
};

pub struct EnrolledFingerprints {
    pub max_count: u32,
    pub fingerprints: Vec<FingerprintHandle>,
}

pub struct EnrollmentDiagnostics {
    pub finger_coverage_valid: bool,
    pub finger_coverage: u32,

    pub common_mode_noise_valid: bool,
    pub common_mode_noise: u32,

    pub image_quality_valid: bool,
    pub image_quality: u32,

    pub sensor_coverage_valid: bool,
    pub sensor_coverage: u32,

    pub template_data_update_valid: bool,
    pub template_data_update: u32,
}

pub struct FingerprintEnrollmentResult {
    pub status: FingerprintEnrollmentStatus,
    pub pass_count: Option<u32>,
    pub fail_count: Option<u32>,
    pub diagnostics: Option<EnrollmentDiagnostics>,
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_fingerprint_enrollment_status(
    is_enrollment_context_aware: bool,
) -> Result<FingerprintEnrollmentResult, CommandError> {
    let apdu: apdu::Command = GetFingerprintEnrollmentStatusCmd {
        app_knows_about_this_field: is_enrollment_context_aware,
    }
    .try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = WalletRsp::decode(std::io::Cursor::new(response.data))?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::GetFingerprintEnrollmentStatusRsp(GetFingerprintEnrollmentStatusRsp {
        rsp_status,
        fingerprint_status,
        pass_count,
        fail_count,
        diagnostics,
    }) = message
    {
        if let Ok(status) = GetFingerprintEnrollmentStatusRspStatus::try_from(rsp_status) {
            if status != GetFingerprintEnrollmentStatusRspStatus::Success {
                return Err(CommandError::GeneralCommandError);
            }
            let fingerprint_status = FingerprintEnrollmentStatus::try_from(fingerprint_status)
                .map_err(|_| CommandError::GeneralCommandError)?;
            Ok(FingerprintEnrollmentResult {
                status: fingerprint_status,
                pass_count: Some(pass_count),
                fail_count: Some(fail_count),
                diagnostics: diagnostics.map(|d| EnrollmentDiagnostics {
                    finger_coverage_valid: d.finger_coverage_valid,
                    finger_coverage: d.finger_coverage,
                    common_mode_noise_valid: d.common_mode_noise_valid,
                    common_mode_noise: d.common_mode_noise,
                    image_quality_valid: d.image_quality_valid,
                    image_quality: d.image_quality,
                    sensor_coverage_valid: d.sensor_coverage_valid,
                    sensor_coverage: d.sensor_coverage,
                    template_data_update_valid: d.template_data_update_valid,
                    template_data_update: d.template_data_update,
                }),
            })
        } else {
            println!("Got a bad status: {}", rsp_status);
            Err(CommandError::BadStatus(rsp_status))
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn start_fingerprint_enrollment(index: u32, label: String) -> Result<bool, CommandError> {
    let apdu: apdu::Command = StartFingerprintEnrollmentCmd {
        handle: Some(FingerprintHandle { index, label }),
    }
    .try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::StartFingerprintEnrollmentRsp(StartFingerprintEnrollmentRsp {
        rsp_status, ..
    }) = message
    {
        match StartFingerprintEnrollmentRspStatus::try_from(rsp_status) {
            Ok(StartFingerprintEnrollmentRspStatus::Unspecified) => {
                Err(CommandError::UnspecifiedCommandError)
            }
            Ok(StartFingerprintEnrollmentRspStatus::Success) => Ok(true),
            Ok(StartFingerprintEnrollmentRspStatus::Error) => {
                Err(CommandError::GeneralCommandError)
            }
            Ok(StartFingerprintEnrollmentRspStatus::Unauthenticated) => {
                Err(CommandError::Unauthenticated)
            }
            Err(_) => Ok(false),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn set_fingerprint_label(index: u32, label: String) -> Result<bool, CommandError> {
    let apdu: apdu::Command = SetFingerprintLabelCmd {
        handle: Some(FingerprintHandle { index, label }),
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);

    wca::decode_and_check(response).map(|_| true)
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_enrolled_fingerprints() -> Result<EnrolledFingerprints, CommandError> {
    let apdu: apdu::Command = GetEnrolledFingerprintsCmd {}.try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    match message {
        Msg::GetEnrolledFingerprintsRsp(GetEnrolledFingerprintsRsp { max_count, handles }) => {
            Ok(EnrolledFingerprints {
                max_count,
                fingerprints: handles,
            })
        }
        _ => Err(CommandError::MissingMessage),
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn delete_fingerprint(index: u32) -> Result<bool, CommandError> {
    let apdu: apdu::Command = DeleteFingerprintCmd { index }.try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);

    let result = wca::decode_and_check(response);
    match result {
        // Attempting to delete a template or label that doesn't exist can be
        // assumed to be successful.
        Err(crate::errors::CommandError::FileNotFound) => Ok(true),
        _ => result.map(|_| true),
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn cancel_fingerprint_enrollment() -> Result<bool, CommandError> {
    let apdu: apdu::Command = CancelFingerprintEnrollmentCmd {}.try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    wca::decode_and_check(response).map(|_| true)
}

command!(StartFingerprintEnrollment = start_fingerprint_enrollment -> bool, index: u32, label: String);
command!(GetFingerprintEnrollmentStatus = get_fingerprint_enrollment_status -> FingerprintEnrollmentResult, is_enrollment_context_aware: bool);
command!(SetFingerprintLabel = set_fingerprint_label -> bool, index: u32, label: String);
command!(GetEnrolledFingerprints = get_enrolled_fingerprints -> EnrolledFingerprints);
command!(DeleteFingerprint = delete_fingerprint -> bool, index: u32);
command!(CancelFingerprintEnrollment = cancel_fingerprint_enrollment -> bool);
