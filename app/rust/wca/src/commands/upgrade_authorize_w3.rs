use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{Status, UpgradeAuthorizeW3Cmd},
    wca::decode_and_check,
};

use crate::command_interface::command;

#[derive(Debug, Clone)]
pub enum UpgradeAuthorizeW3Result {
    ConfirmationPending {
        response_handle: Vec<u8>,
        confirmation_handle: Vec<u8>,
    },
}

/// Sends DDK private key + binding strings to firmware for W3 upgrade composite tap.
/// Firmware shows "Approve wallet upgrade" prompt. After user confirms, the result (sealed DDK,
/// SAP signatures) is returned via get_confirmation_result_rsp.
#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn upgrade_authorize_w3(
    ddk_private_key: Vec<u8>,
    descriptor_backups_bindings: String,
    activate_keyset_bindings: String,
    action_proof_version: u32,
) -> Result<UpgradeAuthorizeW3Result, CommandError> {
    let apdu: apdu::Command = UpgradeAuthorizeW3Cmd {
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
        return Ok(UpgradeAuthorizeW3Result::ConfirmationPending {
            response_handle: wallet_rsp.response_handle,
            confirmation_handle: wallet_rsp.confirmation_handle,
        });
    }

    Err(CommandError::InvalidResponse)
}

command!(UpgradeAuthorizeW3 = upgrade_authorize_w3 -> UpgradeAuthorizeW3Result,
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

    use super::{UpgradeAuthorizeW3, UpgradeAuthorizeW3Result};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn confirmation_pending() -> Result<(), CommandError> {
        let command = UpgradeAuthorizeW3::new(
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
                    UpgradeAuthorizeW3Result::ConfirmationPending {
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
