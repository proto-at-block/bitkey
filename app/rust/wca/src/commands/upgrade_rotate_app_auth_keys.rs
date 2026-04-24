use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{Status, UpgradeRotateAppAuthKeysCmd},
    wca::decode_and_check,
};

use crate::command_interface::command;

#[derive(Debug, Clone)]
pub enum UpgradeRotateAppAuthKeysResult {
    ConfirmationPending {
        response_handle: Vec<u8>,
        confirmation_handle: Vec<u8>,
    },
}

/// Sends upgrade rotate app auth keys command to firmware.
/// Like RotateAppAuthKeys but without action proof signing.
/// The firmware shows a confirmation prompt; returns ConfirmationPending with handles.
/// After user confirms, the caller uses GetConfirmationResult to retrieve the
/// UpgradeRotateAppAuthKeysRsp with account ID signature, app auth key signature,
/// and HW auth public key.
#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn upgrade_rotate_app_auth_keys(
    account_id: String,
    app_global_auth_key: String,
) -> Result<UpgradeRotateAppAuthKeysResult, CommandError> {
    let apdu: apdu::Command = UpgradeRotateAppAuthKeysCmd {
        account_id,
        app_global_auth_key,
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    if Status::try_from(wallet_rsp.status) == Ok(Status::ConfirmationPending) {
        return Ok(UpgradeRotateAppAuthKeysResult::ConfirmationPending {
            response_handle: wallet_rsp.response_handle,
            confirmation_handle: wallet_rsp.confirmation_handle,
        });
    }

    Err(CommandError::InvalidResponse)
}

command!(UpgradeRotateAppAuthKeys = upgrade_rotate_app_auth_keys -> UpgradeRotateAppAuthKeysResult,
    account_id: String,
    app_global_auth_key: String
);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{Status, WalletRsp},
    };

    use super::{UpgradeRotateAppAuthKeys, UpgradeRotateAppAuthKeysResult};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn upgrade_rotate_app_auth_keys_confirmation_pending() -> Result<(), CommandError> {
        let command = UpgradeRotateAppAuthKeys::new(
            "account-id-123".to_string(),
            "02".to_string() + &"ab".repeat(32),
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
                    UpgradeRotateAppAuthKeysResult::ConfirmationPending {
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
