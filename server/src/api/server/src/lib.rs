#![forbid(unsafe_code)]

extern crate core;

use std::collections::{HashMap, HashSet};
use std::fmt::Debug;

use std::sync::Arc;
use std::time::Duration;

use axum::{middleware, Router};
use axum_tracing_opentelemetry::middleware::{OtelAxumLayer, OtelInResponseLayer};
use instrumentation::metrics::system::init_tokio_metrics;
use instrumentation::middleware::{request_baggage, HttpMetrics};
use jwt_authorizer::IntoLayer;
use notification::clients::iterable::IterableClient;
use queue::sqs::SqsQueue;
use thiserror::Error;
use userpool::userpool::UserPoolService;
use utoipa_swagger_ui::SwaggerUi;

use account::repository::Repository as AccountRepository;
use account::service::Service as AccountService;
use authn_authz::authorizer::{
    authorize_account_or_recovery_token_for_path, authorize_recovery_token_for_path,
    authorize_token_for_path, AuthorizerConfig,
};
use bdk_utils::{TransactionBroadcaster, TransactionBroadcasterTrait};
use chain_indexer::{
    repository::Repository as ChainIndexerRepository, service::Service as ChainIndexerService,
};
use comms_verification::Service as CommsVerificationService;
use database::ddb::{self, DDBService};
use exchange_rate::service::Service as ExchangeRateService;

use http_server::config::Config;
use http_server::middlewares::identifier_generator::IdentifierGenerator;
use http_server::middlewares::wsm;
use http_server::swagger::SwaggerEndpoint;
use http_server::{config, healthcheck};
use mobile_pay::daily_spend_record::{
    repository::Repository as DailySpendRecordRepository,
    service::Service as DailySpendRecordService,
};
use mobile_pay::signed_psbt_cache::{
    repository::Repository as SignedPsbtCacheRepository, service::Service as SignedPsbtCacheService,
};
use notification::address_repo::ddb::service::Service as AddressWatchlistService;
use notification::address_repo::errors::Error;
use notification::address_repo::AddressWatchlistTrait;
use notification::repository::Repository as NotificationRepository;
use notification::service::Service as NotificationService;
use recovery::repository::Repository as RecoveryRepository;
use recovery::service::social::{
    challenge::Service as SocialChallengeService,
    relationship::Service as RecoveryRelationshipService,
};
use repository::consent::Repository as ConsentRepository;
use repository::recovery::social::Repository as SocialRecoveryRepository;
pub use routes::axum::axum;
use screener::service::Service as ScreenerService;
use wallet_telemetry::{set_global_telemetry, METRICS_REPORTING_PERIOD_SECS};

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
    pub notification_service: NotificationService,
    pub recovery_service: RecoveryRepository,
    pub recovery_relationship_service: RecoveryRelationshipService,
    pub account_service: AccountService,
    pub chain_indexer_service: ChainIndexerService,
    pub broadcaster: Arc<dyn TransactionBroadcasterTrait>,
    pub daily_spend_record_service: DailySpendRecordService,
    pub address_repo: Box<dyn AddressWatchlistTrait>,
    pub userpool_service: UserPoolService,
    pub sqs: SqsQueue,
    pub feature_flags_service: feature_flags::service::Service,
    pub exchange_rate_service: ExchangeRateService,
    pub iterable_client: IterableClient,
    pub consent_repository: ConsentRepository,
    pub social_challenge_service: SocialChallengeService,
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
        self.feature_flags = Some(feature_flags);
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
    let config = Config::new(profile)?;
    set_global_telemetry(&config.wallet_telemetry)?;
    init_tokio_metrics(Duration::from_secs(METRICS_REPORTING_PERIOD_SECS))?;

    let cognito_connection = config::extract::<userpool::userpool::Config>(profile)?
        .to_connection()
        .await;
    let userpool_service = UserPoolService::new(cognito_connection.clone());

    // Construct overriden feature config if feature_flags is present.
    let feature_flags = match overrides.feature_flags {
        None => config::extract::<feature_flags::config::Config>(profile)?,
        Some(flags) => feature_flags::config::Config::new_with_overrides(flags),
    }
    .to_service()
    .await?;

    let authorizer =
        AuthorizerConfig::from(config::extract::<userpool::userpool::Config>(profile)?)
            .into_authorizer()
            .build()
            .await
            .map_err(BootstrapError::AuthorizerInit)?
            .into_layer();

    let ddb = config::extract::<ddb::Config>(profile)?
        .to_connection()
        .await;
    let wsm_service = config::extract::<wsm::Config>(profile)?.to_client()?;
    let health_checks = healthcheck::Service::new(feature_flags.clone())?;

    let account_repository = AccountRepository::new(ddb.clone());
    account_repository.create_table_if_necessary().await?;
    let consent_repository = ConsentRepository::new(ddb.clone());
    consent_repository.create_table_if_necessary().await?;
    let account_service = AccountService::new(
        account_repository.clone(),
        consent_repository.clone(),
        userpool_service.clone(),
    );

    let sqs = SqsQueue::new(config::extract(profile)?).await;
    let notification_repository = NotificationRepository::new(ddb.clone());
    notification_repository.create_table_if_necessary().await?;
    let iterable_client = IterableClient::from(config::extract::<
        notification::clients::iterable::Config,
    >(profile)?);
    let notification_service = NotificationService::new(
        notification_repository,
        account_repository,
        account_service.clone(),
        sqs.clone(),
        iterable_client.clone(),
        consent_repository.clone(),
    )
    .await;
    let wallet_recovery_service =
        RecoveryRepository::new_with_override(ddb.clone(), config.override_current_time);
    wallet_recovery_service.create_table_if_necessary().await?;
    let social_recovery_repository = SocialRecoveryRepository::new(ddb.clone());
    social_recovery_repository
        .create_table_if_necessary()
        .await?;
    let recovery_relationship_service = RecoveryRelationshipService::new(
        social_recovery_repository.clone(),
        notification_service.clone(),
    );
    let social_challenge_service = SocialChallengeService::new(
        social_recovery_repository,
        recovery_relationship_service.clone(),
        notification_service.clone(),
        account_service.clone(),
    );
    let chain_indexer_repository = ChainIndexerRepository::new(ddb.clone());
    chain_indexer_repository.create_table_if_necessary().await?;
    let chain_indexer_service = ChainIndexerService::new(chain_indexer_repository);

    let identifier_generator = config::extract::<IdentifierGenerator>(profile)?;

    let daily_spend_record_repository = DailySpendRecordRepository::new(ddb.clone());
    daily_spend_record_repository
        .create_table_if_necessary()
        .await?;
    let daily_spend_record_service = DailySpendRecordService::new(daily_spend_record_repository);

    let signed_psbt_cache_repository = SignedPsbtCacheRepository::new(ddb.clone());
    signed_psbt_cache_repository
        .create_table_if_necessary()
        .await?;
    let signed_psbt_cache_service = SignedPsbtCacheService::new(signed_psbt_cache_repository);

    let broadcaster = overrides
        .broadcaster
        .unwrap_or(Arc::new(TransactionBroadcaster));
    let address_repo = overrides.address_repo.unwrap_or(Box::new(
        AddressWatchlistService::create(ddb.clone()).await?,
    ));

    let comms_verification_service =
        CommsVerificationService::new(account_service.clone(), notification_service.clone()).await;

    let exchange_rate_service = ExchangeRateService::new();

    let screener_config = config::extract::<screener::Config>(profile)?;
    let screener_service =
        ScreenerService::new_and_load_data(overrides.blocked_addresses, screener_config).await;

    let notification = notification::routes::RouteState(
        notification_service.clone(),
        account_service.clone(),
        address_repo.clone(),
        config::extract::<onboarding::routes::Config>(profile)?
            .twilio
            .to_client(),
        userpool_service.clone(),
    );
    let onboarding = onboarding::routes::RouteState(
        userpool_service.clone(),
        config::extract(profile)?,
        identifier_generator,
        account_service.clone(),
        wallet_recovery_service.clone(),
        wsm_service.client.clone(),
        comms_verification_service.clone(),
        iterable_client.clone(),
        config::extract::<onboarding::routes::Config>(profile)?
            .twilio
            .to_client(),
        feature_flags.clone(),
    );
    let mobile_pay = mobile_pay::routes::RouteState(
        config::extract(profile)?,
        wsm_service.client.clone(),
        account_service.clone(),
        userpool_service.clone(),
        broadcaster.clone(),
        daily_spend_record_service.clone(),
        exchange_rate_service.clone(),
        signed_psbt_cache_service.clone(),
        feature_flags.clone(),
        Arc::new(screener_service.clone()),
    );
    let recovery = recovery::routes::RouteState(
        account_service.clone(),
        notification_service.clone(),
        comms_verification_service,
        userpool_service.clone(),
        wallet_recovery_service.clone(),
        wsm_service.client.clone(),
        recovery_relationship_service.clone(),
        social_challenge_service.clone(),
        feature_flags.clone(),
    );
    let experimentation = experimentation::routes::RouteState(
        config::extract(profile)?,
        account_service.clone(),
        feature_flags.clone(),
    );
    let exchange_rate =
        exchange_rate::routes::RouteState(exchange_rate_service.clone(), feature_flags.clone());
    let customer_feedback_config = config::extract::<customer_feedback::routes::Config>(profile)?;
    let customer_feedback = customer_feedback::routes::RouteState(
        account_service.clone(),
        customer_feedback_config.zendesk.to_client(),
        customer_feedback_config.known_fields.into(),
    );
    let authentication =
        authn_authz::routes::RouteState(userpool_service.clone(), account_service.clone());
    let analytics = config::extract::<analytics::routes::Config>(profile)?.to_state();
    #[allow(unused_mut)]
    let mut router = Router::new()
        .merge(notification.authed_router())
        .merge(Router::from(mobile_pay.clone()))
        .merge(recovery.authed_router())
        .merge(onboarding.authed_router())
        .merge(experimentation.authed_router())
        .route_layer(middleware::from_fn(authorize_token_for_path));

    let recovery_router = Router::new()
        .merge(recovery.recovery_authed_router())
        .merge(onboarding.recovery_authed_router())
        .route_layer(middleware::from_fn(authorize_recovery_token_for_path));
    router = router.merge(recovery_router);

    let account_or_recovery_router = Router::new()
        .merge(recovery.account_or_recovery_authed_router())
        .merge(onboarding.account_or_recovery_authed_router())
        .route_layer(middleware::from_fn(
            authorize_account_or_recovery_token_for_path,
        ));
    router = router.merge(account_or_recovery_router);

    #[cfg(feature = "partnerships")]
    {
        let route_state = partnerships::routes::RouteState::new(
            userpool_service.clone(),
            feature_flags.clone(),
            account_service.clone(),
        )
        .await;
        router = router.merge(Router::from(route_state));
    }

    router = router
        .merge(exchange_rate.basic_validation_router())
        .merge(customer_feedback.basic_validation_router())
        .layer(authorizer)
        .merge(authentication.unauthed_router())
        .merge(notification.unauthed_router())
        .merge(onboarding.unauthed_router())
        .merge(customer_feedback.unauthed_router())
        .merge(recovery.unauthed_router())
        .merge(exchange_rate.unauthed_router())
        .merge(experimentation.unauthed_router())
        .merge(Router::from(health_checks))
        .merge(Router::from(analytics))
        .merge(SwaggerUi::new("/docs/swagger-ui").urls(vec![
            SwaggerEndpoint::from(onboarding),
            SwaggerEndpoint::from(mobile_pay),
            SwaggerEndpoint::from(notification),
            SwaggerEndpoint::from(recovery),
            SwaggerEndpoint::from(exchange_rate),
            SwaggerEndpoint::from(customer_feedback),
            SwaggerEndpoint::from(authentication),
            SwaggerEndpoint::from(experimentation),
        ]))
        .layer(HttpMetrics::new())
        .layer(OtelInResponseLayer)
        .layer(middleware::from_fn(request_baggage))
        .layer(OtelAxumLayer::default());

    Ok(Bootstrap {
        config,
        services: Services {
            notification_service,
            recovery_service: wallet_recovery_service,
            recovery_relationship_service,
            account_service,
            chain_indexer_service,
            broadcaster,
            daily_spend_record_service,
            address_repo,
            userpool_service,
            sqs,
            feature_flags_service: feature_flags,
            exchange_rate_service,
            iterable_client,
            consent_repository,
            social_challenge_service,
        },
        router,
    })
}
