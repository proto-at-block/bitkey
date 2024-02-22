use anyhow::{bail, Result};
use rustify::blocking::clients::reqwest::Client;
use wca::pcsc::NullTransactor;

use crate::{
    db::transactions::FromDatabase,
    entities::{Account, AuthenticationToken, HardwareSignerProxy, SignerHistory},
    nfc::SafeTransactor,
    requests::{
        helper::EndpointExt, CancelDelayNotifyRequest, Factor, PendingDelayNotify,
        RecoveryStatusRequest,
    },
    signers::seed::SeedSigner,
};

pub fn cancel_delay_notify(client: &Client, db: &sled::Db) -> Result<()> {
    let account_id = Account::from_database(db)?.id;
    let token = AuthenticationToken::from_database(db)?;

    let response = RecoveryStatusRequest {
        account_id: account_id.clone(),
    }
    .exec_authenticated(client, &token)?;

    let signers = SignerHistory::from_database(db)?;
    let (application, hardware, context) = match response.pending_delay_notify {
        Some(PendingDelayNotify {
            lost_factor: Factor::App,
            ..
        }) => {
            let transactor = signers.active.hardware.sign_context()?;
            (
                None::<&SeedSigner>,
                Some(&signers.active.hardware),
                transactor,
            )
        }
        Some(PendingDelayNotify {
            lost_factor: Factor::Hw,
            ..
        }) => (
            Some(&signers.active.application),
            None::<&HardwareSignerProxy>,
            SafeTransactor::new(NullTransactor),
        ),
        None => bail!("no recovery to cancel"),
    };

    CancelDelayNotifyRequest {
        account_id,
        signature: None,
        check_signature: false,
        verification_code: None,
    }
    .exec_keyproofed(client, &token, application, hardware, &context)?;

    Ok(())
}
