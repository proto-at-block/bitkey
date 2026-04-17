use next_gen::generator;

use crate::{
    errors::CommandError,
    fwpb::{
        show_confirmation_screen_cmd::ConfirmationScreenType, wallet_rsp::Msg,
        ShowConfirmationScreenCmd, ShowConfirmationScreenRsp,
    },
    wca,
};

use crate::command_interface::command;

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn show_confirmation_screen(lock_on_dismiss: bool) -> Result<bool, CommandError> {
    let apdu: apdu::Command = ShowConfirmationScreenCmd {
        r#type: ConfirmationScreenType::Success.into(),
        lock_on_dismiss,
    }
    .try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::ShowConfirmationScreenRsp(ShowConfirmationScreenRsp {}) = message {
        Ok(true)
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(ShowConfirmationScreen = show_confirmation_screen -> bool,
    lock_on_dismiss: bool
);
