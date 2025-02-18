use crate::service::inheritance::error::ServiceError;
use crate::service::inheritance::lock_inheritance_claim::LockInheritanceClaimInput;
use crate::service::inheritance::tests::{
    cancel_claim, construct_test_inheritance_service, create_inheritance_package,
    create_pending_inheritance_claim, setup_accounts, setup_keys_and_signatures,
};
use account::service::tests::{
    construct_test_account_service, create_full_account_for_test, generate_test_authkeys,
};
use bdk_utils::bdk::bitcoin::key::Secp256k1;
use time::{Duration, OffsetDateTime};
use types::account::bitcoin::Network;
use types::recovery::inheritance::claim::{InheritanceClaim, InheritanceClaimId};

#[tokio::test]
async fn test_lock_inheritance_claim_success() {
    // arrange
    let inheritance_service = construct_test_inheritance_service().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;

    let secp = Secp256k1::new();
    let (auth_keys, challenge, app_signature, _) = setup_keys_and_signatures(&secp);

    let delay_end_time = OffsetDateTime::now_utc() - Duration::minutes(5);
    let pending_claim = create_pending_inheritance_claim(
        &benefactor_account,
        &beneficiary_account,
        &auth_keys,
        Some(delay_end_time),
    )
    .await;

    let recovery_relationship_id = pending_claim.common_fields.recovery_relationship_id.clone();

    let sealed_dek = "TEST_SEALED_DEK".to_string();
    let sealed_mobile_key = "TEST_SEALED_MOBILE_KEY".to_string();
    create_inheritance_package(&recovery_relationship_id, &sealed_dek, &sealed_mobile_key).await;

    // act
    let input = LockInheritanceClaimInput {
        inheritance_claim_id: pending_claim.common_fields.id.clone(),
        beneficiary_account,
        challenge,
        app_signature,
    };
    let result = inheritance_service.lock(input).await;

    // assert
    assert!(result.is_ok());

    let locked_claim = result.expect("Expected locked claim");
    assert_eq!(
        locked_claim.common_fields.recovery_relationship_id,
        pending_claim.common_fields.recovery_relationship_id
    );
    assert_eq!(
        locked_claim.common_fields.auth_keys,
        pending_claim.common_fields.auth_keys
    );
    assert_eq!(
        locked_claim.common_fields.created_at,
        pending_claim.common_fields.created_at
    );
    assert_eq!(
        locked_claim.common_fields.id,
        pending_claim.common_fields.id
    );
    assert!(locked_claim.common_fields.updated_at > pending_claim.common_fields.updated_at);

    let expected_descriptor_keyset = benefactor_account
        .active_descriptor_keyset()
        .unwrap()
        .into_multisig_descriptor()
        .unwrap();
    assert_eq!(
        locked_claim.benefactor_descriptor_keyset.to_string(),
        expected_descriptor_keyset.to_string()
    );

    assert_eq!(locked_claim.sealed_dek, sealed_dek);
    assert_eq!(locked_claim.sealed_mobile_key, sealed_mobile_key);
}

#[tokio::test]
async fn test_lock_inheritance_claim_before_delay_end_fails() {
    // arrange
    let inheritance_service = construct_test_inheritance_service().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;

    let secp = Secp256k1::new();
    let (auth_keys, challenge, app_signature, _) = setup_keys_and_signatures(&secp);

    let pending_claim = create_pending_inheritance_claim(
        &benefactor_account,
        &beneficiary_account,
        &auth_keys,
        None,
    )
    .await;

    let recovery_relationship_id = pending_claim.common_fields.recovery_relationship_id.clone();

    create_inheritance_package(&recovery_relationship_id, "test_dek", "test_mobile_key").await;

    // act
    let input = LockInheritanceClaimInput {
        inheritance_claim_id: pending_claim.common_fields.id.clone(),
        beneficiary_account,
        challenge,
        app_signature,
    };
    let result = inheritance_service.lock(input).await;

    // assert
    assert_eq!(
        result.unwrap_err().to_string(),
        ServiceError::ClaimDelayNotComplete.to_string()
    );
}

#[tokio::test]
async fn test_lock_inheritance_claim_no_pending_claim() {
    // arrange
    let inheritance_service = construct_test_inheritance_service().await;
    let account_service = construct_test_account_service().await;

    let beneficiary_account = create_full_account_for_test(
        &account_service,
        Network::BitcoinSignet,
        &generate_test_authkeys().into(),
    )
    .await;

    let challenge = "challenge".to_string();
    let app_signature = "app_signature".to_string();

    let inheritance_claim_id = InheritanceClaimId::gen().expect("generate claim id");

    // act
    let input = LockInheritanceClaimInput {
        inheritance_claim_id,
        beneficiary_account,
        challenge,
        app_signature,
    };
    let result = inheritance_service.lock(input).await;

    // assert
    assert_eq!(
        result.unwrap_err().to_string(),
        "Could not fetch Inheritance"
    );
}

#[tokio::test]
async fn test_lock_inheritance_claim_invalid_challenge() {
    // arrange
    let inheritance_service = construct_test_inheritance_service().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;

    let secp = Secp256k1::new();
    let (auth_keys, _, _, _) = setup_keys_and_signatures(&secp);

    let challenge = "invalid_challenge".to_string();
    let app_signature = "app_signature".to_string();

    let pending_claim = create_pending_inheritance_claim(
        &benefactor_account,
        &beneficiary_account,
        &auth_keys,
        None,
    )
    .await;

    // act
    let input = LockInheritanceClaimInput {
        inheritance_claim_id: pending_claim.common_fields.id,
        beneficiary_account,
        challenge,
        app_signature,
    };
    let result = inheritance_service.lock(input).await;

    // assert
    assert_eq!(
        result.unwrap_err().to_string(),
        ServiceError::InvalidChallengeSignature.to_string()
    );
}

#[tokio::test]
async fn test_lock_inheritance_claim_missing_package() {
    // arrange
    let inheritance_service = construct_test_inheritance_service().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;

    let secp = Secp256k1::new();
    let (auth_keys, challenge, app_signature, _) = setup_keys_and_signatures(&secp);

    let delay_end_time = OffsetDateTime::now_utc() - Duration::minutes(5);
    let pending_claim = create_pending_inheritance_claim(
        &benefactor_account,
        &beneficiary_account,
        &auth_keys,
        Some(delay_end_time),
    )
    .await;

    // act
    let input = LockInheritanceClaimInput {
        inheritance_claim_id: pending_claim.common_fields.id,
        beneficiary_account,
        challenge,
        app_signature,
    };
    let result = inheritance_service.lock(input).await;

    // assert
    assert_eq!(
        result.unwrap_err().to_string(),
        ServiceError::NoInheritancePackage.to_string()
    );
}

#[tokio::test]
async fn test_lock_inheritance_claim_canceled_claim() {
    // arrange
    let inheritance_service = construct_test_inheritance_service().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;

    let secp = Secp256k1::new();
    let (auth_keys, challenge, app_signature, _) = setup_keys_and_signatures(&secp);

    let pending_claim = create_pending_inheritance_claim(
        &benefactor_account,
        &beneficiary_account,
        &auth_keys,
        None,
    )
    .await;

    let recovery_relationship_id = pending_claim.common_fields.recovery_relationship_id.clone();

    let sealed_dek = "TEST_SEALED_DEK".to_string();
    let sealed_mobile_key = "TEST_SEALED_MOBILE_KEY".to_string();

    create_inheritance_package(&recovery_relationship_id, &sealed_dek, &sealed_mobile_key).await;

    cancel_claim(
        &InheritanceClaim::Pending(pending_claim.clone()),
        &beneficiary_account,
    )
    .await;

    // act
    let input = LockInheritanceClaimInput {
        inheritance_claim_id: pending_claim.common_fields.id,
        beneficiary_account: beneficiary_account.clone(),
        challenge,
        app_signature,
    };
    let result = inheritance_service.lock(input).await;

    // assert
    assert_eq!(
        result.unwrap_err().to_string(),
        ServiceError::PendingClaimNotFound.to_string()
    );
}

#[tokio::test]
async fn test_lock_inheritance_claim_locked_claim_success() {
    // arrange
    let inheritance_service = construct_test_inheritance_service().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;

    let secp = Secp256k1::new();
    let (auth_keys, challenge, app_signature, _) = setup_keys_and_signatures(&secp);

    let delay_end_time = OffsetDateTime::now_utc() - Duration::minutes(5);
    let pending_claim = create_pending_inheritance_claim(
        &benefactor_account,
        &beneficiary_account,
        &auth_keys,
        Some(delay_end_time),
    )
    .await;

    let recovery_relationship_id = pending_claim.common_fields.recovery_relationship_id.clone();

    let sealed_dek = "TEST_SEALED_DEK".to_string();
    let sealed_mobile_key = "TEST_SEALED_MOBILE_KEY".to_string();
    create_inheritance_package(&recovery_relationship_id, &sealed_dek, &sealed_mobile_key).await;

    let input = LockInheritanceClaimInput {
        inheritance_claim_id: pending_claim.common_fields.id,
        beneficiary_account,
        challenge,
        app_signature,
    };
    let locked_claim = inheritance_service
        .lock(input.clone())
        .await
        .expect("Expected locked claim");

    // act
    let result = inheritance_service
        .lock(input)
        .await
        .expect("Expected already locked claim");

    // assert
    assert_eq!(result, locked_claim);
}
