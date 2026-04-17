use next_gen::generator;
use prost::Message;

use crate::{
    errors::CommandError,
    fwpb::{LostAppRecoveryCmd, SealedData, Status},
    wca::decode_and_check,
};

use crate::command_interface::command;

#[derive(Debug, Clone)]
pub enum LostAppRecoveryResult {
    ConfirmationPending {
        response_handle: Vec<u8>,
        confirmation_handle: Vec<u8>,
    },
}

/// Sends sealed SSEK bytes to firmware for the lost app recovery composite.
/// The `sealed_ssek` parameter is a protobuf-encoded `sealed_data` message
/// (same format as stored in SealedSsek/SealedData ByteString on the app side).
#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn lost_app_recovery(sealed_ssek: Vec<u8>) -> Result<LostAppRecoveryResult, CommandError> {
    let sealed_data =
        SealedData::decode(&*sealed_ssek).map_err(|_| CommandError::InvalidArguments)?;

    let apdu: apdu::Command = LostAppRecoveryCmd {
        sealed_ssek: Some(sealed_data),
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    if Status::try_from(wallet_rsp.status) == Ok(Status::ConfirmationPending) {
        return Ok(LostAppRecoveryResult::ConfirmationPending {
            response_handle: wallet_rsp.response_handle,
            confirmation_handle: wallet_rsp.confirmation_handle,
        });
    }

    Err(CommandError::InvalidResponse)
}

command!(LostAppRecovery = lost_app_recovery -> LostAppRecoveryResult,
    sealed_ssek: Vec<u8>
);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{Status, WalletRsp},
    };

    use super::{LostAppRecovery, LostAppRecoveryResult};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn lost_app_recovery_confirmation_pending() -> Result<(), CommandError> {
        // Build a valid protobuf-encoded sealed_data
        let sealed_data = crate::fwpb::SealedData {
            data: vec![0u8; 32],
            nonce: vec![0u8; 12],
            tag: vec![0u8; 16],
        };
        let sealed_ssek = sealed_data.encode_to_vec();

        let command = LostAppRecovery::new(sealed_ssek);
        command.next(Vec::default())?;

        let response_handle = vec![0x01, 0x02, 0x03, 0x04];
        let confirmation_handle = vec![0x05, 0x06, 0x07, 0x08];

        let response = make_response(WalletRsp {
            status: Status::ConfirmationPending.into(),
            response_handle: response_handle.clone(),
            confirmation_handle: confirmation_handle.clone(),
            msg: None,
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value:
                    LostAppRecoveryResult::ConfirmationPending {
                        response_handle: rh,
                        confirmation_handle: ch,
                    },
            }) => {
                assert_eq!(rh, response_handle);
                assert_eq!(ch, confirmation_handle);
            }
            other => panic!("Expected ConfirmationPending, got {:?}", other),
        }

        Ok(())
    }
}
