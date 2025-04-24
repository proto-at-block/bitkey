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
use recovery::entities::{RecoveryDestination, RecoveryStatus, RecoveryType};
use recovery::error::RecoveryError;
use recovery::routes::delay_notify::{
    CompleteDelayNotifyRequest, CreateAccountDelayNotifyRequest,
    SendAccountVerificationCodeRequest, UpdateDelayForTestRecoveryRequest,
    VerifyAccountVerificationCodeRequest,
};
use time::{Duration, OffsetDateTime};
use types::account::bitcoin::Network;
use types::account::entities::{Factor, FullAccountAuthKeysPayload};
use types::account::identifiers::AccountId;
use types::account::keys::FullAccountAuthKeys;

use crate::tests;
use crate::tests::gen_services;
use crate::tests::lib::{
    create_auth_keyset_model, create_default_account_with_predefined_wallet, create_full_account,
    create_keypair, create_new_authkeys, create_phone_touchpoint, create_plain_keys, create_pubkey,
    create_push_touchpoint, generate_delay_and_notify_recovery,
};
use crate::tests::requests::axum::TestClient;
use crate::tests::requests::Response;

#[derive(Debug)]
struct CreateDelayNotifyTestVector {
    override_account_id: Option<AccountId>,
    lost_factor: Factor,
    include_recovery_auth_pubkey: (bool, bool),
    create_test_account: bool,
    expected_status: StatusCode,
    app_signed: bool,
    hw_signed: bool,
    prior_contest: bool,
}

async fn create_delay_notify_test(vector: CreateDelayNotifyTestVector) {
    let (mut context, bootstrap) = gen_services().await;
    let fixed_cur_time = bootstrap.services.recovery_service.cur_time();
    let client = TestClient::new(bootstrap.router).await;

    let network = match vector.create_test_account {
        true => Network::BitcoinSignet,
        false => Network::BitcoinMain,
    };

    let (account_has_recovery_key, recovery_has_recovery_key) = vector.include_recovery_auth_pubkey;

    let mut keys = create_auth_keyset_model(&mut context);
    if !account_has_recovery_key {
        keys.recovery_pubkey = None;
    }

    let account = create_full_account(&mut context, &bootstrap.services, network, Some(keys)).await;
    let account_keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .expect("Invalid keys for account");
    let query_account_id = vector.override_account_id.unwrap_or(account.clone().id);

    let touchpoint_id =
        create_phone_touchpoint(&bootstrap.services, &account.clone().id, true).await;
    if vector.prior_contest {
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
        lost_factor: vector.lost_factor,
        delay_period_num_sec: None,
        auth: FullAccountAuthKeysPayload {
            app: create_pubkey(),
            hardware: if vector.lost_factor == Factor::App {
                account.hardware_auth_pubkey
            } else {
                create_pubkey()
            },
            recovery: if recovery_has_recovery_key {
                Some(create_pubkey())
            } else {
                None
            },
        },
    };
    let mut response = client
        .create_delay_notify_recovery(
            &query_account_id.to_string(),
            &request,
            vector.app_signed,
            vector.hw_signed,
            &account_keys,
        )
        .await;

    // We expect a verification code to be required if theres a prior contest
    if vector.prior_contest {
        assert_eq!(
            Response {
                status_code: StatusCode::FORBIDDEN,
                body: None,
                body_string: r#"{"errors":[{"category":"AUTHENTICATION_ERROR","code":"COMMS_VERIFICATION_REQUIRED","detail":"Comms verification required"}]}"#.to_string(),
            },
            response,
        );

        // Request the verification code
        let send_code_response = client
            .send_delay_notify_verification_code(
                &query_account_id.to_string(),
                &SendAccountVerificationCodeRequest { touchpoint_id },
                vector.app_signed,
                vector.hw_signed,
                &account_keys,
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
                vector.app_signed,
                vector.hw_signed,
                &account_keys,
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
                vector.app_signed,
                vector.hw_signed,
                &account_keys,
            )
            .await;
    }

    assert_eq!(
        response.status_code, vector.expected_status,
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
                vector.app_signed,
                vector.hw_signed,
                &account_keys,
            )
            .await,
        "same keys should return same response, idempotency!"
    );

    let request_with_different_keys = CreateAccountDelayNotifyRequest {
        lost_factor: vector.lost_factor,
        delay_period_num_sec: None,
        auth: FullAccountAuthKeysPayload {
            app: create_pubkey(),
            hardware: if vector.lost_factor == Factor::App {
                account.hardware_auth_pubkey
            } else {
                create_pubkey()
            },
            recovery: if recovery_has_recovery_key {
                Some(create_pubkey())
            } else {
                None
            },
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
                vector.app_signed,
                vector.hw_signed,
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
    let delay_period = if vector.create_test_account {
        Duration::seconds(20)
    } else {
        Duration::days(7)
    };
    let expected_delay_end_time = fixed_cur_time + delay_period;
    assert_eq!(fetch_delay_end_time, expected_delay_end_time,);
    assert_eq!(response.active_contest, vector.prior_contest);

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
    if !vector.create_test_account {
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
    if vector.prior_contest {
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

tests! {
    runner = create_delay_notify_test,
    test_create_delay_notify_with_invalid_wallet_id: CreateDelayNotifyTestVector {
        override_account_id: AccountId::gen().ok(),
        lost_factor: Factor::Hw,
        include_recovery_auth_pubkey: (true, true),
        create_test_account: true,
        expected_status: StatusCode::NOT_FOUND,
        app_signed: true,
        hw_signed: false,
        prior_contest: false,
    },
    test_create_delay_notify: CreateDelayNotifyTestVector {
        override_account_id: None,
        lost_factor: Factor::Hw,
        include_recovery_auth_pubkey: (true, true),
        create_test_account: true,
        expected_status: StatusCode::OK,
        app_signed: true,
        hw_signed: false,
        prior_contest: false,
    },
    test_create_delay_notify_without_recovery_auth_pubkey: CreateDelayNotifyTestVector {
        override_account_id: None,
        lost_factor: Factor::Hw,
        include_recovery_auth_pubkey: (false, false),
        create_test_account: true,
        expected_status: StatusCode::OK,
        app_signed: true,
        hw_signed: false,
        prior_contest: false,
    },
    test_create_delay_notify_with_seven_day_delay_period_override: CreateDelayNotifyTestVector {
        override_account_id: None,
        lost_factor: Factor::Hw,
        include_recovery_auth_pubkey: (true, true),
        create_test_account: false,
        expected_status: StatusCode::OK,
        app_signed: true,
        hw_signed: false,
        prior_contest: false,
    },
    test_create_delay_notify_no_signers: CreateDelayNotifyTestVector {
        override_account_id: None,
        lost_factor: Factor::Hw,
        include_recovery_auth_pubkey: (true, true),
        create_test_account: true,
        expected_status: StatusCode::BAD_REQUEST,
        app_signed: false,
        hw_signed: false,
        prior_contest: false,
    },
    test_create_delay_notify_wrong_signer: CreateDelayNotifyTestVector {
        override_account_id: None,
        lost_factor: Factor::Hw,
        include_recovery_auth_pubkey: (true, true),
        create_test_account: true,
        expected_status: StatusCode::BAD_REQUEST,
        app_signed: false,
        hw_signed: true,
        prior_contest: false,
    },
    test_create_delay_notify_both_signers: CreateDelayNotifyTestVector {
        override_account_id: None,
        lost_factor: Factor::Hw,
        include_recovery_auth_pubkey: (true, true),
        create_test_account: true,
        expected_status: StatusCode::BAD_REQUEST,
        app_signed: true,
        hw_signed: true,
        prior_contest: false,
    },
    test_create_delay_notify_prior_contest: CreateDelayNotifyTestVector {
        override_account_id: None,
        lost_factor: Factor::Hw,
        include_recovery_auth_pubkey: (true, true),
        create_test_account: true,
        expected_status: StatusCode::OK,
        app_signed: true,
        hw_signed: false,
        prior_contest: true,
    },
    test_create_delay_notify_prior_contest_without_recovery_auth_pubkey: CreateDelayNotifyTestVector {
        override_account_id: None,
        lost_factor: Factor::Hw,
        include_recovery_auth_pubkey: (false, false),
        create_test_account: true,
        expected_status: StatusCode::OK,
        app_signed: true,
        hw_signed: false,
        prior_contest: true,
    },
    test_create_delay_notify_upgrade_to_recovery_key: CreateDelayNotifyTestVector {
        override_account_id: None,
        lost_factor: Factor::Hw,
        include_recovery_auth_pubkey: (false, true),
        create_test_account: true,
        expected_status: StatusCode::OK,
        app_signed: true,
        hw_signed: false,
        prior_contest: false,
    },
    test_create_delay_notify_downgrade_to_no_recovery_key: CreateDelayNotifyTestVector {
        override_account_id: None,
        lost_factor: Factor::Hw,
        include_recovery_auth_pubkey: (true, false),
        create_test_account: true,
        expected_status: StatusCode::BAD_REQUEST,
        app_signed: true,
        hw_signed: false,
        prior_contest: false,
    },
}

#[derive(Debug)]
struct CancelDelayNotifyTestVector {
    create_recovery: bool,
    lost_factor: Factor,
    expected_status: StatusCode,
    app_signed: bool,
    hw_signed: bool,
    prior_contest: bool,
}

async fn cancel_delay_notify_test(vector: CancelDelayNotifyTestVector) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .expect("Invalid keys for account");
    if vector.create_recovery {
        let request = CreateAccountDelayNotifyRequest {
            lost_factor: vector.lost_factor,
            delay_period_num_sec: None,
            auth: FullAccountAuthKeysPayload {
                app: create_pubkey(),
                hardware: if vector.lost_factor == Factor::App {
                    account.hardware_auth_pubkey
                } else {
                    create_pubkey()
                },
                recovery: Some(create_pubkey()),
            },
        };
        let (app_signed_create, hw_signed_create) = match vector.lost_factor {
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
    if vector.prior_contest {
        // Insert a recent contested recovery
        let prior_recovery = generate_delay_and_notify_recovery(
            account.clone().id,
            RecoveryDestination {
                source_auth_keys_id: account.clone().common_fields.active_auth_keys_id,
                app_auth_pubkey: create_pubkey(),
                hardware_auth_pubkey: account.hardware_auth_pubkey,
                recovery_auth_pubkey: Some(create_pubkey()),
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
        .cancel_delay_notify_recovery(
            &account.id.to_string(),
            vector.app_signed,
            vector.hw_signed,
            &keys,
        )
        .await;

    // We expect a verification code to be required if theres a prior contest and
    //   the request wasn't signed by the non-lost factor
    if vector.prior_contest
        && (vector.lost_factor == Factor::App && !vector.hw_signed
            || vector.lost_factor == Factor::Hw && !vector.app_signed)
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
        let send_code_response = client
            .send_delay_notify_verification_code(
                &account.id.to_string(),
                &SendAccountVerificationCodeRequest { touchpoint_id },
                vector.app_signed,
                vector.hw_signed,
                &keys,
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
                vector.app_signed,
                vector.hw_signed,
                &keys,
            )
            .await;

        assert_eq!(
            verify_code_response.status_code, 200,
            "{}",
            response.body_string
        );

        response = client
            .cancel_delay_notify_recovery(
                &account.id.to_string(),
                vector.app_signed,
                vector.hw_signed,
                &keys,
            )
            .await;
    }

    assert_eq!(
        response.status_code, vector.expected_status,
        "{}",
        response.body_string
    );
}

tests! {
    runner = cancel_delay_notify_test,
    test_cancel_delay_notify_for_lost_hardware_without_recovery_and_prior_contest: CancelDelayNotifyTestVector {
        create_recovery: false,
        lost_factor: Factor::Hw,
        expected_status: StatusCode::CONFLICT,
        app_signed: true,
        hw_signed: false,
        prior_contest: false,
    },
    test_cancel_delay_notify_for_lost_hardware_without_prior_contest: CancelDelayNotifyTestVector {
        create_recovery: true,
        lost_factor: Factor::Hw,
        expected_status: StatusCode::OK,
        app_signed: true,
        hw_signed: false,
        prior_contest: false,
    },
    test_cancel_delay_notify_for_lost_app_key_without_prior_contest: CancelDelayNotifyTestVector {
        create_recovery: true,
        lost_factor: Factor::App,
        expected_status: StatusCode::OK,
        app_signed: false,
        hw_signed: true,
        prior_contest: false,
    },
    test_cancel_delay_notify_no_signers: CancelDelayNotifyTestVector {
        create_recovery: true,
        lost_factor: Factor::Hw,
        expected_status: StatusCode::BAD_REQUEST,
        app_signed: false,
        hw_signed: false,
        prior_contest: false,
    },
    test_contest_delay_notify_without_prior_contest: CancelDelayNotifyTestVector {
        create_recovery: true,
        lost_factor: Factor::Hw,
        expected_status: StatusCode::OK,
        app_signed: false,
        hw_signed: true,
        prior_contest: false,
    },
    test_cancel_delay_notify_prior_contest: CancelDelayNotifyTestVector {
        create_recovery: true,
        lost_factor: Factor::Hw,
        expected_status: StatusCode::OK,
        app_signed: true,
        hw_signed: false,
        prior_contest: true,
    },
    test_contest_delay_notify_prior_contest: CancelDelayNotifyTestVector {
        create_recovery: true,
        lost_factor: Factor::Hw,
        expected_status: StatusCode::OK,
        app_signed: false,
        hw_signed: true,
        prior_contest: true,
    },
}

#[derive(Debug)]
struct CompleteDelayNotifyTestVector {
    create_recovery: bool,
    lost_factor: Factor,
    include_recovery_auth_pubkey: bool,
    offset_delay_end_time_days: Duration,
    expected_status: StatusCode,
    expect_key_rotation: bool,
}

async fn complete_delay_notify_test(vector: CompleteDelayNotifyTestVector) {
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

    let offset_dt = fixed_cur_time + vector.offset_delay_end_time_days;
    let (app_auth_seckey, app_auth_pubkey) = create_plain_keys();
    let (hardware_auth_seckey, hardware_auth_pubkey) = create_plain_keys();
    let (_recovery_auth_seckey, recovery_auth_pubkey) = create_plain_keys();
    if vector.create_recovery {
        let recovery = generate_delay_and_notify_recovery(
            account_id.clone(),
            RecoveryDestination {
                source_auth_keys_id: account.common_fields.active_auth_keys_id,
                app_auth_pubkey,
                hardware_auth_pubkey,
                recovery_auth_pubkey: if vector.include_recovery_auth_pubkey {
                    Some(recovery_auth_pubkey)
                } else {
                    None
                },
            },
            offset_dt,
            RecoveryStatus::Pending,
            vector.lost_factor,
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
        + if vector.include_recovery_auth_pubkey {
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
        response.status_code, vector.expected_status,
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
    if vector.expect_key_rotation {
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

tests! {
    runner = complete_delay_notify_test,
    test_complete_lost_app_delay_notify_without_recovery: CompleteDelayNotifyTestVector {
        create_recovery: false,
        lost_factor: Factor::App,
        include_recovery_auth_pubkey: true,
        offset_delay_end_time_days: Duration::days(0),
        expected_status: StatusCode::CONFLICT,
        expect_key_rotation: false,
    },
    test_complete_lost_hw_delay_notify_without_recovery: CompleteDelayNotifyTestVector {
        create_recovery: false,
        lost_factor: Factor::Hw,
        include_recovery_auth_pubkey: true,
        offset_delay_end_time_days: Duration::days(0),
        expected_status: StatusCode::CONFLICT,
        expect_key_rotation: false,
    },
    test_complete_lost_app_delay_notify_with_delay_period_completed_just_now: CompleteDelayNotifyTestVector {
        create_recovery: true,
        lost_factor: Factor::App,
        include_recovery_auth_pubkey: true,
        offset_delay_end_time_days: Duration::days(0),
        expected_status: StatusCode::OK,
        expect_key_rotation: true,
    },
    test_complete_lost_hw_delay_notify_with_delay_period_completed_just_now: CompleteDelayNotifyTestVector {
        create_recovery: true,
        lost_factor: Factor::Hw,
        include_recovery_auth_pubkey: true,
        offset_delay_end_time_days: Duration::days(0),
        expected_status: StatusCode::OK,
        expect_key_rotation: true,
    },
    test_complete_lost_app_delay_notify_with_delay_period_completed_yesterday: CompleteDelayNotifyTestVector {
        create_recovery: true,
        lost_factor: Factor::App,
        include_recovery_auth_pubkey: true,
        offset_delay_end_time_days: Duration::days(-1),
        expected_status: StatusCode::OK,
        expect_key_rotation: true,
    },
    test_complete_lost_app_delay_notify_while_in_delay_period: CompleteDelayNotifyTestVector {
        create_recovery: true,
        lost_factor: Factor::App,
        include_recovery_auth_pubkey: true,
        offset_delay_end_time_days: Duration::days(1),
        expected_status: StatusCode::BAD_REQUEST,
        expect_key_rotation: false,
    },
    test_complete_lost_hw_delay_notify_while_in_delay_period: CompleteDelayNotifyTestVector {
        create_recovery: true,
        lost_factor: Factor::Hw,
        include_recovery_auth_pubkey: true,
        offset_delay_end_time_days: Duration::days(1),
        expected_status: StatusCode::BAD_REQUEST,
        expect_key_rotation: false,
    },
    test_complete_lost_app_delay_notify_while_in_delay_period_further_away: CompleteDelayNotifyTestVector {
        create_recovery: true,
        lost_factor: Factor::App,
        include_recovery_auth_pubkey: true,
        offset_delay_end_time_days: Duration::days(30),
        expected_status: StatusCode::BAD_REQUEST,
        expect_key_rotation: false,
    },
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

#[derive(Debug)]
struct CompleteDelayNotifyIdempotencyTestVector {
    lost_factor: Factor,
    override_challenge: Option<String>,
    override_app_signature: Option<String>,
    override_hw_signature: Option<String>,
    expected_status: StatusCode,
}

async fn complete_delay_notify_idempotency_test(vector: CompleteDelayNotifyIdempotencyTestVector) {
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
        },
        fixed_cur_time,
        RecoveryStatus::Pending,
        vector.lost_factor,
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
    let challenge = vector.override_challenge.unwrap_or(challenge);
    let app_signature = vector.override_app_signature.unwrap_or(sign_message(
        &Secp256k1::new(),
        &challenge,
        &app_auth_seckey,
    ));
    let hardware_signature = vector.override_hw_signature.unwrap_or(sign_message(
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
        response.status_code, vector.expected_status,
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

tests! {
    runner = complete_delay_notify_idempotency_test,
    test_complete_lost_app_delay_notify_idempotency_success: CompleteDelayNotifyIdempotencyTestVector {
        lost_factor: Factor::App,
        override_challenge: None,
        override_app_signature: None,
        override_hw_signature: None,
        expected_status: StatusCode::OK,
    },
    test_complete_lost_hw_delay_notify_idempotency_success: CompleteDelayNotifyIdempotencyTestVector {
        lost_factor: Factor::Hw,
        override_challenge: None,
        override_app_signature: None,
        override_hw_signature: None,
        expected_status: StatusCode::OK,
    },
    test_complete_lost_app_delay_notify_invalid_signature: CompleteDelayNotifyIdempotencyTestVector {
        lost_factor: Factor::App,
        override_challenge: None,
        override_app_signature: Some("lol".to_string()),
        override_hw_signature: None,
        expected_status: StatusCode::CONFLICT,
    },
    test_complete_lost_hw_delay_notify_invalid_signature: CompleteDelayNotifyIdempotencyTestVector {
        lost_factor: Factor::Hw,
        override_challenge: None,
        override_app_signature: None,
        override_hw_signature: Some("lol".to_string()),
        expected_status: StatusCode::CONFLICT,
    },
    test_complete_lost_app_delay_notify_idempotency_failure: CompleteDelayNotifyIdempotencyTestVector {
        lost_factor: Factor::App,
        override_challenge: Some("test".to_string()),
        override_app_signature: None,
        override_hw_signature: None,
        expected_status: StatusCode::CONFLICT,
    },
    test_complete_lost_hw_delay_notify_idempotency_failure: CompleteDelayNotifyIdempotencyTestVector {
        lost_factor: Factor::Hw,
        override_challenge: Some("test".to_string()),
        override_app_signature: None,
        override_hw_signature: None,
        expected_status: StatusCode::CONFLICT,
    },
}

#[derive(Debug)]
struct GetStatusWithDelayNotifyTestVector {
    override_query_account_id: Option<AccountId>,
    create_recovery: bool,
    delay_end_time_offset: Option<Duration>,
    recovery_status: RecoveryStatus,
    expected_in_recovery: bool,
    expected_status: StatusCode,
}

async fn get_status_with_delay_notify_test(vector: GetStatusWithDelayNotifyTestVector) {
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
    let query_account_id = vector
        .override_query_account_id
        .unwrap_or(account_id.clone());

    let offset_dt = OffsetDateTime::now_utc();
    let app_auth_pubkey = create_pubkey();
    let recovery_auth_pubkey = Some(create_pubkey());
    if vector.create_recovery {
        let recovery = generate_delay_and_notify_recovery(
            account_id,
            RecoveryDestination {
                source_auth_keys_id: account.common_fields.active_auth_keys_id,
                app_auth_pubkey,
                hardware_auth_pubkey: account.hardware_auth_pubkey,
                recovery_auth_pubkey,
            },
            offset_dt + vector.delay_end_time_offset.unwrap(),
            vector.recovery_status,
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
        response.status_code, vector.expected_status,
        "{}",
        response.body_string
    );
    let payload = match response.body {
        Some(body) => body,
        None => return,
    };
    assert_eq!(
        payload.pending_delay_notify.is_some(),
        vector.expected_in_recovery
    );
    if vector.expected_in_recovery {
        let r = payload.pending_delay_notify.unwrap();
        let expected_delay_end_time = offset_dt + vector.delay_end_time_offset.unwrap();
        assert_eq!(r.delay_end_time, expected_delay_end_time);
        assert_eq!(r.lost_factor, Factor::App);
        assert_eq!(
            r.auth_keys,
            FullAccountAuthKeysPayload {
                app: app_auth_pubkey,
                hardware: account.hardware_auth_pubkey,
                recovery: recovery_auth_pubkey
            }
        );
    }
}

tests! {
    runner = get_status_with_delay_notify_test,
    test_status_with_invalid_account_id: GetStatusWithDelayNotifyTestVector {
        override_query_account_id: AccountId::gen().ok(),
        create_recovery: false,
        delay_end_time_offset: None,
        recovery_status: RecoveryStatus::Pending,
        expected_in_recovery: false,
        expected_status: StatusCode::NOT_FOUND,
    },
    test_status_with_no_delay_notify_recovery: GetStatusWithDelayNotifyTestVector {
        override_query_account_id: None,
        create_recovery: false,
        delay_end_time_offset: None,
        recovery_status: RecoveryStatus::Pending,
        expected_in_recovery: false,
        expected_status: StatusCode::OK,
    },
    test_status_with_expired_delay_notify_recovery: GetStatusWithDelayNotifyTestVector {
        override_query_account_id: None,
        create_recovery: true,
        delay_end_time_offset: Some(Duration::days(-1)),
        recovery_status: RecoveryStatus::Complete,
        expected_in_recovery: false,
        expected_status: StatusCode::OK,
    },
    test_status_in_period_delay_notify_recovery: GetStatusWithDelayNotifyTestVector {
        override_query_account_id: None,
        create_recovery: true,
        delay_end_time_offset: Some(Duration::days(1)),
        recovery_status: RecoveryStatus::Pending,
        expected_in_recovery: true,
        expected_status: StatusCode::OK,
    },
    test_status_out_of_period_delay_notify_recovery: GetStatusWithDelayNotifyTestVector {
        override_query_account_id: None,
        create_recovery: true,
        delay_end_time_offset: Some(Duration::days(-1)),
        recovery_status: RecoveryStatus::Pending,
        expected_in_recovery: true,
        expected_status: StatusCode::OK,
    },
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
        auth: FullAccountAuthKeysPayload {
            app: create_pubkey(),
            hardware: hw_authkey,
            recovery: Some(create_pubkey()),
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

#[derive(Debug)]
struct UpdateDelayForTestRecoveryTestVector {
    is_test_account: bool,
    delay_period_num_sec: Option<i64>,
    expected_status: StatusCode,
}

async fn update_delay_for_test_recovery_test(vector: UpdateDelayForTestRecoveryTestVector) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let network = if vector.is_test_account {
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
                delay_period_num_sec: vector.delay_period_num_sec,
            },
            true,
            false,
            &keys,
        )
        .await;
    assert_eq!(
        response.status_code, vector.expected_status,
        "{}",
        response.body_string
    );
    let payload = match response.body {
        Some(body) => body,
        None => return,
    };
    let expected_delay_end_time = if let Some(delay_period) = vector.delay_period_num_sec {
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
                delay_period_num_sec: vector.delay_period_num_sec,
                auth: FullAccountAuthKeysPayload {
                    app: create_pubkey(),
                    hardware: create_pubkey(),
                    recovery: Some(create_pubkey()),
                },
            },
            true,
            false,
            &keys,
        )
        .await;
    assert_eq!(
        response.status_code, vector.expected_status,
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
    let expected_delay_end_time = if let Some(delay_period) = vector.delay_period_num_sec {
        recovery.created_at + Duration::seconds(delay_period)
    } else {
        recovery.created_at + Duration::seconds(20)
    };
    assert_eq!(
        payload.pending_delay_notify.delay_end_time,
        expected_delay_end_time
    );
}

tests! {
    runner = update_delay_for_test_recovery_test,
    test_update_delay_with_test_account: UpdateDelayForTestRecoveryTestVector {
        is_test_account: true,
        delay_period_num_sec: Some(10),
        expected_status: StatusCode::OK,
    },
    test_update_delay_without_test_account: UpdateDelayForTestRecoveryTestVector {
        is_test_account: false,
        delay_period_num_sec: Some(10),
        expected_status: StatusCode::BAD_REQUEST,
    },
    test_update_to_existing_delay_with_test_account: UpdateDelayForTestRecoveryTestVector {
        is_test_account: true,
        delay_period_num_sec: None,
        expected_status: StatusCode::OK,
    },
    test_update_to_existing_delay_without_test_account: UpdateDelayForTestRecoveryTestVector {
        is_test_account: false,
        delay_period_num_sec: None,
        expected_status: StatusCode::BAD_REQUEST,
    },
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

#[derive(Debug)]
struct ReuseAuthPubkeyTestVector {
    lost_factor: Factor,
    auth_key_reuse: AuthKeyReuse,
    expected_error: Option<ApiError>,
}

async fn reuse_auth_pubkey_test(vector: ReuseAuthPubkeyTestVector) {
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
        match vector.auth_key_reuse {
            AuthKeyReuse::MyAccountApp => (
                account.application_auth_pubkey.unwrap(),
                if vector.lost_factor == Factor::Hw {
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
                if vector.lost_factor == Factor::Hw {
                    create_pubkey()
                } else {
                    account.hardware_auth_pubkey
                },
                account.common_fields.recovery_auth_pubkey,
            ),
            AuthKeyReuse::OtherAccountApp => (
                other_account.application_auth_pubkey.unwrap(),
                if vector.lost_factor == Factor::Hw {
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
                if vector.lost_factor == Factor::Hw {
                    create_pubkey()
                } else {
                    account.hardware_auth_pubkey
                },
                other_account.common_fields.recovery_auth_pubkey,
            ),
            AuthKeyReuse::OtherRecoveryApp => (
                other_recovery.destination_app_auth_pubkey.unwrap(),
                if vector.lost_factor == Factor::Hw {
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
                if vector.lost_factor == Factor::Hw {
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
                lost_factor: vector.lost_factor,
                delay_period_num_sec: Some(Duration::days(7).as_seconds_f32() as i64),
                auth: FullAccountAuthKeysPayload {
                    app: recovery_app_pubkey,
                    hardware: recovery_hardware_pubkey,
                    recovery: recovery_recover_pubkey,
                },
            },
            vector.lost_factor == Factor::Hw,
            vector.lost_factor == Factor::App,
            &account_keys,
        )
        .await;

    if let Some(expected_error) = vector.expected_error {
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

tests! {
    runner = reuse_auth_pubkey_test,
    lost_app_reuse_my_account_app: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::App,
        auth_key_reuse: AuthKeyReuse::MyAccountApp,
        expected_error: Some(RecoveryError::AppAuthPubkeyReuseAccount.into()),
    },
    lost_hw_reuse_my_account_app: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::Hw,
        auth_key_reuse: AuthKeyReuse::MyAccountApp,
        expected_error: Some(RecoveryError::AppAuthPubkeyReuseAccount.into()),
    },
    lost_app_reuse_my_account_hw: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::App,
        auth_key_reuse: AuthKeyReuse::MyAccountHw,
        expected_error: None,
    },
    lost_hw_reuse_my_account_hw: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::Hw,
        auth_key_reuse: AuthKeyReuse::MyAccountHw,
        expected_error: Some(RecoveryError::HwAuthPubkeyReuseAccount.into()),
    },
    lost_app_reuse_my_account_recovery: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::App,
        auth_key_reuse: AuthKeyReuse::MyAccountRecovery,
        expected_error: Some(RecoveryError::RecoveryAuthPubkeyReuseAccount.into()),
    },
    lost_hw_reuse_my_account_recovery: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::Hw,
        auth_key_reuse: AuthKeyReuse::MyAccountRecovery,
        expected_error: Some(RecoveryError::RecoveryAuthPubkeyReuseAccount.into()),
    },
    lost_app_reuse_other_account_app: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::App,
        auth_key_reuse: AuthKeyReuse::OtherAccountApp,
        expected_error: Some(RecoveryError::AppAuthPubkeyReuseAccount.into()),
    },
    lost_hw_reuse_other_account_app: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::Hw,
        auth_key_reuse: AuthKeyReuse::OtherAccountApp,
        expected_error: Some(RecoveryError::AppAuthPubkeyReuseAccount.into()),
    },
    lost_app_reuse_other_account_hw: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::App,
        auth_key_reuse: AuthKeyReuse::OtherAccountHw,
        expected_error: Some(RecoveryError::InvalidRecoveryDestination.into()),
    },
    lost_hw_reuse_other_account_hw: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::Hw,
        auth_key_reuse: AuthKeyReuse::OtherAccountHw,
        expected_error: Some(RecoveryError::HwAuthPubkeyReuseAccount.into()),
    },
    lost_app_reuse_other_account_recovery: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::App,
        auth_key_reuse: AuthKeyReuse::OtherAccountRecovery,
        expected_error: Some(RecoveryError::RecoveryAuthPubkeyReuseAccount.into()),
    },
    lost_hw_reuse_other_account_recovery: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::Hw,
        auth_key_reuse: AuthKeyReuse::OtherAccountRecovery,
        expected_error: Some(RecoveryError::RecoveryAuthPubkeyReuseAccount.into()),
    },
    lost_app_reuse_other_recovery_app: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::App,
        auth_key_reuse: AuthKeyReuse::OtherRecoveryApp,
        expected_error: Some(RecoveryError::AppAuthPubkeyReuseRecovery.into()),
    },
    lost_hw_reuse_other_recovery_app: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::Hw,
        auth_key_reuse: AuthKeyReuse::OtherRecoveryApp,
        expected_error: Some(RecoveryError::AppAuthPubkeyReuseRecovery.into()),
    },
    lost_app_reuse_other_recovery_hw: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::App,
        auth_key_reuse: AuthKeyReuse::OtherRecoveryHw,
        expected_error: Some(RecoveryError::InvalidRecoveryDestination.into()),
    },
    lost_hw_reuse_other_recovery_hw: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::Hw,
        auth_key_reuse: AuthKeyReuse::OtherRecoveryHw,
        expected_error: Some(RecoveryError::HwAuthPubkeyReuseRecovery.into()),
    },
    lost_app_reuse_other_recovery_recovery: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::App,
        auth_key_reuse: AuthKeyReuse::OtherRecoveryRecovery,
        expected_error: Some(RecoveryError::RecoveryAuthPubkeyReuseRecovery.into()),
    },
    lost_hw_reuse_other_recovery_recovery: ReuseAuthPubkeyTestVector {
        lost_factor: Factor::Hw,
        auth_key_reuse: AuthKeyReuse::OtherRecoveryRecovery,
        expected_error: Some(RecoveryError::RecoveryAuthPubkeyReuseRecovery.into()),
    },
}
