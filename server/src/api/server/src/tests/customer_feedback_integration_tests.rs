use customer_feedback::routes::{CreateEncryptedAttachmentRequest, UploadSealedAttachmentRequest};
use http::StatusCode;
use types::{
    account::{entities::FullAccount, identifiers::AccountId},
    encrypted_attachment::{identifiers::EncryptedAttachmentId, EncryptedAttachment},
};

use super::{gen_services, lib::create_full_account, requests::axum::TestClient, Bootstrap};

// Helper function to set up test environment
async fn setup_test() -> (TestClient, FullAccount, Bootstrap) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router.clone()).await;
    let account = create_full_account(
        &mut context,
        &bootstrap.services,
        types::account::bitcoin::Network::BitcoinSignet,
        None,
    )
    .await;
    (client, account, bootstrap)
}

// Helper function to create an encrypted attachment
async fn create_encrypted_attachment_helper(
    client: &TestClient,
    account_id: &AccountId,
) -> customer_feedback::routes::CreateEncryptedAttachmentResponse {
    let request = CreateEncryptedAttachmentRequest {};
    let response = client
        .create_encrypted_attachment(account_id, &request)
        .await;
    assert_eq!(response.status_code, StatusCode::OK);
    response.body.unwrap()
}

// Helper function to verify stored attachment properties
fn assert_stored_attachment_basic_properties(
    stored_attachment: &EncryptedAttachment,
    expected_id: &EncryptedAttachmentId,
    expected_account_id: &AccountId,
    expected_public_key: &[u8],
) {
    assert_eq!(stored_attachment.id, *expected_id);
    assert_eq!(stored_attachment.account_id, *expected_account_id);
    assert_eq!(stored_attachment.public_key, expected_public_key);
    assert!(!stored_attachment.kms_key_id.is_empty());
    assert!(!stored_attachment.private_key_ciphertext.is_empty());
}

// Helper function to verify repository state after creation
async fn assert_attachment_created_in_repository(
    bootstrap: &Bootstrap,
    attachment_id: &EncryptedAttachmentId,
    account_id: &AccountId,
    public_key: &[u8],
) {
    let stored_attachment = bootstrap
        .services
        .encrypted_attachment_repository
        .fetch_by_id(attachment_id)
        .await
        .unwrap();

    assert_stored_attachment_basic_properties(
        &stored_attachment,
        attachment_id,
        account_id,
        public_key,
    );
    assert!(stored_attachment.sealed_attachment.is_none());
}

// Helper function to verify repository state after upload
async fn assert_attachment_uploaded_in_repository(
    bootstrap: &Bootstrap,
    attachment_id: &EncryptedAttachmentId,
    account_id: &AccountId,
    public_key: &[u8],
    expected_sealed_data: &[u8],
) {
    let stored_attachment = bootstrap
        .services
        .encrypted_attachment_repository
        .fetch_by_id(attachment_id)
        .await
        .unwrap();

    assert_stored_attachment_basic_properties(
        &stored_attachment,
        attachment_id,
        account_id,
        public_key,
    );
    assert_eq!(
        stored_attachment.sealed_attachment,
        Some(expected_sealed_data.to_vec())
    );
}

#[tokio::test]
async fn test_create_encrypted_attachment_success() {
    let (client, account, bootstrap) = setup_test().await;

    let body = create_encrypted_attachment_helper(&client, &account.id).await;
    assert!(!body.encrypted_attachment_id.to_string().is_empty());
    assert!(!body.public_key.is_empty());

    // Verify the encrypted attachment was persisted correctly
    assert_attachment_created_in_repository(
        &bootstrap,
        &body.encrypted_attachment_id,
        &account.id,
        &body.public_key,
    )
    .await;
}

#[tokio::test]
async fn test_upload_sealed_attachment_success() {
    let (client, account, bootstrap) = setup_test().await;

    // First create an encrypted attachment
    let create_body = create_encrypted_attachment_helper(&client, &account.id).await;

    // Then upload sealed attachment
    let sealed_data = vec![1, 2, 3, 4, 5]; // Mock encrypted data
    let upload_request = UploadSealedAttachmentRequest {
        sealed_attachment: sealed_data.clone(),
    };

    let upload_response = client
        .upload_sealed_attachment(
            &account.id,
            &create_body.encrypted_attachment_id,
            &upload_request,
        )
        .await;

    assert_eq!(upload_response.status_code, StatusCode::OK);

    // Verify the sealed attachment was persisted correctly
    assert_attachment_uploaded_in_repository(
        &bootstrap,
        &create_body.encrypted_attachment_id,
        &account.id,
        &create_body.public_key,
        &sealed_data,
    )
    .await;
}

#[tokio::test]
async fn test_upload_sealed_attachment_not_found() {
    let (client, account, _bootstrap) = setup_test().await;

    // Try to upload to non-existent encrypted attachment
    let fake_id = EncryptedAttachmentId::gen().unwrap();
    let sealed_data = vec![1, 2, 3, 4, 5];
    let upload_request = UploadSealedAttachmentRequest {
        sealed_attachment: sealed_data,
    };

    let upload_response = client
        .upload_sealed_attachment(&account.id, &fake_id, &upload_request)
        .await;

    assert_eq!(upload_response.status_code, StatusCode::NOT_FOUND);
}

#[tokio::test]
async fn test_encrypted_attachment_workflow() {
    let (client, account, bootstrap) = setup_test().await;

    // Step 1: Create encrypted attachment
    let create_body = create_encrypted_attachment_helper(&client, &account.id).await;

    // Verify response structure
    assert!(!create_body.encrypted_attachment_id.to_string().is_empty());
    assert!(!create_body.public_key.is_empty());

    // Verify attachment was created correctly in the repository
    let stored_attachment_after_create = bootstrap
        .services
        .encrypted_attachment_repository
        .fetch_by_id(&create_body.encrypted_attachment_id)
        .await
        .unwrap();

    assert_stored_attachment_basic_properties(
        &stored_attachment_after_create,
        &create_body.encrypted_attachment_id,
        &account.id,
        &create_body.public_key,
    );
    assert!(stored_attachment_after_create.sealed_attachment.is_none());

    // Step 2: Upload sealed attachment
    let sealed_data = vec![0u8; 1024]; // 1KB of test data
    let upload_request = UploadSealedAttachmentRequest {
        sealed_attachment: sealed_data.clone(),
    };

    let upload_response = client
        .upload_sealed_attachment(
            &account.id,
            &create_body.encrypted_attachment_id,
            &upload_request,
        )
        .await;

    assert_eq!(upload_response.status_code, StatusCode::OK);

    // Verify sealed attachment was stored correctly in the repository
    let stored_attachment_after_upload = bootstrap
        .services
        .encrypted_attachment_repository
        .fetch_by_id(&create_body.encrypted_attachment_id)
        .await
        .unwrap();

    assert_stored_attachment_basic_properties(
        &stored_attachment_after_upload,
        &create_body.encrypted_attachment_id,
        &account.id,
        &create_body.public_key,
    );
    assert_eq!(
        stored_attachment_after_upload.sealed_attachment,
        Some(sealed_data)
    );
    assert!(stored_attachment_after_upload.updated_at > stored_attachment_after_create.updated_at);

    // Step 3: Try to upload again (should succeed - idempotent)
    let second_upload_response = client
        .upload_sealed_attachment(
            &account.id,
            &create_body.encrypted_attachment_id,
            &upload_request,
        )
        .await;

    assert_eq!(second_upload_response.status_code, StatusCode::OK);
}

#[tokio::test]
async fn test_create_multiple_encrypted_attachments() {
    let (client, account, bootstrap) = setup_test().await;

    // Create first attachment
    let body1 = create_encrypted_attachment_helper(&client, &account.id).await;

    // Create second attachment
    let body2 = create_encrypted_attachment_helper(&client, &account.id).await;

    // Verify they have different IDs
    assert_ne!(body1.encrypted_attachment_id, body2.encrypted_attachment_id);

    // Verify both attachments are stored separately in the repository
    let stored_attachment1 = bootstrap
        .services
        .encrypted_attachment_repository
        .fetch_by_id(&body1.encrypted_attachment_id)
        .await
        .unwrap();

    let stored_attachment2 = bootstrap
        .services
        .encrypted_attachment_repository
        .fetch_by_id(&body2.encrypted_attachment_id)
        .await
        .unwrap();

    assert_stored_attachment_basic_properties(
        &stored_attachment1,
        &body1.encrypted_attachment_id,
        &account.id,
        &body1.public_key,
    );
    assert!(stored_attachment1.sealed_attachment.is_none());

    assert_stored_attachment_basic_properties(
        &stored_attachment2,
        &body2.encrypted_attachment_id,
        &account.id,
        &body2.public_key,
    );
    assert!(stored_attachment2.sealed_attachment.is_none());
}
