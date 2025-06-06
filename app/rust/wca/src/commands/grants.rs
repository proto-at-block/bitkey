use crate::command_interface::command;
use crate::errors::CommandError;
use crate::fwpb::{
    wallet_rsp::Msg as WalletRspMsg,
    FingerprintResetFinalizeCmd as FwpbFingerprintResetFinalizeCmd,
    FingerprintResetRequestCmd as FwpbFingerprintResetRequestCmd,
};
use crate::log_buffer::LogBuffer;
use next_gen::generator;

// Macro to handle the common WCA command flow within a generator
macro_rules! wca_command_flow {
    (
        $generator_name_str:expr,
        $proto_cmd_expr:expr,
        $success_msg_pat:pat => $success_value_expr:expr
    ) => {{
        let proto_cmd = $proto_cmd_expr;
        let apdu_cmd: apdu::Command = proto_cmd.try_into()?;
        LogBuffer::put(format!(
            "{}: sending APDU: {:?}",
            $generator_name_str, apdu_cmd
        ));
        let response_bytes = yield_!(apdu_cmd.into());
        let apdu_response = apdu::Response::from(response_bytes);
        LogBuffer::put(format!(
            "{}: received APDU response: {:?}",
            $generator_name_str, apdu_response
        ));
        let wallet_rsp = $crate::wca::decode_and_check(apdu_response)?;
        match wallet_rsp.msg {
            Some($success_msg_pat) => {
                LogBuffer::put(format!("{}: success", $generator_name_str));
                Ok($success_value_expr)
            }
            _ => {
                LogBuffer::put(format!(
                    "{}: error - InvalidResponse (unexpected WalletRsp.msg or None)",
                    $generator_name_str
                ));
                Err($crate::errors::CommandError::InvalidResponse)
            }
        }
    }};
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn fingerprint_reset_request_generator() -> Result<Vec<u8>, CommandError> {
    wca_command_flow!(
        "fingerprint_reset_request_generator",
        FwpbFingerprintResetRequestCmd {},
        WalletRspMsg::FingerprintResetRequestRsp(rsp) => rsp.grant_request
    )
}
command!(FingerprintResetRequest = fingerprint_reset_request_generator -> Vec<u8>);

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn fingerprint_reset_finalize_generator(grant_payload: Vec<u8>) -> Result<bool, CommandError> {
    wca_command_flow!(
        "fingerprint_reset_finalize_generator",
        FwpbFingerprintResetFinalizeCmd { grant: grant_payload },
        WalletRspMsg::FingerprintResetFinalizeRsp(_) => true
    )
}
command!(FingerprintResetFinalize = fingerprint_reset_finalize_generator -> bool, grant_payload: Vec<u8>);
