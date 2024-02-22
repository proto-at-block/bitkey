use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{
        start_fingerprint_enrollment_rsp::StartFingerprintEnrollmentRspStatus, wallet_rsp::Msg,
        StartFingerprintEnrollmentCmd, StartFingerprintEnrollmentRsp,
    },
    wca,
};

use crate::command_interface::command;

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn start_fingerprint_enrollment() -> Result<bool, CommandError> {
    let apdu: apdu::Command = StartFingerprintEnrollmentCmd {}.try_into()?;
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

command!(StartFingerprintEnrollment = start_fingerprint_enrollment -> bool);
