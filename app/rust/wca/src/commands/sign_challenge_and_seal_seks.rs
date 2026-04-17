use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{SignChallengeAndSealSeksCmd, Status},
    wca::decode_and_check,
};

use crate::command_interface::command;

#[derive(Debug, Clone)]
pub enum SignChallengeAndSealSeksResult {
    ConfirmationPending {
        response_handle: Vec<u8>,
        confirmation_handle: Vec<u8>,
    },
}

/// Sends challenge + unsealed CSEK/SSEK to firmware for the recovery start composite.
/// Firmware shows "Start Recovery" prompt. After user confirms, the signed challenge
/// and sealed keys are returned via get_confirmation_result_rsp.
#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn sign_challenge_and_seal_seks(
    challenge: Vec<u8>,
    unsealed_csek: Vec<u8>,
    unsealed_ssek: Vec<u8>,
) -> Result<SignChallengeAndSealSeksResult, CommandError> {
    let apdu: apdu::Command = SignChallengeAndSealSeksCmd {
        challenge,
        unsealed_csek,
        unsealed_ssek,
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    if Status::try_from(wallet_rsp.status) == Ok(Status::ConfirmationPending) {
        return Ok(SignChallengeAndSealSeksResult::ConfirmationPending {
            response_handle: wallet_rsp.response_handle,
            confirmation_handle: wallet_rsp.confirmation_handle,
        });
    }

    Err(CommandError::InvalidResponse)
}

command!(SignChallengeAndSealSeks = sign_challenge_and_seal_seks -> SignChallengeAndSealSeksResult,
    challenge: Vec<u8>,
    unsealed_csek: Vec<u8>,
    unsealed_ssek: Vec<u8>
);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{Status, WalletRsp},
    };

    use super::{SignChallengeAndSealSeks, SignChallengeAndSealSeksResult};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn confirmation_pending() -> Result<(), CommandError> {
        let command = SignChallengeAndSealSeks::new(vec![0u8; 32], vec![0u8; 32], vec![0u8; 32]);
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
                    SignChallengeAndSealSeksResult::ConfirmationPending {
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
