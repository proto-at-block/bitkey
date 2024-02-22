use serde::Deserialize;
use serde::Serialize;

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct MigrationRecord {
    /// Name of the service that gets migrations, used as hash key in ddb
    pub service_identifier: String,
    pub latest_migration: String,
}
