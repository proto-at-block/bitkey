use std::collections::HashMap;

use crate::service::inheritance::recreate_pending_claims_for_beneficiary::RecreatePendingClaimsForBeneficiaryInput;
use crate::service::inheritance::tests::{
    construct_test_inheritance_service, create_canceled_claim, create_completed_claim,
    create_locked_claim, create_pending_inheritance_claim, setup_accounts_with_network,
    setup_keys_and_signatures,
};
use crate::service::inheritance::Service as InheritanceService;

use account::service::tests::{construct_test_account_service, generate_test_authkeys};
use account::service::{CreateAndRotateAuthKeysInput, Service as AccountService};
use bdk_utils::bdk::bitcoin::key::Secp256k1;
use notification::service::tests::construct_test_notification_service;
use notification::service::{FetchForAccountInput, Service as NotificationService};
use notification::NotificationPayloadType;
use time::{Duration, OffsetDateTime};
use types::account::bitcoin::Network;
use types::account::entities::{Account, FullAccount};
use types::account::identifiers::AccountId;
use types::recovery::inheritance::claim::{
    InheritanceClaim, InheritanceClaimAuthKeys, InheritanceClaimCanceledBy,
};

// Helper function to rotate auth keys and get updated beneficiary account
async fn rotate_auth_keys(
    account_service: &AccountService,
    beneficiary_account: &FullAccount,
) -> FullAccount {
    let new_auth_keys = generate_test_authkeys();
    let updated_account = account_service
        .create_and_rotate_auth_keys(CreateAndRotateAuthKeysInput {
            account_id: &beneficiary_account.id,
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
async fn setup_test_services() -> (AccountService, InheritanceService, NotificationService) {
    (
        construct_test_account_service().await,
        construct_test_inheritance_service().await,
        construct_test_notification_service().await,
    )
}

async fn validate_notifications_were_scheduled(
    notification_service: &NotificationService,
    account_id: &AccountId,
    is_checking_benefactor_account: bool,
    expected_num_claim_notifications: usize,
) {
    let mut scheduled_notifications = notification_service
        .fetch_scheduled_for_account(FetchForAccountInput {
            account_id: account_id.clone(),
        })
        .await
        .unwrap();
    scheduled_notifications.sort_by_key(|n| {
        // Get rid of jitter
        let jitter = n
            .schedule
            .as_ref()
            .and_then(|s| s.jitter)
            .unwrap_or(Duration::seconds(0));

        n.execution_date_time - jitter
    });
    let scheduled_notifications_types = scheduled_notifications
        .iter()
        .map(|n| n.payload_type)
        .collect::<Vec<NotificationPayloadType>>();
    let expected_notification_types = [
        // Benefactor accounts should have the scheduled RecoveryRelationshipBenefactorInvitationPending notifications
        vec![
            NotificationPayloadType::RecoveryRelationshipBenefactorInvitationPending;
            is_checking_benefactor_account as usize
        ],
        // Only Benefactor accounts should have the scheduled InheritanceClaimPeriodInitiated notifications
        vec![
            NotificationPayloadType::InheritanceClaimPeriodInitiated;
            if is_checking_benefactor_account {
                expected_num_claim_notifications
            } else {
                0
            }
        ],
        // The rest of the notifications should be scheduled for both benefactor and beneficiary accounts
        vec![
            NotificationPayloadType::InheritanceClaimPeriodAlmostOver;
            expected_num_claim_notifications
        ],
        vec![
            NotificationPayloadType::InheritanceClaimPeriodCompleted;
            expected_num_claim_notifications
        ],
    ]
    .concat();
    assert_eq!(expected_notification_types, scheduled_notifications_types);
}

// Tests
#[tokio::test]
async fn test_recreate_with_multiple_pending_claims() {
    // Arrange
    let (account_service, inheritance_service, notification_service) = setup_test_services().await;
    let (first_benefactor_account, beneficiary_account) =
        setup_accounts_with_network(Network::BitcoinMain).await;
    let (second_benefactor_account, _) = setup_accounts_with_network(Network::BitcoinMain).await;

    let current_auth_keys = get_auth_keys_from_full_account(&beneficiary_account);

    // Create two pending claims with same beneficiary
    let first_pending_claim = create_pending_inheritance_claim(
        &first_benefactor_account,
        &beneficiary_account,
        &current_auth_keys,
        None,
    )
    .await;
    let second_pending_claim = create_pending_inheritance_claim(
        &second_benefactor_account,
        &beneficiary_account,
        &current_auth_keys,
        None,
    )
    .await;
    let initial_delay_end_times = HashMap::from([
        (
            first_pending_claim.common_fields.recovery_relationship_id,
            first_pending_claim.common_fields.created_at + Duration::days(180),
        ),
        (
            second_pending_claim.common_fields.recovery_relationship_id,
            second_pending_claim.common_fields.created_at + Duration::days(180),
        ),
    ]);

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
        let expected_delay_end_time =
            initial_delay_end_times[&recreated_claim.common_fields.recovery_relationship_id];
        assert_eq!(expected_delay_end_time, recreated_claim.delay_end_time);
    }
    validate_notifications_were_scheduled(
        &notification_service,
        &first_benefactor_account.id,
        true,
        2,
    )
    .await;
    validate_notifications_were_scheduled(
        &notification_service,
        &second_benefactor_account.id,
        true,
        2,
    )
    .await;
    validate_notifications_were_scheduled(&notification_service, &beneficiary_account.id, false, 4)
        .await;
}

#[tokio::test]
async fn test_recreate_with_pending_claim_same_auth_keys_success() {
    // arrange
    let (_, inheritance_service, notification_service) = setup_test_services().await;
    let (benefactor_account, beneficiary_account) =
        setup_accounts_with_network(Network::BitcoinMain).await;

    let auth_keys = get_auth_keys_from_full_account(&beneficiary_account);
    let original_claim = create_pending_inheritance_claim(
        &benefactor_account,
        &beneficiary_account,
        &auth_keys,
        None,
    )
    .await;

    // act
    let recreated_claims = inheritance_service
        .recreate_pending_claims_for_beneficiary(RecreatePendingClaimsForBeneficiaryInput {
            beneficiary: &beneficiary_account,
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
    validate_notifications_were_scheduled(&notification_service, &benefactor_account.id, true, 1)
        .await;
    validate_notifications_were_scheduled(&notification_service, &beneficiary_account.id, false, 1)
        .await;
}

#[tokio::test]
async fn test_recreate_with_pending_claim_new_auth_keys_success() {
    // arrange
    let (account_service, inheritance_service, notification_service) = setup_test_services().await;
    let (benefactor_account, beneficiary_account) =
        setup_accounts_with_network(Network::BitcoinMain).await;

    let initial_auth_keys = get_auth_keys_from_full_account(&beneficiary_account);
    let pending_claim = create_pending_inheritance_claim(
        &benefactor_account,
        &beneficiary_account,
        &initial_auth_keys,
        None,
    )
    .await;
    let initial_delay_end_time = pending_claim.common_fields.created_at + Duration::days(180);

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

    validate_notifications_were_scheduled(&notification_service, &benefactor_account.id, true, 2)
        .await;
    validate_notifications_were_scheduled(&notification_service, &beneficiary_account.id, false, 2)
        .await;
}

#[tokio::test]
async fn test_recreate_with_pending_claim_new_auth_keys_success_with_test_delay() {
    // arrange
    let (account_service, inheritance_service, _) = setup_test_services().await;
    let (benefactor_account, beneficiary_account) =
        setup_accounts_with_network(Network::BitcoinSignet).await;

    let initial_auth_keys = get_auth_keys_from_full_account(&beneficiary_account);
    let pending_claim = create_pending_inheritance_claim(
        &benefactor_account,
        &beneficiary_account,
        &initial_auth_keys,
        None,
    )
    .await;
    let initial_delay_end_time = pending_claim.common_fields.created_at + Duration::minutes(10);

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
    let (account_service, inheritance_service, notification_service) = setup_test_services().await;
    let (benefactor_account, beneficiary_account) =
        setup_accounts_with_network(Network::BitcoinMain).await;

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
    validate_notifications_were_scheduled(&notification_service, &benefactor_account.id, true, 1)
        .await;
    validate_notifications_were_scheduled(&notification_service, &beneficiary_account.id, false, 1)
        .await;
}

#[tokio::test]
async fn test_recreate_with_locked_claim() {
    // arrange
    let (account_service, inheritance_service, notification_service) = setup_test_services().await;
    let (benefactor_account, beneficiary_account) =
        setup_accounts_with_network(Network::BitcoinMain).await;

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
    validate_notifications_were_scheduled(&notification_service, &benefactor_account.id, true, 1)
        .await;
    validate_notifications_were_scheduled(&notification_service, &beneficiary_account.id, false, 1)
        .await;
}

#[tokio::test]
async fn test_recreate_with_canceled_claim() {
    // arrange
    let (account_service, inheritance_service, notification_service) = setup_test_services().await;
    let (benefactor_account, beneficiary_account) =
        setup_accounts_with_network(Network::BitcoinMain).await;

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
    validate_notifications_were_scheduled(&notification_service, &benefactor_account.id, true, 1)
        .await;
    validate_notifications_were_scheduled(&notification_service, &beneficiary_account.id, false, 1)
        .await;
}
