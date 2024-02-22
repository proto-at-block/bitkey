use anyhow::{bail, Result};
use wca::pcsc::PCSCTransactor;

use crate::nfc::NFCTransactions;

pub(crate) fn wipe() -> Result<()> {
    if !PCSCTransactor::new()?.wipe()? {
        bail!("failed")
    }

    Ok(())
}
