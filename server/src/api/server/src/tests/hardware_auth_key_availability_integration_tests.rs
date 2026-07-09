use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use http::StatusCode;
use recovery::entities::{RecoveryDestination, RecoveryStatus};
use recovery::routes::delay_notify::{
    HardwareAuthKeyAvailabilityRequest, HardwareAuthKeyAvailabilityResponse,
    HardwareAuthKeyAvailabilityStatus,
};
use time::{Duration, OffsetDateTime};
use types::account::bitcoin::Network;
use types::account::entities::{Account, Factor, HardwareType};
use types::account::identifiers::AccountId;

use account::service::CreateAndRotateAuthKeysInput;

use crate::tests::{
    gen_services,
    lib::{
        create_full_account_v2, create_new_authkeys, create_pubkey,
        generate_delay_and_notify_recovery,
    },
    requests::{Response, axum::TestClient},
};

async fn check_pubkey_availability(
    client: &TestClient,
    account_id: &AccountId,
    hardware_auth_pubkey: PublicKey,
) -> Response<HardwareAuthKeyAvailabilityResponse> {
    client
        .check_hardware_auth_key_availability(
            account_id,
            &HardwareAuthKeyAvailabilityRequest {
                hardware_auth_pubkey,
            },
        )
        .await
}

fn assert_availability_status(
    response: Response<HardwareAuthKeyAvailabilityResponse>,
    expected_status: HardwareAuthKeyAvailabilityStatus,
) {
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    assert_eq!(response.body.unwrap().status, expected_status);
}

fn assert_hw_auth_pubkey_in_use(response: Response<HardwareAuthKeyAvailabilityResponse>) {
    assert_eq!(
        response.status_code,
        StatusCode::BAD_REQUEST,
        "{}",
        response.body_string
    );
    assert!(response.body_string.contains("HW_AUTH_PUBKEY_IN_USE"));
}

#[tokio::test]
async fn unused_key_returns_available_without_claiming() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let account = create_full_account_v2(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let unused_pubkey = create_pubkey();

    let response = check_pubkey_availability(&client, &account.id, unused_pubkey).await;

    assert_availability_status(response, HardwareAuthKeyAvailabilityStatus::Available);
    let public_key_record = bootstrap
        .services
        .public_key_repository
        .fetch_by_public_key(&unused_pubkey.to_string())
        .await
        .unwrap();
    assert!(
        public_key_record.is_none(),
        "availability check must not create a public_keys record"
    );
}

#[tokio::test]
async fn claimed_by_other_account_blocks() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let account = create_full_account_v2(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let other_account = create_full_account_v2(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let inactive_claimed_pubkey = other_account.hardware_auth_pubkey;
    let rotated_keys = create_new_authkeys(&mut context);
    bootstrap
        .services
        .account_service
        .create_and_rotate_auth_keys(CreateAndRotateAuthKeysInput {
            account_id: &other_account.id,
            app_auth_pubkey: rotated_keys.app.public_key,
            hardware_auth_pubkey: rotated_keys.hw.public_key,
            recovery_auth_pubkey: Some(rotated_keys.recovery.public_key),
            hardware_type: HardwareType::W3,
        })
        .await
        .unwrap();

    let response = check_pubkey_availability(&client, &account.id, inactive_claimed_pubkey).await;

    assert_hw_auth_pubkey_in_use(response);
}

#[tokio::test]
async fn active_on_other_account_blocks() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let account = create_full_account_v2(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let other_account = create_full_account_v2(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;

    let response =
        check_pubkey_availability(&client, &account.id, other_account.hardware_auth_pubkey).await;

    assert_hw_auth_pubkey_in_use(response);
}

#[tokio::test]
async fn claimed_by_current_account_returns_claimed() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let account = create_full_account_v2(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let inactive_claimed_pubkey = account.hardware_auth_pubkey;
    let rotated_keys = create_new_authkeys(&mut context);
    bootstrap
        .services
        .account_service
        .create_and_rotate_auth_keys(CreateAndRotateAuthKeysInput {
            account_id: &account.id,
            app_auth_pubkey: rotated_keys.app.public_key,
            hardware_auth_pubkey: rotated_keys.hw.public_key,
            recovery_auth_pubkey: Some(rotated_keys.recovery.public_key),
            hardware_type: HardwareType::W3,
        })
        .await
        .unwrap();

    let response = check_pubkey_availability(&client, &account.id, inactive_claimed_pubkey).await;

    assert_availability_status(
        response,
        HardwareAuthKeyAvailabilityStatus::ClaimedByCurrentAccount,
    );
}

#[tokio::test]
async fn claimed_by_current_account_reserved_by_pending_recovery_blocks() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let account = create_full_account_v2(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let inactive_claimed_pubkey = account.hardware_auth_pubkey;
    let rotated_keys = create_new_authkeys(&mut context);
    let Account::Full(updated_account) = bootstrap
        .services
        .account_service
        .create_and_rotate_auth_keys(CreateAndRotateAuthKeysInput {
            account_id: &account.id,
            app_auth_pubkey: rotated_keys.app.public_key,
            hardware_auth_pubkey: rotated_keys.hw.public_key,
            recovery_auth_pubkey: Some(rotated_keys.recovery.public_key),
            hardware_type: HardwareType::W3,
        })
        .await
        .unwrap()
    else {
        panic!("expected full account");
    };

    let recovery_destination_keys = create_new_authkeys(&mut context);
    let pending_recovery = generate_delay_and_notify_recovery(
        account.id.clone(),
        RecoveryDestination {
            source_auth_keys_id: updated_account.common_fields.active_auth_keys_id.clone(),
            app_auth_pubkey: recovery_destination_keys.app.public_key,
            hardware_auth_pubkey: inactive_claimed_pubkey,
            recovery_auth_pubkey: Some(recovery_destination_keys.recovery.public_key),
            hardware_type: HardwareType::W3,
        },
        OffsetDateTime::now_utc() + Duration::days(7),
        RecoveryStatus::Pending,
        Factor::Hw,
    );
    bootstrap
        .services
        .recovery_service
        .create(&pending_recovery)
        .await
        .unwrap();

    let response = check_pubkey_availability(&client, &account.id, inactive_claimed_pubkey).await;

    assert_hw_auth_pubkey_in_use(response);
}

#[tokio::test]
async fn active_on_current_account_returns_active() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let account = create_full_account_v2(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;

    let response =
        check_pubkey_availability(&client, &account.id, account.hardware_auth_pubkey).await;

    assert_availability_status(
        response,
        HardwareAuthKeyAvailabilityStatus::ActiveOnCurrentAccount,
    );
}

#[tokio::test]
async fn active_on_current_account_returns_active_during_pending_lost_app_recovery() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let account = create_full_account_v2(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let recovery_destination_keys = create_new_authkeys(&mut context);
    let pending_recovery = generate_delay_and_notify_recovery(
        account.id.clone(),
        RecoveryDestination {
            source_auth_keys_id: account.common_fields.active_auth_keys_id.clone(),
            app_auth_pubkey: recovery_destination_keys.app.public_key,
            hardware_auth_pubkey: account.hardware_auth_pubkey,
            recovery_auth_pubkey: Some(recovery_destination_keys.recovery.public_key),
            hardware_type: HardwareType::W3,
        },
        OffsetDateTime::now_utc() + Duration::days(7),
        RecoveryStatus::Pending,
        Factor::App,
    );
    bootstrap
        .services
        .recovery_service
        .create(&pending_recovery)
        .await
        .unwrap();

    let response =
        check_pubkey_availability(&client, &account.id, account.hardware_auth_pubkey).await;

    assert_availability_status(
        response,
        HardwareAuthKeyAvailabilityStatus::ActiveOnCurrentAccount,
    );
}

#[tokio::test]
async fn reserved_by_pending_recovery_blocks() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let account = create_full_account_v2(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let recovery_destination_keys = create_new_authkeys(&mut context);
    let pending_recovery = generate_delay_and_notify_recovery(
        account.id.clone(),
        RecoveryDestination {
            source_auth_keys_id: account.common_fields.active_auth_keys_id.clone(),
            app_auth_pubkey: recovery_destination_keys.app.public_key,
            hardware_auth_pubkey: recovery_destination_keys.hw.public_key,
            recovery_auth_pubkey: Some(recovery_destination_keys.recovery.public_key),
            hardware_type: HardwareType::W3,
        },
        OffsetDateTime::now_utc() + Duration::days(7),
        RecoveryStatus::Pending,
        Factor::Hw,
    );
    bootstrap
        .services
        .recovery_service
        .create(&pending_recovery)
        .await
        .unwrap();

    let response = check_pubkey_availability(
        &client,
        &account.id,
        recovery_destination_keys.hw.public_key,
    )
    .await;

    assert_hw_auth_pubkey_in_use(response);
}
