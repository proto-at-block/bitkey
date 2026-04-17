use bitcoin::secp256k1::ecdsa::Signature;
use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{
        get_confirmation_result_rsp::Result as ConfirmationResult, wallet_rsp::Msg,
        GetConfirmationResultCmd, GetConfirmationResultRsp, Status,
    },
    wca::decode_and_check,
};

use super::InputSignatureTuple;
use crate::command_interface::command;

#[derive(Debug, Clone)]
pub enum ConfirmedCommandResult {
    WipeState {
        success: bool,
    },
    FwupStart {
        success: bool,
    },
    ChunkedDataAvailable {
        total_size: u32,
    },
    SignActionProof {
        signature: Vec<u8>,
    },
    SignTx {
        signatures: Vec<InputSignatureTuple>,
    },
    /// Streaming signing session confirmed by user.
    /// App should now call `get_tx_signature_cmd` for each input index 0..num_inputs-1.
    SignStreamReady {
        num_inputs: u32,
    },
    LostAppRecoverySsek {
        ssek: Vec<u8>,
    },
    LostAppRecoverySignChallenge {
        /// DER-encoded hex string, matching the format returned by `signChallenge`.
        signature: String,
    },
    RotateAppAuthKeys {
        action_proof_signature: Vec<u8>,
        hw_signed_account_id: Vec<u8>,
        app_auth_key_signature: Vec<u8>,
        hw_auth_public_key: Vec<u8>,
    },
    UpgradeRotateAppAuthKeys {
        hw_signed_account_id: Vec<u8>,
        app_auth_key_signature: Vec<u8>,
        hw_auth_public_key: Vec<u8>,
    },
    SignChallengeAndSealSeks {
        signature: Vec<u8>,
        sealed_csek: Vec<u8>,
        sealed_ssek: Vec<u8>,
    },
    RecoveryAuthorizeLostApp {
        descriptor_backups_signature: Vec<u8>,
        activate_keyset_signature: Vec<u8>,
        unsealed_ddk_data: Vec<u8>,
        unsealed_ssek: Vec<u8>,
    },
    RecoveryAuthorizeLostHw {
        descriptor_backups_signature: Vec<u8>,
        activate_keyset_signature: Vec<u8>,
        sealed_ddk_data: Vec<u8>,
    },
    UpgradeAuthorizeW3 {
        descriptor_backups_signature: Vec<u8>,
        activate_keyset_signature: Vec<u8>,
        sealed_ddk_data: Vec<u8>,
    },
    EekRestorationUnsealSymmetricKey {
        unsealed_key: Vec<u8>,
    },
    FullAccountCloudBackupRestoration,
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_confirmation_result(
    response_handle: Vec<u8>,
    confirmation_handle: Vec<u8>,
) -> Result<ConfirmedCommandResult, CommandError> {
    let apdu: apdu::Command = GetConfirmationResultCmd {
        response_handle,
        confirmation_handle,
    }
    .try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;
    if Status::try_from(wallet_rsp.status) == Ok(Status::ConfirmationPending) {
        return Err(CommandError::InProgress);
    }

    let message = wallet_rsp.msg.ok_or(CommandError::MissingMessage)?;

    if let Msg::GetConfirmationResultRsp(GetConfirmationResultRsp { result }) = message {
        match result {
            Some(ConfirmationResult::WipeStateResult(wipe_rsp)) => {
                use crate::fwpb::wipe_state_rsp::WipeStateRspStatus;
                match WipeStateRspStatus::try_from(wipe_rsp.rsp_status) {
                    Ok(WipeStateRspStatus::Unspecified) => {
                        Err(CommandError::UnspecifiedCommandError)
                    }
                    Ok(WipeStateRspStatus::Success) => {
                        Ok(ConfirmedCommandResult::WipeState { success: true })
                    }
                    Ok(WipeStateRspStatus::Error) => Err(CommandError::WipeStateFailed),
                    Ok(WipeStateRspStatus::Unauthenticated) => Err(CommandError::UserDenied),
                    Err(_) => Err(CommandError::InvalidResponse),
                }
            }
            Some(ConfirmationResult::FwupStartResult(fwup_rsp)) => {
                use crate::fwpb::fwup_start_rsp::FwupStartRspStatus;
                match FwupStartRspStatus::try_from(fwup_rsp.rsp_status) {
                    Ok(FwupStartRspStatus::Unspecified) => {
                        Err(CommandError::UnspecifiedCommandError)
                    }
                    Ok(FwupStartRspStatus::Success) => {
                        Ok(ConfirmedCommandResult::FwupStart { success: true })
                    }
                    Ok(FwupStartRspStatus::Error) => Err(CommandError::FirmwareUpdateStartFailed),
                    Ok(FwupStartRspStatus::Unauthenticated) => Err(CommandError::Unauthenticated),
                    Err(_) => Err(CommandError::InvalidResponse),
                }
            }
            Some(ConfirmationResult::ChunkedDataAvailable(chunked_info)) => {
                Ok(ConfirmedCommandResult::ChunkedDataAvailable {
                    total_size: chunked_info.total_size,
                })
            }
            Some(ConfirmationResult::SignActionProofResult(sign_rsp)) => {
                Ok(ConfirmedCommandResult::SignActionProof {
                    signature: sign_rsp.signature,
                })
            }
            Some(ConfirmationResult::SignTxResult(sign_tx_rsp)) => {
                Ok(ConfirmedCommandResult::SignTx {
                    signatures: sign_tx_rsp
                        .signatures
                        .into_iter()
                        .map(|sig| InputSignatureTuple {
                            input_index: sig.input_index,
                            public_key: sig.public_key,
                            signature: sig.signature,
                        })
                        .collect(),
                })
            }
            Some(ConfirmationResult::SignStreamSignaturesReady(ready)) => {
                Ok(ConfirmedCommandResult::SignStreamReady {
                    num_inputs: ready.num_inputs,
                })
            }
            Some(ConfirmationResult::LostAppRecoverySsekRsp(ssek_rsp)) => {
                Ok(ConfirmedCommandResult::LostAppRecoverySsek {
                    ssek: ssek_rsp.unsealed_ssek,
                })
            }
            Some(ConfirmationResult::LostAppRecoverySignChallengeResult(sign_rsp)) => {
                // Firmware returns compact (r||s) 64-byte signature.
                // Convert to DER hex to match the format of `signChallenge`.
                let sig = Signature::from_compact(&sign_rsp.signature)
                    .map_err(|_| CommandError::InvalidResponse)?;
                Ok(ConfirmedCommandResult::LostAppRecoverySignChallenge {
                    signature: sig.to_string(),
                })
            }
            Some(ConfirmationResult::RotateAppAuthKeysRsp(raak_rsp)) => {
                Ok(ConfirmedCommandResult::RotateAppAuthKeys {
                    action_proof_signature: raak_rsp.action_proof_signature,
                    hw_signed_account_id: raak_rsp.hw_signed_account_id,
                    app_auth_key_signature: raak_rsp.app_auth_key_signature,
                    hw_auth_public_key: raak_rsp.hw_auth_public_key,
                })
            }
            Some(ConfirmationResult::UpgradeRotateAppAuthKeysRsp(uraak_rsp)) => {
                Ok(ConfirmedCommandResult::UpgradeRotateAppAuthKeys {
                    hw_signed_account_id: uraak_rsp.hw_signed_account_id,
                    app_auth_key_signature: uraak_rsp.app_auth_key_signature,
                    hw_auth_public_key: uraak_rsp.hw_auth_public_key,
                })
            }
            Some(ConfirmationResult::SignChallengeAndSealSeksResult(rsp)) => {
                use bitcoin::secp256k1::ecdsa::Signature as Secp256k1Signature;
                use prost::Message;
                // Firmware returns raw 64-byte compact (R||S) signature; convert to DER
                // for consistency with the W1 SignChallenge which returns DER-encoded sigs.
                let compact_sig = Secp256k1Signature::from_compact(&rsp.signature)
                    .map_err(|_| CommandError::InvalidResponse)?;
                Ok(ConfirmedCommandResult::SignChallengeAndSealSeks {
                    signature: compact_sig.serialize_der().to_vec(),
                    sealed_csek: rsp
                        .sealed_csek
                        .ok_or(CommandError::MissingMessage)?
                        .encode_to_vec(),
                    sealed_ssek: rsp
                        .sealed_ssek
                        .ok_or(CommandError::MissingMessage)?
                        .encode_to_vec(),
                })
            }
            Some(ConfirmationResult::RecoveryAuthorizeLostAppResult(rsp)) => {
                Ok(ConfirmedCommandResult::RecoveryAuthorizeLostApp {
                    descriptor_backups_signature: rsp.descriptor_backups_signature,
                    activate_keyset_signature: rsp.activate_keyset_signature,
                    unsealed_ddk_data: rsp.unsealed_ddk_data,
                    unsealed_ssek: rsp.unsealed_ssek,
                })
            }
            Some(ConfirmationResult::RecoveryAuthorizeLostHwResult(rsp)) => {
                use prost::Message;
                Ok(ConfirmedCommandResult::RecoveryAuthorizeLostHw {
                    descriptor_backups_signature: rsp.descriptor_backups_signature,
                    activate_keyset_signature: rsp.activate_keyset_signature,
                    sealed_ddk_data: rsp
                        .sealed_ddk_data
                        .map(|s| s.encode_to_vec())
                        .unwrap_or_default(),
                })
            }
            Some(ConfirmationResult::UpgradeAuthorizeW3Result(rsp)) => {
                use prost::Message;
                Ok(ConfirmedCommandResult::UpgradeAuthorizeW3 {
                    descriptor_backups_signature: rsp.descriptor_backups_signature,
                    activate_keyset_signature: rsp.activate_keyset_signature,
                    sealed_ddk_data: rsp
                        .sealed_ddk_data
                        .map(|s| s.encode_to_vec())
                        .unwrap_or_default(),
                })
            }
            Some(ConfirmationResult::EekRestorationUnsealSymmetricKeyResult(rsp)) => {
                Ok(ConfirmedCommandResult::EekRestorationUnsealSymmetricKey {
                    unsealed_key: rsp.unsealed_key,
                })
            }
            Some(ConfirmationResult::FullAccountCloudBackupRestorationResult(_)) => {
                Ok(ConfirmedCommandResult::FullAccountCloudBackupRestoration)
            }
            None => Err(CommandError::MissingMessage),
            // GetAddressResult is not expected through confirmation protocol
            _ => Err(CommandError::InvalidResponse),
        }
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(GetConfirmationResult = get_confirmation_result -> ConfirmedCommandResult, response_handle: Vec<u8>, confirmation_handle: Vec<u8>);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{
            get_confirmation_result_rsp::Result as ConfirmationResult, wallet_rsp::Msg,
            wipe_state_rsp::WipeStateRspStatus, ChunkedDataInfo, GetConfirmationResultRsp,
            InputSignature, LostAppRecoverySignChallengeRsp, LostAppRecoverySsekRsp,
            SignActionProofRsp, SignStreamSignaturesReady, SignTxResponse, Status, WalletRsp,
            WipeStateRsp,
        },
    };

    use super::{ConfirmedCommandResult, GetConfirmationResult};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn get_confirmation_result_wipe_state_success() -> Result<(), CommandError> {
        let command =
            GetConfirmationResult::new(vec![0x01, 0x02, 0x03, 0x04], vec![0x05, 0x06, 0x07, 0x08]);
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetConfirmationResultRsp(GetConfirmationResultRsp {
                result: Some(ConfirmationResult::WipeStateResult(WipeStateRsp {
                    rsp_status: WipeStateRspStatus::Success.into(),
                })),
            })),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Ok(State::Result {
                value: ConfirmedCommandResult::WipeState { success: true }
            })
        ));

        Ok(())
    }

    #[test]
    fn get_confirmation_result_missing_result() -> Result<(), CommandError> {
        let command =
            GetConfirmationResult::new(vec![0x01, 0x02, 0x03, 0x04], vec![0x05, 0x06, 0x07, 0x08]);
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetConfirmationResultRsp(GetConfirmationResultRsp {
                result: None,
            })),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Err(CommandError::MissingMessage)
        ));

        Ok(())
    }

    #[test]
    fn get_confirmation_result_pending_status_maps_to_in_progress() -> Result<(), CommandError> {
        let command =
            GetConfirmationResult::new(vec![0x01, 0x02, 0x03, 0x04], vec![0x05, 0x06, 0x07, 0x08]);
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::ConfirmationPending.into(),
            msg: Some(Msg::GetConfirmationResultRsp(GetConfirmationResultRsp {
                result: None,
            })),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Err(CommandError::InProgress)
        ));

        Ok(())
    }

    #[test]
    fn get_confirmation_result_wipe_state_unauthenticated_maps_to_user_denied(
    ) -> Result<(), CommandError> {
        let command =
            GetConfirmationResult::new(vec![0x01, 0x02, 0x03, 0x04], vec![0x05, 0x06, 0x07, 0x08]);
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetConfirmationResultRsp(GetConfirmationResultRsp {
                result: Some(ConfirmationResult::WipeStateResult(WipeStateRsp {
                    rsp_status: WipeStateRspStatus::Unauthenticated.into(),
                })),
            })),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Err(CommandError::UserDenied)
        ));

        Ok(())
    }

    #[test]
    fn get_confirmation_result_chunked_data_available() -> Result<(), CommandError> {
        let command =
            GetConfirmationResult::new(vec![0x01, 0x02, 0x03, 0x04], vec![0x05, 0x06, 0x07, 0x08]);
        command.next(Vec::default())?;

        let total_size = 1234u32;
        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetConfirmationResultRsp(GetConfirmationResultRsp {
                result: Some(ConfirmationResult::ChunkedDataAvailable(ChunkedDataInfo {
                    total_size,
                })),
            })),
            ..Default::default()
        });

        assert!(matches!(
            command.next(response),
            Ok(State::Result {
                value: ConfirmedCommandResult::ChunkedDataAvailable { total_size: 1234 }
            })
        ));

        Ok(())
    }

    #[test]
    fn get_confirmation_result_sign_action_proof_success() -> Result<(), CommandError> {
        let command =
            GetConfirmationResult::new(vec![0x01, 0x02, 0x03, 0x04], vec![0x05, 0x06, 0x07, 0x08]);
        command.next(Vec::default())?;

        let signature = vec![0u8; 64];

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetConfirmationResultRsp(GetConfirmationResultRsp {
                result: Some(ConfirmationResult::SignActionProofResult(
                    SignActionProofRsp {
                        signature: signature.clone(),
                    },
                )),
            })),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value: ConfirmedCommandResult::SignActionProof { signature: sig },
            }) => {
                assert_eq!(sig, signature);
            }
            other => panic!("Expected SignActionProof success, got {:?}", other),
        }

        Ok(())
    }

    #[test]
    fn get_confirmation_result_sign_tx_success() -> Result<(), CommandError> {
        let command =
            GetConfirmationResult::new(vec![0x01, 0x02, 0x03, 0x04], vec![0x05, 0x06, 0x07, 0x08]);
        command.next(Vec::default())?;

        let public_key = vec![0x02; 33];
        let signature = vec![0x30; 72];

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetConfirmationResultRsp(GetConfirmationResultRsp {
                result: Some(ConfirmationResult::SignTxResult(SignTxResponse {
                    signatures: vec![InputSignature {
                        input_index: 0,
                        public_key: public_key.clone(),
                        signature: signature.clone(),
                    }],
                })),
            })),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value: ConfirmedCommandResult::SignTx { signatures },
            }) => {
                assert_eq!(signatures.len(), 1);
                assert_eq!(signatures[0].input_index, 0);
                assert_eq!(signatures[0].public_key, public_key);
                assert_eq!(signatures[0].signature, signature);
            }
            other => panic!("Expected SignTx success, got {:?}", other),
        }

        Ok(())
    }

    #[test]
    fn get_confirmation_result_sign_stream_ready() -> Result<(), CommandError> {
        let command =
            GetConfirmationResult::new(vec![0x01, 0x02, 0x03, 0x04], vec![0x05, 0x06, 0x07, 0x08]);
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetConfirmationResultRsp(GetConfirmationResultRsp {
                result: Some(ConfirmationResult::SignStreamSignaturesReady(
                    SignStreamSignaturesReady { num_inputs: 42 },
                )),
            })),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value: ConfirmedCommandResult::SignStreamReady { num_inputs },
            }) => {
                assert_eq!(num_inputs, 42);
            }
            other => panic!("Expected SignStreamReady, got {:?}", other),
        }

        Ok(())
    }

    #[test]
    fn get_confirmation_result_lost_app_recovery_ssek() -> Result<(), CommandError> {
        let command =
            GetConfirmationResult::new(vec![0x01, 0x02, 0x03, 0x04], vec![0x05, 0x06, 0x07, 0x08]);
        command.next(Vec::default())?;

        let ssek = vec![0xAB; 32];

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetConfirmationResultRsp(GetConfirmationResultRsp {
                result: Some(ConfirmationResult::LostAppRecoverySsekRsp(
                    LostAppRecoverySsekRsp {
                        unsealed_ssek: ssek.clone(),
                    },
                )),
            })),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value: ConfirmedCommandResult::LostAppRecoverySsek { ssek: s },
            }) => {
                assert_eq!(s, ssek);
            }
            other => panic!("Expected LostAppRecoverySsek, got {:?}", other),
        }

        Ok(())
    }

    #[test]
    fn get_confirmation_result_lost_app_recovery_sign_challenge() -> Result<(), CommandError> {
        use bitcoin::secp256k1::ecdsa::Signature as Secp256k1Signature;

        let command =
            GetConfirmationResult::new(vec![0x01, 0x02, 0x03, 0x04], vec![0x05, 0x06, 0x07, 0x08]);
        command.next(Vec::default())?;

        // Valid compact signature (r=1, s=1) for testing
        let mut compact_sig = vec![0u8; 64];
        compact_sig[31] = 1; // r = 1
        compact_sig[63] = 1; // s = 1

        let expected_der = Secp256k1Signature::from_compact(&compact_sig)
            .unwrap()
            .to_string();

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetConfirmationResultRsp(GetConfirmationResultRsp {
                result: Some(ConfirmationResult::LostAppRecoverySignChallengeResult(
                    LostAppRecoverySignChallengeRsp {
                        signature: compact_sig,
                    },
                )),
            })),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value: ConfirmedCommandResult::LostAppRecoverySignChallenge { signature: sig },
            }) => {
                assert_eq!(sig, expected_der);
            }
            other => panic!("Expected LostAppRecoverySignChallenge, got {:?}", other),
        }

        Ok(())
    }
}
