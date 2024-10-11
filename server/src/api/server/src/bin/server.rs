use clap::{Parser, Subcommand};
use tracing::instrument;

use database::ddb;
use database::ddb::Repository;
use http_server::config;
use migration::repository::MigrationRepository;
use migration::MigrationError;
use types::notification::NotificationChannel;

#[derive(Parser)]
#[clap()]
pub(crate) struct Cli {
    #[clap(subcommand)]
    pub(crate) command: Commands,
}

#[derive(Subcommand)]
pub(crate) enum Commands {
    /// Run the server
    Server,
    /// Run the specified worker
    Worker {
        #[clap(subcommand)]
        command: WorkerCommands,
    },
    Migrate,
    Cron {
        #[clap(subcommand)]
        command: CronCommands,
    },
}

#[allow(clippy::upper_case_acronyms)]
#[derive(Subcommand)]
pub(crate) enum WorkerCommands {
    /// Run the Push worker
    Push,
    /// Run the Email worker
    Email,
    /// Run the SMS worker
    Sms,
    /// Run the Scheduled Notification worker
    ScheduledNotification {
        /// Number of seconds to sleep per iteration
        #[arg(long, default_value_t = 300, env = "SLEEP_DURATION_SECONDS")]
        sleep_duration_seconds: u64,
    },
    /// Run the Blockchain Polling worker
    BlockchainPolling {
        /// Number of seconds to sleep per iteration
        #[arg(long, default_value_t = 10, env = "SLEEP_DURATION_SECONDS")]
        sleep_duration_seconds: u64,
    },
    MempoolPolling {
        /// Number of seconds to sleep per iteration
        #[arg(long, default_value_t = 10, env = "SLEEP_DURATION_SECONDS")]
        sleep_duration_seconds: u64,
    },
    /// Run the Metrics worker
    Metrics {
        /// Number of seconds to sleep per iteration
        #[arg(long, default_value_t = 300, env = "SLEEP_DURATION_SECONDS")]
        sleep_duration_seconds: u64,
    },
}

#[allow(clippy::upper_case_acronyms)]
#[derive(Subcommand)]
pub(crate) enum CronCommands {
    /// Grinds signet coins for our mobile integration tests wallet (for testing purposes only)
    CoinGrinder,
}

#[derive(Debug, thiserror::Error)]
enum Error {
    #[error(transparent)]
    Bootstrap(#[from] server::BootstrapError),
    #[error(transparent)]
    Service(#[from] Box<dyn std::error::Error>),
    #[error(transparent)]
    Worker(#[from] workers::error::WorkerError),
    #[error(transparent)]
    Migration(#[from] migration::MigrationError),
}

#[tokio::main]
#[instrument(err)]
async fn main() -> Result<(), Error> {
    let cli = Cli::parse();

    match cli.command {
        Commands::Server => {
            server_handler().await?;
        }
        Commands::Worker { command } => {
            let profile = None;
            let bootstrap = server::create_bootstrap(profile).await?;
            let state = workers::jobs::WorkerState {
                config: http_server::config::extract(profile).unwrap(),
                notification_service: bootstrap.services.notification_service,
                account_service: bootstrap.services.account_service,
                recovery_service: bootstrap.services.recovery_service,
                chain_indexer_service: bootstrap.services.chain_indexer_service,
                mempool_indexer_service: bootstrap.services.mempool_indexer_service,
                address_service: bootstrap.services.address_service,
                sqs: bootstrap.services.sqs,
                feature_flags_service: bootstrap.services.feature_flags_service,
                privileged_action_repository: bootstrap.services.privileged_action_repository,
                inheritance_repository: bootstrap.services.inheritance_repository,
                social_recovery_repository: bootstrap.services.social_recovery_repository,
            };

            match command {
                WorkerCommands::Push => {
                    workers::jobs::customer_notification::handler(
                        &state,
                        NotificationChannel::Push,
                    )
                    .await?;
                }
                WorkerCommands::Email => {
                    workers::jobs::customer_notification::handler(
                        &state,
                        NotificationChannel::Email,
                    )
                    .await?;
                }
                WorkerCommands::ScheduledNotification {
                    sleep_duration_seconds,
                } => {
                    workers::jobs::scheduled_notification::handler(state, sleep_duration_seconds)
                        .await?;
                }
                WorkerCommands::Sms => {
                    workers::jobs::customer_notification::handler(&state, NotificationChannel::Sms)
                        .await?;
                }
                WorkerCommands::BlockchainPolling {
                    sleep_duration_seconds,
                } => {
                    workers::jobs::blockchain_polling::handler(&state, sleep_duration_seconds)
                        .await?;
                }
                WorkerCommands::MempoolPolling {
                    sleep_duration_seconds,
                } => {
                    workers::jobs::mempool_polling::handler(&state, sleep_duration_seconds).await?;
                }
                WorkerCommands::Metrics {
                    sleep_duration_seconds,
                } => {
                    workers::jobs::metrics::handler(&state, sleep_duration_seconds).await?;
                }
            }
        }
        Commands::Migrate => {
            let profile = None;
            let bootstrap = server::create_bootstrap(profile).await?;
            // TODO make better errors
            let ddb = config::extract::<ddb::Config>(profile)
                .map_err(|_| MigrationError::CantStartMigrations)?
                .to_connection()
                .await;
            let migration_repository = MigrationRepository::new(ddb.clone());
            migration_repository
                .create_table_if_necessary()
                .await
                .map_err(|_| MigrationError::CantStartMigrations)?;
            {
                let migration_runner = migration::Runner::new(
                    migration_repository,
                    vec![
                        Box::new(bootstrap.services.account_service),
                        Box::new(bootstrap.services.userpool_service),
                        Box::new(bootstrap.services.notification_service),
                        Box::new(bootstrap.services.daily_spend_record_service),
                        Box::new(bootstrap.services.recovery_relationship_service),
                    ],
                );
                migration_runner.run_migrations().await?;
            }
        }
        Commands::Cron { command } => match command {
            CronCommands::CoinGrinder => {
                workers::jobs::coin_grinder::handler().await?;
            }
        },
    }

    Ok(())
}

async fn server_handler() -> Result<(), Box<dyn std::error::Error>> {
    let (listener, router) = server::axum().await?;
    tracing::info!("listening on {}", listener.local_addr().unwrap());
    axum::serve(
        listener,
        router.into_make_service_with_connect_info::<std::net::SocketAddr>(),
    )
    .await?;
    Ok(())
}
