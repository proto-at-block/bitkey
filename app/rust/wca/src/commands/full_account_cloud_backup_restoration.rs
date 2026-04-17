use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{FullAccountCloudBackupRestorationCmd, Status},
    wca::decode_and_check,
};

use crate::command_interface::command;

#[derive(Debug, Clone)]
pub enum FullAccountCloudBackupRestorationResult {
    ConfirmationPending {
        response_handle: Vec<u8>,
        confirmation_handle: Vec<u8>,
    },
}

/// Initiates the full account cloud backup restoration flow on W3 hardware.
/// Firmware shows a confirmation prompt; returns CONFIRMATION_PENDING + handles.
/// After user confirms, the app may send continuation commands with sealed CSEKs.
#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn full_account_cloud_backup_restoration(
) -> Result<FullAccountCloudBackupRestorationResult, CommandError> {
    let apdu: apdu::Command = FullAccountCloudBackupRestorationCmd {}.try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    if Status::try_from(wallet_rsp.status) == Ok(Status::ConfirmationPending) {
        return Ok(
            FullAccountCloudBackupRestorationResult::ConfirmationPending {
                response_handle: wallet_rsp.response_handle,
                confirmation_handle: wallet_rsp.confirmation_handle,
            },
        );
    }

    Err(CommandError::InvalidResponse)
}

command!(FullAccountCloudBackupRestoration = full_account_cloud_backup_restoration -> FullAccountCloudBackupRestorationResult);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{Status, WalletRsp},
    };

    use super::{FullAccountCloudBackupRestoration, FullAccountCloudBackupRestorationResult};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn full_account_cloud_backup_restoration_confirmation_pending() -> Result<(), CommandError> {
        let command = FullAccountCloudBackupRestoration::new();
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
                    FullAccountCloudBackupRestorationResult::ConfirmationPending {
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
