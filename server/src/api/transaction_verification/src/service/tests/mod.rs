use std::{str::FromStr, sync::Arc};

use crate::{
    repository::TransactionVerificationRepository,
    service::{mock::MockGrantService, Config, Service},
};

use account::service::tests::{
    construct_test_account_service, create_full_account_for_test, generate_test_authkeys,
};
use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction as Psbt;
use database::ddb::{Config as DDBConfig, Repository};
use exchange_rate::service::Service as ExchangeRateService;
use http_server::config;
use notification::service::tests::construct_test_notification_service;
use types::{
    account::{
        bitcoin::Network,
        entities::{FullAccount, TransactionVerificationPolicy},
    },
    currencies::CurrencyCode,
    transaction_verification::entities::{BitcoinDisplayUnit, TransactionVerification},
};

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

// Helper to set up transaction verification data
pub(crate) async fn setup_test_verification(
    service: &Service,
) -> (TransactionVerification, String, String) {
    // Create a test PSBT
    let psbt_str = "cHNidP8BAHUCAAAAASaBcTce3/KF6Tet7qSze3gADAVmy7OtZGQXE8pCFxv2AAAAAAD+////AtPf9QUAAAAAGXapFNDFmQPFusKGh2DpD9UhpGZap2UgiKwA4fUFAAAAABepFDVF5uM7gyxHBQ8k0+65PJwDlIvHh7MuEwAAAQD9pQEBAAAAAAECiaPHHqtNIOA3G7ukzGmPopXJRjr6Ljl/hTPMti+VZ+UBAAAAFxYAFL4Y0VKpsBIDna89p95PUzSe7LmF/////4b4qkOnHf8USIk6UwpyN+9rRgi7st0tAXHmOuxqSJC0AQAAABcWABT+Pp7xp0XpdNkCxDVZQ6vLNL1TU/////8CAMLrCwAAAAAZdqkUhc/xCX/Z4Ai7NK9wnGIZeziXikiIrHL++E4sAAAAF6kUM5cluiHv1irHU6m80GfWx6ajnQWHAkcwRAIgJxK+IuAnDzlPVoMR3HyppolwuAJf3TskAinwf4pfOiQCIAGLONfc0xTnNMkna9b7QPZzMlvEuqFEyADS8vAtsnZcASED0uFWdJQbrUqZY3LLh+GFbTZSYG2YVi/jnF6efkE/IQUCSDBFAiEA0SuFLYXc2WHS9fSrZgZU327tzHlMDDPOXMMJ/7X85Y0CIGczio4OFyXBl/saiK9Z9R5E5CVbIBZ8hoQDHAXR8lkqASECI7cr7vCWXRC+B3jv7NYfysb3mk6haTkzgHNEZPhPKrMAAAAAAAAA";
    let psbt = Psbt::from_str(psbt_str).unwrap();

    // Get a test account
    let account = setup_account_with_transaction_verification_policy(None).await;

    // Create a transaction verification
    let tx_verification = TransactionVerification::new_pending(
        &account.id,
        psbt,
        CurrencyCode::USD,
        BitcoinDisplayUnit::Satoshi,
    );

    // Extract tokens
    let (_, confirmation_token, cancellation_token) = match &tx_verification {
        TransactionVerification::Pending(pending) => (
            pending.web_auth_token.clone(),
            pending.confirmation_token.clone(),
            pending.cancellation_token.clone(),
        ),
        _ => panic!("Expected a pending transaction verification"),
    };

    // Save it
    service.repo.persist(&tx_verification).await.unwrap();

    (tx_verification, confirmation_token, cancellation_token)
}

#[cfg(test)]
mod cancel_tests;

#[cfg(test)]
mod initiate_tests;

#[cfg(test)]
mod verification_tests;
