use next_gen::generator;
use prost::Message;

use crate::{
    errors::CommandError,
    fwpb::{KeysetRepairUnsealSymmetricKeyCmd, SealedData, Status},
    wca::decode_and_check,
};

use crate::command_interface::command;

#[derive(Debug, Clone)]
pub enum KeysetRepairUnsealResult {
    ConfirmationPending {
        response_handle: Vec<u8>,
        confirmation_handle: Vec<u8>,
    },
}

/// Sends sealed symmetric key to firmware for stale-keyset-repair unseal.
/// Same shape as EEK restoration unseal but with a repair-specific on-device prompt.
/// The firmware shows a confirmation prompt; returns CONFIRMATION_PENDING + handles.
/// The unsealed key is returned via get_confirmation_result after user confirms.
#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn keyset_repair_unseal(sealed_key: Vec<u8>) -> Result<KeysetRepairUnsealResult, CommandError> {
    let sealed_data =
        SealedData::decode(&*sealed_key).map_err(|_| CommandError::InvalidArguments)?;

    let apdu: apdu::Command = KeysetRepairUnsealSymmetricKeyCmd {
        sealed_key: Some(sealed_data),
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    if Status::try_from(wallet_rsp.status) == Ok(Status::ConfirmationPending) {
        return Ok(KeysetRepairUnsealResult::ConfirmationPending {
            response_handle: wallet_rsp.response_handle,
            confirmation_handle: wallet_rsp.confirmation_handle,
        });
    }

    Err(CommandError::InvalidResponse)
}

command!(KeysetRepairUnseal = keyset_repair_unseal -> KeysetRepairUnsealResult,
    sealed_key: Vec<u8>
);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{Status, WalletRsp},
    };

    use super::{KeysetRepairUnseal, KeysetRepairUnsealResult};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn keyset_repair_unseal_confirmation_pending() -> Result<(), CommandError> {
        let sealed_data = crate::fwpb::SealedData {
            data: vec![0u8; 32],
            nonce: vec![0u8; 12],
            tag: vec![0u8; 16],
        };
        let sealed_key = sealed_data.encode_to_vec();

        let command = KeysetRepairUnseal::new(sealed_key);
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
                    KeysetRepairUnsealResult::ConfirmationPending {
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
