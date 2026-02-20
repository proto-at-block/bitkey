use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{wallet_rsp::Msg, SignActionProofCmd, SignActionProofRsp, Status},
    wca::decode_and_check,
};

use crate::command_interface::command;

#[derive(Debug, Clone)]
pub enum SignActionProofResult {
    Success {
        signature: Vec<u8>,
    },
    ConfirmationPending {
        response_handle: Vec<u8>,
        confirmation_handle: Vec<u8>,
    },
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn sign_action_proof(
    version: u32,
    action: String,
    field: String,
    value: Option<String>,
    current: Option<String>,
    bindings: String,
) -> Result<SignActionProofResult, CommandError> {
    let apdu: apdu::Command = SignActionProofCmd {
        version,
        action,
        field,
        value: value.unwrap_or_default(),
        current: current.unwrap_or_default(),
        bindings,
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    if Status::try_from(wallet_rsp.status) == Ok(Status::ConfirmationPending) {
        return Ok(SignActionProofResult::ConfirmationPending {
            response_handle: wallet_rsp.response_handle,
            confirmation_handle: wallet_rsp.confirmation_handle,
        });
    }

    let message = wallet_rsp.msg.ok_or(CommandError::MissingMessage)?;

    if let Msg::SignActionProofRsp(SignActionProofRsp { signature }) = message {
        Ok(SignActionProofResult::Success { signature })
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(SignActionProof = sign_action_proof -> SignActionProofResult,
    version: u32,
    action: String,
    field: String,
    value: Option<String>,
    current: Option<String>,
    bindings: String
);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{wallet_rsp::Msg, SignActionProofRsp, Status, WalletRsp},
    };

    use super::{SignActionProof, SignActionProofResult};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn sign_action_proof_success() -> Result<(), CommandError> {
        let command = SignActionProof::new(
            1,
            "Add".to_string(),
            "RecoveryEmail".to_string(),
            Some("test@example.com".to_string()),
            None,
            "eid=ABC,tb=59dc".to_string(),
        );
        command.next(Vec::default())?;

        let signature = vec![0u8; 65];

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::SignActionProofRsp(SignActionProofRsp {
                signature: signature.clone(),
            })),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value: SignActionProofResult::Success { signature: sig },
            }) => {
                assert_eq!(sig, signature);
            }
            other => panic!("Expected Success, got {:?}", other),
        }

        Ok(())
    }

    #[test]
    fn sign_action_proof_confirmation_pending() -> Result<(), CommandError> {
        let command = SignActionProof::new(
            1,
            "Add".to_string(),
            "RecoveryEmail".to_string(),
            Some("test@example.com".to_string()),
            None,
            "eid=ABC,tb=59dc".to_string(),
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
                    SignActionProofResult::ConfirmationPending {
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
