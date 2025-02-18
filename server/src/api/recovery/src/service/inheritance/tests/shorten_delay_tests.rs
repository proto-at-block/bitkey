use time::{Duration, OffsetDateTime};
use types::account::bitcoin::Network;
use types::recovery::inheritance::claim::InheritanceClaim;

use crate::service::inheritance::error::ServiceError;
use crate::service::inheritance::shorten_delay::ShortenDelayForBeneficiaryInput;
use crate::service::inheritance::tests::{
    construct_test_inheritance_service, create_locked_claim, create_pending_inheritance_claim,
    get_auth_keys, setup_accounts, setup_accounts_with_network,
};

#[tokio::test]
async fn test_shorten_delay_for_beneficiary_success() {
    // arrange
    let inheritance_service = construct_test_inheritance_service().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;
    let auth_keys = get_auth_keys(&beneficiary_account);

    let delay_end_time = OffsetDateTime::now_utc() - Duration::minutes(5);
    let pending_claim = create_pending_inheritance_claim(
        &benefactor_account,
        &beneficiary_account,
        &auth_keys,
        Some(delay_end_time),
    )
    .await;

    // act
    let input = ShortenDelayForBeneficiaryInput {
        inheritance_claim_id: pending_claim.common_fields.id.clone(),
        beneficiary_account_id: beneficiary_account.id,
        delay_period_seconds: 100,
    };
    let result = inheritance_service
        .shorten_delay_for_beneficiary(input)
        .await;

    // assert
    assert!(result.is_ok());

    let Ok(InheritanceClaim::Pending(shortened_claim)) = result else {
        panic!("Expected pending shortened claim");
    };
    assert_eq!(
        shortened_claim.delay_end_time,
        pending_claim.common_fields.created_at + Duration::seconds(100)
    );
    assert_eq!(
        shortened_claim.common_fields.auth_keys,
        pending_claim.common_fields.auth_keys
    );
    assert_eq!(
        shortened_claim.common_fields.created_at,
        pending_claim.common_fields.created_at
    );
    assert_eq!(
        shortened_claim.common_fields.id,
        pending_claim.common_fields.id
    );
    assert!(shortened_claim.common_fields.updated_at > pending_claim.common_fields.updated_at);
}

#[tokio::test]
async fn test_shorten_claim_delay_for_nontest_account_fails() {
    // arrange
    let inheritance_service = construct_test_inheritance_service().await;
    let (benefactor_account, beneficiary_account) =
        setup_accounts_with_network(Network::BitcoinMain).await;
    let auth_keys = get_auth_keys(&beneficiary_account);

    let delay_end_time = OffsetDateTime::now_utc() - Duration::minutes(5);
    let pending_claim = create_pending_inheritance_claim(
        &benefactor_account,
        &beneficiary_account,
        &auth_keys,
        Some(delay_end_time),
    )
    .await;

    // act
    let input = ShortenDelayForBeneficiaryInput {
        inheritance_claim_id: pending_claim.common_fields.id.clone(),
        beneficiary_account_id: beneficiary_account.id,
        delay_period_seconds: 100,
    };
    let result = inheritance_service
        .shorten_delay_for_beneficiary(input)
        .await;

    // assert
    assert!(result.is_err());

    assert_eq!(
        result.unwrap_err().to_string(),
        ServiceError::ShortenDelayForNonTestAccount.to_string()
    );
}

#[tokio::test]
async fn test_shorten_claim_delay_for_locked_claim_fails() {
    // arrange
    let inheritance_service = construct_test_inheritance_service().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;
    let locked_claim = create_locked_claim(&benefactor_account, &beneficiary_account).await;

    // act
    let input = ShortenDelayForBeneficiaryInput {
        inheritance_claim_id: locked_claim.common_fields.id.clone(),
        beneficiary_account_id: beneficiary_account.id,
        delay_period_seconds: 100,
    };
    let result = inheritance_service
        .shorten_delay_for_beneficiary(input)
        .await;

    // assert
    assert!(result.is_err());

    assert_eq!(
        result.unwrap_err().to_string(),
        ServiceError::InvalidClaimStateForShortening.to_string()
    );
}
