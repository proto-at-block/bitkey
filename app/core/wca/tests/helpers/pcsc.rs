use wca::pcsc::{NullTransactor, PCSCTransactor, Transactor, TransactorError};

use super::expectations::Expectations;

pub struct RecordingTransactor<'a> {
    expectations: &'a Expectations,
    real_transactor: Box<dyn Transactor>,
}

impl<'a> RecordingTransactor<'a> {
    pub fn new(expectations: &'a Expectations) -> Result<Self, TransactorError> {
        Ok(Self {
            expectations,
            real_transactor: match expectations.is_recording() {
                true => Box::new(PCSCTransactor::new()?),
                false => Box::new(NullTransactor),
            },
        })
    }
}

impl Transactor for RecordingTransactor<'_> {
    fn transmit(&self, message: &[u8]) -> Result<Vec<u8>, pcsc::Error> {
        let buffer = self
            .expectations
            .next_expectation(message.to_owned())
            .expect("input expectation");
        assert_eq!(
            message, buffer,
            "transmitted different message than expected"
        );
        let response = self.real_transactor.transmit(&buffer)?;
        let response = self
            .expectations
            .next_expectation(response)
            .expect("output expectation");
        Ok(response)
    }

    fn reset(&mut self) -> Result<(), pcsc::Error> {
        // TODO: Record this too?
        Ok(())
    }
}
