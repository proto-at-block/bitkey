use anyhow::Result;
use rustify::blocking::clients::reqwest::Client;

use crate::{
    cache::FromCache,
    db::transactions::{FromDatabase, ToDatabase},
    entities::{Account, AuthenticationToken, SignerHistory, SignerPair},
    requests::{
        helper::EndpointExt, CreateKeysetRequest, SetActiveKeysetRequest, SpendingKeysetRequest,
    },
    signers::Spending,
};

pub(crate) fn rotate(client: &Client, db: &sled::Db) -> Result<()> {
    let signers = SignerHistory::from_database(db)?;
    println!("{}", &signers.active);
    let context = signers.active.hardware.sign_context()?;

    let account = Account::from_cache(client, db)?;
    let account_id = account.id;

    let new_active = SignerPair {
        network: signers.active.network,
        application: signers.active.application.next(
            account.keysets.iter().map(|ks| ks.keys.application.clone()),
            &context,
        )?,
        hardware: signers.active.hardware.next(
            account.keysets.iter().map(|ks| ks.keys.hardware.clone()),
            &context,
        )?,
    };
    println!("{}", &new_active);

    let signers = if signers.active == new_active {
        signers
    } else {
        SignerHistory {
            active: new_active,
            inactive: {
                let mut inactive = vec![signers.active];
                inactive.extend(signers.inactive);
                inactive
            },
        }
        .to_database(db)?;
        SignerHistory::from_database(db)?
    };

    let token = AuthenticationToken::from_database(db)?;

    let keyset = CreateKeysetRequest {
        account_id: account_id.clone(),
        spending: SpendingKeysetRequest {
            network: signers.active.network,
            app: signers.active.application.public_key(),
            hardware: signers.active.hardware.public_key(),
        },
    }
    .exec_keyproofed(
        client,
        &token,
        Some(&signers.active.application),
        Some(&signers.active.hardware),
        &context,
    )?;

    println!("{}", keyset.keyset_id);

    SetActiveKeysetRequest {
        account_id,
        keyset_id: keyset.keyset_id,
        dummy: true,
    }
    .exec_keyproofed(
        client,
        &token,
        Some(&signers.active.application),
        Some(&signers.active.hardware),
        &context,
    )?;

    Ok(())
}
