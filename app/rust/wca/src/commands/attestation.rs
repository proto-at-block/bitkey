use next_gen::generator;

use crate::attestation::{Attestation, AttestationError};
use crate::fwpb::cert_get_cmd::CertType;
use crate::fwpb::cert_get_rsp::CertGetRspStatus;
use crate::fwpb::{
    wallet_rsp::Msg, CertGetCmd, CertGetRsp, HardwareAttestationCmd, HardwareAttestationRsp,
};
use crate::{command, errors::CommandError, wca};

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_cert(kind: CertType) -> Result<Vec<u8>, CommandError> {
    let apdu: apdu::Command = CertGetCmd { kind: kind.into() }.try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::CertGetRsp(CertGetRsp { rsp_status, cert }) = message {
        match CertGetRspStatus::try_from(rsp_status) {
            Ok(CertGetRspStatus::Unspecified) => return Err(CommandError::UnspecifiedCommandError),
            Ok(CertGetRspStatus::Success) => {}
            Ok(CertGetRspStatus::CertReadFail) => return Err(CommandError::CertReadFail),
            Ok(CertGetRspStatus::Unimplemented) => return Err(CommandError::Unimplemented),
            Err(_) => return Err(CommandError::InvalidResponse),
        };

        Ok(cert)
    } else {
        Err(CommandError::MissingMessage)
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn sign_verify_attestation_challenge(
    device_identity_der: Vec<u8>,
    challenge: Vec<u8>,
) -> Result<bool, CommandError> {
    let apdu: apdu::Command = HardwareAttestationCmd {
        nonce: challenge.clone(),
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::HardwareAttestationRsp(HardwareAttestationRsp { signature }) = message {
        Attestation {}
            .verify_challenge_response(challenge.clone(), device_identity_der, signature)
            .map_err(|e| match e {
                AttestationError::VerificationFailure => CommandError::SignatureInvalid,
                AttestationError::ParseFailure => CommandError::CertReadFail,
                _ => CommandError::AttestationError,
            })?;
        Ok(true)
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(GetCert = get_cert -> Vec<u8>,
    kind: CertType
);

command!(SignVerifyAttestationChallenge = sign_verify_attestation_challenge -> bool,
    device_identity_der: Vec<u8>,
    challenge: Vec<u8>
);
