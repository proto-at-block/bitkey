use std::{
    sync::{Arc, Mutex},
    thread::sleep,
    time::Duration,
};

use bdk::{
    bitcoin::{
        bip32::Fingerprint,
        psbt::PartiallySignedTransaction,
        secp256k1::{ecdsa::Signature, PublicKey},
    },
    miniscript::{descriptor::DescriptorKeyParseError, DescriptorPublicKey},
};
use thiserror::Error;
use wca::{
    commands::{
        DeviceInfo, FirmwareMetadata, FwupFinish, FwupFinishRspStatus, FwupStart, FwupTransfer,
        GetAuthenticationKey, GetFirmwareMetadata, GetInitialSpendingKey, QueryAuthentication,
        SignTransaction,
    },
    pcsc::{Performer, Transactor, TransactorError},
};
use wca::{
    commands::{
        FingerprintEnrollmentStatus, GetDeviceInfo, GetFingerprintEnrollmentStatus,
        GetNextSpendingKey, SignChallenge, StartFingerprintEnrollment, WipeState, WipeStateResult,
    },
    errors::CommandError,
};

#[derive(Clone)]
pub(crate) struct SafeTransactor(Arc<Mutex<dyn Transactor>>);

impl SafeTransactor {
    pub(crate) fn new<T: Transactor + 'static>(transactor: T) -> Self {
        Self(Arc::new(Mutex::new(transactor)))
    }
}

impl Transactor for SafeTransactor {
    fn transmit(&self, buffer: &[u8]) -> Result<Vec<u8>, pcsc::Error> {
        self.0.lock().unwrap().transmit(buffer)
    }

    fn reset(&mut self) -> Result<(), pcsc::Error> {
        self.0.lock().unwrap().reset()
    }
}

#[derive(Error, Debug)]
pub enum PairingError {
    #[error(transparent)]
    Transaction(#[from] TransactorError),
    #[error("could not parse key from hardware")]
    ParseKey(#[from] DescriptorKeyParseError),
    #[error("authentication error: {0:?}")]
    Authentication(FingerprintEnrollmentStatus),
    #[error("could not start firmware upload")]
    FwupStart,
}

pub trait NFCTransactions {
    fn is_enrollment_finished(&self) -> Result<bool, PairingError>;
    fn is_authenticated(&self) -> Result<bool, TransactorError>;
    fn enroll(&self) -> Result<bool, TransactorError>;
    fn sign_message(&self, message: &[u8]) -> Result<Signature, TransactorError>;
    fn sign_transaction(
        &self,
        psbt: PartiallySignedTransaction,
        fingerprint: Fingerprint,
    ) -> Result<PartiallySignedTransaction, TransactorError>;
    fn device_info(&self) -> Result<DeviceInfo, TransactorError>;
    fn metadata(&self) -> Result<FirmwareMetadata, TransactorError>;
    fn upload(&mut self, upload: &Upload) -> Result<FwupFinishRspStatus, PairingError>;
    fn get_authentication_key(&self) -> Result<PublicKey, TransactorError>;
    fn get_initial_spending_key(
        &self,
        network: bdk::bitcoin::Network,
    ) -> Result<DescriptorPublicKey, TransactorError>;
    fn get_next_spending_key(
        &self,
        existing: Vec<DescriptorPublicKey>,
        network: bdk::bitcoin::Network,
    ) -> Result<DescriptorPublicKey, TransactorError>;
    fn wipe(&self) -> Result<WipeStateResult, TransactorError>;
}

impl<T: Transactor + ?Sized> NFCTransactions for T {
    fn is_enrollment_finished(&self) -> Result<bool, PairingError> {
        let result = self.perform(GetFingerprintEnrollmentStatus::new(false))?;
        match result.status {
            s @ FingerprintEnrollmentStatus::StatusUnspecified => {
                Err(PairingError::Authentication(s))
            }
            FingerprintEnrollmentStatus::Incomplete => Ok(false),
            FingerprintEnrollmentStatus::Complete => Ok(true),
            FingerprintEnrollmentStatus::NotInProgress => Ok(false),
        }
    }

    fn is_authenticated(&self) -> Result<bool, TransactorError> {
        self.perform(QueryAuthentication::new())
    }

    fn enroll(&self) -> Result<bool, TransactorError> {
        self.perform(StartFingerprintEnrollment::new(0, "".to_string()))
    }

    fn sign_message(&self, message: &[u8]) -> Result<Signature, TransactorError> {
        let command = SignChallenge::new(message.to_vec(), false);
        self.perform(command)
    }

    fn sign_transaction(
        &self,
        psbt: PartiallySignedTransaction,
        fingerprint: Fingerprint,
    ) -> Result<PartiallySignedTransaction, TransactorError> {
        self.perform(SignTransaction::new(psbt, fingerprint, false))
    }

    fn device_info(&self) -> Result<DeviceInfo, TransactorError> {
        self.perform(GetDeviceInfo::new())
    }

    fn metadata(&self) -> Result<FirmwareMetadata, TransactorError> {
        self.perform(GetFirmwareMetadata::new())
    }

    fn upload(&mut self, upload: &Upload) -> Result<FwupFinishRspStatus, PairingError> {
        if !self.perform(FwupStart::new(None, wca::commands::FwupMode::Normal))? {
            return Err(PairingError::FwupStart);
        }

        upload_asset(self, upload.chunk_size, &upload.application)?;
        upload_asset(self, upload.chunk_size, &upload.signature)?;

        Ok(self.perform(FwupFinish::new(
            upload.app_properties_offset,
            upload.signature.offset,
            wca::commands::FwupMode::Normal,
        ))?)
    }

    fn get_authentication_key(&self) -> Result<PublicKey, TransactorError> {
        self.perform(GetAuthenticationKey::new())
    }

    fn get_initial_spending_key(
        &self,
        network: bdk::bitcoin::Network,
    ) -> Result<DescriptorPublicKey, TransactorError> {
        self.perform(GetInitialSpendingKey::new(network.into()))
    }

    fn get_next_spending_key(
        &self,
        existing: Vec<DescriptorPublicKey>,
        network: bdk::bitcoin::Network,
    ) -> Result<DescriptorPublicKey, TransactorError> {
        self.perform(GetNextSpendingKey::new(existing, network.into()))
    }

    fn wipe(&self) -> Result<WipeStateResult, TransactorError> {
        self.perform(WipeState::new())
    }
}

pub struct Asset {
    pub data: Vec<u8>,
    pub offset: u32,
}

pub struct Upload {
    pub chunk_size: usize,
    pub app_properties_offset: u32,
    pub application: Asset,
    pub signature: Asset,
}

fn upload_asset<T: Transactor + ?Sized>(
    transactor: &mut T,
    chunk_size: usize,
    asset: &Asset,
) -> Result<(), TransactorError> {
    let chunks = asset.data.chunks(chunk_size).enumerate();
    let bar = indicatif::ProgressBar::new(chunks.len() as u64);
    for (sequence_id, chunk) in chunks {
        let command = transactor.perform(FwupTransfer::new(
            sequence_id as u32,
            chunk.to_vec(),
            asset.offset,
            wca::commands::FwupMode::Normal,
        ));

        match command {
            Err(TransactorError::CommandError(CommandError::Unauthenticated)) => {
                bar.println("Please unlock your hardware...");

                while !transactor.is_authenticated().is_ok_and(|x| x) {
                    sleep(Duration::from_secs(1));

                    match transactor.reset() {
                        Ok(_) | Err(pcsc::Error::NoSmartcard) => continue,
                        Err(err) => {
                            bar.abandon_with_message("Giving up due to an error");
                            return Err(TransactorError::ReaderError(err));
                        }
                    }
                }
            }
            Err(e) => {
                bar.abandon();
                return Err(e);
            }
            Ok(_) => bar.inc(1),
        }
    }
    bar.finish_and_clear();

    Ok(())
}
