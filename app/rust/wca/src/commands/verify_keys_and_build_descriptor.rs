use bitcoin::secp256k1::ecdsa::Signature;
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
    account_index: u32,
) -> Result<Signature, CommandError> {
    let apdu: apdu::Command = VerifyKeysAndBuildDescriptorCmd {
        app_spending_key,
        app_spending_key_chaincode,
        network_mainnet,
        app_auth_key,
        server_spending_key,
        server_spending_key_chaincode,
        wsm_signature,
        account_index,
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::VerifyKeysAndBuildDescriptorRsp(VerifyKeysAndBuildDescriptorRsp {
        app_auth_key_signature,
    }) = message
    {
        let signature = Signature::from_compact(&app_auth_key_signature)
            .map_err(|_| CommandError::InvalidResponse)?;
        Ok(signature)
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(VerifyKeysAndBuildDescriptor = verify_keys_and_build_descriptor -> Signature, app_spending_key: Vec<u8>, app_spending_key_chaincode: Vec<u8>, network_mainnet: bool, app_auth_key: Vec<u8>, server_spending_key: Vec<u8>, server_spending_key_chaincode: Vec<u8>, wsm_signature: Vec<u8>, account_index: u32);

#[cfg(test)]
mod tests {
    use prost::Message;

    use bitcoin::secp256k1::ecdsa::Signature;

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

    // A valid compact ECDSA signature (64 bytes) for testing
    fn fake_signature_bytes() -> Vec<u8> {
        Signature::from_compact(&[0x01; 64])
            .expect("test signature")
            .serialize_compact()
            .to_vec()
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
        let account_index = 0u32;

        let command = VerifyKeysAndBuildDescriptor::new(
            app_spending_key,
            app_spending_key_chaincode,
            network_mainnet,
            app_auth_key,
            server_spending_key,
            server_spending_key_chaincode,
            wsm_signature,
            account_index,
        );
        command.next(Vec::default())?;

        let sig_bytes = fake_signature_bytes();
        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::VerifyKeysAndBuildDescriptorRsp(
                VerifyKeysAndBuildDescriptorRsp {
                    app_auth_key_signature: sig_bytes.clone(),
                },
            )),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result { value }) => {
                assert_eq!(value.serialize_compact().to_vec(), sig_bytes);
            }
            other => panic!("Expected signature result, got {:?}", other),
        }

        Ok(())
    }
}
