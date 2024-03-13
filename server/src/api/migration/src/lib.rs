mod entities;
pub mod repository;

use async_trait::async_trait;
use thiserror::Error;
use tracing::event;

use crate::entities::MigrationRecord;
use crate::repository::Repository;
use database::ddb::DatabaseError;

#[derive(Debug, Error)]
pub enum MigrationError {
    #[error("Can't run migrations")]
    CantStartMigrations,
    #[error("Can't enumerate database table in migration: {0}")]
    CantEnumerateTable(String),
    #[error("Database item with primary key {0} is missing critical field {1}")]
    MissingCriticalField(String, String),
    #[error("Could not update account due to error: {0}")]
    UpdateAccount(String),
    #[error("Could not update notifications preferences due to error: {0}")]
    UpdateNotificationsPreferences(String),
    #[error("Could not send ext beta push blast due to error: {0}")]
    ExtBetaPushBlast(String),
    #[error("Could not write item to database: {0}")]
    DbPersist(DatabaseError),
    #[error("Could not read database: {0}")]
    DbRead(DatabaseError),
}

#[async_trait]
/// A trait for a single migration for a service.
/// The `name` method should return a service-unique name/identifier for the migration.
/// The migrations runner will use a lexicographic
/// sort of the migration names to keep track of which have been run and which still need to be
/// run, so it's recommended that you date-prefix the name.
/// Implement the actual migration logic in the `run` method
pub trait Migration: Sync + Send {
    /// sort-friendly name for the migration
    fn name(&self) -> &str;

    /// The actual business logic for the migration
    async fn run(&self) -> Result<(), MigrationError>;
    async fn rollback(&self) -> Result<(), MigrationError> {
        Ok(())
    }
}

pub trait MigratableService {
    fn get_service_identifier(&self) -> &str;
    fn list_migrations(&self) -> Vec<Box<dyn Migration + '_>>;
}

pub struct Runner {
    services: Vec<Box<dyn MigratableService>>,
    repo: Repository,
}

impl Runner {
    pub fn new(repo: Repository, services: Vec<Box<dyn MigratableService>>) -> Self {
        Self { services, repo }
    }

    pub async fn run_migrations(&self) -> Result<(), MigrationError> {
        for service in &self.services {
            let id = service.get_service_identifier();
            let latest_migration = self.get_latest_migration(id).await?;
            let migrations = service.list_migrations();
            let mut migrations_to_run: Vec<Box<dyn Migration>>;
            if let Some(latest) = latest_migration {
                migrations_to_run = migrations
                    .into_iter()
                    .filter(|m| m.name() > latest.as_str())
                    .collect();
            } else {
                migrations_to_run = migrations;
            };
            if migrations_to_run.is_empty() {
                continue;
            }
            migrations_to_run.sort_by(|a, b| a.name().cmp(b.name()));
            let mut needs_undo = false;
            let mut undo_stack = Vec::new();
            let mut last_migration_name = String::new();
            for migration in migrations_to_run {
                let result = migration.run().await;
                if result.is_ok() {
                    last_migration_name = migration.name().to_string();
                    undo_stack.push(migration);
                } else {
                    event!(
                        tracing::Level::ERROR,
                        "Error running migration {}: {}",
                        migration.name(),
                        result.unwrap_err()
                    );
                    needs_undo = true;
                    break;
                }
            }
            if needs_undo {
                undo_stack.reverse();
                for migration in undo_stack {
                    let rollback_result = migration.rollback().await;
                    if let Err(e) = rollback_result {
                        // log out the error but keep going
                        event!(
                            tracing::Level::ERROR,
                            "Error rolling back migration {}: {}",
                            migration.name(),
                            e
                        );
                    }
                }
            } else {
                self.update_latest_migration(id, &last_migration_name)
                    .await?;
            }
        }
        Ok(())
    }
    async fn get_latest_migration(
        &self,
        service_identifier: &str,
    ) -> Result<Option<String>, MigrationError> {
        match self.repo.fetch(service_identifier).await {
            Ok(record) => Ok(Some(record.latest_migration)),
            Err(err) => match err {
                DatabaseError::ObjectNotFound(_) => Ok(None),
                _ => Err(MigrationError::DbRead(err)),
            },
        }
    }

    async fn update_latest_migration(
        &self,
        service_identifier: &str,
        migration_name: &str,
    ) -> Result<(), MigrationError> {
        self.repo
            .persist(&MigrationRecord {
                service_identifier: service_identifier.to_string(),
                latest_migration: migration_name.to_string(),
            })
            .await
            .map_err(MigrationError::DbPersist)
    }
}
