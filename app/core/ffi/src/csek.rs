use wca::{
    command_interface::{Command, State},
    errors::CommandError,
};

use crate::BytesState;

type SealedKey = Vec<u8>;
type UnsealedKey = Vec<u8>;

pub struct SealKey(wca::commands::SealKey);
pub struct UnsealKey(wca::commands::UnsealKey);

impl SealKey {
    pub fn new(key: UnsealedKey) -> Result<Self, CommandError> {
        let unsealed_key = key.try_into().map_err(CommandError::KeySizeError)?;
        Ok(Self(wca::commands::SealKey::new(unsealed_key)))
    }

    pub fn next(&self, response: Vec<u8>) -> Result<BytesState, CommandError> {
        self.0.next(response)
    }
}

impl UnsealKey {
    pub fn new(sealed_key: SealedKey) -> Self {
        Self(wca::commands::UnsealKey::new(sealed_key))
    }

    pub fn next(&self, response: Vec<u8>) -> Result<BytesState, CommandError> {
        let state = match self.0.next(response)? {
            State::Data { response } => BytesState::Data { response },
            State::Result { value } => BytesState::Result {
                value: value.into(),
            },
        };
        Ok(state)
    }
}
