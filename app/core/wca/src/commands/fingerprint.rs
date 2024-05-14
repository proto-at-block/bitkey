use next_gen::generator;
use prost::Message;
use std::result::Result;

use crate::fwpb::GetEnrolledFingerprintsCmd;
use crate::{command_interface::command, errors::CommandError};

use crate::{
    fwpb::{
        get_fingerprint_enrollment_status_rsp::FingerprintEnrollmentStatus,
        start_fingerprint_enrollment_rsp::StartFingerprintEnrollmentRspStatus, wallet_rsp::Msg,
        DeleteFingerprintCmd, FingerprintHandle, GetEnrolledFingerprintsRsp,
        GetFingerprintEnrollmentStatusCmd, GetFingerprintEnrollmentStatusRsp,
        SetFingerprintLabelCmd, StartFingerprintEnrollmentCmd, StartFingerprintEnrollmentRsp,
        WalletRsp,
    },
    wca,
};

pub struct EnrolledFingerprints {
    pub max_count: u32,
    pub fingerprints: Vec<FingerprintHandle>,
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_fingerprint_enrollment_status() -> Result<FingerprintEnrollmentStatus, CommandError> {
    let apdu: apdu::Command = GetFingerprintEnrollmentStatusCmd {}.try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = WalletRsp::decode(std::io::Cursor::new(response.data))?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::GetFingerprintEnrollmentStatusRsp(GetFingerprintEnrollmentStatusRsp {
        fingerprint_status,
        ..
    }) = message
    {
        Ok(FingerprintEnrollmentStatus::from_i32(fingerprint_status)
            .ok_or(CommandError::MissingMessage)?)
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
        match StartFingerprintEnrollmentRspStatus::from_i32(rsp_status) {
            Some(StartFingerprintEnrollmentRspStatus::Unspecified) => {
                Err(CommandError::UnspecifiedCommandError)
            }
            Some(StartFingerprintEnrollmentRspStatus::Success) => Ok(true),
            Some(StartFingerprintEnrollmentRspStatus::Error) => {
                Err(CommandError::GeneralCommandError)
            }
            Some(StartFingerprintEnrollmentRspStatus::Unauthenticated) => {
                Err(CommandError::Unauthenticated)
            }
            None => Ok(false),
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
    let message = WalletRsp::decode(std::io::Cursor::new(response.data))?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::GetEnrolledFingerprintsRsp(GetEnrolledFingerprintsRsp { max_count, handles }) =
        message
    {
        Ok(EnrolledFingerprints {
            max_count,
            fingerprints: handles,
        })
    } else {
        Err(CommandError::MissingMessage)
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn delete_fingerprint(index: u32) -> Result<bool, CommandError> {
    let apdu: apdu::Command = DeleteFingerprintCmd { index }.try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    wca::decode_and_check(response).map(|_| true)
}

command!(StartFingerprintEnrollment = start_fingerprint_enrollment -> bool, index: u32, label: String);
command!(GetFingerprintEnrollmentStatus = get_fingerprint_enrollment_status -> FingerprintEnrollmentStatus);
command!(SetFingerprintLabel = set_fingerprint_label -> bool, index: u32, label: String);
command!(GetEnrolledFingerprints = get_enrolled_fingerprints -> EnrolledFingerprints);
command!(DeleteFingerprint = delete_fingerprint -> bool, index: u32);
