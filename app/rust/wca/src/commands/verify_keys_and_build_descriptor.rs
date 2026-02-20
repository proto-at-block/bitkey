use next_gen::generator;

use crate::{
    command_interface::command,
    errors::CommandError,
    fwpb::{wallet_rsp::Msg, VerifyKeysAndBuildDescriptorCmd, VerifyKeysAndBuildDescriptorRsp},
    wca,
};

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn verify_keys_and_build_descriptor(
    app_spending_key: Vec<u8>,
    app_spending_key_chaincode: Vec<u8>,
    network_mainnet: bool,
    app_auth_key: Vec<u8>,
    server_spending_key: Vec<u8>,
    server_spending_key_chaincode: Vec<u8>,
    wsm_signature: Vec<u8>,
) -> Result<bool, CommandError> {
    let apdu: apdu::Command = VerifyKeysAndBuildDescriptorCmd {
        app_spending_key,
        app_spending_key_chaincode,
        network_mainnet,
        app_auth_key,
        server_spending_key,
        server_spending_key_chaincode,
        wsm_signature,
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::VerifyKeysAndBuildDescriptorRsp(VerifyKeysAndBuildDescriptorRsp {}) = message {
        Ok(true)
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(VerifyKeysAndBuildDescriptor = verify_keys_and_build_descriptor -> bool, app_spending_key: Vec<u8>, app_spending_key_chaincode: Vec<u8>, network_mainnet: bool, app_auth_key: Vec<u8>, server_spending_key: Vec<u8>, server_spending_key_chaincode: Vec<u8>, wsm_signature: Vec<u8>);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{wallet_rsp::Msg, Status, VerifyKeysAndBuildDescriptorRsp, WalletRsp},
    };

    use super::VerifyKeysAndBuildDescriptor;

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn verify_keys_and_build_descriptor_success() -> Result<(), CommandError> {
        let app_spending_key = vec![0x02; 33];
        let app_spending_key_chaincode = vec![0x01; 32];
        let network_mainnet = false; // testnet
        let app_auth_key = vec![0x03; 33];
        let server_spending_key = vec![0x04; 33];
        let server_spending_key_chaincode = vec![0x05; 32];
        let wsm_signature = vec![0x06; 64];

        let command = VerifyKeysAndBuildDescriptor::new(
            app_spending_key,
            app_spending_key_chaincode,
            network_mainnet,
            app_auth_key,
            server_spending_key,
            server_spending_key_chaincode,
            wsm_signature,
        );
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::VerifyKeysAndBuildDescriptorRsp(
                VerifyKeysAndBuildDescriptorRsp {},
            )),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result { value: true }) => {}
            other => panic!("Expected true result, got {:?}", other),
        }

        Ok(())
    }
}
