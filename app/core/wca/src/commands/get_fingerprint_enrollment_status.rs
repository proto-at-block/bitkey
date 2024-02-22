use next_gen::generator;
use prost::Message;

use crate::{
    errors::CommandError,
    fwpb::{
        get_fingerprint_enrollment_status_rsp::FingerprintEnrollmentStatus, wallet_rsp::Msg,
        GetFingerprintEnrollmentStatusCmd, GetFingerprintEnrollmentStatusRsp, WalletRsp,
    },
};

use crate::command_interface::command;

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

command!(GetFingerprintEnrollmentStatus = get_fingerprint_enrollment_status -> FingerprintEnrollmentStatus);
