#![forbid(unsafe_code)]

pub(crate) mod cache;
mod commands;
mod db;
mod entities;
mod nfc;
mod requests;
mod serde_helpers;
mod signers;

use std::path::PathBuf;

use anyhow::Result;
use bdk::bitcoin::network::constants::Network;

use bdk::bitcoin::Address;
use bdk::blockchain::ElectrumBlockchain;
use bdk::electrum_client::Client as ElectrumClient;

use clap::{Parser, Subcommand};
use rustify::blocking::clients::reqwest::Client;
use tracing_subscriber::{prelude::*, EnvFilter, Registry};

#[derive(Clone, Parser)]
#[clap()]
pub struct Cli {
    /// Filename for the wallet database
    #[clap(short, long, default_value = "wallet.db")]
    wallet: String,

    /// URL for the server
    #[clap(short, long, default_value = "https://api.dev.wallet.build")]
    server: String,

    /// Cognito Client ID for the wallet user-pool
    /// This one is from user pool us-west-2_YNkgrK6JC in the dev account
    #[clap(short, long, default_value = "dk4rvffhp6k55bjb05vbemdn5")]
    auth_client_id: String,

    /// URL for the Electrum node
    #[clap(short, long, default_value = "ssl://electrum.nodes.wallet.build:51002")]
    electrum: String,

    #[clap(subcommand)]
    command: Commands,
}

#[derive(Clone, Subcommand)]
enum Commands {
    /// Pair with the hardware
    Pair {
        /// Use which network
        #[clap(short, long, default_value_t = Network::Signet)]
        network: Network,

        /// Fake the hardware key (does NOT talk to the hardware)
        #[clap(short, long)]
        fake: bool,
    },
    /// Wipe the hardware
    Wipe {},
    /// Account operations (e.g. create, recover)
    Account {
        #[clap(subcommand)]
        command: AccountCommands,
    },
    /// Wallet operations (e.g. send and receive)
    Wallet {
        #[clap(subcommand)]
        command: WalletCommands,
    },
    /// Do an end-to-end test, exercising all(?) the features
    EndToEnd {
        /// root key for treasury wallet that is used to provide sats for the test
        treasury_root_key: Option<String>,
    },
    /// Firmware operations (e.g. upload)
    Firmware {
        #[clap(subcommand)]
        command: FirmwareCommands,
    },

    /// Wallet snoop command (for debugging wallets)
    Snoop {
        // default is the account table in dev
        #[arg(short, long, default_value_t = String::from("PrototypeOnboardingStack-main-AccountsBE8A900E-16KVFQVF91LZH"))]
        account_table: String,
        // default is the recovery table in dev
        #[arg(short, long, default_value_t = String::from("PrototypeOnboardingStack-main-AccountRecoveryB2C16AE3-10PU42EY82ML6"))]
        recovery_table: String,
        #[arg(short, long, default_value_t = 20)]
        gap_limit: u32,
        #[arg(long)]
        address_balance: bool,
        account_id: String,
    },

    CheckKeyproofs {
        // default is the account table in dev
        #[arg(short, long, default_value_t = String::from("PrototypeOnboardingStack-main-AccountsBE8A900E-16KVFQVF91LZH"))]
        account_table: String,
        account_id: String,
        access_token: String,
        app_signature: String,
        hardware_signature: String,
    },
}

#[derive(Clone, Subcommand)]
enum AccountCommands {
    /// Create an account on the server
    Create {},
    /// Authenticate with the App Key
    Authenticate {},
    /// Recover an account via the hardware factor
    Recover {},
    /// Rotate the server active keyset
    Rotate {},
    /// Lookup an account by hardware public key
    Lookup { hardware_publickey: String },
}

#[derive(Clone, Subcommand)]
enum WalletCommands {
    /// Display wallet status
    Status {},
    /// Display wallet balance
    Balance {},
    /// Display transaction history
    Transactions {},
    /// Receive funds
    Receive {},
    /// Drain a wallet (send all funds to an address)
    Drain { recipient: Address },
    /// Send funds (with server authorisation)
    ServerSend { recipient: Address, amount: u64 },
    /// Send funds (with hardware authorisation)
    HardwareSend { recipient: Address, amount: u64 },
    /// Display server status
    ServerStatus {},
    /// Setup mobilepay
    SetupMobilePay { amount: u64 },
    /// Delay + Notify recovery
    Recovery {
        #[clap(subcommand)]
        command: RecoveryCommands,
    },
    /// List the UTXOs in the wallet
    Utxos {},
}

#[derive(Clone, Subcommand)]
enum RecoveryCommands {
    /// Begin a Lost Appplication recovery
    LostApplication {},
    /// Status of a recovery
    Status {},
    /// Cancel a recovery
    Cancel {},
    /// Complete a recovery
    Complete {},
}

#[derive(Clone, Subcommand)]
enum FirmwareCommands {
    /// Display firmware metadata
    Metadata {},
    /// Upload firmware
    Upload {
        /// Path to the firmware file (defaults to the latest release from Memfault)
        firmware_bundle: Option<PathBuf>,
    },
}

fn main() -> Result<()> {
    Registry::default()
        .with(EnvFilter::from_default_env())
        .with(tracing_subscriber::fmt::layer().with_ansi(true))
        .init();

    let cli = Cli::parse();
    let client = Client::default(&cli.server);
    let db = sled::open(&cli.wallet)?;
    let blockchain = blockchain(&cli.electrum)?;

    match cli.command {
        Commands::Pair { network, fake } => commands::pair::pair(&db, network, fake)?,
        Commands::Wipe {} => commands::wipe()?,
        Commands::Account { command } => match command {
            AccountCommands::Create {} => commands::account::create(&client, &db)?,
            AccountCommands::Authenticate {} => {
                commands::account::authenticate_with_app_key(&db, &cli.auth_client_id)?
            }
            AccountCommands::Recover {} => {
                commands::account::recover(&client, &db, &cli.auth_client_id)?
            }
            AccountCommands::Rotate {} => commands::account::rotate(&client, &db)?,
            AccountCommands::Lookup { hardware_publickey } => {
                commands::account::lookup(&client, hardware_publickey)?
            }
        },
        Commands::Wallet { command } => match command {
            WalletCommands::Status {} => commands::wallet::status(&client, &db)?,
            WalletCommands::Balance {} => commands::wallet::balance(&client, &db, blockchain)?,
            WalletCommands::Transactions {} => {
                commands::wallet::transactions(&client, &db, blockchain)?
            }
            WalletCommands::Drain { recipient } => {
                commands::wallet::drain(&client, &db, blockchain, recipient)?
            }
            WalletCommands::ServerSend { recipient, amount } => {
                commands::wallet::server_send(&client, &db, blockchain, recipient, amount)?
            }
            WalletCommands::HardwareSend { recipient, amount } => {
                commands::wallet::hardware_send(&client, &db, blockchain, recipient, amount)?
            }
            WalletCommands::Receive {} => commands::wallet::receive(&client, &db)?,
            WalletCommands::ServerStatus {} => commands::wallet::server_status(&client, &db)?,
            WalletCommands::SetupMobilePay { amount } => {
                commands::wallet::setup_mobile_pay(&client, &db, amount)?
            }
            WalletCommands::Recovery { command } => match command {
                RecoveryCommands::LostApplication {} => {
                    commands::wallet::recovery::begin::lost_app(&client, &db)?
                }
                RecoveryCommands::Status {} => {
                    commands::wallet::recovery::status::status_delay_notify(&client, &db)?
                }
                RecoveryCommands::Cancel {} => {
                    commands::wallet::recovery::cancel::cancel_delay_notify(&client, &db)?
                }
                RecoveryCommands::Complete {} => {
                    commands::wallet::recovery::complete::complete_delay_notify(&client, &db)?
                }
            },
            WalletCommands::Utxos {} => commands::wallet::utxos(&client, &db, blockchain)?,
        },
        Commands::EndToEnd {
            ref treasury_root_key,
        } => commands::end_to_end::end_to_end(
            &client,
            blockchain,
            &cli.auth_client_id,
            treasury_root_key,
        )?,
        Commands::Firmware { command } => match command {
            FirmwareCommands::Metadata {} => commands::firmware::metadata()?,
            FirmwareCommands::Upload {
                firmware_bundle: None,
            } => commands::firmware::upload_latest(&client)?,
            FirmwareCommands::Upload {
                firmware_bundle: Some(firmware_bundle),
            } => commands::firmware::upload_bundle(firmware_bundle)?,
        },

        Commands::Snoop {
            account_table,
            recovery_table,
            gap_limit,
            address_balance,
            account_id,
        } => commands::snoop::snoop(
            cli.electrum,
            account_table,
            recovery_table,
            gap_limit,
            address_balance,
            account_id,
        )?,

        Commands::CheckKeyproofs {
            account_table,
            account_id,
            access_token,
            app_signature,
            hardware_signature,
        } => commands::check_keyproofs::check_keyproofs(
            account_table,
            account_id,
            access_token,
            app_signature,
            hardware_signature,
        )?,
    }

    Ok(())
}

fn blockchain(electrum: &str) -> Result<ElectrumBlockchain> {
    Ok(ElectrumBlockchain::from(ElectrumClient::new(electrum)?))
}
