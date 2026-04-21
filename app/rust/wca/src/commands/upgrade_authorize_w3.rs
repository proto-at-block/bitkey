use next_gen::generator;
use prost::Message;

use crate::{
    errors::CommandError,
    fwpb::{SealedData, Status, UpgradeAuthorizeW3Cmd},
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
    sealed_ssek_for_decryption: Vec<u8>,
    descriptor_backups_bindings: String,
    activate_keyset_bindings: String,
    action_proof_version: u32,
) -> Result<UpgradeAuthorizeW3Result, CommandError> {
    let sealed_ssek_data = if sealed_ssek_for_decryption.is_empty() {
        None
    } else {
        Some(
            SealedData::decode(&*sealed_ssek_for_decryption)
                .map_err(|_| CommandError::InvalidArguments)?,
        )
    };
    let apdu: apdu::Command = UpgradeAuthorizeW3Cmd {
        ddk_private_key,
        sealed_ssek_for_decryption: sealed_ssek_data,
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
    sealed_ssek_for_decryption: Vec<u8>,
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
        fwpb::{SealedData, Status, WalletRsp},
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
            vec![],
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

    #[test]
    fn valid_sealed_ssek_accepted() -> Result<(), CommandError> {
        let sealed = SealedData {
            data: vec![0xAA; 32],
            nonce: vec![0xBB; 12],
            tag: vec![0xCC; 16],
        };
        let sealed_bytes = sealed.encode_to_vec();

        let command = UpgradeAuthorizeW3::new(
            vec![0u8; 32],
            sealed_bytes,
            "bindings1".to_string(),
            "bindings2".to_string(),
            1,
        );
        // Should proceed past decoding to yield the APDU
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::ConfirmationPending.into(),
            response_handle: vec![0x01],
            confirmation_handle: vec![0x02],
            msg: None,
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value: UpgradeAuthorizeW3Result::ConfirmationPending { .. },
            }) => {}
            other => panic!("Expected ConfirmationPending, got {:?}", other),
        }

        Ok(())
    }

    #[test]
    fn invalid_sealed_ssek_returns_error() {
        let command = UpgradeAuthorizeW3::new(
            vec![0u8; 32],
            vec![0xFF, 0xFF, 0xFF], // not valid protobuf
            "bindings1".to_string(),
            "bindings2".to_string(),
            1,
        );
        match command.next(Vec::default()) {
            Err(CommandError::InvalidArguments) => {} // expected
            other => panic!("Expected InvalidArguments, got {:?}", other),
        }
    }
}
