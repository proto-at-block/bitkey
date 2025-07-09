use std::{
    collections::HashMap,
    env,
    fmt::{self, Debug, Formatter},
    time::Duration,
};

use async_trait::async_trait;
use aws_config::{retry::RetryConfig, timeout::TimeoutConfig, BehaviorVersion, SdkConfig};
use aws_sdk_dynamodb::{
    config::Builder,
    error::{BuildError, ProvideErrorMetadata, SdkError},
    operation::{
        batch_write_item::BatchWriteItemError, scan::ScanError,
        update_item::builders::UpdateItemFluentBuilder,
    },
    types::{
        builders::UpdateBuilder, AttributeDefinition, AttributeValue as AwsAttributeValue,
        BillingMode, GlobalSecondaryIndex, KeySchemaElement, KeyType, KeysAndAttributes,
        Projection, ProjectionType, ScalarAttributeType, Update, WriteRequest,
    },
    Client,
};
use aws_smithy_async::rt::sleep::default_async_sleep;
use aws_types::region::Region;
use errors::ApiError;
use http::Uri;
use serde::{Deserialize, Serialize};
use serde_dynamo::{
    from_item, from_items, to_attribute_value, to_item, AttributeValue, Item, Items,
};
use thiserror::Error;
use tracing::{event, instrument, Level};

use crate::{build_fake_sdk_config, DBMode};

pub const GET_BATCH_MAX: usize = 100;
pub const WRITE_BATCH_MAX: usize = 25;

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
            DatabaseObject::MempoolIndexer => ("MEMPOOL_INDEXER_TABLE", "MempoolIndexer"),
            DatabaseObject::Migration => ("MIGRATION_TABLE", "Migration"),
            DatabaseObject::SocialRecovery => ("SOCIAL_RECOVERY_TABLE", "SocialRecovery"),
            DatabaseObject::Consent => ("CONSENT_TABLE", "Consent"),
            DatabaseObject::PrivilegedAction => ("PRIVILEGED_ACTION_TABLE", "PrivilegedAction"),
            DatabaseObject::Inheritance => ("INHERITANCE_TABLE", "Inheritance"),
            DatabaseObject::PromotionCode => ("PROMOTION_CODE_TABLE", "PromotionCode"),
            DatabaseObject::TransactionVerification => {
                ("TRANSACTION_VERIFICATION_TABLE", "TransactionVerification")
            }
            DatabaseObject::EncryptedAttachment => {
                ("ENCRYPTED_ATTACHMENT_TABLE", "EncryptedAttachment")
            }
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

#[derive(Clone)]
pub struct BaseRepository {
    pub connection: Connection,
    pub database_object: DatabaseObject,
}

/// TableKey represents a key for a DynamoDB table
#[derive(Clone)]
pub struct TableKey {
    pub name: String,
    pub key_type: KeyType,
    pub attribute_type: ScalarAttributeType,
}

/// GlobalSecondaryIndexDef represents a GSI definition for a DynamoDB table
pub struct GlobalSecondaryIndexDef {
    pub name: String,
    pub pk: TableKey,
    pub sk: Option<TableKey>,
}

/// Create a DynamoDB table with common configuration
pub async fn create_dynamodb_table(
    client: &Client,
    table_name: String,
    database_object: DatabaseObject,
    partition_key: TableKey,
    sort_key: Option<TableKey>,
    gsis: Vec<GlobalSecondaryIndexDef>,
) -> Result<(), DatabaseError> {
    // Partition key setup
    let pk_attribute_def = AttributeDefinition::builder()
        .attribute_name(&partition_key.name)
        .attribute_type(partition_key.attribute_type)
        .build()?;

    let pk_key_schema = KeySchemaElement::builder()
        .attribute_name(&partition_key.name)
        .key_type(partition_key.key_type)
        .build()?;

    // Initialize the table builder
    let mut table_builder = client
        .create_table()
        .table_name(table_name.clone())
        .billing_mode(BillingMode::PayPerRequest)
        .attribute_definitions(pk_attribute_def)
        .key_schema(pk_key_schema);

    // Add sort key if provided
    if let Some(sk) = &sort_key {
        let sk_attribute_def = AttributeDefinition::builder()
            .attribute_name(&sk.name)
            .attribute_type(sk.attribute_type.clone())
            .build()?;

        let sk_key_schema = KeySchemaElement::builder()
            .attribute_name(&sk.name)
            .key_type(sk.key_type.clone())
            .build()?;

        table_builder = table_builder
            .attribute_definitions(sk_attribute_def)
            .key_schema(sk_key_schema);
    }

    // Track attribute definitions that have already been added
    let mut defined_attrs = vec![partition_key.name.clone()];
    if let Some(sk) = &sort_key {
        defined_attrs.push(sk.name.clone());
    }

    // Add GSIs if provided
    for gsi in gsis {
        // Only add attribute definition if not already added
        if !defined_attrs.contains(&gsi.pk.name) {
            let attr_def = AttributeDefinition::builder()
                .attribute_name(&gsi.pk.name)
                .attribute_type(gsi.pk.attribute_type)
                .build()?;

            table_builder = table_builder.attribute_definitions(attr_def);
            defined_attrs.push(gsi.pk.name.clone());
        }

        let pk_key_schema = KeySchemaElement::builder()
            .attribute_name(&gsi.pk.name)
            .key_type(gsi.pk.key_type)
            .build()?;

        let mut gsi_builder = GlobalSecondaryIndex::builder()
            .index_name(gsi.name)
            .projection(
                Projection::builder()
                    .projection_type(ProjectionType::All)
                    .build(),
            )
            .key_schema(pk_key_schema);

        // Add sort key for GSI if provided
        if let Some(sk) = gsi.sk {
            if !defined_attrs.contains(&sk.name) {
                let attr_def = AttributeDefinition::builder()
                    .attribute_name(&sk.name)
                    .attribute_type(sk.attribute_type.clone())
                    .build()?;

                table_builder = table_builder.attribute_definitions(attr_def);
                defined_attrs.push(sk.name.clone());
            }

            let sk_key_schema = KeySchemaElement::builder()
                .attribute_name(&sk.name)
                .key_type(sk.key_type.clone())
                .build()?;

            gsi_builder = gsi_builder.key_schema(sk_key_schema);
        }

        table_builder = table_builder.global_secondary_indexes(gsi_builder.build()?);
    }

    // Send the request
    table_builder.send().await.map_err(|err| {
        let service_err = err.into_service_error();
        event!(
            Level::ERROR,
            "Could not create {} table: {service_err:?} with message: {:?}",
            database_object,
            service_err.message()
        );
        DatabaseError::CreateTableError(database_object)
    })?;

    Ok(())
}

#[async_trait]
pub trait Repository {
    fn new(connection: Connection) -> Self
    where
        Self: Sized;

    fn get_connection(&self) -> &Connection;

    fn get_database_object(&self) -> DatabaseObject;

    async fn get_table_name(&self) -> Result<String, DatabaseError> {
        self.get_connection()
            .get_table_name(self.get_database_object())
    }

    async fn table_exists(&self) -> Result<bool, DatabaseError> {
        let table_name = self.get_table_name().await?;
        Ok(self
            .get_connection()
            .client
            .describe_table()
            .table_name(table_name)
            .send()
            .await
            .is_ok())
    }

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

impl BaseRepository {
    pub fn new(connection: Connection, database_object: DatabaseObject) -> Self {
        Self {
            connection,
            database_object,
        }
    }
}

#[async_trait]
impl Repository for BaseRepository {
    fn new(connection: Connection) -> Self
    where
        Self: Sized,
    {
        Self {
            connection,
            database_object: DatabaseObject::default(),
        }
    }

    fn get_connection(&self) -> &Connection {
        &self.connection
    }

    fn get_database_object(&self) -> DatabaseObject {
        self.database_object
    }

    async fn create_table(&self) -> Result<(), DatabaseError> {
        Err(DatabaseError::CreateTableError(self.database_object))
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
    MempoolIndexer,
    Migration,
    SocialRecovery,
    Consent,
    PrivilegedAction,
    Inheritance,
    PromotionCode,
    TransactionVerification,
    EncryptedAttachment,
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
            DatabaseObject::MempoolIndexer => write!(f, "MempoolIndexer"),
            DatabaseObject::Migration => write!(f, "Migration"),
            DatabaseObject::SocialRecovery => write!(f, "SocialRecovery"),
            DatabaseObject::Consent => write!(f, "Consent"),
            DatabaseObject::PrivilegedAction => write!(f, "PrivilegedAction"),
            DatabaseObject::Inheritance => write!(f, "Inheritance"),
            DatabaseObject::PromotionCode => write!(f, "PromotionCode"),
            DatabaseObject::TransactionVerification => write!(f, "TransactionVerification"),
            DatabaseObject::EncryptedAttachment => write!(f, "EncryptedAttachment"),
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
    #[error("Could not add TTL specification on {0}")]
    TimeToLiveSpecification(DatabaseObject),
    #[error("Dependant object not found")]
    DependantObjectNotFound(DatabaseObject),
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
            | DatabaseError::DeleteItemsError(_)
            | DatabaseError::TimeToLiveSpecification(_) => {
                ApiError::GenericInternalApplicationError(err_msg)
            }
            DatabaseError::DependantObjectNotFound(_) => ApiError::GenericBadRequest(err_msg),
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

#[async_trait]
pub trait FetchBatchTrait<'a, T> {
    async fn fetch(
        &self,
        client: &Client,
        table_name: &str,
        database_object: DatabaseObject,
    ) -> Result<Vec<T>, DatabaseError>;

    async fn fetch_batch(
        client: &Client,
        table_name: &str,
        database_object: DatabaseObject,
        ops: &[ReadRequest],
    ) -> Result<Vec<T>, DatabaseError>;
}

#[derive(Debug)]
pub struct ReadRequest {
    pub partition_key: (String, AwsAttributeValue),
    pub sort_key: Option<(String, AwsAttributeValue)>,
}

impl From<&ReadRequest> for HashMap<String, AwsAttributeValue> {
    fn from(op: &ReadRequest) -> Self {
        let mut ka = HashMap::new();
        let (partition_key, hash_value) = op.partition_key.to_owned();
        ka.insert(partition_key, hash_value);
        if let Some((k, v)) = op.sort_key.to_owned() {
            ka.insert(k, v);
        }
        ka
    }
}

#[async_trait]
impl<'a, T> FetchBatchTrait<'a, T> for Vec<ReadRequest>
where
    T: Send + Deserialize<'a>,
{
    #[instrument]
    async fn fetch(
        &self,
        client: &Client,
        table_name: &str,
        database_object: DatabaseObject,
    ) -> Result<Vec<T>, DatabaseError> {
        let mut results = Vec::new();

        // Split the requests into chunks < DDB limitation
        for chunk in self.chunks(GET_BATCH_MAX) {
            if let Ok(result) = <Self as FetchBatchTrait<T>>::fetch_batch(
                client,
                table_name,
                database_object,
                chunk,
            )
            .await
            {
                results.extend(result);
            }
        }
        Ok(results)
    }

    #[instrument]
    async fn fetch_batch(
        client: &Client,
        table_name: &str,
        database_object: DatabaseObject,
        ops: &[ReadRequest],
    ) -> Result<Vec<T>, DatabaseError> {
        let ka = ops
            .iter()
            .map(|op| op.into())
            .collect::<Vec<HashMap<String, AwsAttributeValue>>>();
        let keys_and_attributes = KeysAndAttributes::builder().set_keys(Some(ka)).build()?;

        let mut fetched_values = Vec::new();
        let mut unprocessed_keys = Some(HashMap::from([(
            table_name.to_string(),
            keys_and_attributes,
        )]));

        // On completion, unprocessed_keys is Some({}). Use a filter to break out of the loop.
        while let Some(unprocessed) = unprocessed_keys.filter(|m| !m.is_empty()) {
            let result = client
                .batch_get_item()
                .set_request_items(Some(unprocessed))
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not fetch records: {service_err:?} with message: {:?}",
                        service_err.message()
                    );
                    DatabaseError::FetchError(database_object)
                })?;

            let values = result
                .responses()
                .and_then(|tables| {
                    tables.get(table_name).map(|rows| {
                        rows.iter()
                            .map(|item| try_from_item(item.clone(), database_object))
                    })
                })
                .into_iter()
                .flatten()
                .collect::<Result<Vec<T>, DatabaseError>>()?;

            fetched_values.extend(values);
            unprocessed_keys = result.unprocessed_keys().cloned();
        }
        Ok(fetched_values)
    }
}

#[trait_variant::make(PersistBatchTrait: Send)]
pub trait LocalPersistBatchTrait {
    async fn persist(
        &self,
        client: &Client,
        table_name: &str,
        database_object: DatabaseObject,
    ) -> Result<(), DatabaseError>;

    async fn persist_batch(
        client: &Client,
        table_name: &str,
        ops: &[WriteRequest],
    ) -> Result<(), SdkError<BatchWriteItemError>>;

    fn unprocessed_count(
        unprocessed: Option<&HashMap<String, Vec<WriteRequest>>>,
        table_name: &str,
    ) -> usize;
}

impl PersistBatchTrait for Vec<WriteRequest> {
    #[instrument]
    async fn persist(
        &self,
        client: &Client,
        table_name: &str,
        database_object: DatabaseObject,
    ) -> Result<(), DatabaseError> {
        // Split the requests into chunks < DDB limitation
        for chunk in self.chunks(WRITE_BATCH_MAX) {
            if let Err(err) =
                <Self as PersistBatchTrait>::persist_batch(client, table_name, chunk).await
            {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not persist records: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                return Err(DatabaseError::PersistenceError(database_object));
            }
        }
        Ok(())
    }

    #[instrument]
    async fn persist_batch(
        client: &Client,
        table_name: &str,
        ops: &[WriteRequest],
    ) -> Result<(), SdkError<BatchWriteItemError>> {
        let mut unprocessed = Some(HashMap::from([(table_name.to_string(), ops.to_vec())]));
        while <Self as PersistBatchTrait>::unprocessed_count(unprocessed.as_ref(), table_name) > 0 {
            unprocessed = client
                .batch_write_item()
                .set_request_items(unprocessed)
                .send()
                .await?
                .unprocessed_items;
        }

        Ok(())
    }

    fn unprocessed_count(
        unprocessed: Option<&HashMap<String, Vec<WriteRequest>>>,
        table_name: &str,
    ) -> usize {
        unprocessed
            .map(|m| m.get(table_name).map(|v| v.len()).unwrap_or_default())
            .unwrap_or_default()
    }
}

pub struct UpdateItemOp(UpdateItemFluentBuilder);
impl UpdateItemOp {
    pub fn new(builder: UpdateItemFluentBuilder) -> Self {
        Self(builder)
    }
}

impl TryFrom<UpdateItemOp> for UpdateBuilder {
    type Error = DatabaseError;

    fn try_from(op: UpdateItemOp) -> Result<Self, Self::Error> {
        let update =
            op.0.as_input()
                .clone()
                .build()
                .map_err(DatabaseError::RequestContruction)?;

        let ret = Update::builder()
            .set_key(update.key)
            .set_table_name(update.table_name)
            .set_update_expression(update.update_expression)
            .set_condition_expression(update.condition_expression)
            .set_expression_attribute_values(update.expression_attribute_values)
            .set_expression_attribute_names(update.expression_attribute_names)
            .set_return_values_on_condition_check_failure(
                update.return_values_on_condition_check_failure,
            );

        Ok(ret)
    }
}

pub trait Upsertable {
    const KEY_PROPERTIES: &'static [&'static str];
    const IF_NOT_EXISTS_PROPERTIES: &'static [&'static str];
}

pub trait Upsert {
    fn try_upsert<T>(
        &self,
        item: T,
        database_object: DatabaseObject,
    ) -> Result<UpdateItemFluentBuilder, DatabaseError>
    where
        T: Serialize + Upsertable;
}

impl Upsert for Client {
    fn try_upsert<T>(
        &self,
        input: T,
        database_object: DatabaseObject,
    ) -> Result<UpdateItemFluentBuilder, DatabaseError>
    where
        T: Serialize + Upsertable,
    {
        let item: Item = try_to_item(input, database_object)?;
        let updates = item
            .iter()
            .filter(|(key, _)| !T::KEY_PROPERTIES.contains(&key.as_str()))
            .map(
                |(key, _)| match T::IF_NOT_EXISTS_PROPERTIES.contains(&key.as_str()) {
                    true => format!("#{} = if_not_exists(#{}, :{})", key, key, key),
                    false => format!("#{} = :{}", key, key),
                },
            )
            .collect::<Vec<String>>()
            .join(", ");

        let builder = item
            .iter()
            .fold(self.update_item(), |builder, (key, value)| {
                if T::KEY_PROPERTIES.contains(&key.as_str()) {
                    return builder.key(key.to_owned(), AwsAttributeValue::from(value.clone()));
                }

                builder
                    .expression_attribute_names(format!("#{}", key), key)
                    .expression_attribute_values(
                        format!(":{}", key),
                        AwsAttributeValue::from(value.clone()),
                    )
            })
            .update_expression(format!("SET {}", updates));

        Ok(builder)
    }
}
