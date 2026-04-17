use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{RecoveryAuthorizeLostHwCmd, Status},
    wca::decode_and_check,
};

use crate::command_interface::command;

#[derive(Debug, Clone)]
pub enum RecoveryAuthorizeLostHwResult {
    ConfirmationPending {
        response_handle: Vec<u8>,
        confirmation_handle: Vec<u8>,
    },
}

/// Sends DDK private key + binding strings to firmware for lost-hw recovery tap 2.
/// Firmware shows "Complete Wallet" prompt. After user confirms, the result (sealed DDK,
/// SAP signatures) is returned via get_confirmation_result_rsp.
#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn recovery_authorize_lost_hw(
    ddk_private_key: Vec<u8>,
    descriptor_backups_bindings: String,
    activate_keyset_bindings: String,
    action_proof_version: u32,
) -> Result<RecoveryAuthorizeLostHwResult, CommandError> {
    let apdu: apdu::Command = RecoveryAuthorizeLostHwCmd {
        ddk_private_key,
        descriptor_backups_bindings,
        activate_keyset_bindings,
        action_proof_version,
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    if Status::try_from(wallet_rsp.status) == Ok(Status::ConfirmationPending) {
        return Ok(RecoveryAuthorizeLostHwResult::ConfirmationPending {
            response_handle: wallet_rsp.response_handle,
            confirmation_handle: wallet_rsp.confirmation_handle,
        });
    }

    Err(CommandError::InvalidResponse)
}

command!(RecoveryAuthorizeLostHw = recovery_authorize_lost_hw -> RecoveryAuthorizeLostHwResult,
    ddk_private_key: Vec<u8>,
    descriptor_backups_bindings: String,
    activate_keyset_bindings: String,
    action_proof_version: u32
);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{Status, WalletRsp},
    };

    use super::{RecoveryAuthorizeLostHw, RecoveryAuthorizeLostHwResult};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn confirmation_pending() -> Result<(), CommandError> {
        let command = RecoveryAuthorizeLostHw::new(
            vec![0u8; 32],
            "bindings1".to_string(),
            "bindings2".to_string(),
            1,
        );
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
                    RecoveryAuthorizeLostHwResult::ConfirmationPending {
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
