use crate::service::tests::setup_test_verification;

use super::construct_test_transaction_verification_service;

use types::transaction_verification::entities::TransactionVerification::Failed;

#[tokio::test]
async fn test_cancel_pending_verification() {
    // Setup test service
    let service = construct_test_transaction_verification_service().await;

    // Setup test data
    let (tx_verification, _, _) = setup_test_verification(&service).await;
    let tx_verification_id = tx_verification.common_fields().id.clone();

    // Cancel the verification
    let result = service
        .cancel(
            &tx_verification.common_fields().account_id,
            &tx_verification_id,
        )
        .await;

    // Verify the result
    assert!(
        result.is_ok(),
        "Expected success but got {:?}",
        result.err()
    );

    // Check that the transaction was updated to failed state
    let updated_tx = service.repo.fetch(&tx_verification_id).await.unwrap();
    match updated_tx {
        Failed(_) => {
            // This is the expected state
        }
        _ => panic!("Expected a failed transaction verification"),
    }
}

#[tokio::test]
async fn test_cancel_success_verification() {
    // Setup test service
    let service = construct_test_transaction_verification_service().await;

    // Setup test data
    let (tx_verification, confirmation_token, _) = setup_test_verification(&service).await;
    let tx_verification_id = tx_verification.common_fields().id.clone();

    // Validate the verification with the confirmation token
    let _ = service
        .verify_with_confirmation_token(&tx_verification.common_fields().id, &confirmation_token)
        .await;

    // Try cancelling the verification
    let cancel_result = service
        .cancel(
            &tx_verification.common_fields().account_id,
            &tx_verification_id,
        )
        .await;

    // Verify the result is an error
    assert!(cancel_result.is_err());
    let err_str = cancel_result.unwrap_err().to_string();
    assert_eq!(
        err_str,
        "Expected verification state: Pending, but got: Success"
    )
}

#[tokio::test]
async fn test_cancel_failed_verification() {
    // Setup test service
    let service = construct_test_transaction_verification_service().await;

    // Setup test data
    let (tx_verification, _, _) = setup_test_verification(&service).await;
    let tx_verification_id = tx_verification.common_fields().id.clone();

    // Cancel the verification once
    let _ = service
        .cancel(
            &tx_verification.common_fields().account_id,
            &tx_verification_id,
        )
        .await;

    // Try cancelling the verification again, should be a no-op
    let cancel_result = service
        .cancel(
            &tx_verification.common_fields().account_id,
            &tx_verification.common_fields().id,
        )
        .await;

    // Verify the result
    assert!(
        cancel_result.is_ok(),
        "Expected success but got {:?}",
        cancel_result.err()
    );

    // Check that the transaction was updated to failed state
    let updated_tx = service.repo.fetch(&tx_verification_id).await.unwrap();
    match updated_tx {
        Failed(_) => {
            // This is the expected state
        }
        _ => panic!("Expected a failed transaction verification"),
    }
}
