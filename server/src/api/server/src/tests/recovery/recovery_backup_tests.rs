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
#[case::full_account(AccountType::Full, false, StatusCode::OK, false)]
#[case::lite_account(AccountType::Lite, false, StatusCode::FORBIDDEN, false)]
#[case::invalid_account(AccountType::Full, true, StatusCode::NOT_FOUND, false)]
#[case::not_found_before_upload(AccountType::Full, false, StatusCode::OK, true)]
#[tokio::test]
async fn recovery_backup_upload_and_fetch_test(
    #[case] account_type: AccountType,
    #[case] invalid_account: bool,
    #[case] expected_status_code: StatusCode,
    #[case] check_not_found_before_upload: bool,
) {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let account = match account_type {
        AccountType::Full => Account::Full(
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
        AccountType::Software => {
            panic!("Software account type not implemented in tests");
        }
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

    if check_not_found_before_upload {
        let fetch_response = client.fetch_recovery_backup(&account_id, &keys).await;

        assert_eq!(fetch_response.status_code, StatusCode::NOT_FOUND);
    }

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

            let fetch_response = client.fetch_recovery_backup(&account_id, &keys).await;

            let backup = bootstrap
                .services
                .recovery_relationship_service
                .repository
                .fetch_recovery_backup(account.get_id())
                .await
                .unwrap();

            assert_eq!(backup.account_id, account.get_id().clone());
            assert_eq!(
                fetch_response.body.unwrap().recovery_backup_material,
                "RANDOM_SEALED_KEY"
            );
        }
        _ => {
            assert_eq!(create_response.status_code, expected_status_code);
        }
    }
}
