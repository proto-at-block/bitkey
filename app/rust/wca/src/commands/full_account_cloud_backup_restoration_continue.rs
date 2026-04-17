use next_gen::generator;
use prost::Message;

use crate::{
    errors::CommandError,
    fwpb::{FullAccountCloudBackupRestorationContinueCmd, SealedData},
    wca::decode_and_check,
};

use crate::command_interface::command;

#[derive(Debug, Clone)]
pub struct FullAccountCloudBackupRestorationContinueResult {
    pub unsealed_csek: Vec<u8>,
    /// Zero-based index of the CSEK that was successfully unsealed.
    pub csek_index: u32,
}

/// Sends a sealed CSEK with its index to firmware for unsealing within an already-confirmed
/// cloud backup restoration session. May be called repeatedly with different CSEKs until
/// firmware successfully unseals one and returns the key with its index.
///
/// `session_token` must be the `response_handle` returned by the initial
/// `FullAccountCloudBackupRestoration` command to bind this call to the confirmed session.
#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn full_account_cloud_backup_restoration_continue(
    sealed_csek: Vec<u8>,
    csek_index: u32,
    session_token: Vec<u8>,
) -> Result<FullAccountCloudBackupRestorationContinueResult, CommandError> {
    let sealed_data =
        SealedData::decode(&*sealed_csek).map_err(|_| CommandError::InvalidArguments)?;

    let apdu: apdu::Command = FullAccountCloudBackupRestorationContinueCmd {
        sealed_csek: Some(sealed_data),
        csek_index,
        session_token,
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    let msg = wallet_rsp.msg.ok_or(CommandError::MissingMessage)?;
    if let crate::fwpb::wallet_rsp::Msg::FullAccountCloudBackupRestorationContinueRsp(rsp) = msg {
        Ok(FullAccountCloudBackupRestorationContinueResult {
            unsealed_csek: rsp.unsealed_csek,
            csek_index: rsp.csek_index,
        })
    } else {
        Err(CommandError::InvalidResponse)
    }
}

command!(FullAccountCloudBackupRestorationContinue = full_account_cloud_backup_restoration_continue -> FullAccountCloudBackupRestorationContinueResult,
    sealed_csek: Vec<u8>,
    csek_index: u32,
    session_token: Vec<u8>
);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{wallet_rsp::Msg, FullAccountCloudBackupRestorationContinueRsp, Status, WalletRsp},
    };

    use super::{
        FullAccountCloudBackupRestorationContinue, FullAccountCloudBackupRestorationContinueResult,
    };

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn full_account_cloud_backup_restoration_continue_success() -> Result<(), CommandError> {
        let sealed_data = crate::fwpb::SealedData {
            data: vec![0u8; 32],
            nonce: vec![0u8; 12],
            tag: vec![0u8; 16],
        };
        let sealed_csek = sealed_data.encode_to_vec();
        let csek_index = 2u32;

        let session_token = vec![0xBB; 32];
        let command =
            FullAccountCloudBackupRestorationContinue::new(sealed_csek, csek_index, session_token);
        command.next(Vec::default())?;

        let unsealed_csek = vec![0xAB; 32];

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::FullAccountCloudBackupRestorationContinueRsp(
                FullAccountCloudBackupRestorationContinueRsp {
                    unsealed_csek: unsealed_csek.clone(),
                    csek_index,
                },
            )),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value:
                    FullAccountCloudBackupRestorationContinueResult {
                        unsealed_csek: key,
                        csek_index: idx,
                    },
            }) => {
                assert_eq!(key, unsealed_csek);
                assert_eq!(idx, csek_index);
            }
            other => panic!("Expected success, got {:?}", other),
        }

        Ok(())
    }
}
