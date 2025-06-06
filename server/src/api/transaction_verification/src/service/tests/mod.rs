use crate::service::mock::MockGrantService;
use crate::{
    repository::TransactionVerificationRepository,
    service::{Config, Service},
};
use account::service::tests::{
    construct_test_account_service, create_full_account_for_test, generate_test_authkeys,
};
use database::ddb::{Config as DDBConfig, Repository};
use exchange_rate::service::Service as ExchangeRateService;
use http_server::config;
use notification::service::tests::construct_test_notification_service;
use std::sync::Arc;
use types::account::entities::TransactionVerificationPolicy;
use types::account::{bitcoin::Network, entities::FullAccount};

pub async fn construct_test_transaction_verification_service() -> Service {
    let profile = Some("test");
    let ddb_config = config::extract::<DDBConfig>(profile).expect("extract ddb config");
    let ddb_connection = ddb_config.to_connection().await;
    let account_service = construct_test_account_service().await;
    let notification_service = construct_test_notification_service().await;
    let mock_grant_service = MockGrantService::new();
    let transaction_verification_repository =
        TransactionVerificationRepository::new(ddb_connection.clone());
    Service::new(
        config::extract::<Config>(profile).expect("extract tx verify config"),
        transaction_verification_repository,
        account_service,
        ExchangeRateService::new(),
        notification_service,
        Arc::new(mock_grant_service),
    )
}

pub async fn setup_account_with_transaction_verification_policy(
    policy: Option<TransactionVerificationPolicy>,
) -> FullAccount {
    let account_service = construct_test_account_service().await;
    let auth = generate_test_authkeys();
    let account =
        create_full_account_for_test(&account_service, Network::BitcoinSignet, &auth.into()).await;
    if let Some(policy) = policy {
        account_service
            .put_transaction_verification_policy(&account.id, policy)
            .await
            .unwrap();
    }
    account
}

#[cfg(test)]
mod initiate_tests;
