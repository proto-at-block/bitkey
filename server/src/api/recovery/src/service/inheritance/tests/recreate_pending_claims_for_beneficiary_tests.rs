use crate::service::inheritance::recreate_pending_claims_for_beneficiary::RecreatePendingClaimsForBeneficiaryInput;
use crate::service::inheritance::tests::{
    construct_test_inheritance_service, create_canceled_claim, create_completed_claim,
    create_locked_claim, create_pending_inheritance_claim, setup_accounts,
    setup_keys_and_signatures,
};
use crate::service::inheritance::Service as InheritanceService;

use account::service::tests::{construct_test_account_service, generate_test_authkeys};
use account::service::{CreateAndRotateAuthKeysInput, Service as AccountService};
use bdk_utils::bdk::bitcoin::key::Secp256k1;
use time::{Duration, OffsetDateTime};
use types::account::entities::{Account, FullAccount};
use types::recovery::inheritance::claim::{
    InheritanceClaim, InheritanceClaimAuthKeys, InheritanceClaimCanceledBy,
};

// Helper function to rotate auth keys and get updated beneficiary account
async fn rotate_auth_keys(
    account_service: &AccountService,
    beneficiary_account: &Account,
) -> FullAccount {
    let new_auth_keys = generate_test_authkeys();
    let updated_account = account_service
        .create_and_rotate_auth_keys(CreateAndRotateAuthKeysInput {
            account_id: beneficiary_account.get_id(),
            app_auth_pubkey: new_auth_keys.app.public_key,
            hardware_auth_pubkey: new_auth_keys.hw.public_key,
            recovery_auth_pubkey: Some(new_auth_keys.recovery.public_key),
        })
        .await
        .expect("Failed to update auth keys");

    match updated_account {
        Account::Full(full_account) => full_account,
        _ => panic!("Account is not a full account"),
    }
}

// Helper function to get auth keys from account
fn get_auth_keys_from_account(account: &Account) -> InheritanceClaimAuthKeys {
    match account {
        Account::Full(full_account) => get_auth_keys_from_full_account(full_account),
        _ => panic!("Account is not a full account"),
    }
}

// Helper function to get auth keys from full account
fn get_auth_keys_from_full_account(full_account: &FullAccount) -> InheritanceClaimAuthKeys {
    InheritanceClaimAuthKeys::FullAccount(
        full_account
            .active_auth_keys()
            .expect("Account has active auth keys")
            .to_owned(),
    )
}

// Test helpers
async fn setup_test_services() -> (AccountService, InheritanceService) {
    (
        construct_test_account_service().await,
        construct_test_inheritance_service().await,
    )
}

// Tests
#[tokio::test]
async fn test_recreate_with_multiple_pending_claims() {
    // Arrange
    let (account_service, inheritance_service) = setup_test_services().await;
    let (first_benefactor_account, beneficiary_account) = setup_accounts().await;
    let (second_benefactor_account, _) = setup_accounts().await;

    let current_auth_keys = get_auth_keys_from_account(&beneficiary_account);

    // Create two pending claims with same beneficiary
    let initial_delay_end_time = OffsetDateTime::now_utc() - Duration::minutes(5);
    let first_pending_claim = create_pending_inheritance_claim(
        &first_benefactor_account,
        &beneficiary_account,
        &current_auth_keys,
        Some(initial_delay_end_time),
    )
    .await;
    let second_pending_claim = create_pending_inheritance_claim(
        &second_benefactor_account,
        &beneficiary_account,
        &current_auth_keys,
        Some(initial_delay_end_time),
    )
    .await;

    // Rotate beneficiary's auth keys
    let updated_beneficiary_account =
        rotate_auth_keys(&account_service, &beneficiary_account).await;
    let expected_auth_keys = get_auth_keys_from_full_account(&updated_beneficiary_account);

    // act
    let recreated_claims = inheritance_service
        .recreate_pending_claims_for_beneficiary(RecreatePendingClaimsForBeneficiaryInput {
            beneficiary: &updated_beneficiary_account,
        })
        .await
        .expect("Failed to recreate claims");

    // assert
    assert_eq!(recreated_claims.len(), 2);

    let initial_claim_ids = [
        first_pending_claim.common_fields.id,
        second_pending_claim.common_fields.id,
    ];
    for claim in recreated_claims {
        let InheritanceClaim::Pending(recreated_claim) = claim else {
            panic!("Expected pending claim");
        };

        // Verify new claim has new ID
        assert!(!initial_claim_ids.contains(&recreated_claim.common_fields.id));

        // Verify claim has updated auth keys but same delay end time
        assert_eq!(expected_auth_keys, recreated_claim.common_fields.auth_keys);
        assert_eq!(initial_delay_end_time, recreated_claim.delay_end_time);
    }
}

#[tokio::test]
async fn test_recreate_with_pending_claim_same_auth_keys_success() {
    // arrange
    let inheritance_service = construct_test_inheritance_service().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;

    let auth_keys = get_auth_keys_from_account(&beneficiary_account);
    let delay_end_time = OffsetDateTime::now_utc() - Duration::minutes(5);
    let original_claim = create_pending_inheritance_claim(
        &benefactor_account,
        &beneficiary_account,
        &auth_keys,
        Some(delay_end_time),
    )
    .await;

    // act
    let Account::Full(beneficiary_full_account) = beneficiary_account else {
        panic!("Account is not a full account");
    };
    let recreated_claims = inheritance_service
        .recreate_pending_claims_for_beneficiary(RecreatePendingClaimsForBeneficiaryInput {
            beneficiary: &beneficiary_full_account,
        })
        .await
        .expect("Failed to recreate claims");

    // assert
    assert_eq!(recreated_claims.len(), 1);
    let InheritanceClaim::Pending(recreated_claim) = recreated_claims.first().expect("Valid claim")
    else {
        panic!("Expected pending claim");
    };

    // Verify all fields match since auth keys haven't changed
    assert_eq!(
        original_claim.common_fields.recovery_relationship_id,
        recreated_claim.common_fields.recovery_relationship_id
    );
    assert_eq!(
        original_claim.common_fields.auth_keys,
        recreated_claim.common_fields.auth_keys,
    );
    assert_eq!(
        original_claim.common_fields.created_at,
        recreated_claim.common_fields.created_at,
    );
    assert_eq!(
        original_claim.common_fields.id,
        recreated_claim.common_fields.id,
    );
    assert_eq!(
        original_claim.delay_end_time,
        recreated_claim.delay_end_time
    );
    assert!(recreated_claim.common_fields.updated_at == original_claim.common_fields.updated_at);
}

#[tokio::test]
async fn test_recreate_with_pending_claim_new_auth_keys_success() {
    // arrange
    let (account_service, inheritance_service) = setup_test_services().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;

    let initial_auth_keys = get_auth_keys_from_account(&beneficiary_account);
    let initial_delay_end_time = OffsetDateTime::now_utc() - Duration::minutes(5);
    let pending_claim = create_pending_inheritance_claim(
        &benefactor_account,
        &beneficiary_account,
        &initial_auth_keys,
        Some(initial_delay_end_time),
    )
    .await;

    // Rotate auth keys
    let updated_beneficiary = rotate_auth_keys(&account_service, &beneficiary_account).await;
    let expected_auth_keys = get_auth_keys_from_full_account(&updated_beneficiary);

    // act
    let recreated_claims = inheritance_service
        .recreate_pending_claims_for_beneficiary(RecreatePendingClaimsForBeneficiaryInput {
            beneficiary: &updated_beneficiary,
        })
        .await
        .expect("Failed to recreate claims");

    // assert
    assert_eq!(recreated_claims.len(), 1);
    let InheritanceClaim::Pending(recreated_claim) = recreated_claims.first().expect("Valid claim")
    else {
        panic!("Expected pending claim");
    };

    assert_eq!(expected_auth_keys, recreated_claim.common_fields.auth_keys);
    assert_eq!(initial_delay_end_time, recreated_claim.delay_end_time);
    assert_eq!(
        pending_claim.common_fields.recovery_relationship_id,
        recreated_claim.common_fields.recovery_relationship_id,
    );
    assert_ne!(
        pending_claim.common_fields.id,
        recreated_claim.common_fields.id,
    );
    assert!(recreated_claim.common_fields.created_at > pending_claim.common_fields.created_at);
    assert!(recreated_claim.common_fields.updated_at > pending_claim.common_fields.updated_at);
}

#[tokio::test]
async fn test_recreate_with_completed_claim() {
    // arrange
    let (account_service, inheritance_service) = setup_test_services().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;

    let locked_claim = create_locked_claim(&benefactor_account, &beneficiary_account).await;
    create_completed_claim(&locked_claim).await;

    // Rotate beneficiary auth keys
    let updated_beneficiary = rotate_auth_keys(&account_service, &beneficiary_account).await;

    // act
    let recreated_claims = inheritance_service
        .recreate_pending_claims_for_beneficiary(RecreatePendingClaimsForBeneficiaryInput {
            beneficiary: &updated_beneficiary,
        })
        .await
        .expect("Failed to recreate claims");

    // assert
    assert_eq!(recreated_claims.len(), 0);
}

#[tokio::test]
async fn test_recreate_with_locked_claim() {
    // arrange
    let (account_service, inheritance_service) = setup_test_services().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;

    create_locked_claim(&benefactor_account, &beneficiary_account).await;

    // Rotate beneficiary auth keys
    let updated_beneficiary = rotate_auth_keys(&account_service, &beneficiary_account).await;

    // act
    let recreated_claims = inheritance_service
        .recreate_pending_claims_for_beneficiary(RecreatePendingClaimsForBeneficiaryInput {
            beneficiary: &updated_beneficiary,
        })
        .await
        .expect("Failed to recreate claims");

    // assert
    assert_eq!(recreated_claims.len(), 0);
}

#[tokio::test]
async fn test_recreate_with_canceled_claim() {
    // arrange
    let (account_service, inheritance_service) = setup_test_services().await;
    let (benefactor_account, beneficiary_account) = setup_accounts().await;

    let secp = Secp256k1::new();
    let (auth_keys, _, _, _) = setup_keys_and_signatures(&secp);

    let delay_end_time = OffsetDateTime::now_utc() - Duration::minutes(5);
    let pending_claim = create_pending_inheritance_claim(
        &benefactor_account,
        &beneficiary_account,
        &auth_keys,
        Some(delay_end_time),
    )
    .await;
    create_canceled_claim(&pending_claim, InheritanceClaimCanceledBy::Beneficiary).await;

    // Rotate beneficiary auth keys
    let updated_beneficiary = rotate_auth_keys(&account_service, &beneficiary_account).await;

    // act
    let recreated_claims = inheritance_service
        .recreate_pending_claims_for_beneficiary(RecreatePendingClaimsForBeneficiaryInput {
            beneficiary: &updated_beneficiary,
        })
        .await
        .expect("Failed to recreate claims");

    // assert
    assert_eq!(recreated_claims.len(), 0);
}
