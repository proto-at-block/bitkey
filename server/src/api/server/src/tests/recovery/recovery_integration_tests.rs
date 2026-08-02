use std::collections::HashSet;

use account::service::{
    tests::{TestAuthenticationKeys, TestKeypair},
    FetchAccountInput,
};
use axum::response::IntoResponse;
use bdk_utils::bdk::bitcoin::key::Secp256k1;
use bdk_utils::signature::sign_message;
use comms_verification::TEST_CODE;
use errors::ApiError;
use http::StatusCode;
use http_body_util::BodyExt;
use notification::service::FetchForAccountInput;
use notification::NotificationPayloadType;
use privileged_action::service::configure_delay_notify_period::ConfigureDelayNotifyPeriodInput;
use recovery::entities::{RecoveryDestination, RecoveryStatus, RecoveryType};
use recovery::error::RecoveryError;
use recovery::routes::delay_notify::{
    CompleteDelayNotifyRequest, ConfigureDelayNotifyPeriodRequest, CreateAccountDelayNotifyRequest,
    SendAccountVerificationCodeRequest, UpdateDelayForTestRecoveryRequest,
    VerifyAccountVerificationCodeRequest,
};
use time::{Duration, OffsetDateTime};
use types::account::bitcoin::Network;
use types::account::entities::{Factor, FullAccountAuthKeysInput, HardwareType};
use types::account::identifiers::AccountId;
use types::account::keys::FullAccountAuthKeys;
use types::privileged_action::repository::{
    AuthorizationStrategyRecord, DelayAndNotifyRecord, PrivilegedActionInstanceRecord, RecordStatus,
};
use types::privileged_action::shared::{PrivilegedActionInstanceId, PrivilegedActionType};

use crate::tests::gen_services;
use crate::tests::lib::{
    create_auth_keyset_model, create_default_account_with_predefined_wallet, create_full_account,
    create_keypair, create_new_authkeys, create_onboarded_w3_account, create_phone_touchpoint,
    create_plain_keys, create_pubkey, create_push_touchpoint, generate_delay_and_notify_recovery,
};
use crate::tests::requests::axum::TestClient;
use crate::tests::requests::Response;
use rstest::rstest;

const ONE_DAY_SECS: usize = 24 * 60 * 60;

#[rstest]
#[case::invalid_wallet_id(Some(AccountId::gen().unwrap()), Factor::Hw, (true, true), true, StatusCode::NOT_FOUND, true, false, false)]
#[case::basic(None, Factor::Hw, (true, true), true, StatusCode::OK, true, false, false)]
#[case::without_recovery_auth_pubkey(None, Factor::Hw, (false, false), true, StatusCode::OK, true, false, false)]
#[case::seven_day_delay_override(None, Factor::Hw, (true, true), false, StatusCode::OK, true, false, false)]
#[case::no_signers(None, Factor::Hw, (true, true), true, StatusCode::FORBIDDEN, false, false, false)]
#[case::wrong_signer(None, Factor::Hw, (true, true), true, StatusCode::BAD_REQUEST, false, true, false)]
#[case::both_signers(None, Factor::Hw, (true, true), true, StatusCode::BAD_REQUEST, true, true, false)]
#[case::prior_contest(None, Factor::Hw, (true, true), true, StatusCode::OK, true, false, true)]
#[case::prior_contest_without_recovery_key(None, Factor::Hw, (false, false), true, StatusCode::OK, true, false, true)]
#[case::upgrade_to_recovery_key(None, Factor::Hw, (false, true), true, StatusCode::OK, true, false, false)]
#[case::downgrade_to_no_recovery_key(None, Factor::Hw, (true, false), true, StatusCode::BAD_REQUEST, true, false, false)]
#[tokio::test]
async fn test_create_delay_notify(
    #[case] override_account_id: Option<AccountId>,
    #[case] lost_factor: Factor,
    #[case] include_recovery_auth_pubkey: (bool, bool),
    #[case] create_test_account: bool,
    #[case] expected_status: StatusCode,
    #[case] app_signed: bool,
    #[case] hw_signed: bool,
    #[case] prior_contest: bool,
) {
    let (mut context, bootstrap) = gen_services().await;
    let fixed_cur_time = bootstrap.services.recovery_service.cur_time();
    let client = TestClient::new(bootstrap.router).await;

    let network = match create_test_account {
        true => Network::BitcoinSignet,
        false => Network::BitcoinMain,
    };

    let (account_has_recovery_key, recovery_has_recovery_key) = include_recovery_auth_pubkey;

    let mut keys = create_auth_keyset_model(&mut context);
    if !account_has_recovery_key {
        keys.recovery_pubkey = None;
    }

    let account = create_full_account(&mut context, &bootstrap.services, network, Some(keys)).await;
    let account_keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .expect("Invalid keys for account");
    let query_account_id = override_account_id.unwrap_or(account.clone().id);

    let touchpoint_id =
        create_phone_touchpoint(&bootstrap.services, &account.clone().id, true).await;
    if prior_contest {
        // Insert a recent contested recovery
        let prior_recovery = generate_delay_and_notify_recovery(
            account.clone().id,
            RecoveryDestination {
                source_auth_keys_id: account.clone().common_fields.active_auth_keys_id,
                app_auth_pubkey: create_pubkey(),
                hardware_auth_pubkey: account.hardware_auth_pubkey,
                recovery_auth_pubkey: if recovery_has_recovery_key {
                    create_pubkey().into()
                } else {
                    None
                },
                hardware_type: HardwareType::default(),
            },
            fixed_cur_time - Duration::days(15),
            RecoveryStatus::CanceledInContest,
            Factor::App,
        );
        bootstrap
            .services
            .recovery_service
            .create(&prior_recovery)
            .await
            .unwrap();
    }

    let request = CreateAccountDelayNotifyRequest {
        lost_factor,
        delay_period_num_sec: None,
        auth: FullAccountAuthKeysInput {
            app: create_pubkey(),
            hardware: if lost_factor == Factor::App {
                account.hardware_auth_pubkey
            } else {
                create_pubkey()
            },
            recovery: if recovery_has_recovery_key {
                Some(create_pubkey())
            } else {
                None
            },
            hardware_type: HardwareType::default(),
        },
    };
    let mut response = client
        .create_delay_notify_recovery(
            &query_account_id.to_string(),
            &request,
            app_signed,
            hw_signed,
            &account_keys,
        )
        .await;

    // We expect a verification code to be required if theres a prior contest
    if prior_contest {
        assert_eq!(
            Response {
                status_code: StatusCode::FORBIDDEN,
                body: None,
                body_string: r#"{"errors":[{"category":"AUTHENTICATION_ERROR","code":"COMMS_VERIFICATION_REQUIRED","detail":"Comms verification required"}]}"#.to_string(),
            },
            response,
        );

        // Request the verification code
        let verification_factor = if app_signed { Factor::App } else { Factor::Hw };
        let send_code_response = client
            .send_delay_notify_verification_code(
                &query_account_id.to_string(),
                &SendAccountVerificationCodeRequest { touchpoint_id },
                verification_factor,
            )
            .await;

        assert_eq!(
            send_code_response.status_code, 200,
            "{}",
            response.body_string
        );

        // Submit the code
        let verify_code_response = client
            .verify_delay_notify_verification_code(
                &query_account_id.to_string(),
                &VerifyAccountVerificationCodeRequest {
                    verification_code: TEST_CODE.to_owned(),
                },
                verification_factor,
            )
            .await;

        assert_eq!(
            verify_code_response.status_code, 200,
            "{}",
            response.body_string
        );

        response = client
            .create_delay_notify_recovery(
                &query_account_id.to_string(),
                &request,
                app_signed,
                hw_signed,
                &account_keys,
            )
            .await;
    }

    assert_eq!(
        response.status_code, expected_status,
        "{}",
        response.body_string
    );
    let create_delay_end_time = if let Some(r) = response.body.as_ref() {
        r.pending_delay_notify.delay_end_time
    } else {
        return;
    };

    assert_eq!(
        response,
        client
            .create_delay_notify_recovery(
                &account.id.to_string(),
                &request,
                app_signed,
                hw_signed,
                &account_keys,
            )
            .await,
        "same keys should return same response, idempotency!"
    );

    let request_with_different_keys = CreateAccountDelayNotifyRequest {
        lost_factor,
        delay_period_num_sec: None,
        auth: FullAccountAuthKeysInput {
            app: create_pubkey(),
            hardware: if lost_factor == Factor::App {
                account.hardware_auth_pubkey
            } else {
                create_pubkey()
            },
            recovery: if recovery_has_recovery_key {
                Some(create_pubkey())
            } else {
                None
            },
            hardware_type: HardwareType::default(),
        },
    };
    assert_eq!(
        Response {
            status_code: StatusCode::CONFLICT,
            body: None,
            body_string: r#"{"errors":[{"category":"INVALID_REQUEST_ERROR","code":"RECOVERY_ALREADY_EXISTS","detail":"Unable to start new recovery for Account"}]}"#.to_string(),
        },
        client
            .create_delay_notify_recovery(
                &account.id.to_string(),
                &request_with_different_keys,
                app_signed,
                hw_signed,
                &account_keys,
            )
            .await,
        "idempotency error"
    );

    let response = client
        .get_recovery_status(&query_account_id.to_string())
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let response = response.body.unwrap();

    let fetch_delay_end_time = response.pending_delay_notify.unwrap().delay_end_time;
    assert_eq!(fetch_delay_end_time, create_delay_end_time);
    let delay_period = if create_test_account {
        Duration::seconds(60)
    } else {
        Duration::days(7)
    };
    let expected_delay_end_time = fixed_cur_time + delay_period;
    assert_eq!(fetch_delay_end_time, expected_delay_end_time,);
    assert_eq!(response.active_contest, prior_contest);

    // Check whether the notifications were created
    let mut scheduled_notifications = bootstrap
        .services
        .notification_service
        .fetch_scheduled_for_account(FetchForAccountInput {
            account_id: query_account_id.clone(),
        })
        .await
        .unwrap();
    scheduled_notifications.sort_by_key(|n| n.execution_date_time);
    let scheduled_notifications_types = scheduled_notifications
        .iter()
        .map(|n| n.payload_type)
        .collect::<Vec<NotificationPayloadType>>();

    let mut customer_notifications = bootstrap
        .services
        .notification_service
        .fetch_customer_for_account(FetchForAccountInput {
            account_id: query_account_id,
        })
        .await
        .unwrap();
    customer_notifications.sort_by_key(|n| n.created_at);
    let customer_notifications_types = customer_notifications
        .iter()
        .map(|n| n.payload_type)
        .collect::<HashSet<NotificationPayloadType>>();

    let mut expected_scheduled_notification_types = vec![];

    // The delay has to be greater than 2 days for the pending to get scheduled
    // because the pending schedule gets split into 1 that gets sent immediately
    // and a schedule that starts in 2 days
    if !create_test_account {
        expected_scheduled_notification_types
            .push(NotificationPayloadType::RecoveryPendingDelayPeriod);
    }

    // Two completed schedules
    expected_scheduled_notification_types.extend([
        NotificationPayloadType::RecoveryCompletedDelayPeriod,
        NotificationPayloadType::RecoveryCompletedDelayPeriod,
    ]);

    // One of each type for each channel, account only has 1 touchpoint so we expect only 1
    let mut expected_customer_notification_types =
        HashSet::from([NotificationPayloadType::RecoveryPendingDelayPeriod]);
    if prior_contest {
        expected_customer_notification_types.insert(NotificationPayloadType::CommsVerification);
    }

    assert_eq!(
        scheduled_notifications_types,
        expected_scheduled_notification_types
    );
    assert!(
        // Depending on the timing of the tests, either the completed notification could have been sent out or not
        customer_notifications_types.is_superset(&expected_customer_notification_types)
            && matches!(
                customer_notifications_types
                    .difference(&expected_customer_notification_types)
                    .collect::<Vec<_>>()
                    .as_slice(),
                [] | [NotificationPayloadType::RecoveryCompletedDelayPeriod]
            )
    );
    assert!(scheduled_notifications
        .iter()
        .all(|n| n.account_id == account.id));
    assert!(customer_notifications
        .iter()
        .all(|n| n.account_id == account.id));
}

#[rstest]
#[case::lost_hw_without_recovery_and_prior_contest(
    false,
    Factor::Hw,
    StatusCode::CONFLICT,
    true,
    false,
    false
)]
#[case::lost_hw_without_prior_contest(true, Factor::Hw, StatusCode::OK, true, false, false)]
#[case::lost_app_without_prior_contest(true, Factor::App, StatusCode::OK, false, true, false)]
#[case::no_signers(true, Factor::Hw, StatusCode::FORBIDDEN, false, false, false)]
#[case::contest_without_prior_contest(true, Factor::Hw, StatusCode::OK, false, true, false)]
#[case::cancel_prior_contest(true, Factor::Hw, StatusCode::OK, true, false, true)]
#[case::contest_prior_contest(true, Factor::Hw, StatusCode::OK, false, true, true)]
#[tokio::test]
async fn test_cancel_delay_notify(
    #[case] create_recovery: bool,
    #[case] lost_factor: Factor,
    #[case] expected_status: StatusCode,
    #[case] app_signed: bool,
    #[case] hw_signed: bool,
    #[case] prior_contest: bool,
) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .expect("Invalid keys for account");
    if create_recovery {
        let request = CreateAccountDelayNotifyRequest {
            lost_factor,
            delay_period_num_sec: None,
            auth: FullAccountAuthKeysInput {
                app: create_pubkey(),
                hardware: if lost_factor == Factor::App {
                    account.hardware_auth_pubkey
                } else {
                    create_pubkey()
                },
                recovery: Some(create_pubkey()),
                hardware_type: HardwareType::default(),
            },
        };
        let (app_signed_create, hw_signed_create) = match lost_factor {
            Factor::App => (false, true),
            Factor::Hw => (true, false),
        };
        let response = client
            .create_delay_notify_recovery(
                &account.id.to_string(),
                &request,
                app_signed_create,
                hw_signed_create,
                &keys,
            )
            .await;
        assert_eq!(
            response.status_code,
            StatusCode::OK,
            "{}",
            response.body_string
        );
    }

    let touchpoint_id =
        create_phone_touchpoint(&bootstrap.services, &account.clone().id, true).await;
    if prior_contest {
        // Insert a recent contested recovery
        let prior_recovery = generate_delay_and_notify_recovery(
            account.clone().id,
            RecoveryDestination {
                source_auth_keys_id: account.clone().common_fields.active_auth_keys_id,
                app_auth_pubkey: create_pubkey(),
                hardware_auth_pubkey: account.hardware_auth_pubkey,
                recovery_auth_pubkey: Some(create_pubkey()),
                hardware_type: HardwareType::default(),
            },
            bootstrap.services.recovery_service.cur_time() - Duration::days(15),
            RecoveryStatus::CanceledInContest,
            Factor::App,
        );
        bootstrap
            .services
            .recovery_service
            .create(&prior_recovery)
            .await
            .unwrap();
    }

    let mut response = client
        .cancel_delay_notify_recovery(&account.id.to_string(), app_signed, hw_signed, &keys)
        .await;

    // We expect a verification code to be required if theres a prior contest and
    //   the request wasn't signed by the non-lost factor
    if prior_contest
        && (lost_factor == Factor::App && !hw_signed || lost_factor == Factor::Hw && !app_signed)
    {
        assert_eq!(
            Response {
                status_code: StatusCode::FORBIDDEN,
                body: None,
                body_string: r#"{"errors":[{"category":"AUTHENTICATION_ERROR","code":"COMMS_VERIFICATION_REQUIRED","detail":"Comms verification required"}]}"#.to_string(),
            },
            response,
        );

        // Request the verification code
        let verification_factor = if app_signed { Factor::App } else { Factor::Hw };
        let send_code_response = client
            .send_delay_notify_verification_code(
                &account.id.to_string(),
                &SendAccountVerificationCodeRequest { touchpoint_id },
                verification_factor,
            )
            .await;

        assert_eq!(
            send_code_response.status_code, 200,
            "{}",
            response.body_string
        );

        // Submit the code
        let verify_code_response = client
            .verify_delay_notify_verification_code(
                &account.id.to_string(),
                &VerifyAccountVerificationCodeRequest {
                    verification_code: TEST_CODE.to_owned(),
                },
                verification_factor,
            )
            .await;

        assert_eq!(
            verify_code_response.status_code, 200,
            "{}",
            response.body_string
        );

        response = client
            .cancel_delay_notify_recovery(&account.id.to_string(), app_signed, hw_signed, &keys)
            .await;
    }

    assert_eq!(
        response.status_code, expected_status,
        "{}",
        response.body_string
    );
}

#[rstest]
#[case::lost_app_without_recovery(
    false,
    Factor::App,
    true,
    Duration::days(0),
    StatusCode::CONFLICT,
    false
)]
#[case::lost_hw_without_recovery(
    false,
    Factor::Hw,
    true,
    Duration::days(0),
    StatusCode::CONFLICT,
    false
)]
#[case::lost_app_delay_completed_now(
    true,
    Factor::App,
    true,
    Duration::days(0),
    StatusCode::OK,
    true
)]
#[case::lost_hw_delay_completed_now(
    true,
    Factor::Hw,
    true,
    Duration::days(0),
    StatusCode::OK,
    true
)]
#[case::lost_app_delay_completed_yesterday(true, Factor::App, true, Duration::days(-1), StatusCode::OK, true)]
#[case::lost_app_in_delay_period(
    true,
    Factor::App,
    true,
    Duration::days(1),
    StatusCode::BAD_REQUEST,
    false
)]
#[case::lost_hw_in_delay_period(
    true,
    Factor::Hw,
    true,
    Duration::days(1),
    StatusCode::BAD_REQUEST,
    false
)]
#[case::lost_app_in_delay_period_far(
    true,
    Factor::App,
    true,
    Duration::days(30),
    StatusCode::BAD_REQUEST,
    false
)]
#[tokio::test]
async fn test_complete_delay_notify(
    #[case] create_recovery: bool,
    #[case] lost_factor: Factor,
    #[case] include_recovery_auth_pubkey: bool,
    #[case] offset_delay_end_time_days: Duration,
    #[case] expected_status: StatusCode,
    #[case] expect_key_rotation: bool,
) {
    let (mut context, bootstrap) = gen_services().await;
    let fixed_cur_time = bootstrap.services.recovery_service.cur_time();
    let client = TestClient::new(bootstrap.router).await;

    let (account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;

    // Create some touchpoints to ensure push get cleared if applicable
    create_push_touchpoint(&bootstrap.services, &account.id).await;
    create_phone_touchpoint(&bootstrap.services, &account.id, true).await;

    let account_id = account.id.clone();
    let old_keyset_id = account.active_keyset_id.clone();
    let old_auth_key_id = account.common_fields.active_auth_keys_id.clone();
    let old_hardware_auth_pubkey = account.active_auth_keys().unwrap().hardware_pubkey;

    let offset_dt = fixed_cur_time + offset_delay_end_time_days;
    let (app_auth_seckey, app_auth_pubkey) = create_plain_keys();
    let (hardware_auth_seckey, hardware_auth_pubkey) = create_plain_keys();
    let (_recovery_auth_seckey, recovery_auth_pubkey) = create_plain_keys();
    if create_recovery {
        let recovery = generate_delay_and_notify_recovery(
            account_id.clone(),
            RecoveryDestination {
                source_auth_keys_id: account.common_fields.active_auth_keys_id,
                app_auth_pubkey,
                hardware_auth_pubkey,
                recovery_auth_pubkey: if include_recovery_auth_pubkey {
                    Some(recovery_auth_pubkey)
                } else {
                    None
                },
                hardware_type: HardwareType::default(),
            },
            offset_dt,
            RecoveryStatus::Pending,
            lost_factor,
        );
        bootstrap
            .services
            .recovery_service
            .create(&recovery)
            .await
            .unwrap();
    }

    let account_id_str = account_id.to_owned().to_string();
    let challenge = "CompleteDelayNotify".to_string()
        + &hardware_auth_pubkey.to_string()
        + &app_auth_pubkey.to_string()
        + if include_recovery_auth_pubkey {
            recovery_auth_pubkey.to_string()
        } else {
            "".to_string()
        }
        .as_str();
    let app_signature = sign_message(&Secp256k1::new(), &challenge, &app_auth_seckey);
    let hardware_signature = sign_message(&Secp256k1::new(), &challenge, &hardware_auth_seckey);
    let data = CompleteDelayNotifyRequest {
        challenge,
        app_signature,
        hardware_signature,
    };

    let response = client
        .complete_delay_notify_recovery(&account_id_str, &data)
        .await;
    assert_eq!(
        response.status_code, expected_status,
        "{}",
        response.body_string
    );

    let account = bootstrap
        .services
        .account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .unwrap();
    if expect_key_rotation {
        assert_ne!(
            old_hardware_auth_pubkey,
            account.active_auth_keys().unwrap().hardware_pubkey
        );
        assert_ne!(
            old_hardware_auth_pubkey,
            account
                .auth_keys
                .get(&account.common_fields.active_auth_keys_id)
                .unwrap()
                .hardware_pubkey
        );
        assert_ne!(old_hardware_auth_pubkey, account.hardware_auth_pubkey);
        assert_eq!(hardware_auth_pubkey, account.hardware_auth_pubkey);
        assert_eq!(old_keyset_id, account.active_keyset_id);
        assert_ne!(old_auth_key_id, account.common_fields.active_auth_keys_id);
        assert_eq!(account.common_fields.touchpoints.len(), 1);
    } else {
        assert_eq!(old_hardware_auth_pubkey, account.hardware_auth_pubkey);
        assert_ne!(hardware_auth_pubkey, account.hardware_auth_pubkey);
        assert_eq!(account.common_fields.touchpoints.len(), 2);
    }
}

#[tokio::test]
async fn complete_delay_notify_invalid_account_id() {
    let (_, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (app_auth_seckey, app_auth_pubkey) = create_plain_keys();
    let (hardware_auth_seckey, hardware_auth_pubkey) = create_plain_keys();

    let account_id_str = AccountId::gen().unwrap().to_string();

    let challenge = "CompleteDelayNotify".to_string()
        + &hardware_auth_pubkey.to_string()
        + &app_auth_pubkey.to_string();
    let app_signature = sign_message(&Secp256k1::new(), &challenge, &app_auth_seckey);
    let hardware_signature = sign_message(&Secp256k1::new(), &challenge, &hardware_auth_seckey);
    let data: CompleteDelayNotifyRequest = CompleteDelayNotifyRequest {
        challenge,
        app_signature,
        hardware_signature,
    };

    let response = client
        .complete_delay_notify_recovery(&account_id_str, &data)
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::NOT_FOUND,
        "{}",
        response.body_string
    );
}

#[rstest]
#[case::lost_app_idempotency_success(Factor::App, None, None, None, StatusCode::OK)]
#[case::lost_hw_idempotency_success(Factor::Hw, None, None, None, StatusCode::OK)]
#[case::lost_app_invalid_signature(Factor::App, None, Some("lol".to_string()), None, StatusCode::CONFLICT)]
#[case::lost_hw_invalid_signature(Factor::Hw, None, None, Some("lol".to_string()), StatusCode::CONFLICT)]
#[case::lost_app_idempotency_failure(Factor::App, Some("test".to_string()), None, None, StatusCode::CONFLICT)]
#[case::lost_hw_idempotency_failure(Factor::Hw, Some("test".to_string()), None, None, StatusCode::CONFLICT)]
#[tokio::test]
async fn test_complete_delay_notify_idempotency(
    #[case] lost_factor: Factor,
    #[case] override_challenge: Option<String>,
    #[case] override_app_signature: Option<String>,
    #[case] override_hw_signature: Option<String>,
    #[case] expected_status: StatusCode,
) {
    let (mut context, bootstrap) = gen_services().await;
    let fixed_cur_time = bootstrap.services.recovery_service.cur_time();
    let client = TestClient::new(bootstrap.router).await;

    let (account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let account_id = account.id.clone();

    // Create a pending Recovery to complete
    let (app_auth_seckey, app_auth_pubkey) = create_plain_keys();
    let (hardware_auth_seckey, hardware_auth_pubkey) = create_plain_keys();
    let (_recovery_auth_seckey, recovery_auth_pubkey) = create_plain_keys();
    let recovery = generate_delay_and_notify_recovery(
        account_id.clone(),
        RecoveryDestination {
            source_auth_keys_id: account.common_fields.active_auth_keys_id,
            app_auth_pubkey,
            hardware_auth_pubkey,
            recovery_auth_pubkey: Some(recovery_auth_pubkey),
            hardware_type: HardwareType::default(),
        },
        fixed_cur_time,
        RecoveryStatus::Pending,
        lost_factor,
    );
    bootstrap
        .services
        .recovery_service
        .create(&recovery)
        .await
        .unwrap();

    let account_id_str = account.id.to_string();

    let challenge = "CompleteDelayNotify".to_string()
        + &hardware_auth_pubkey.to_string()
        + &app_auth_pubkey.to_string()
        + &recovery_auth_pubkey.to_string();
    let app_signature = sign_message(&Secp256k1::new(), &challenge, &app_auth_seckey);
    let hardware_signature = sign_message(&Secp256k1::new(), &challenge, &hardware_auth_seckey);
    let data = CompleteDelayNotifyRequest {
        challenge: challenge.clone(),
        app_signature,
        hardware_signature,
    };

    // Call CompleteDelayNotify the first time to rotate the authentication keys
    let response = client
        .complete_delay_notify_recovery(&account_id_str, &data)
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let active_auth_key_id_after_first_completion = bootstrap
        .services
        .account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .expect("Existing account")
        .common_fields
        .active_auth_keys_id;

    // Complete again (this should still work if the request is correct)
    let challenge = override_challenge.unwrap_or(challenge);
    let app_signature = override_app_signature.unwrap_or(sign_message(
        &Secp256k1::new(),
        &challenge,
        &app_auth_seckey,
    ));
    let hardware_signature = override_hw_signature.unwrap_or(sign_message(
        &Secp256k1::new(),
        &challenge,
        &hardware_auth_seckey,
    ));
    let data = CompleteDelayNotifyRequest {
        challenge,
        app_signature,
        hardware_signature,
    };

    let response = client
        .complete_delay_notify_recovery(&account_id_str, &data)
        .await;
    assert_eq!(
        response.status_code, expected_status,
        "{}",
        response.body_string
    );

    let account = bootstrap
        .services
        .account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .expect("Existing account after second CompleteDelayNotify");
    assert_eq!(
        account.common_fields.active_auth_keys_id,
        active_auth_key_id_after_first_completion
    );
}

#[rstest]
#[case::invalid_account_id(AccountId::gen().ok(), false, None, RecoveryStatus::Pending, false, StatusCode::NOT_FOUND)]
#[case::no_delay_notify_recovery(None, false, None, RecoveryStatus::Pending, false, StatusCode::OK)]
#[case::expired_delay_notify_recovery(None, true, Some(Duration::days(-1)), RecoveryStatus::Complete, false, StatusCode::OK)]
#[case::in_period_delay_notify_recovery(
    None,
    true,
    Some(Duration::days(1)),
    RecoveryStatus::Pending,
    true,
    StatusCode::OK
)]
#[case::out_of_period_delay_notify_recovery(None, true, Some(Duration::days(-1)), RecoveryStatus::Pending, true, StatusCode::OK)]
#[tokio::test]
async fn test_get_status_with_delay_notify(
    #[case] override_query_account_id: Option<AccountId>,
    #[case] create_recovery: bool,
    #[case] delay_end_time_offset: Option<Duration>,
    #[case] recovery_status: RecoveryStatus,
    #[case] expected_in_recovery: bool,
    #[case] expected_status: StatusCode,
) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let account_id = account.id;
    let query_account_id = override_query_account_id.unwrap_or(account_id.clone());

    let offset_dt = OffsetDateTime::now_utc();
    let app_auth_pubkey = create_pubkey();
    let recovery_auth_pubkey = Some(create_pubkey());
    if create_recovery {
        let recovery = generate_delay_and_notify_recovery(
            account_id,
            RecoveryDestination {
                source_auth_keys_id: account.common_fields.active_auth_keys_id,
                app_auth_pubkey,
                hardware_auth_pubkey: account.hardware_auth_pubkey,
                recovery_auth_pubkey,
                hardware_type: HardwareType::default(),
            },
            offset_dt + delay_end_time_offset.unwrap(),
            recovery_status,
            Factor::App,
        );
        bootstrap
            .services
            .recovery_service
            .create(&recovery)
            .await
            .unwrap();
    }

    let response = client
        .get_recovery_status(&query_account_id.to_string())
        .await;
    assert_eq!(
        response.status_code, expected_status,
        "{}",
        response.body_string
    );
    let payload = match response.body {
        Some(body) => body,
        None => return,
    };
    assert_eq!(payload.pending_delay_notify.is_some(), expected_in_recovery);
    if expected_in_recovery {
        let r = payload.pending_delay_notify.unwrap();
        let expected_delay_end_time = offset_dt + delay_end_time_offset.unwrap();
        assert_eq!(r.delay_end_time, expected_delay_end_time);
        assert_eq!(r.lost_factor, Factor::App);
        assert_eq!(
            r.auth_keys,
            FullAccountAuthKeysInput {
                app: app_auth_pubkey,
                hardware: account.hardware_auth_pubkey,
                recovery: recovery_auth_pubkey,
                hardware_type: HardwareType::default(),
            }
        );
    }
}

#[tokio::test]
async fn test_create_lost_hw_delay_notify_with_existing_hardware_auth_key() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .expect("Invalid keys for account");
    let hw_authkey = account.active_auth_keys().unwrap().hardware_pubkey;

    let request_with_different_keys = CreateAccountDelayNotifyRequest {
        lost_factor: Factor::Hw,
        delay_period_num_sec: None,
        auth: FullAccountAuthKeysInput {
            app: create_pubkey(),
            hardware: hw_authkey,
            recovery: Some(create_pubkey()),
            hardware_type: HardwareType::default(),
        },
    };

    assert_eq!(
        Response {
            status_code: StatusCode::BAD_REQUEST,
            body: None,
            body_string: r#"{"errors":[{"category":"INVALID_REQUEST_ERROR","code":"HW_AUTH_PUBKEY_IN_USE","detail":"Destination hardware auth pubkey in use by an account"}]}"#.to_string(),
        },
        client
            .create_delay_notify_recovery(
                &account.id.to_string(),
                &request_with_different_keys,
                true,
                false,
                &keys,
            )
            .await,
        "Shouldn't be able to create a DelayNotify when the hardware authentication keys match"
    );
}

#[rstest]
#[case::with_test_account(true, Some(10), StatusCode::OK)]
#[case::without_test_account(false, Some(10), StatusCode::BAD_REQUEST)]
#[case::existing_delay_with_test_account(true, None, StatusCode::OK)]
#[case::existing_delay_without_test_account(false, None, StatusCode::BAD_REQUEST)]
#[tokio::test]
async fn test_update_delay_for_test_recovery(
    #[case] is_test_account: bool,
    #[case] delay_period_num_sec: Option<i64>,
    #[case] expected_status: StatusCode,
) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let network = if is_test_account {
        Network::BitcoinSignet
    } else {
        Network::BitcoinMain
    };
    let account = create_full_account(&mut context, &bootstrap.services, network, None).await;
    let account_id = account.id;
    let keys = context
        .get_authentication_keys_for_account_id(&account_id)
        .expect("Invalid keys for account");

    let offset_dt = OffsetDateTime::now_utc();
    let (app_auth_seckey, app_auth_pubkey) = create_keypair();
    let (recovery_auth_seckey, recovery_auth_pubkey) = create_keypair();
    context.add_authentication_keys(TestAuthenticationKeys {
        app: TestKeypair {
            public_key: app_auth_pubkey,
            secret_key: app_auth_seckey,
        },
        hw: keys.hw.clone(),
        recovery: TestKeypair {
            public_key: recovery_auth_pubkey,
            secret_key: recovery_auth_seckey,
        },
    });
    let recovery = generate_delay_and_notify_recovery(
        account_id.clone(),
        RecoveryDestination {
            source_auth_keys_id: account.common_fields.active_auth_keys_id,
            app_auth_pubkey,
            hardware_auth_pubkey: account.hardware_auth_pubkey,
            recovery_auth_pubkey: Some(recovery_auth_pubkey),
            hardware_type: HardwareType::default(),
        },
        offset_dt,
        RecoveryStatus::Pending,
        Factor::App,
    );
    bootstrap
        .services
        .recovery_service
        .create(&recovery)
        .await
        .unwrap();

    let response = client
        .update_delay_for_test_recovery(
            &account_id.to_string(),
            &UpdateDelayForTestRecoveryRequest {
                delay_period_num_sec,
            },
            true,
            false,
            &keys,
        )
        .await;
    assert_eq!(
        response.status_code, expected_status,
        "{}",
        response.body_string
    );
    let payload = match response.body {
        Some(body) => body,
        None => return,
    };
    let expected_delay_end_time = if let Some(delay_period) = delay_period_num_sec {
        recovery.created_at + Duration::seconds(delay_period)
    } else {
        offset_dt
    };
    assert_eq!(
        payload.pending_delay_notify.delay_end_time,
        expected_delay_end_time
    );

    // TODO: Remove these tests once we don't allow the changing of the delay period in CreateDelayNotify
    let account = create_full_account(&mut context, &bootstrap.services, network, None).await;
    let account_id = account.id;
    let keys = context
        .get_authentication_keys_for_account_id(&account_id)
        .expect("Invalid keys for account");
    let response = client
        .create_delay_notify_recovery(
            &account_id.to_string(),
            &CreateAccountDelayNotifyRequest {
                lost_factor: Factor::Hw,
                delay_period_num_sec,
                auth: FullAccountAuthKeysInput {
                    app: create_pubkey(),
                    hardware: create_pubkey(),
                    recovery: Some(create_pubkey()),
                    hardware_type: HardwareType::default(),
                },
            },
            true,
            false,
            &keys,
        )
        .await;
    assert_eq!(
        response.status_code, expected_status,
        "{}",
        response.body_string
    );
    let payload = match response.body {
        Some(body) => body,
        None => return,
    };

    let recovery = bootstrap
        .services
        .recovery_service
        .fetch_pending(&account_id, RecoveryType::DelayAndNotify)
        .await
        .unwrap()
        .expect("Existing Recovery for Account");
    let expected_delay_end_time = if let Some(delay_period) = delay_period_num_sec {
        recovery.created_at + Duration::seconds(delay_period)
    } else {
        recovery.created_at + Duration::seconds(60)
    };
    assert_eq!(
        payload.pending_delay_notify.delay_end_time,
        expected_delay_end_time
    );
}

#[rstest]
#[case::fourteen_days(14, 2 * 60)]
#[case::thirty_days(30, 3 * 60)]
#[tokio::test]
async fn test_create_delay_notify_uses_configured_period_for_test_account(
    #[case] delay_period_days: u32,
    #[case] expected_delay_secs: i64,
) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let account_id = account.id;
    let keys = context
        .get_authentication_keys_for_account_id(&account_id)
        .expect("Invalid keys for account");

    bootstrap
        .services
        .privileged_action_service
        .configure_delay_notify_period(ConfigureDelayNotifyPeriodInput {
            account_id: &account_id,
            delay_period_days,
        })
        .await
        .unwrap();

    let response = client
        .create_delay_notify_recovery(
            &account_id.to_string(),
            &CreateAccountDelayNotifyRequest {
                lost_factor: Factor::Hw,
                delay_period_num_sec: Some(20),
                auth: FullAccountAuthKeysInput {
                    app: create_pubkey(),
                    hardware: create_pubkey(),
                    recovery: Some(create_pubkey()),
                    hardware_type: HardwareType::default(),
                },
            },
            true,
            false,
            &keys,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let payload = response.body.expect("Expected pending recovery response");

    let recovery = bootstrap
        .services
        .recovery_service
        .fetch_pending(&account_id, RecoveryType::DelayAndNotify)
        .await
        .unwrap()
        .expect("Existing Recovery for Account");
    assert_eq!(
        payload.pending_delay_notify.delay_end_time,
        recovery.created_at + Duration::seconds(expected_delay_secs)
    );
}

#[tokio::test]
async fn test_get_and_configure_delay_notify_period_routes() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let account_id = account.id;
    let keys = context
        .get_authentication_keys_for_account_id(&account_id)
        .expect("Invalid keys for account");

    let get_response = client
        .get_delay_notify_period(&account_id.to_string())
        .await;
    assert_eq!(
        get_response.status_code,
        StatusCode::OK,
        "{}",
        get_response.body_string
    );
    assert_eq!(
        get_response
            .body
            .expect("Expected delay notify period response")
            .delay_period_days,
        7
    );

    let put_response = client
        .configure_delay_notify_period(
            &account_id.to_string(),
            &ConfigureDelayNotifyPeriodRequest {
                delay_period_days: 14,
            },
            true,
            true,
            &keys,
        )
        .await;
    assert_eq!(
        put_response.status_code,
        StatusCode::OK,
        "{}",
        put_response.body_string
    );
    assert_eq!(
        put_response
            .body
            .expect("Expected configure delay notify period response")
            .delay_period_days,
        14
    );

    let get_response = client
        .get_delay_notify_period(&account_id.to_string())
        .await;
    assert_eq!(
        get_response.status_code,
        StatusCode::OK,
        "{}",
        get_response.body_string
    );
    assert_eq!(
        get_response
            .body
            .expect("Expected delay notify period response")
            .delay_period_days,
        14
    );

    let account = bootstrap
        .services
        .account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .unwrap();
    assert_eq!(
        account.common_fields.delay_notify_period_secs,
        Some(14 * ONE_DAY_SECS)
    );
    assert!(account
        .common_fields
        .configured_privileged_action_delay_durations
        .iter()
        .any(
            |duration| duration.privileged_action_type == PrivilegedActionType::ResetFingerprint
                && duration.delay_duration_secs == 14 * ONE_DAY_SECS
        ));
}

#[tokio::test]
async fn test_configure_delay_notify_period_route_accepts_w3_action_proof() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let (account_id, keys) = create_onboarded_w3_account(&mut context, &client).await;

    let response = client
        .configure_delay_notify_period_with_action_proof(
            &account_id,
            &ConfigureDelayNotifyPeriodRequest {
                delay_period_days: 30,
            },
            &keys,
            true,
            true,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    assert_eq!(
        response
            .body
            .expect("Expected configure delay notify period response")
            .delay_period_days,
        30
    );

    let account_id = account_id.parse::<AccountId>().unwrap();
    let account = bootstrap
        .services
        .account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .unwrap();
    assert_eq!(
        account.common_fields.delay_notify_period_secs,
        Some(30 * ONE_DAY_SECS)
    );
}

#[rstest]
#[case::too_short(1)]
#[case::unsupported_period(21)]
#[case::too_long(365)]
#[tokio::test]
async fn test_configure_delay_notify_period_rejects_invalid_days(#[case] delay_period_days: u32) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let account_id = account.id;
    let keys = context
        .get_authentication_keys_for_account_id(&account_id)
        .expect("Invalid keys for account");

    let response = client
        .configure_delay_notify_period_with_action_proof(
            &account_id.to_string(),
            &ConfigureDelayNotifyPeriodRequest { delay_period_days },
            &keys,
            true,
            true,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::BAD_REQUEST,
        "{}",
        response.body_string
    );
}

#[tokio::test]
async fn test_configure_delay_notify_period_rejects_pending_recovery() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let account_id = account.id;
    let keys = context
        .get_authentication_keys_for_account_id(&account_id)
        .expect("Invalid keys for account");

    let create_response = client
        .create_delay_notify_recovery(
            &account_id.to_string(),
            &CreateAccountDelayNotifyRequest {
                lost_factor: Factor::Hw,
                delay_period_num_sec: None,
                auth: FullAccountAuthKeysInput {
                    app: create_pubkey(),
                    hardware: create_pubkey(),
                    recovery: Some(create_pubkey()),
                    hardware_type: HardwareType::default(),
                },
            },
            true,
            false,
            &keys,
        )
        .await;
    assert_eq!(
        create_response.status_code,
        StatusCode::OK,
        "{}",
        create_response.body_string
    );

    let response = client
        .configure_delay_notify_period_with_action_proof(
            &account_id.to_string(),
            &ConfigureDelayNotifyPeriodRequest {
                delay_period_days: 30,
            },
            &keys,
            true,
            true,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::CONFLICT,
        "{}",
        response.body_string
    );
}

#[tokio::test]
async fn test_configure_delay_notify_period_rejects_pending_privileged_action() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let account_id = account.id;
    let keys = context
        .get_authentication_keys_for_account_id(&account_id)
        .expect("Invalid keys for account");
    let privileged_action_instance_id = PrivilegedActionInstanceId::gen().unwrap();
    bootstrap
        .services
        .privileged_action_repository
        .persist(&PrivilegedActionInstanceRecord {
            id: privileged_action_instance_id,
            account_id: account_id.clone(),
            privileged_action_type: PrivilegedActionType::ResetFingerprint,
            authorization_strategy: AuthorizationStrategyRecord::DelayAndNotify(
                DelayAndNotifyRecord {
                    status: RecordStatus::Pending,
                    cancellation_token: "cancel-token".to_string(),
                    completion_token: "complete-token".to_string(),
                    delay_end_time: OffsetDateTime::now_utc() + Duration::days(14),
                },
            ),
            request: serde_json::json!({ "test": "pending reset fingerprint" }),
            created_at: OffsetDateTime::now_utc(),
            updated_at: OffsetDateTime::now_utc(),
        })
        .await
        .expect("Failed to persist privileged action instance");

    let response = client
        .configure_delay_notify_period_with_action_proof(
            &account_id.to_string(),
            &ConfigureDelayNotifyPeriodRequest {
                delay_period_days: 30,
            },
            &keys,
            true,
            true,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::CONFLICT,
        "{}",
        response.body_string
    );
}

#[derive(Debug)]
enum AuthKeyReuse {
    MyAccountApp,
    MyAccountHw,
    MyAccountRecovery,
    OtherAccountApp,
    OtherAccountHw,
    OtherAccountRecovery,
    OtherRecoveryApp,
    OtherRecoveryHw,
    OtherRecoveryRecovery,
}

#[rstest]
#[case::lost_app_reuse_my_account_app(Factor::App, AuthKeyReuse::MyAccountApp, Some(RecoveryError::AppAuthPubkeyReuseAccount.into()))]
#[case::lost_hw_reuse_my_account_app(Factor::Hw, AuthKeyReuse::MyAccountApp, Some(RecoveryError::AppAuthPubkeyReuseAccount.into()))]
#[case::lost_app_reuse_my_account_hw(Factor::App, AuthKeyReuse::MyAccountHw, None)]
#[case::lost_hw_reuse_my_account_hw(Factor::Hw, AuthKeyReuse::MyAccountHw, Some(RecoveryError::HwAuthPubkeyReuseAccount.into()))]
#[case::lost_app_reuse_my_account_recovery(Factor::App, AuthKeyReuse::MyAccountRecovery, Some(RecoveryError::RecoveryAuthPubkeyReuseAccount.into()))]
#[case::lost_hw_reuse_my_account_recovery(Factor::Hw, AuthKeyReuse::MyAccountRecovery, Some(RecoveryError::RecoveryAuthPubkeyReuseAccount.into()))]
#[case::lost_app_reuse_other_account_app(Factor::App, AuthKeyReuse::OtherAccountApp, Some(RecoveryError::AppAuthPubkeyReuseAccount.into()))]
#[case::lost_hw_reuse_other_account_app(Factor::Hw, AuthKeyReuse::OtherAccountApp, Some(RecoveryError::AppAuthPubkeyReuseAccount.into()))]
#[case::lost_app_reuse_other_account_hw(Factor::App, AuthKeyReuse::OtherAccountHw, Some(RecoveryError::InvalidRecoveryDestination.into()))]
#[case::lost_hw_reuse_other_account_hw(Factor::Hw, AuthKeyReuse::OtherAccountHw, Some(RecoveryError::HwAuthPubkeyReuseAccount.into()))]
#[case::lost_app_reuse_other_account_recovery(Factor::App, AuthKeyReuse::OtherAccountRecovery, Some(RecoveryError::RecoveryAuthPubkeyReuseAccount.into()))]
#[case::lost_hw_reuse_other_account_recovery(Factor::Hw, AuthKeyReuse::OtherAccountRecovery, Some(RecoveryError::RecoveryAuthPubkeyReuseAccount.into()))]
#[case::lost_app_reuse_other_recovery_app(Factor::App, AuthKeyReuse::OtherRecoveryApp, Some(RecoveryError::AppAuthPubkeyReuseRecovery.into()))]
#[case::lost_hw_reuse_other_recovery_app(Factor::Hw, AuthKeyReuse::OtherRecoveryApp, Some(RecoveryError::AppAuthPubkeyReuseRecovery.into()))]
#[case::lost_app_reuse_other_recovery_hw(Factor::App, AuthKeyReuse::OtherRecoveryHw, Some(RecoveryError::InvalidRecoveryDestination.into()))]
#[case::lost_hw_reuse_other_recovery_hw(Factor::Hw, AuthKeyReuse::OtherRecoveryHw, Some(RecoveryError::HwAuthPubkeyReuseRecovery.into()))]
#[case::lost_app_reuse_other_recovery_recovery(Factor::App, AuthKeyReuse::OtherRecoveryRecovery, Some(RecoveryError::RecoveryAuthPubkeyReuseRecovery.into()))]
#[case::lost_hw_reuse_other_recovery_recovery(Factor::Hw, AuthKeyReuse::OtherRecoveryRecovery, Some(RecoveryError::RecoveryAuthPubkeyReuseRecovery.into()))]
#[tokio::test]
async fn test_reuse_auth_pubkey(
    #[case] lost_factor: Factor,
    #[case] auth_key_reuse: AuthKeyReuse,
    #[case] expected_error: Option<ApiError>,
) {
    let (mut context, bootstrap) = gen_services().await;
    let fixed_cur_time = bootstrap.services.recovery_service.cur_time();
    let client = TestClient::new(bootstrap.router).await;

    let other_account_keys = create_new_authkeys(&mut context);
    let other_account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        Some(FullAccountAuthKeys {
            app_pubkey: other_account_keys.app.public_key,
            hardware_pubkey: other_account_keys.hw.public_key,
            recovery_pubkey: Some(other_account_keys.recovery.public_key),
            hardware_type: HardwareType::default(),
        }),
    )
    .await;

    let other_recovery_keys = create_new_authkeys(&mut context);
    let other_recovery = generate_delay_and_notify_recovery(
        other_account.clone().id,
        RecoveryDestination {
            source_auth_keys_id: other_account.common_fields.active_auth_keys_id,
            app_auth_pubkey: other_recovery_keys.app.public_key,
            hardware_auth_pubkey: other_recovery_keys.hw.public_key,
            recovery_auth_pubkey: Some(other_recovery_keys.recovery.public_key),
            hardware_type: HardwareType::default(),
        },
        fixed_cur_time + Duration::days(7),
        RecoveryStatus::Pending,
        Factor::Hw,
    );
    bootstrap
        .services
        .recovery_service
        .create(&other_recovery)
        .await
        .unwrap();

    let account = create_full_account(
        &mut context,
        &bootstrap.services,
        Network::BitcoinSignet,
        None,
    )
    .await;
    let account_keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .expect("Invalid keys for account");

    let (recovery_app_pubkey, recovery_hardware_pubkey, recovery_recover_pubkey) =
        match auth_key_reuse {
            AuthKeyReuse::MyAccountApp => (
                account.application_auth_pubkey.unwrap(),
                if lost_factor == Factor::Hw {
                    create_pubkey()
                } else {
                    account.hardware_auth_pubkey
                },
                Some(create_pubkey()),
            ),
            AuthKeyReuse::MyAccountHw => (
                create_pubkey(),
                account.hardware_auth_pubkey,
                Some(create_pubkey()),
            ),
            AuthKeyReuse::MyAccountRecovery => (
                create_pubkey(),
                if lost_factor == Factor::Hw {
                    create_pubkey()
                } else {
                    account.hardware_auth_pubkey
                },
                account.common_fields.recovery_auth_pubkey,
            ),
            AuthKeyReuse::OtherAccountApp => (
                other_account.application_auth_pubkey.unwrap(),
                if lost_factor == Factor::Hw {
                    create_pubkey()
                } else {
                    account.hardware_auth_pubkey
                },
                Some(create_pubkey()),
            ),
            AuthKeyReuse::OtherAccountHw => (
                create_pubkey(),
                other_account.hardware_auth_pubkey,
                Some(create_pubkey()),
            ),
            AuthKeyReuse::OtherAccountRecovery => (
                create_pubkey(),
                if lost_factor == Factor::Hw {
                    create_pubkey()
                } else {
                    account.hardware_auth_pubkey
                },
                other_account.common_fields.recovery_auth_pubkey,
            ),
            AuthKeyReuse::OtherRecoveryApp => (
                other_recovery.destination_app_auth_pubkey.unwrap(),
                if lost_factor == Factor::Hw {
                    create_pubkey()
                } else {
                    account.hardware_auth_pubkey
                },
                Some(create_pubkey()),
            ),
            AuthKeyReuse::OtherRecoveryHw => (
                create_pubkey(),
                other_recovery.destination_hardware_auth_pubkey.unwrap(),
                Some(create_pubkey()),
            ),
            AuthKeyReuse::OtherRecoveryRecovery => (
                create_pubkey(),
                if lost_factor == Factor::Hw {
                    create_pubkey()
                } else {
                    account.hardware_auth_pubkey
                },
                other_recovery.destination_recovery_auth_pubkey,
            ),
        };

    let response = client
        .create_delay_notify_recovery(
            &account.id.to_string(),
            &CreateAccountDelayNotifyRequest {
                lost_factor,
                delay_period_num_sec: Some(Duration::days(7).as_seconds_f32() as i64),
                auth: FullAccountAuthKeysInput {
                    app: recovery_app_pubkey,
                    hardware: recovery_hardware_pubkey,
                    recovery: recovery_recover_pubkey,
                    hardware_type: HardwareType::default(),
                },
            },
            lost_factor == Factor::Hw,
            lost_factor == Factor::App,
            &account_keys,
        )
        .await;

    if let Some(expected_error) = expected_error {
        let expected_response = expected_error.into_response();
        assert_eq!(
            Response {
                status_code: expected_response.status(),
                body: None,
                body_string: String::from_utf8(
                    expected_response
                        .collect()
                        .await
                        .unwrap()
                        .to_bytes()
                        .to_vec()
                )
                .unwrap(),
            },
            response,
        );
    } else {
        assert_eq!(StatusCode::OK, response.status_code);
    }
}

// ---- W3 ActionProof tests for migrated recovery routes ----

#[tokio::test]
async fn w3_create_delay_notify_with_action_proof_succeeds() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keys) = create_onboarded_w3_account(&mut context, &client).await;

    let new_keys = create_new_authkeys(&mut context);
    let response = client
        .create_delay_notify_recovery_with_action_proof(
            &account_id,
            &CreateAccountDelayNotifyRequest {
                lost_factor: Factor::Hw,
                delay_period_num_sec: None,
                auth: FullAccountAuthKeysInput {
                    app: new_keys.app.public_key,
                    hardware: new_keys.hw.public_key,
                    recovery: Some(new_keys.recovery.public_key),
                    hardware_type: HardwareType::default(),
                },
            },
            &keys,
            true,  // sign_with_app (the non-lost factor)
            false, // sign_with_hw (the lost factor)
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "W3 create_delay_notify with ActionProof should succeed"
    );
}

#[tokio::test]
async fn w3_create_delay_notify_rejects_keyclaims() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keys) = create_onboarded_w3_account(&mut context, &client).await;

    let new_keys = create_new_authkeys(&mut context);
    let response = client
        .create_delay_notify_recovery(
            &account_id,
            &CreateAccountDelayNotifyRequest {
                lost_factor: Factor::Hw,
                delay_period_num_sec: None,
                auth: FullAccountAuthKeysInput {
                    app: new_keys.app.public_key,
                    hardware: new_keys.hw.public_key,
                    recovery: Some(new_keys.recovery.public_key),
                    hardware_type: HardwareType::default(),
                },
            },
            true,  // app_signed (KeyClaims)
            false, // hw_signed (KeyClaims)
            &keys,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::FORBIDDEN,
        "W3 create_delay_notify with KeyClaims should be rejected"
    );
}

#[tokio::test]
async fn w3_cancel_delay_notify_with_action_proof_succeeds() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keys) = create_onboarded_w3_account(&mut context, &client).await;

    // First, create a recovery using ActionProof
    let new_keys = create_new_authkeys(&mut context);
    let create_response = client
        .create_delay_notify_recovery_with_action_proof(
            &account_id,
            &CreateAccountDelayNotifyRequest {
                lost_factor: Factor::Hw,
                delay_period_num_sec: None,
                auth: FullAccountAuthKeysInput {
                    app: new_keys.app.public_key,
                    hardware: new_keys.hw.public_key,
                    recovery: Some(new_keys.recovery.public_key),
                    hardware_type: HardwareType::default(),
                },
            },
            &keys,
            true,
            false,
        )
        .await;
    assert_eq!(create_response.status_code, StatusCode::OK);

    // Cancel using ActionProof
    let response = client
        .cancel_delay_notify_recovery_with_action_proof(
            &account_id,
            Factor::Hw,
            &keys,
            true,  // sign_with_app
            false, // sign_with_hw (lost factor)
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "W3 cancel_delay_notify with ActionProof should succeed"
    );
}

#[tokio::test]
async fn w3_create_delay_notify_lost_app_with_action_proof_succeeds() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keys) = create_onboarded_w3_account(&mut context, &client).await;

    let new_keys = create_new_authkeys(&mut context);
    let response = client
        .create_delay_notify_recovery_with_action_proof(
            &account_id,
            &CreateAccountDelayNotifyRequest {
                lost_factor: Factor::App,
                delay_period_num_sec: None,
                auth: FullAccountAuthKeysInput {
                    app: new_keys.app.public_key,
                    hardware: keys.hw.public_key,
                    recovery: Some(new_keys.recovery.public_key),
                    hardware_type: HardwareType::default(),
                },
            },
            &keys,
            false, // sign_with_app (lost factor)
            true,  // sign_with_hw (the non-lost factor)
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "W3 create_delay_notify lost app with ActionProof should succeed: {}",
        response.body_string
    );
}

#[tokio::test]
async fn w3_cancel_delay_notify_lost_app_with_action_proof_succeeds() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keys) = create_onboarded_w3_account(&mut context, &client).await;

    // Create a lost-app recovery
    let new_keys = create_new_authkeys(&mut context);
    let create_response = client
        .create_delay_notify_recovery_with_action_proof(
            &account_id,
            &CreateAccountDelayNotifyRequest {
                lost_factor: Factor::App,
                delay_period_num_sec: None,
                auth: FullAccountAuthKeysInput {
                    app: new_keys.app.public_key,
                    hardware: keys.hw.public_key,
                    recovery: Some(new_keys.recovery.public_key),
                    hardware_type: HardwareType::default(),
                },
            },
            &keys,
            false,
            true,
        )
        .await;
    assert_eq!(create_response.status_code, StatusCode::OK);

    // Cancel using ActionProof
    let response = client
        .cancel_delay_notify_recovery_with_action_proof(
            &account_id,
            Factor::App,
            &keys,
            false, // sign_with_app (lost factor)
            true,  // sign_with_hw (the non-lost factor)
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "W3 cancel_delay_notify lost app with ActionProof should succeed: {}",
        response.body_string
    );
}

#[tokio::test]
async fn w3_cancel_delay_notify_no_active_recovery_returns_conflict() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keys) = create_onboarded_w3_account(&mut context, &client).await;

    // Cancel without ever creating a recovery — should get 409 Conflict.
    // Factor::Hw is arbitrary here; the route errors before checking the action proof.
    let response = client
        .cancel_delay_notify_recovery_with_action_proof(&account_id, Factor::Hw, &keys, true, false)
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::CONFLICT,
        "W3 cancel with no active recovery should return 409: {}",
        response.body_string
    );
}

#[tokio::test]
async fn w3_cancel_delay_notify_wrong_factor_action_proof_rejected() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keys) = create_onboarded_w3_account(&mut context, &client).await;

    // Create a lost-hw recovery
    let new_keys = create_new_authkeys(&mut context);
    let create_response = client
        .create_delay_notify_recovery_with_action_proof(
            &account_id,
            &CreateAccountDelayNotifyRequest {
                lost_factor: Factor::Hw,
                delay_period_num_sec: None,
                auth: FullAccountAuthKeysInput {
                    app: new_keys.app.public_key,
                    hardware: new_keys.hw.public_key,
                    recovery: Some(new_keys.recovery.public_key),
                    hardware_type: HardwareType::default(),
                },
            },
            &keys,
            true,
            false,
        )
        .await;
    assert_eq!(create_response.status_code, StatusCode::OK);

    // Cancel with CancelLostAppRecovery action proof (wrong — recovery is lost-hw).
    // The server determines the correct action from DB, so the mismatch is rejected.
    let response = client
        .cancel_delay_notify_recovery_with_explicit_action(
            &account_id,
            action_proof::Action::CancelLostAppRecovery,
            &keys,
            false,
            true,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::FORBIDDEN,
        "Cancel with wrong factor action proof should be rejected: {}",
        response.body_string
    );
}

#[tokio::test]
async fn w3_create_delay_notify_wrong_factor_action_proof_rejected() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keys) = create_onboarded_w3_account(&mut context, &client).await;

    // Request says lost_factor: Hw, but sign with CreateLostAppRecovery action proof
    let new_keys = create_new_authkeys(&mut context);
    let response = client
        .create_delay_notify_recovery_with_explicit_action(
            &account_id,
            &CreateAccountDelayNotifyRequest {
                lost_factor: Factor::Hw,
                delay_period_num_sec: None,
                auth: FullAccountAuthKeysInput {
                    app: new_keys.app.public_key,
                    hardware: new_keys.hw.public_key,
                    recovery: Some(new_keys.recovery.public_key),
                    hardware_type: HardwareType::default(),
                },
            },
            action_proof::Action::CreateLostAppRecovery, // wrong factor
            &keys,
            true,  // sign_with_app
            false, // sign_with_hw
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::FORBIDDEN,
        "Create with wrong factor action proof should be rejected: {}",
        response.body_string
    );
}

#[tokio::test]
async fn w3_cancel_delay_notify_rejects_keyclaims() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keys) = create_onboarded_w3_account(&mut context, &client).await;

    // First, create a recovery using ActionProof
    let new_keys = create_new_authkeys(&mut context);
    let create_response = client
        .create_delay_notify_recovery_with_action_proof(
            &account_id,
            &CreateAccountDelayNotifyRequest {
                lost_factor: Factor::Hw,
                delay_period_num_sec: None,
                auth: FullAccountAuthKeysInput {
                    app: new_keys.app.public_key,
                    hardware: new_keys.hw.public_key,
                    recovery: Some(new_keys.recovery.public_key),
                    hardware_type: HardwareType::default(),
                },
            },
            &keys,
            true,
            false,
        )
        .await;
    assert_eq!(create_response.status_code, StatusCode::OK);

    // Cancel with KeyClaims should be rejected for W3
    let response = client
        .cancel_delay_notify_recovery(
            &account_id,
            true,  // app_signed
            false, // hw_signed (lost factor)
            &keys,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::FORBIDDEN,
        "W3 cancel_delay_notify with KeyClaims should be rejected"
    );
}

#[rstest]
#[case::app_factor(Factor::Hw, Factor::App)]
#[case::hw_factor(Factor::App, Factor::Hw)]
#[tokio::test]
async fn send_and_verify_code_succeeds(
    #[case] lost_factor: Factor,
    #[case] verification_factor: Factor,
) {
    let (mut context, bootstrap) = gen_services().await;
    let fixed_cur_time = bootstrap.services.recovery_service.cur_time();
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keys) = create_onboarded_w3_account(&mut context, &client).await;
    let account_id_parsed: AccountId = account_id.parse().unwrap();

    // Set up phone touchpoint (required for comms verification)
    let touchpoint_id =
        create_phone_touchpoint(&bootstrap.services, &account_id_parsed, true).await;

    // Fetch the full account to get fields needed for generating a prior recovery
    let full_account = bootstrap
        .services
        .account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id_parsed,
        })
        .await
        .unwrap();

    // Insert a prior contested recovery to trigger comms verification requirement
    let prior_recovery = generate_delay_and_notify_recovery(
        account_id_parsed.clone(),
        RecoveryDestination {
            source_auth_keys_id: full_account.common_fields.active_auth_keys_id.clone(),
            app_auth_pubkey: create_pubkey(),
            hardware_auth_pubkey: full_account.active_auth_keys().unwrap().hardware_pubkey,
            recovery_auth_pubkey: Some(create_pubkey()),
            hardware_type: HardwareType::default(),
        },
        fixed_cur_time - Duration::days(15),
        RecoveryStatus::CanceledInContest,
        lost_factor,
    );
    bootstrap
        .services
        .recovery_service
        .create(&prior_recovery)
        .await
        .unwrap();

    // Attempt D&N recovery — should require comms verification due to prior contest
    let sign_with_app = verification_factor == Factor::App;
    let sign_with_hw = verification_factor == Factor::Hw;
    let new_keys = create_new_authkeys(&mut context);
    let dn_request = CreateAccountDelayNotifyRequest {
        lost_factor,
        delay_period_num_sec: None,
        auth: FullAccountAuthKeysInput {
            app: new_keys.app.public_key,
            hardware: if lost_factor == Factor::App {
                full_account.active_auth_keys().unwrap().hardware_pubkey
            } else {
                new_keys.hw.public_key
            },
            recovery: Some(new_keys.recovery.public_key),
            hardware_type: HardwareType::default(),
        },
    };
    let response = client
        .create_delay_notify_recovery_with_action_proof(
            &account_id,
            &dn_request,
            &keys,
            sign_with_app,
            sign_with_hw,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::FORBIDDEN,
        "D&N with prior contest should require comms verification"
    );

    // Send verification code as the surviving factor
    let send_response = client
        .send_delay_notify_verification_code(
            &account_id,
            &SendAccountVerificationCodeRequest { touchpoint_id },
            verification_factor,
        )
        .await;
    assert_eq!(
        send_response.status_code,
        StatusCode::OK,
        "send_verification_code should succeed"
    );

    // Verify the code
    let verify_response = client
        .verify_delay_notify_verification_code(
            &account_id,
            &VerifyAccountVerificationCodeRequest {
                verification_code: TEST_CODE.to_owned(),
            },
            verification_factor,
        )
        .await;
    assert_eq!(
        verify_response.status_code,
        StatusCode::OK,
        "verify_code should succeed"
    );

    // Now the D&N recovery should succeed
    let retry_response = client
        .create_delay_notify_recovery_with_action_proof(
            &account_id,
            &dn_request,
            &keys,
            sign_with_app,
            sign_with_hw,
        )
        .await;
    assert_eq!(
        retry_response.status_code,
        StatusCode::OK,
        "D&N should succeed after comms verification"
    );
}
