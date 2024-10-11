use crate::service::social::relationship::Service;
use database::ddb;
use database::ddb::Repository;
use http_server::config;
use notification::service::tests::construct_test_notification_service;
use repository::recovery::social::SocialRecoveryRepository;

pub async fn construct_test_recovery_relationship_service() -> Service {
    let profile = Some("test");
    let ddb_config = config::extract::<ddb::Config>(profile).expect("extract ddb config");
    let ddb_connection = ddb_config.to_connection().await;

    Service::new(
        SocialRecoveryRepository::new(ddb_connection),
        construct_test_notification_service().await,
    )
}
