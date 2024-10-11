use bitcoin::secp256k1::ecdsa::Signature;
use next_gen::generator;

use crate::wca;
use crate::{
    errors::CommandError,
    fwpb::{self, derive_and_sign_rsp::DeriveAndSignRspStatus, DeriveKeyDescriptorAndSignCmd},
};

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
pub(crate) fn derive_and_sign(
    hash: Vec<u8>,
    derivation_path: fwpb::DerivationPath,
    async_sign: bool,
) -> Result<Signature, CommandError> {
    let apdu: apdu::Command = DeriveKeyDescriptorAndSignCmd {
        derivation_path: Some(derivation_path),
        hash,
        async_sign,
    }
    .try_into()?;
    let data = yield_!(apdu.clone().into());

    let response = apdu::Response::from(data);
    let message_outer = wca::decode_and_check(response)?;
    let message = message_outer.msg.ok_or(CommandError::MissingMessage)?;

    // Handle backwards compatibility with old firmware.
    if got_sync_signing_status(&message) {
        // Firmware did sync signing, so we should return the result.
        // This is to ensure that we don't break compatibility with old firmware, which will always return sync results.
        return extract_sync_signing_result(message);
    }

    // Async signing
    let check_async_signing_result = |message| {
        if let fwpb::wallet_rsp::Msg::DeriveAndSignRsp(fwpb::DeriveAndSignRsp {
            status: _, // Async signing uses new-style firmware status codes.
            signature,
        }) = message
        {
            Ok(Signature::from_compact(&signature)?)
        } else {
            Err(CommandError::InvalidResponse)
        }
    };

    match crate::fwpb::Status::from_i32(message_outer.status) {
        Some(crate::fwpb::Status::InProgress) => {}
        Some(crate::fwpb::Status::Success) => {
            return check_async_signing_result(message);
        }
        _ => return Err(CommandError::InvalidResponse),
    }

    // Poll every 50ms until we get a response. Give up after 10 tries.
    for _ in 0..10 {
        std::thread::sleep(std::time::Duration::from_millis(50));

        let data = yield_!(apdu.clone().into()); // The firmware will return InProgress until signing completes.
        let response = apdu::Response::from(data);
        let message_outer = wca::decode_and_check(response)?;
        let message = message_outer.msg.ok_or(CommandError::MissingMessage)?;

        if message_outer.status == crate::fwpb::Status::InProgress as i32 {
            continue;
        }

        return check_async_signing_result(message);
    }
    Err(CommandError::Timeout)
}

fn extract_sync_signing_result(message: fwpb::wallet_rsp::Msg) -> Result<Signature, CommandError> {
    if let fwpb::wallet_rsp::Msg::DeriveAndSignRsp(fwpb::DeriveAndSignRsp { status, signature }) =
        message
    {
        match DeriveAndSignRspStatus::from_i32(status) {
            Some(DeriveAndSignRspStatus::Success) => Ok(Signature::from_compact(&signature)?),
            Some(DeriveAndSignRspStatus::DerivationFailed) => {
                Err(CommandError::KeyGenerationFailed)
            }
            Some(DeriveAndSignRspStatus::Error) => Err(CommandError::GeneralCommandError),
            Some(DeriveAndSignRspStatus::Unauthenticated) => Err(CommandError::Unauthenticated),
            Some(DeriveAndSignRspStatus::Unspecified) => Err(CommandError::UnspecifiedCommandError),
            None => Err(CommandError::InvalidResponse),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

// Return true if the firmware returned anything within the sync signing field.
fn got_sync_signing_status(message: &fwpb::wallet_rsp::Msg) -> bool {
    if let fwpb::wallet_rsp::Msg::DeriveAndSignRsp(fwpb::DeriveAndSignRsp {
        status,
        signature: _,
    }) = message
    {
        match DeriveAndSignRspStatus::from_i32(*status) {
            Some(DeriveAndSignRspStatus::Unspecified) => false,
            _ => true,
        }
    } else {
        false
    }
}
