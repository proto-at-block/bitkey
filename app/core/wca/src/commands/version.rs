use next_gen::generator;

use crate::{errors::CommandError, wca};

use crate::command_interface::command;

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn version() -> Result<u16, CommandError> {
    let apdu: apdu::Command = wca::WCA::Version.try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data)
        .data
        .try_into()
        .map_err(|_| CommandError::InvalidResponse)?;
    let result = Some(u16::from_be_bytes(response)).ok_or(CommandError::InvalidResponse)?;

    Ok(result)
}

command!(Version = version -> u16);

#[cfg(test)]
mod tests {
    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
    };

    use super::Version;

    #[test]
    fn version() -> Result<(), CommandError> {
        let command = Version::new();

        assert!(matches!(
            command.next(Vec::default()),
            Ok(State::Data { response }) if response == vec![0x87, 0x74, 0x00, 0x00],
        ));

        assert!(matches!(
            command.next(vec![0x00, 0x01, 0x90, 0x00]),
            Ok(State::Result { value: 1 }),
        ));

        Ok(())
    }
}
