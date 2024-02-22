use crate::{
    command_interface::{Command, State},
    errors::CommandError,
};
use pcsc::{Card, Context, Protocols, Scope, ShareMode, MAX_BUFFER_SIZE_EXTENDED};

pub trait Transactor: Send + Sync {
    fn transmit(&self, buffer: &[u8]) -> Result<Vec<u8>, pcsc::Error>;
    fn reset(&mut self) -> Result<(), pcsc::Error>;
}

pub trait Performer<T: Transactor + ?Sized> {
    fn perform<V, E>(&self, command: impl Command<V, E>) -> Result<V, TransactorError>
    where
        TransactorError: From<E>;
}

impl<T: Transactor + ?Sized> Performer<T> for T {
    fn perform<V, E>(&self, command: impl Command<V, E>) -> Result<V, TransactorError>
    where
        TransactorError: From<E>,
    {
        let mut response = vec![];
        loop {
            response = match command.next(response)? {
                State::Data { response } => self.transmit(&response)?,
                State::Result { value } => break Ok(value),
            }
        }
    }
}

pub struct NullTransactor;

impl Transactor for NullTransactor {
    fn transmit(&self, _buffer: &[u8]) -> Result<Vec<u8>, pcsc::Error> {
        Ok(vec![])
    }

    fn reset(&mut self) -> Result<(), pcsc::Error> {
        Ok(())
    }
}

pub struct PCSCTransactor {
    card: Option<Card>,
}

#[derive(Debug, thiserror::Error)]
pub enum TransactorError {
    #[error("no smartcard reader not found")]
    ReaderNotFound,
    #[error("reader error")]
    ReaderError(#[from] pcsc::Error),
    #[error("command error")]
    CommandError(#[from] CommandError),
}

impl PCSCTransactor {
    pub fn new() -> Result<Self, TransactorError> {
        let context = Context::establish(Scope::User)?;
        let readers = context.list_readers_owned()?;
        let reader = readers.first().ok_or(TransactorError::ReaderNotFound)?;
        let card = context.connect(reader, ShareMode::Shared, Protocols::ANY)?;
        Ok(Self { card: Some(card) })
    }
}

impl Drop for PCSCTransactor {
    fn drop(&mut self) {
        self.card
            .take()
            .unwrap()
            .disconnect(pcsc::Disposition::LeaveCard)
            .unwrap_or_else(|_| panic!("Could not disconnect PCSC card"));
    }
}

impl Transactor for PCSCTransactor {
    fn transmit(&self, send_buffer: &[u8]) -> Result<Vec<u8>, pcsc::Error> {
        let mut receive_buffer = [0; MAX_BUFFER_SIZE_EXTENDED];
        Ok(self
            .card
            .as_ref()
            .unwrap()
            .transmit(send_buffer, &mut receive_buffer)?
            .into())
    }

    fn reset(&mut self) -> Result<(), pcsc::Error> {
        self.card.as_mut().unwrap().reconnect(
            ShareMode::Shared,
            Protocols::ANY,
            pcsc::Disposition::LeaveCard,
        )
    }
}
