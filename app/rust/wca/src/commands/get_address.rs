use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{wallet_rsp::Msg, GetAddressCmd, GetAddressRsp},
    wca::decode_and_check,
};

use crate::command_interface::command;

/// Result from the getAddress NFC command containing the derived bitcoin address.
#[derive(Debug, Clone)]
pub struct GetAddressResult {
    pub address: String,
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_address(address_index: u32) -> Result<GetAddressResult, CommandError> {
    let apdu: apdu::Command = GetAddressCmd { address_index }.try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    // Global status is checked by decode_and_check, just extract the address
    let message = wallet_rsp.msg.ok_or(CommandError::MissingMessage)?;

    if let Msg::GetAddressRsp(GetAddressRsp { address }) = message {
        Ok(GetAddressResult { address })
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(GetAddress = get_address -> GetAddressResult, address_index: u32);

#[cfg(test)]
mod tests {
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{wallet_rsp::Msg, GetAddressRsp, Status, WalletRsp},
    };

    use super::{GetAddress, GetAddressResult};

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[test]
    fn get_address_success() -> Result<(), CommandError> {
        let command = GetAddress::new(0);
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetAddressRsp(GetAddressRsp {
                address: "bc1qtest123".to_string(),
            })),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value: GetAddressResult { address },
            }) => {
                assert_eq!(address, "bc1qtest123");
            }
            other => panic!("Expected GetAddressResult, got {:?}", other),
        }

        Ok(())
    }

    #[test]
    fn get_address_different_index() -> Result<(), CommandError> {
        let command = GetAddress::new(5);
        command.next(Vec::default())?;

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::GetAddressRsp(GetAddressRsp {
                address: "bc1qaddr5".to_string(),
            })),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result {
                value: GetAddressResult { address },
            }) => {
                assert_eq!(address, "bc1qaddr5");
            }
            other => panic!("Expected GetAddressResult, got {:?}", other),
        }

        Ok(())
    }
}
