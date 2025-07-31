use crate::service::tests::setup_test_verification;

use super::construct_test_transaction_verification_service;
use types::transaction_verification::entities::{
    TransactionVerification::{Failed, Success},
    TransactionVerificationSuccess,
};

#[tokio::test]
async fn test_verify_with_confirmation_token_success() {
    // Setup test service
    let service = construct_test_transaction_verification_service().await;

    // Setup test data
    let (tx_verification, confirmation_token, _) = setup_test_verification(&service).await;
    let tx_id = tx_verification.common_fields().id.clone();

    // Execute the verification with confirmation token
    let result = service
        .verify_with_confirmation_token(&tx_verification.common_fields().id, &confirmation_token)
        .await;

    // Verify the result
    assert!(
        result.is_ok(),
        "Expected success but got {:?}",
        result.err()
    );

    // Check that the transaction was updated to success state
    let updated_tx = service.repo.fetch(&tx_id).await.unwrap();
    match updated_tx {
        Success(TransactionVerificationSuccess {
            signed_hw_grant, ..
        }) => {
            // Verify the signed_hw_grant fields
            assert_eq!(signed_hw_grant.version, 0);
            assert!(!signed_hw_grant.commitment.is_empty());
        }
        _ => panic!("Expected a success hw grant transaction verification"),
    }
}

#[tokio::test]
async fn test_verify_with_confirmation_token_invalid_token() {
    // Setup test service
    let service = construct_test_transaction_verification_service().await;

    // Setup test data
    let (tx_verification, _, _) = setup_test_verification(&service).await;

    // Execute the verification with an invalid token
    let result = service
        .verify_with_confirmation_token(&tx_verification.common_fields().id, "invalid_token")
        .await;

    // Verify the result is an error
    assert!(result.is_err());
    let err_str = result.unwrap_err().to_string();
    assert_eq!(
        err_str,
        "Invalid token for completion of transaction verification"
    )
}

#[tokio::test]
async fn test_verify_with_cancellation_token_success() {
    // Setup test service
    let service = construct_test_transaction_verification_service().await;

    // Setup test data
    let (tx_verification, _, cancellation_token) = setup_test_verification(&service).await;
    let tx_id = tx_verification.common_fields().id.clone();

    // Execute the verification with cancellation token
    let result = service
        .verify_with_cancellation_token(&tx_id, &cancellation_token)
        .await;

    // Verify the result
    assert!(
        result.is_ok(),
        "Expected success but got {:?}",
        result.err()
    );

    // Check that the transaction was updated to failed state
    let updated_tx = service.repo.fetch(&tx_id).await.unwrap();
    match updated_tx {
        Failed(_) => {
            // This is the expected state
        }
        _ => panic!("Expected a failed transaction verification"),
    }
}

#[tokio::test]
async fn test_verify_with_cancellation_token_invalid_token() {
    // Setup test service
    let service = construct_test_transaction_verification_service().await;

    // Setup test data
    let (tx_verification, _, _) = setup_test_verification(&service).await;

    // Execute the verification with an invalid token
    let result = service
        .verify_with_cancellation_token(&tx_verification.common_fields().id, "invalid_token")
        .await;

    // Verify the result is an error
    assert!(result.is_err());
    let err_str = result.unwrap_err().to_string();
    assert_eq!(
        err_str,
        "Invalid token for completion of transaction verification"
    )
}
