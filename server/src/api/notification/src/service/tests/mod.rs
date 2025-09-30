use crate::address_repo::memory::Service as MemoryAddressService;
use crate::clients::iterable;
use crate::clients::iterable::IterableClient;
use crate::repository::NotificationRepository;
use crate::service::Service;
use account::service::tests::construct_test_account_service;
use database::ddb;
use database::ddb::Repository;
use http_server::config;
use queue::sqs::SqsQueue;
use repository::account::AccountRepository;
use repository::consent::ConsentRepository;

pub async fn construct_test_notification_service() -> Service {
    let profile = Some("test");
    let ddb_config = config::extract::<ddb::Config>(profile).expect("extract ddb config");
    let ddb_connection = ddb_config.to_connection().await;
    let notification_repository = NotificationRepository::new(ddb_connection.clone());

    let account_repository = AccountRepository::new(ddb_connection.clone());
    let consent_repository = ConsentRepository::new(ddb_connection);

    let sqs = SqsQueue::new(config::extract(profile).expect("extract sqs config")).await;
    let iterable_client = IterableClient::from(
        config::extract::<iterable::Config>(profile).expect("extract iterable config"),
    );

    let address_service = Box::new(MemoryAddressService::default());
    Service::new(
        notification_repository,
        account_repository,
        construct_test_account_service().await,
        sqs.clone(),
        iterable_client,
        consent_repository,
        address_service,
    )
    .await
}
