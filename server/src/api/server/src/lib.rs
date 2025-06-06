extern crate core;

use std::collections::{HashMap, HashSet};
use std::fmt::Debug;
use std::sync::Arc;
use std::time::Duration;

use crate::request_baggage::request_baggage;
use account::service::Service as AccountService;
use authn_authz::authorizer::{
    authorize_account_or_recovery_token_for_path, authorize_recovery_token_for_path,
    authorize_token_for_path, AuthorizerConfig,
};
use axum::body::Body;
use axum::extract::Request;
use axum::routing::any_service;
use axum::{middleware, Router};
use axum_tracing_opentelemetry::middleware::{OtelAxumLayer, OtelInResponseLayer};
use bdk_utils::{TransactionBroadcaster, TransactionBroadcasterTrait};
use chain_indexer::{repository::ChainIndexerRepository, service::Service as ChainIndexerService};
use comms_verification::Service as CommsVerificationService;
use database::ddb::{self, Repository};
use exchange_rate::service::Service as ExchangeRateService;
use feature_flags::service::Service as FeatureFlagsService;
use http::header::HOST;
use http_server::config::Config;
use http_server::middlewares::identifier_generator::IdentifierGenerator;
use http_server::middlewares::wsm;
use http_server::router::RouterBuilder;
use http_server::swagger::SwaggerEndpoint;
use http_server::{config, healthcheck};
use instrumentation::metrics::system::init_tokio_metrics;
use instrumentation::middleware::{client_request_context, HttpMetrics};
use jwt_authorizer::IntoLayer;
use mempool_indexer::{
    repository::MempoolIndexerRepository, service::Service as MempoolIndexerService,
};
use mobile_pay::daily_spend_record::{
    repository::DailySpendRecordRepository, service::Service as DailySpendRecordService,
};
use mobile_pay::signed_psbt_cache::{
    repository::PsbtTxidCacheRepository, service::Service as SignedPsbtCacheService,
};
use notification::address_repo::ddb::repository::AddressRepository;
use notification::address_repo::ddb::service::Service as AddressWatchlistService;
use notification::address_repo::errors::Error;
use notification::address_repo::AddressWatchlistTrait;
use notification::clients::iterable::IterableClient;
use notification::clients::twilio::TwilioClient;
use notification::repository::NotificationRepository;
use notification::service::Service as NotificationService;
use privileged_action::service::Service as PrivilegedActionService;
use promotion_code::repository::PromotionCodeRepository;
use promotion_code::service::Service as PromotionCodeService;
use promotion_code::web_shop::WebShopClient;
use promotion_code::webhook::WebhookValidator;
use queue::sqs::SqsQueue;
use recovery::repository::RecoveryRepository;
use recovery::service::inheritance::Service as InheritanceService;
use recovery::service::social::{
    challenge::Service as SocialChallengeService,
    relationship::Service as RecoveryRelationshipService,
};
use repository::account::AccountRepository;
use repository::consent::ConsentRepository;
use repository::privileged_action::PrivilegedActionRepository;
use repository::recovery::inheritance::InheritanceRepository;
use repository::recovery::social::SocialRecoveryRepository;
use request_logger::{log_requests, RequestLoggerState};
pub use routes::axum::axum;
use screener::service::Service as ScreenerService;
use serde::Deserialize;
use thiserror::Error;
use tokio::try_join;
use tower::{service_fn, ServiceExt};
use tower_http::catch_panic::CatchPanicLayer;
use transaction_verification::{
    repository::TransactionVerificationRepository,
    service::Config as TransactionVerificationConfig,
    service::Service as TransactionVerificationService,
};
use types::time::{Clock, DefaultClock};
use userpool::userpool::UserPoolService;
use utoipa_swagger_ui::SwaggerUi;
use wallet_telemetry::{set_global_telemetry, METRICS_REPORTING_PERIOD_SECS};
use wsm_rust_client::WsmClient;

mod request_baggage;
mod request_logger;
mod routes;
pub mod test_utils;

#[cfg(test)]
mod tests;

#[derive(Clone)]
pub struct Bootstrap {
    pub config: Config,
    pub services: Services,
    pub router: Router,
}

#[derive(Clone)]
pub struct Services {
    pub account_service: AccountService,
    pub address_service: Box<dyn AddressWatchlistTrait>,
    pub broadcaster: Arc<dyn TransactionBroadcasterTrait>,
    pub chain_indexer_service: ChainIndexerService,
    pub comms_verification_service: CommsVerificationService,
    pub daily_spend_record_service: DailySpendRecordService,
    pub exchange_rate_service: ExchangeRateService,
    pub feature_flags_service: FeatureFlagsService,
    pub inheritance_service: InheritanceService,
    pub iterable_client: IterableClient,
    pub mempool_indexer_service: MempoolIndexerService,
    pub notification_service: NotificationService,
    pub privileged_action_service: PrivilegedActionService,
    pub promotion_code_service: PromotionCodeService,
    pub recovery_relationship_service: RecoveryRelationshipService,
    pub recovery_service: RecoveryRepository,
    pub screener_service: Arc<ScreenerService>,
    pub signed_psbt_cache_service: SignedPsbtCacheService,
    pub social_challenge_service: SocialChallengeService,
    pub sqs: SqsQueue,
    pub transaction_verification_service: TransactionVerificationService,
    pub twilio_client: TwilioClient,
    pub userpool_service: UserPoolService,
    pub wsm_client: WsmClient,
    pub web_shop_client: WebShopClient,
    pub webhook_validator: WebhookValidator,
    // Remove the following repositories from services
    pub consent_repository: ConsentRepository,
    pub privileged_action_repository: PrivilegedActionRepository,
    pub inheritance_repository: InheritanceRepository,
    pub social_recovery_repository: SocialRecoveryRepository,
}

#[derive(Debug, Error)]
pub enum BootstrapError {
    #[error(transparent)]
    AddressWatchlist(#[from] Error),
    #[error(transparent)]
    AuthorizerInit(#[from] jwt_authorizer::error::InitError),
    #[error(transparent)]
    BindAddress(#[from] std::io::Error),
    #[error("configuration phase failed: {0}")]
    Configuration(#[from] http_server::config::Error),
    #[error(transparent)]
    FeatureFlags(#[from] feature_flags::Error),
    #[error(transparent)]
    DynamoDatabase(#[from] ddb::DatabaseError),
    #[error(transparent)]
    Wsm(#[from] wsm::Error),
    #[error(transparent)]
    Telemetry(#[from] wallet_telemetry::Error),
    #[error(transparent)]
    Metrics(#[from] instrumentation::metrics::error::MetricsError),
}

#[derive(Default)]
pub struct GenServiceOverrides {
    pub address_repo: Option<Box<dyn AddressWatchlistTrait>>,
    pub broadcaster: Option<Arc<dyn TransactionBroadcasterTrait>>,
    pub blocked_addresses: Option<HashSet<String>>,
    pub feature_flags: Option<HashMap<String, String>>,
    pub clock: Option<Arc<dyn Clock>>,
}

impl GenServiceOverrides {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn address_repo(mut self, address_repo: Box<dyn AddressWatchlistTrait>) -> Self {
        self.address_repo = Some(address_repo);
        self
    }

    pub fn broadcaster(mut self, broadcaster: Arc<dyn TransactionBroadcasterTrait>) -> Self {
        self.broadcaster = Some(broadcaster);
        self
    }

    pub fn blocked_addresses(mut self, blocked_addresses: HashSet<String>) -> Self {
        self.blocked_addresses = Some(blocked_addresses);
        self
    }

    pub fn feature_flags(mut self, feature_flags: HashMap<String, String>) -> Self {
        // Globally-required
        let mut flags = HashMap::from([(
            "f8e-request-logging-enabled".to_string(),
            "false".to_string(),
        )]);
        flags.extend(feature_flags);

        self.feature_flags = Some(flags);
        self
    }

    pub fn clock(mut self, clock: Arc<dyn Clock>) -> Self {
        self.clock = Some(clock);
        self
    }
}

pub async fn create_bootstrap(profile: Option<&str>) -> Result<Bootstrap, BootstrapError> {
    create_bootstrap_with_overrides(profile, GenServiceOverrides::default()).await
}
pub async fn create_bootstrap_with_overrides(
    profile: Option<&str>,
    overrides: GenServiceOverrides,
) -> Result<Bootstrap, BootstrapError> {
    BootstrapBuilder::new(profile, overrides)
        .await?
        .build()
        .await
}

// Define the BootstrapBuilder struct
struct BootstrapBuilder {
    profile: Option<String>,
    config: Config,
    services: Services,
}

struct Repositories {
    account_repository: AccountRepository,
    address_watchlist_repository: AddressRepository,
    consent_repository: ConsentRepository,
    notification_repository: NotificationRepository,
    wallet_recovery_repository: RecoveryRepository,
    social_recovery_repository: SocialRecoveryRepository,
    chain_indexer_repository: ChainIndexerRepository,
    mempool_indexer_repository: MempoolIndexerRepository,
    daily_spend_record_repository: DailySpendRecordRepository,
    signed_psbt_cache_repository: PsbtTxidCacheRepository,
    inheritance_repository: InheritanceRepository,
    privileged_action_repository: PrivilegedActionRepository,
    promotion_code_repository: PromotionCodeRepository,
    transaction_verification_repository: TransactionVerificationRepository,
}

#[derive(Clone, Deserialize)]
pub struct SecureSiteConfig {
    pub(crate) secure_site_base_url: url::Url,
}

impl BootstrapBuilder {
    // Constructor for BootstrapBuilder
    pub async fn new(
        profile: Option<&str>,
        overrides: GenServiceOverrides,
    ) -> Result<Self, BootstrapError> {
        // Load configurations
        let config = Config::new(profile)?;

        // Initialize global telemetry and metrics
        set_global_telemetry(&config.wallet_telemetry)?;
        init_tokio_metrics(Duration::from_secs(METRICS_REPORTING_PERIOD_SECS))?;

        // Initialize repositories and services
        let repositories = Self::init_repositories(&config, profile).await?;
        let services = Self::init_services(&overrides, profile, &repositories).await?;

        Ok(Self {
            profile: profile.map(String::from),
            config,
            services,
        })
    }

    // Initialize repositories
    async fn init_repositories(
        config: &Config,
        profile: Option<&str>,
    ) -> Result<Repositories, BootstrapError> {
        // Initialize DynamoDB connection
        let ddb_config = config::extract::<ddb::Config>(profile)?;
        let ddb_connection = ddb_config.to_connection().await;

        // Initialize repositories to be used for service initialization
        let repositories = Repositories {
            account_repository: AccountRepository::new(ddb_connection.clone()),
            address_watchlist_repository: AddressRepository::new(ddb_connection.clone()),
            consent_repository: ConsentRepository::new(ddb_connection.clone()),
            notification_repository: NotificationRepository::new(ddb_connection.clone()),
            wallet_recovery_repository: RecoveryRepository::new_with_override(
                ddb_connection.clone(),
                config.override_current_time,
            ),
            social_recovery_repository: SocialRecoveryRepository::new(ddb_connection.clone()),
            chain_indexer_repository: ChainIndexerRepository::new(ddb_connection.clone()),
            mempool_indexer_repository: MempoolIndexerRepository::new(ddb_connection.clone()),
            daily_spend_record_repository: DailySpendRecordRepository::new(ddb_connection.clone()),
            signed_psbt_cache_repository: PsbtTxidCacheRepository::new(ddb_connection.clone()),
            inheritance_repository: InheritanceRepository::new(ddb_connection.clone()),
            privileged_action_repository: PrivilegedActionRepository::new(ddb_connection.clone()),
            promotion_code_repository: PromotionCodeRepository::new(ddb_connection.clone()),
            transaction_verification_repository: TransactionVerificationRepository::new(
                ddb_connection.clone(),
            ),
        };

        // Create tables concurrently
        try_join!(
            repositories.account_repository.create_table_if_necessary(),
            repositories
                .address_watchlist_repository
                .create_table_if_necessary(),
            repositories.consent_repository.create_table_if_necessary(),
            repositories
                .notification_repository
                .create_table_if_necessary(),
            repositories
                .wallet_recovery_repository
                .create_table_if_necessary(),
            repositories
                .social_recovery_repository
                .create_table_if_necessary(),
            repositories
                .chain_indexer_repository
                .create_table_if_necessary(),
            repositories
                .mempool_indexer_repository
                .create_table_if_necessary(),
            repositories
                .daily_spend_record_repository
                .create_table_if_necessary(),
            repositories
                .signed_psbt_cache_repository
                .create_table_if_necessary(),
            repositories
                .inheritance_repository
                .create_table_if_necessary(),
            repositories
                .privileged_action_repository
                .create_table_if_necessary(),
            repositories
                .promotion_code_repository
                .create_table_if_necessary(),
            repositories
                .transaction_verification_repository
                .create_table_if_necessary(),
        )?;

        Ok(repositories)
    }

    // Initialize services
    async fn init_services(
        overrides: &GenServiceOverrides,
        profile: Option<&str>,
        repositories: &Repositories,
    ) -> Result<Services, BootstrapError> {
        let cognito_config = config::extract::<userpool::userpool::Config>(profile)?;
        let cognito_connection = cognito_config.to_connection().await;
        let userpool_service = UserPoolService::new(cognito_connection);

        let sqs = SqsQueue::new(config::extract(profile)?).await;
        let iterable_client = IterableClient::from(config::extract::<
            notification::clients::iterable::Config,
        >(profile)?);

        let feature_flags_config = overrides.feature_flags.clone().map_or_else(
            || config::extract::<feature_flags::config::Config>(profile),
            |flags| Ok(feature_flags::config::Config::new_with_overrides(flags)),
        )?;
        let feature_flags_service = feature_flags_config.to_service().await?;

        // Initialize AccountService
        let account_service = AccountService::new(
            repositories.account_repository.clone(),
            repositories.consent_repository.clone(),
            userpool_service.clone(),
        );

        let address_service = if let Some(service) = overrides.address_repo.as_ref() {
            service.to_owned()
        } else {
            Box::new(
                AddressWatchlistService::create(repositories.address_watchlist_repository.clone())
                    .await?,
            )
        };

        let broadcaster = overrides
            .broadcaster
            .clone()
            .unwrap_or_else(|| Arc::new(TransactionBroadcaster));

        // Initialize NotificationService
        let notification_service = NotificationService::new(
            repositories.notification_repository.clone(),
            repositories.account_repository.clone(),
            account_service.clone(),
            sqs.clone(),
            iterable_client.clone(),
            repositories.consent_repository.clone(),
        )
        .await;

        let promotion_code_config = config::extract::<promotion_code::routes::Config>(profile)?;
        let web_shop_client = promotion_code_config.web_shop.to_owned().to_client();
        let webhook_validator = promotion_code_config.webhook.to_owned().to_validator();

        let promotion_code_service = PromotionCodeService::new(
            repositories.promotion_code_repository.clone(),
            web_shop_client.clone(),
        );
        let recovery_service = repositories.wallet_recovery_repository.clone();
        let recovery_relationship_service = RecoveryRelationshipService::new(
            repositories.social_recovery_repository.clone(),
            notification_service.clone(),
            promotion_code_service.clone(),
        );
        let social_challenge_service = SocialChallengeService::new(
            repositories.social_recovery_repository.clone(),
            recovery_relationship_service.clone(),
            notification_service.clone(),
            account_service.clone(),
        );
        let chain_indexer_service =
            ChainIndexerService::new(repositories.chain_indexer_repository.clone());
        let mempool_indexer_service =
            MempoolIndexerService::new(repositories.mempool_indexer_repository.clone());
        let daily_spend_record_service =
            DailySpendRecordService::new(repositories.daily_spend_record_repository.clone());
        let signed_psbt_cache_service =
            SignedPsbtCacheService::new(repositories.signed_psbt_cache_repository.clone());
        let comms_verification_service =
            CommsVerificationService::new(account_service.clone(), notification_service.clone())
                .await;
        let exchange_rate_service = ExchangeRateService::new();
        let screener_config = config::extract::<screener::Config>(profile)?;
        let screener_service = Arc::new(
            ScreenerService::new_and_load_data(
                overrides.blocked_addresses.clone(),
                screener_config,
            )
            .await,
        );
        let inheritance_service = InheritanceService::new(
            repositories.inheritance_repository.clone(),
            recovery_relationship_service.clone(),
            notification_service.clone(),
            account_service.clone(),
            feature_flags_service.clone(),
            screener_service.clone(),
            promotion_code_service.clone(),
        );
        let privileged_action_service = PrivilegedActionService::new(
            repositories.privileged_action_repository.clone(),
            repositories.account_repository.clone(),
            overrides
                .clock
                .clone()
                .unwrap_or_else(|| Arc::new(DefaultClock)),
            notification_service.clone(),
        );

        let wsm_service = config::extract::<wsm::Config>(profile)?.to_client()?;
        let wsm_client = wsm_service.client;

        let transaction_verification_service = TransactionVerificationService::new(
            config::extract::<TransactionVerificationConfig>(profile)?,
            repositories.transaction_verification_repository.clone(),
            account_service.clone(),
            exchange_rate_service.clone(),
            notification_service.clone(),
            Arc::new(wsm_client.clone()),
        );

        let twilio_client = config::extract::<onboarding::routes::Config>(profile)?
            .twilio
            .to_client();

        Ok(Services {
            account_service,
            address_service,
            broadcaster,
            chain_indexer_service,
            comms_verification_service,
            daily_spend_record_service,
            exchange_rate_service,
            feature_flags_service,
            inheritance_service,
            iterable_client,
            mempool_indexer_service,
            notification_service,
            privileged_action_service,
            promotion_code_service,
            recovery_relationship_service,
            recovery_service,
            screener_service,
            signed_psbt_cache_service,
            social_challenge_service,
            sqs,
            transaction_verification_service,
            twilio_client,
            userpool_service,
            wsm_client,
            web_shop_client,
            webhook_validator,
            // Repositories to be removed
            consent_repository: repositories.consent_repository.clone(),
            privileged_action_repository: repositories.privileged_action_repository.clone(),
            inheritance_repository: repositories.inheritance_repository.clone(),
            social_recovery_repository: repositories.social_recovery_repository.clone(),
        })
    }

    // Build the Bootstrap instance
    pub async fn build(self) -> Result<Bootstrap, BootstrapError> {
        let router = self.build_router().await?;
        let bootstrap = Bootstrap {
            router,
            config: self.config,
            services: self.services,
        };
        Ok(bootstrap)
    }

    async fn build_router(&self) -> Result<Router, BootstrapError> {
        let profile = self.profile.as_deref();

        let privileged_action_state = privileged_action::routes::RouteState(
            self.services.userpool_service.clone(),
            self.services.privileged_action_service.clone(),
        );

        let notification_state = notification::routes::RouteState(
            self.services.notification_service.clone(),
            self.services.account_service.clone(),
            self.services.address_service.clone(),
            self.services.twilio_client.clone(),
            self.services.userpool_service.clone(),
        );

        let onboarding_state = onboarding::routes::RouteState(
            self.services.userpool_service.clone(),
            config::extract(profile)?,
            config::extract::<IdentifierGenerator>(profile)?,
            self.services.account_service.clone(),
            self.services.recovery_service.clone(),
            self.services.wsm_client.clone(),
            self.services.comms_verification_service.clone(),
            self.services.iterable_client.clone(),
            self.services.twilio_client.clone(),
            self.services.feature_flags_service.clone(),
            self.services.privileged_action_service.clone(),
        );

        let mobile_pay_state = mobile_pay::routes::RouteState(
            config::extract(profile)?,
            self.services.wsm_client.clone(),
            self.services.account_service.clone(),
            self.services.userpool_service.clone(),
            self.services.broadcaster.clone(),
            self.services.daily_spend_record_service.clone(),
            self.services.exchange_rate_service.clone(),
            self.services.signed_psbt_cache_service.clone(),
            self.services.feature_flags_service.clone(),
            self.services.screener_service.clone(),
        );

        let transaction_verification_state = transaction_verification::routes::RouteState(
            self.services.account_service.clone(),
            self.services.feature_flags_service.clone(),
            self.services.userpool_service.clone(),
            self.services.transaction_verification_service.clone(),
        );

        let delay_notify_state = recovery::routes::delay_notify::RouteState(
            self.services.account_service.clone(),
            self.services.inheritance_service.clone(),
            self.services.notification_service.clone(),
            self.services.comms_verification_service.clone(),
            self.services.userpool_service.clone(),
            self.services.recovery_service.clone(),
            self.services.social_challenge_service.clone(),
            self.services.feature_flags_service.clone(),
            self.services.wsm_client.clone(),
        );

        let distributed_keys_state = recovery::routes::distributed_keys::RouteState(
            self.services.account_service.clone(),
            self.services.wsm_client.clone(),
        );

        let promotion_state = promotion_code::routes::RouteState(
            config::extract(profile)?,
            self.services.promotion_code_service.clone(),
            self.services.web_shop_client.clone(),
            self.services.webhook_validator.clone(),
        );

        let inheritance_state = recovery::routes::inheritance::RouteState(
            self.services.account_service.clone(),
            self.services.notification_service.clone(),
            self.services.inheritance_service.clone(),
            self.services.feature_flags_service.clone(),
            self.services.wsm_client.clone(),
            self.services.broadcaster.clone(),
        );

        let relationship_state = recovery::routes::relationship::RouteState(
            self.services.account_service.clone(),
            self.services.userpool_service.clone(),
            self.services.recovery_relationship_service.clone(),
            self.services.feature_flags_service.clone(),
            self.services.promotion_code_service.clone(),
            self.services.inheritance_service.clone(),
        );

        let social_challenge_state = recovery::routes::social_challenge::RouteState(
            self.services.account_service.clone(),
            self.services.social_challenge_service.clone(),
            self.services.feature_flags_service.clone(),
        );

        let experimentation_state = experimentation::routes::RouteState(
            config::extract(profile)?,
            self.services.account_service.clone(),
            self.services.feature_flags_service.clone(),
        );

        let exchange_rate_state = exchange_rate::routes::RouteState(
            self.services.exchange_rate_service.clone(),
            self.services.feature_flags_service.clone(),
            self.services.account_service.clone(),
        );

        let export_tools_state =
            export_tools::routes::RouteState(self.services.account_service.clone());

        let customer_feedback_config =
            config::extract::<customer_feedback::routes::Config>(profile)?;
        let customer_feedback_state = customer_feedback::routes::RouteState(
            self.services.account_service.clone(),
            customer_feedback_config.zendesk.to_client(),
            customer_feedback_config.known_fields.into(),
        );

        let authentication_state = authn_authz::routes::RouteState(
            self.services.userpool_service.clone(),
            self.services.account_service.clone(),
            self.services.wsm_client.clone(),
            self.services.feature_flags_service.clone(),
        );

        let reset_fingerprint_state = recovery::routes::reset_fingerprint::RouteState(
            self.services.userpool_service.clone(),
            self.services.privileged_action_service.clone(),
            self.services.wsm_client.clone(),
        );

        let analytics_state = config::extract::<analytics::routes::Config>(profile)?.to_state();
        let health_checks_state = healthcheck::Service;

        let linear_state = linear::routes::RouteState::new(config::extract(profile)?);

        let request_logger_state = RequestLoggerState::new(&self.services.feature_flags_service);

        let authorizer =
            AuthorizerConfig::from(config::extract::<userpool::userpool::Config>(profile)?)
                .into_authorizer()
                .build()
                .await
                .map_err(BootstrapError::AuthorizerInit)?
                .into_layer();

        // Authenticated routes protected by "authorize_token_for_path" middleware
        let account_authed_router = Router::new()
            .merge(privileged_action_state.account_authed_router())
            .merge(notification_state.account_authed_router())
            .merge(mobile_pay_state.account_authed_router())
            .merge(transaction_verification_state.account_authed_router())
            .merge(delay_notify_state.account_authed_router())
            .merge(distributed_keys_state.account_authed_router())
            .merge(inheritance_state.account_authed_router())
            .merge(relationship_state.account_authed_router())
            .merge(onboarding_state.account_authed_router())
            .merge(reset_fingerprint_state.account_authed_router())
            .route_layer(middleware::from_fn(authorize_token_for_path));

        // Recovery authenticated routes protected by "authorize_recovery_token_for_path" middleware
        let recovery_authed_router = Router::new()
            .merge(delay_notify_state.recovery_authed_router())
            .merge(inheritance_state.recovery_authed_router())
            .merge(social_challenge_state.recovery_authed_router())
            .merge(onboarding_state.recovery_authed_router())
            .route_layer(middleware::from_fn(authorize_recovery_token_for_path));

        // Routes accessible with either account or recovery tokens
        let account_or_recovery_authed_router = Router::new()
            .merge(privileged_action_state.account_or_recovery_authed_router())
            .merge(relationship_state.account_or_recovery_authed_router())
            .merge(onboarding_state.account_or_recovery_authed_router())
            .merge(experimentation_state.account_or_recovery_authed_router())
            .merge(export_tools_state.account_or_recovery_authed_router())
            .route_layer(middleware::from_fn(
                authorize_account_or_recovery_token_for_path,
            ));

        // Unauthenticated routes
        let unauthed_router = Router::new()
            .merge(privileged_action_state.unauthed_router())
            .merge(authentication_state.unauthed_router())
            .merge(notification_state.unauthed_router())
            .merge(onboarding_state.unauthed_router())
            .merge(customer_feedback_state.unauthed_router())
            .merge(delay_notify_state.unauthed_router())
            .merge(exchange_rate_state.unauthed_router())
            .merge(experimentation_state.unauthed_router())
            .merge(inheritance_state.unauthed_router())
            .merge(promotion_state.unauthed_router())
            .merge(linear_state.unauthed_router());

        // Routes requiring basic validation
        let mut basic_validation_router = Router::new()
            .merge(exchange_rate_state.basic_validation_router())
            .merge(customer_feedback_state.basic_validation_router());

        let mut swagger_endpoints = vec![
            SwaggerEndpoint::from(privileged_action_state),
            SwaggerEndpoint::from(onboarding_state),
            SwaggerEndpoint::from(mobile_pay_state),
            SwaggerEndpoint::from(notification_state),
            SwaggerEndpoint::from(delay_notify_state),
            SwaggerEndpoint::from(inheritance_state),
            SwaggerEndpoint::from(relationship_state),
            SwaggerEndpoint::from(social_challenge_state),
            SwaggerEndpoint::from(exchange_rate_state),
            SwaggerEndpoint::from(customer_feedback_state),
            SwaggerEndpoint::from(authentication_state),
            SwaggerEndpoint::from(experimentation_state),
            SwaggerEndpoint::from(promotion_state),
            SwaggerEndpoint::from(reset_fingerprint_state),
        ];

        #[cfg(feature = "partnerships")]
        {
            let partnerships_state = partnerships::routes::RouteState::new(
                self.services.userpool_service.clone(),
                self.services.feature_flags_service.clone(),
                self.services.account_service.clone(),
            )
            .await;
            basic_validation_router =
                basic_validation_router.merge(partnerships_state.basic_validation_router());
            swagger_endpoints.push(SwaggerEndpoint::from(partnerships_state));
        }

        // Swagger UI with all endpoints
        let swagger_router = SwaggerUi::new("/docs/swagger-ui").urls(swagger_endpoints);

        // Assemble the final API router
        let api_router = Router::new()
            .merge(account_authed_router)
            .merge(recovery_authed_router)
            .merge(account_or_recovery_authed_router)
            .merge(basic_validation_router)
            .layer(authorizer)
            .merge(unauthed_router)
            .layer(middleware::from_fn_with_state(
                request_logger_state,
                log_requests,
            ))
            .merge(Router::from(health_checks_state))
            .merge(Router::from(analytics_state))
            .merge(swagger_router);

        // Assemble the "secure site" router for out-of-band verification
        let secure_site_router = transaction_verification_state.secure_site_router();

        // Merge the API and secure site routers into a single router.
        //
        let secure_site_config = config::extract::<SecureSiteConfig>(profile)?;
        let app = match secure_site_config.secure_site_base_url.host_str() {
            // If the secure site is configured to localhost or left blank, merge the routers and
            // ignore HOST.
            None | Some("localhost") => Router::new().merge(api_router).merge(secure_site_router),

            // If the secure site is configured to a non-localhost URL, HOST routing is enabled so
            // that the api router and the site router do not share paths. This routing acts
            // like a hostname based reverse proxy.
            // Based on https://github.com/tokio-rs/axum/blob/769e4066b1f4da5662641d4097cb9f53f5b4406e/examples/http-proxy/src/main.rs
            Some(secure_site_host) => {
                let secure_site_host = secure_site_host.to_string();
                Router::new().route(
                    "/*any",
                    any_service(service_fn(move |req: Request<_>| {
                        let host = req
                            .headers()
                            .get(HOST)
                            .and_then(|v| v.to_str().ok())
                            .unwrap_or_default()
                            .to_string();
                        let secure_site_router = secure_site_router.clone();
                        let api_router = api_router.clone();
                        let req = req.map(Body::new);
                        let secure_site_host = secure_site_host.clone();
                        async move {
                            if host == secure_site_host {
                                secure_site_router.oneshot(req).await
                            } else {
                                api_router.oneshot(req).await
                            }
                        }
                    })),
                )
            }
        };

        let router = app
            .layer(HttpMetrics::new())
            .layer(OtelInResponseLayer)
            .layer(OtelAxumLayer::default())
            .layer(middleware::from_fn(request_baggage))
            .layer(middleware::from_fn(client_request_context))
            .layer(CatchPanicLayer::new());
        Ok(router)
    }
}
