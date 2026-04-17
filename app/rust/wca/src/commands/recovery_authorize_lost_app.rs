use next_gen::generator;
use prost::Message;

use crate::{
    errors::CommandError,
    fwpb::{RecoveryAuthorizeLostAppCmd, SealedData, Status},
    wca::decode_and_check,
};

use crate::command_interface::command;

#[derive(Debug, Clone)]
pub enum RecoveryAuthorizeLostAppResult {
    ConfirmationPending {
        response_handle: Vec<u8>,
        confirmation_handle: Vec<u8>,
    },
}

/// Sends sealed DDK/SSEK + binding strings to firmware for lost-app recovery tap 2.
/// Firmware shows "Recover Data" prompt. After user confirms, the result (unsealed keys,
/// SAP signatures) is returned via get_confirmation_result_rsp.
#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn recovery_authorize_lost_app(
    sealed_ddk: Vec<u8>,
    sealed_ssek: Vec<u8>,
    descriptor_backups_bindings: String,
    activate_keyset_bindings: String,
    action_proof_version: u32,
) -> Result<RecoveryAuthorizeLostAppResult, CommandError> {
    let sealed_ddk_data = if sealed_ddk.is_empty() {
        None
    } else {
        Some(SealedData::decode(&*sealed_ddk).map_err(|_| CommandError::InvalidArguments)?)
    };
    let sealed_ssek_data = if sealed_ssek.is_empty() {
        None
    } else {
        Some(SealedData::decode(&*sealed_ssek).map_err(|_| CommandError::InvalidArguments)?)
    };

    let apdu: apdu::Command = RecoveryAuthorizeLostAppCmd {
        sealed_ddk: sealed_ddk_data,
        sealed_ssek: sealed_ssek_data,
        descriptor_backups_bindings,
        activate_keyset_bindings,
        action_proof_version,
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    if Status::try_from(wallet_rsp.status) == Ok(Status::ConfirmationPending) {
        return Ok(RecoveryAuthorizeLostAppResult::ConfirmationPending {
            response_handle: wallet_rsp.response_handle,
            confirmation_handle: wallet_rsp.confirmation_handle,
        });
    }

    Err(CommandError::InvalidResponse)
}

command!(RecoveryAuthorizeLostApp = recovery_authorize_lost_app -> RecoveryAuthorizeLostAppResult,
    sealed_ddk: Vec<u8>,
    sealed_ssek: Vec<u8>,
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

    use super::{RecoveryAuthorizeLostApp, RecoveryAuthorizeLostAppResult};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn confirmation_pending() -> Result<(), CommandError> {
        let sealed_data = crate::fwpb::SealedData {
            data: vec![0u8; 32],
            nonce: vec![0u8; 12],
            tag: vec![0u8; 16],
        };

        let command = RecoveryAuthorizeLostApp::new(
            sealed_data.encode_to_vec(),
            sealed_data.encode_to_vec(),
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
                    RecoveryAuthorizeLostAppResult::ConfirmationPending {
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

    #[test]
    fn confirmation_pending_with_optional_inputs_absent() -> Result<(), CommandError> {
        let command = RecoveryAuthorizeLostApp::new(
            vec![],
            vec![],
            "bindings1".to_string(),
            "bindings2".to_string(),
            1,
        );
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::ConfirmationPending.into(),
            response_handle: vec![0x01, 0x02, 0x03, 0x04],
            confirmation_handle: vec![0x05, 0x06, 0x07, 0x08],
            msg: None,
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Ok(State::Result {
                value: RecoveryAuthorizeLostAppResult::ConfirmationPending { .. }
            })
        ));

        Ok(())
    }
}
