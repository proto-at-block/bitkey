use anyhow::Result;
use rustify::blocking::clients::reqwest::Client;
use time::UtcOffset;

use crate::db::transactions::FromDatabase;
use crate::entities::{Account, AuthenticationToken, SignerHistory};
use crate::requests::helper::EndpointExt;
use crate::requests::{CurrencyCode, MobilePaySetupRequest, Money, SpendingLimit};

pub fn setup_mobile_pay(client: &Client, db: &sled::Db, amount: u64) -> Result<()> {
    let account_id = Account::from_database(db)?.id;
    let signers = SignerHistory::from_database(db)?;
    let context = signers.active.hardware.sign_context()?;

    MobilePaySetupRequest {
        account_id,
        limit: SpendingLimit {
            amount: Money {
                amount,
                currency_code: CurrencyCode::USD,
            },
            time_zone_offset: UtcOffset::UTC,
        },
    }
    .exec_keyproofed(
        client,
        &AuthenticationToken::from_database(db)?,
        Some(&signers.active.application),
        Some(&signers.active.hardware),
        &context,
    )?;

    Ok(())
}
