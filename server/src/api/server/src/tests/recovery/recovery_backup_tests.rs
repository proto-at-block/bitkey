use http::StatusCode;
use recovery::routes::relationship::UploadRecoveryBackupRequest;
use rstest::rstest;

use crate::tests::{
    gen_services,
    lib::{create_full_account, create_lite_account},
    requests::axum::TestClient,
};

use types::account::{bitcoin::Network, entities::Account, AccountType};

#[rstest]
#[case::create_with_full_account(AccountType::Full, false, StatusCode::OK)]
#[case::create_with_lite_account(AccountType::Lite, false, StatusCode::FORBIDDEN)]
#[case::create_with_invalid_account(AccountType::Full, true, StatusCode::NOT_FOUND)]
#[tokio::test]
async fn recovery_backup_upload_test(
    #[case] account_type: AccountType,
    #[case] invalid_account: bool,
    #[case] expected_status_code: StatusCode,
) {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let account = match account_type {
        AccountType::Full { .. } => Account::Full(
            create_full_account(
                &mut context,
                &bootstrap.services,
                Network::BitcoinSignet,
                None,
            )
            .await,
        ),
        AccountType::Lite => {
            Account::Lite(create_lite_account(&mut context, &bootstrap.services, None, true).await)
        }
        AccountType::Software => unimplemented!(),
    };

    let keys = context
        .get_authentication_keys_for_account_id(account.get_id())
        .expect("Invalid keys for account");

    // act
    let account_id = if !invalid_account {
        account.get_id().to_string()
    } else {
        "INVALID_ACCOUNT_ID".to_string()
    };
    let create_response = client
        .recovery_backup_upload(
            if !invalid_account {
                &account_id
            } else {
                "urn:wallet-account:01J85RQC36CX8NS4F390DEYFTP"
            },
            &UploadRecoveryBackupRequest {
                recovery_backup_material: "RANDOM_SEALED_KEY".to_string(),
            },
            &keys,
        )
        .await;

    match expected_status_code {
        StatusCode::OK => {
            assert_eq!(
                create_response.status_code, expected_status_code,
                "{:?}",
                create_response.body_string
            );

            let backup = bootstrap
                .services
                .recovery_relationship_service
                .repository
                .fetch_recovery_backup(account.get_id())
                .await
                .unwrap();

            assert_eq!(backup.account_id, account.get_id().clone());
            assert_eq!(backup.material, "RANDOM_SEALED_KEY");
        }
        _ => {
            assert_eq!(create_response.status_code, expected_status_code);
        }
    }
}
