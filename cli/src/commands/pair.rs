use std::{thread::sleep, time::Duration};

use anyhow::Result;
use bdk::bitcoin::Network;
use wca::pcsc::{PCSCTransactor, Transactor, TransactorError};

use crate::{
    db::transactions::{FromDatabase, ToDatabase},
    entities::{HardwareSignerProxy, SignerHistory, SignerPair},
    nfc::{NFCTransactions, PairingError},
    signers::{hardware::HardwareSigner, seed::SeedSigner},
};

pub(crate) fn pair(db: &sled::Db, network: Network, use_fake_hardware: bool) -> Result<()> {
    let active = SignerPair {
        network,
        application: SeedSigner::new(network, 0),
        hardware: if use_fake_hardware {
            HardwareSignerProxy::Fake(SeedSigner::new(network, 0))
        } else {
            HardwareSignerProxy::Real(pair_real(network, &mut PCSCTransactor::new()?)?)
        },
    };

    let inactive = SignerHistory::from_database(db)
        .map(|signers| {
            let mut inactive = vec![signers.active];
            inactive.extend(signers.inactive);
            inactive
        })
        .unwrap_or_default();

    SignerHistory { active, inactive }.to_database(db)?;

    Ok(())
}

pub(crate) fn pair_real(
    network: Network,
    transactor: &mut impl Transactor,
) -> Result<HardwareSigner> {
    if !transactor.is_authenticated()? {
        transactor.enroll()?;

        println!("Touch your finger to the hardware until you see a green light!");

        loop {
            sleep(Duration::from_secs(1)); // TODO: does this need to be after initialising transactor?

            transactor.reset()?;
            match transactor.is_enrollment_finished() {
                Ok(true) => break,
                Ok(false) => println!("(Keep touching!)"),
                Err(PairingError::Transaction(TransactorError::ReaderError(
                    pcsc::Error::NoSmartcard,
                ))) => continue,
                Err(err) => println!("Waiting... (err: {err})"),
            }
        }
    }

    Ok(HardwareSigner::new(network, transactor)?)
}
