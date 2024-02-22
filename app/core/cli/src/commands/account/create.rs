use anyhow::{bail, Context, Result};
use rustify::blocking::clients::reqwest::Client;
use sled::Db;

use crate::db::transactions::{FromDatabase, ToDatabase};
use crate::entities::{Account, DescriptorKeyset, Keyset, SignerHistory};
use crate::requests::helper::EndpointExt;
use crate::requests::CreateAccount;
use crate::requests::{AuthKeypairRequest, SpendingKeysetRequest};
use crate::signers::{Authentication, Spending};

pub fn create(client: &Client, db: &Db) -> Result<()> {
    if Account::from_database(db).is_ok() {
        bail!("already created an account")
    }

    let signers =
        SignerHistory::from_database(db).context("no paired signers found; please `pair` first")?;
    let network = signers.active.network;
    let spending_application = Spending::public_key(&signers.active.application);
    let spending_hardware = Spending::public_key(&signers.active.hardware);

    let response = CreateAccount {
        auth: AuthKeypairRequest {
            app: Authentication::public_key(&signers.active.application),
            hardware: Authentication::public_key(&signers.active.hardware),
        },
        spending: SpendingKeysetRequest {
            network,
            app: spending_application.clone(),
            hardware: spending_hardware.clone(),
        },
        is_test_account: true,
    }
    .exec_unauthenticated(client)?;

    println!("{}", response.account_id);

    let keyset = Keyset {
        id: response.keyset.keyset_id,
        network,
        keys: DescriptorKeyset {
            application: spending_application,
            hardware: spending_hardware,
            server: response.keyset.spending,
        },
    };

    Account {
        id: response.account_id,
        keysets: vec![keyset],
    }
    .to_database(db)?;

    Ok(())
}
