use super::{
    construct_test_transaction_verification_service,
    setup_account_with_transaction_verification_policy,
};
use bdk_utils::bdk::bitcoin::psbt::Psbt;
use std::str::FromStr;
use types::currencies::CurrencyCode;
use types::transaction_verification::entities::{
    BitcoinDisplayUnit, TransactionVerification, TransactionVerification::Failed,
    TransactionVerification::Success,
};

// Helper to set up transaction verification data
async fn setup_test_verification(
    service: &crate::service::Service,
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
        Success(success) => {
            // Verify the signed_hw_grant fields
            assert_eq!(success.signed_hw_grant.version, 0);
            assert!(!success.signed_hw_grant.commitment.is_empty());
        }
        _ => panic!("Expected a success transaction verification"),
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
