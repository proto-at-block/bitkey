use next_gen::generator;

use crate::{
    command_interface::command,
    errors::CommandError,
    fwpb::{wallet_rsp::Msg, ProvisionAppAuthPubkeyCmd, ProvisionAppAuthPubkeyRsp},
    wca,
};

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn provision_app_auth_key(pubkey: Vec<u8>) -> Result<bool, CommandError> {
    let apdu: apdu::Command = ProvisionAppAuthPubkeyCmd { pubkey }.try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::ProvisionAppAuthPubkeyRsp(ProvisionAppAuthPubkeyRsp {}) = message {
        Ok(true)
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(ProvisionAppAuthKey = provision_app_auth_key -> bool, pubkey: Vec<u8>);
