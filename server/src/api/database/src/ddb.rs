use std::{
    env,
    fmt::{self, Debug, Formatter},
    time::Duration,
};

use async_trait::async_trait;
use aws_config::{retry::RetryConfig, timeout::TimeoutConfig, BehaviorVersion, SdkConfig};
use aws_sdk_dynamodb::{config::Builder, error::SdkError, operation::scan::ScanError};
use aws_sdk_dynamodb::{error::BuildError, Client};
use aws_smithy_async::rt::sleep::default_async_sleep;
use aws_types::region::Region;
use errors::ApiError;
use http::Uri;
use serde::{Deserialize, Serialize};
use serde_dynamo::{
    from_item, from_items, to_attribute_value, to_item, AttributeValue, Item, Items,
};
use thiserror::Error;

use crate::{build_fake_sdk_config, DBMode};

#[derive(Deserialize)]
pub struct Config {
    pub dynamodb: DBMode,
}

impl Config {
    pub async fn to_connection(self) -> Connection {
        match self.dynamodb {
            DBMode::Endpoint(endpoint) => {
                Connection::fake_from_endpoint(endpoint.parse::<Uri>().unwrap())
            }
            DBMode::Test => Connection {
                test_run_id: None,
                ..Connection::fake_from_endpoint(Uri::from_static("http://localhost:8000"))
            },
            DBMode::Environment => Connection::from_sdk_config(
                &aws_config::load_defaults(BehaviorVersion::latest()).await,
            ),
        }
    }
}

#[derive(Clone)]
pub struct Connection {
    endpoint: Option<Uri>,
    test_run_id: Option<String>,
    pub client: Client,
}

impl Connection {
    pub fn has_test_run_id(&self) -> bool {
        self.test_run_id.is_some()
    }
}

impl Debug for Connection {
    fn fmt(&self, f: &mut Formatter<'_>) -> fmt::Result {
        f.debug_struct("Connection").finish()
    }
}

#[derive(Debug, Error)]
pub enum HealthCheckError {
    #[error(transparent)]
    Configuration(#[from] DatabaseError),
    #[error(transparent)]
    Communication(#[from] SdkError<ScanError>),
}

impl Connection {
    // Used by the lambdas
    pub fn from_sdk_config(sdk_config: &SdkConfig) -> Self {
        Self {
            endpoint: None,
            test_run_id: None,
            client: Client::new(sdk_config),
        }
    }

    pub fn fake_from_endpoint(endpoint: Uri) -> Self {
        let client = build_fake_ddb_client(&build_fake_sdk_config(), &endpoint);

        Self {
            endpoint: Some(endpoint),
            test_run_id: None,
            client,
        }
    }

    pub fn get_table_name(&self, object: DatabaseObject) -> Result<String, DatabaseError> {
        let (env, table_name) = match object {
            DatabaseObject::Recovery => ("RECOVERY_TABLE", "WalletRecovery"),
            DatabaseObject::Wallet => ("WALLET_TABLE", "Wallets"),
            DatabaseObject::Notification => ("NOTIFICATION_TABLE", "Notification"),
            DatabaseObject::Account => ("ACCOUNT_TABLE", "Account"),
            DatabaseObject::ChainIndexer => ("CHAIN_INDEXER_TABLE", "ChainIndexer"),
            DatabaseObject::DailySpendingRecord => {
                ("DAILY_SPENDING_RECORD_TABLE", "DailySpendingRecord")
            }
            DatabaseObject::SignedPsbtCache => ("SIGNED_PSBT_CACHE_TABLE", "SignedPsbtCache"),
            DatabaseObject::AddressWatchlist => ("ADDRESS_WATCHLIST_TABLE", "AddressWatchlist"),
            DatabaseObject::Migration => ("MIGRATION_TABLE", "Migration"),
            DatabaseObject::SocialRecovery => ("SOCIAL_RECOVERY_TABLE", "SocialRecovery"),
            DatabaseObject::Consent => ("CONSENT_TABLE", "Consent"),
        };

        match self {
            Connection {
                test_run_id: Some(test_run_id),
                ..
            } => Ok(format!(
                "{}-{table_name}",
                test_run_id
                    .chars()
                    .take(254 - table_name.len())
                    .collect::<String>()
            )),
            Connection { endpoint: None, .. } => {
                env::var(env).map_err(|_| DatabaseError::TableNameNotFound(object))
            }
            _ => Ok(table_name.to_owned()),
        }
    }
}

#[async_trait]
pub trait DDBService {
    fn new(config: Connection) -> Self
    where
        Self: Sized;
    fn get_connection(&self) -> &Connection;
    fn get_database_object(&self) -> DatabaseObject;
    async fn get_table_name(&self) -> Result<String, DatabaseError>;
    async fn table_exists(&self) -> Result<bool, DatabaseError>;
    // ⚠️ DDB can only have 5 table creation or index creation calls in flight at any time.
    // ⚠️ If there are more than that, then local DDB will throw errors during tests.
    async fn create_table(&self) -> Result<(), DatabaseError>;
    async fn purge_table_if_necessary(&self) -> Result<(), DatabaseError> {
        Ok(())
    }
    async fn create_table_if_necessary(&self) -> Result<(), DatabaseError> {
        if !self.table_exists().await? {
            self.create_table().await?;
        }
        Ok(())
    }
}

fn build_fake_ddb_client(sdk_config: &SdkConfig, endpoint: &Uri) -> Client {
    let dynamodb_config = Builder::from(sdk_config)
        .behavior_version(BehaviorVersion::latest())
        .region(Region::new("us-west-2"))
        .endpoint_url(endpoint.to_string())
        .sleep_impl(default_async_sleep().unwrap())
        .retry_config(RetryConfig::disabled())
        .timeout_config(
            TimeoutConfig::builder()
                .connect_timeout(Duration::from_secs(3))
                .read_timeout(Duration::from_secs(5))
                .operation_timeout(Duration::from_secs(30))
                .operation_attempt_timeout(Duration::from_secs(10))
                .build(),
        )
        .build();
    Client::from_conf(dynamodb_config)
}

#[derive(Debug, Default, Clone, Copy)]
pub enum DatabaseObject {
    Recovery,
    Wallet,
    Notification,
    #[default]
    Account,
    ChainIndexer,
    DailySpendingRecord,
    SignedPsbtCache,
    AddressWatchlist,
    Migration,
    SocialRecovery,
    Consent,
}

impl fmt::Display for DatabaseObject {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        match self {
            DatabaseObject::Recovery => write!(f, "Recovery"),
            DatabaseObject::Wallet => write!(f, "Wallet"),
            DatabaseObject::Notification => write!(f, "Notification"),
            DatabaseObject::Account => write!(f, "Account"),
            DatabaseObject::ChainIndexer => write!(f, "ChainIndexer"),
            DatabaseObject::DailySpendingRecord => write!(f, "DailySpendingRecord"),
            DatabaseObject::SignedPsbtCache => write!(f, "SignedPsbtCache"),
            DatabaseObject::AddressWatchlist => write!(f, "AddressWatchList"),
            DatabaseObject::Migration => write!(f, "Migration"),
            DatabaseObject::SocialRecovery => write!(f, "SocialRecovery"),
            DatabaseObject::Consent => write!(f, "Consent"),
        }
    }
}

#[derive(Debug, Error)]
pub enum DatabaseError {
    #[error("Could not create request due to error: {0}")]
    RequestContruction(#[from] BuildError),
    #[error("Could not create table for {0}")]
    CreateTableError(DatabaseObject),
    #[error("Could not delete table for {0}")]
    DeleteTableError(DatabaseObject),
    #[error("Could not make requests to DB for {0}")]
    FetchError(DatabaseObject),
    #[error("Table name for {0} not found in env")]
    TableNameNotFound(DatabaseObject),
    #[error("Could not fetch {0}")]
    ObjectNotFound(DatabaseObject),
    #[error("Couldn't persist {0}")]
    PersistenceError(DatabaseObject),
    #[error("Couldn't update field on {0}")]
    UpdateError(DatabaseObject),
    #[error("Couldn't delete items on {0}")]
    DeleteItemsError(DatabaseObject),
    #[error("Couldn't serialize field on {0} due to error {1}")]
    SerializationError(DatabaseObject, serde_dynamo::Error),
    #[error("Couldn't format DateTime field on {0}")]
    DatetimeFormatError(DatabaseObject),
    #[error("Couldn't deserialize field on {0} due to error {1}")]
    DeserializationError(DatabaseObject, serde_dynamo::Error),
    #[error("Couldn't serialize attribute value error on {0} due to error {1}")]
    SerializeAttributeValueError(DatabaseObject, serde_dynamo::Error),
    #[error("Could not fetch unique {0}")]
    ObjectNotUnique(DatabaseObject),
}

impl From<DatabaseError> for ApiError {
    fn from(val: DatabaseError) -> Self {
        let err_msg = val.to_string();
        match &val {
            DatabaseError::CreateTableError(_)
            | DatabaseError::DeleteTableError(_)
            | DatabaseError::FetchError(_)
            | DatabaseError::TableNameNotFound(_)
            | DatabaseError::PersistenceError(_)
            | DatabaseError::UpdateError(_)
            | DatabaseError::SerializationError(_, _)
            | DatabaseError::DeserializationError(_, _)
            | DatabaseError::DatetimeFormatError(_)
            | DatabaseError::SerializeAttributeValueError(_, _)
            | DatabaseError::ObjectNotUnique(_)
            | DatabaseError::RequestContruction(_)
            | DatabaseError::DeleteItemsError(_) => {
                ApiError::GenericInternalApplicationError(err_msg)
            }
            DatabaseError::ObjectNotFound(_) => ApiError::GenericNotFound(err_msg),
        }
    }
}

pub fn try_from_item<'a, T, V>(item: T, database_obj: DatabaseObject) -> Result<V, DatabaseError>
where
    Item: From<T>,
    V: Deserialize<'a>,
{
    from_item(item).map_err(|e| DatabaseError::DeserializationError(database_obj, e))
}

pub fn try_from_items<'a, T, V>(
    items: T,
    database_obj: DatabaseObject,
) -> Result<Vec<V>, DatabaseError>
where
    Items: From<T>,
    V: Deserialize<'a>,
{
    from_items(items).map_err(|e| DatabaseError::DeserializationError(database_obj, e))
}

pub fn try_to_item<T, V>(item: T, database_obj: DatabaseObject) -> Result<V, DatabaseError>
where
    T: Serialize,
    V: From<Item>,
{
    to_item(item).map_err(|e| DatabaseError::SerializationError(database_obj, e))
}

pub fn try_to_attribute_val<T, V>(attr: T, database_obj: DatabaseObject) -> Result<V, DatabaseError>
where
    T: Serialize,
    V: From<AttributeValue>,
{
    to_attribute_value(attr)
        .map_err(|e| DatabaseError::SerializeAttributeValueError(database_obj, e))
}
