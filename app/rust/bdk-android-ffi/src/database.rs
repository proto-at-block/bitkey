use bdk::database::any::{SledDbConfiguration, SqliteDbConfiguration};

/// Type that can contain any of the database configurations defined by the library
/// This allows storing a single configuration that can be loaded into an AnyDatabaseConfig
/// instance. Wallets that plan to offer users the ability to switch blockchain backend at runtime
/// will find this particularly useful.
pub enum DatabaseConfig {
    /// Memory database has no config
    Memory,
    /// Simple key-value embedded database based on sled
    Sled { config: SledDbConfiguration },
    /// Sqlite embedded database using rusqlite
    Sqlite { config: SqliteDbConfiguration },
}
