use anyhow::{bail, Context, Result};
use rustify::blocking::clients::reqwest::Client;
use sled::Db;

use crate::db::transactions::{FromDatabase, ToDatabase};
use crate::entities::{Account, DescriptorKeyset, KeyMaterial, Keyset, SignerHistory};
use crate::requests::helper::EndpointExt;
use crate::requests::{AuthKeypairRequest, SpendingKeysetRequest};
use crate::requests::{CreateAccount, CreateSoftwareAccount, SoftwareOnlyAuthKeypairRequest};
use crate::signers::{Authentication, Spending};
use crate::AccountType;

pub fn create(client: &Client, db: &Db, account_type: &AccountType) -> Result<()> {
    if Account::from_database(db).is_ok() {
        bail!("already created an account")
    }

    let signers =
        SignerHistory::from_database(db).context("no paired signers found; please `pair` first")?;
    let network = signers.active.network;
    let spending_application = Spending::public_key(&signers.active.application);
    let spending_hardware = Spending::public_key(&signers.active.hardware);

    let account = match account_type {
        AccountType::Classic => {
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
                key_material: KeyMaterial::Keyset(vec![keyset]),
            }
        }
        AccountType::Software => {
            let response = CreateSoftwareAccount {
                auth: SoftwareOnlyAuthKeypairRequest {
                    app: Authentication::public_key(&signers.active.application),
                    // We use the "hardware" key in-place as the recovery key for icebox accounts
                    recovery: Authentication::public_key(&signers.active.hardware),
                },
                is_test_account: true,
            }
            .exec_unauthenticated(client)?;
            println!("{}", response.account_id);

            Account {
                id: response.account_id,
                key_material: KeyMaterial::ShareDetail(None),
            }
        }
    };

    account.to_database(db)?;

    Ok(())
}
