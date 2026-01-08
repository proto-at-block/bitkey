use anyhow::{bail, Result};
use wca::commands::WipeStateResult;
use wca::pcsc::PCSCTransactor;

use crate::nfc::NFCTransactions;

pub(crate) fn wipe() -> Result<()> {
    match PCSCTransactor::new()?.wipe()? {
        WipeStateResult::Success { value: true } => Ok(()),
        _ => bail!("failed"),
    }
}
