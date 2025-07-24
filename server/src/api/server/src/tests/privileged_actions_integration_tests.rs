use std::panic;
use std::sync::Arc;

use http::StatusCode;
use notification::service::FetchForAccountInput;
use notification::NotificationPayloadType;
use privileged_action::routes::ProcessPrivilegedActionVerificationRequest;
use privileged_action::routes::{
    CancelPendingDelayAndNotifyInstanceByTokenRequest,
    CancelPendingDelayAndNotifyInstanceByTokenResponse,
    ConfigurePrivilegedActionDelayDurationsRequest,
    ConfigurePrivilegedActionDelayDurationsResponse, GetPendingInstancesParams,
    GetPendingInstancesResponse, GetPrivilegedActionDefinitionsResponse,
};
use time::OffsetDateTime;
use types::account::entities::Account;
use types::account::entities::TransactionVerificationPolicy;
use types::account::identifiers::AccountId;
use types::privileged_action::definition::AuthorizationStrategyDefinition;
use types::privileged_action::repository::{
    AuthorizationStrategyRecord, PrivilegedActionInstanceRecord,
};
use types::privileged_action::router::generic::{
    AuthorizationStrategyInput, AuthorizationStrategyOutput, ContinuePrivilegedActionRequest,
    DelayAndNotifyInput, PrivilegedActionInstanceInput, PrivilegedActionRequest,
    PrivilegedActionResponse,
};
use types::privileged_action::shared::{PrivilegedActionDelayDuration, PrivilegedActionInstanceId};
use types::transaction_verification::router::PutTransactionVerificationPolicyRequest;
use types::{account::AccountType, privileged_action::shared::PrivilegedActionType};

use super::TestContext;
use super::{lib::create_software_account, requests::CognitoAuthentication};
use crate::tests;
use crate::tests::gen_services_with_overrides;
use crate::tests::lib::{create_account, create_phone_touchpoint, OffsetClock};
use crate::tests::requests::axum::TestClient;
use crate::GenServiceOverrides;

async fn get_privileged_action_definitions(
    context: &mut TestContext,
    client: &TestClient,
    account_id: &AccountId,
    auth: &CognitoAuthentication,
    expected_status: StatusCode,
) -> Option<GetPrivilegedActionDefinitionsResponse> {
    let get_resp = client
        .get_privileged_action_definitions(
            &account_id.to_string(),
            auth,
            context.account_authentication_keys.get(account_id).unwrap(),
        )
        .await;
    assert_eq!(get_resp.status_code, expected_status);
    get_resp.body
}

async fn initiate_configure_privileged_action_delays(
    context: &mut TestContext,
    client: &TestClient,
    account_id: &AccountId,
    privileged_action_type: PrivilegedActionType,
    auth: &CognitoAuthentication,
    expected_status: StatusCode,
) -> Option<PrivilegedActionResponse<ConfigurePrivilegedActionDelayDurationsResponse>> {
    let put_resp = client
        .configure_privileged_action_delay_durations(
            &account_id.to_string(),
            &PrivilegedActionRequest::Initiate(ConfigurePrivilegedActionDelayDurationsRequest {
                delays: vec![PrivilegedActionDelayDuration {
                    privileged_action_type,
                    delay_duration_secs: 666,
                }],
            }),
            auth,
            context.account_authentication_keys.get(account_id).unwrap(),
        )
        .await;
    assert_eq!(put_resp.status_code, expected_status);
    put_resp.body
}

async fn continue_configure_privileged_action_delays(
    context: &mut TestContext,
    client: &TestClient,
    account_id: &AccountId,
    privileged_action_instance_id: PrivilegedActionInstanceId,
    completion_token: String,
    auth: &CognitoAuthentication,
    expected_status: StatusCode,
) -> Option<PrivilegedActionResponse<ConfigurePrivilegedActionDelayDurationsResponse>> {
    let put_resp = client
        .configure_privileged_action_delay_durations(
            &account_id.to_string(),
            &PrivilegedActionRequest::Continue(ContinuePrivilegedActionRequest {
                privileged_action_instance: PrivilegedActionInstanceInput {
                    id: privileged_action_instance_id,
                    authorization_strategy: AuthorizationStrategyInput::DelayAndNotify(
                        DelayAndNotifyInput { completion_token },
                    ),
                },
            }),
            auth,
            context.account_authentication_keys.get(account_id).unwrap(),
        )
        .await;
    assert_eq!(put_resp.status_code, expected_status);
    put_resp.body
}

async fn get_pending_privileged_action_instances(
    context: &mut TestContext,
    client: &TestClient,
    account_id: &AccountId,
    auth: &CognitoAuthentication,
    expected_status: StatusCode,
    expected_count: usize,
) -> Option<GetPendingInstancesResponse> {
    let get_resp = client
        .get_pending_instances(
            &account_id.to_string(),
            auth,
            &GetPendingInstancesParams {
                privileged_action_type: None,
            },
            context.account_authentication_keys.get(account_id).unwrap(),
        )
        .await;
    assert_eq!(get_resp.status_code, expected_status);
    let body = get_resp.body;
    assert_eq!(body.as_ref().unwrap().instances.len(), expected_count);
    body
}

async fn cancel_pending_privileged_action_instance(
    client: &TestClient,
    cancellation_token: String,
    expected_status: StatusCode,
) -> Option<CancelPendingDelayAndNotifyInstanceByTokenResponse> {
    let post_resp = client
        .cancel_pending_delay_and_notify_instance_by_token(
            &CancelPendingDelayAndNotifyInstanceByTokenRequest { cancellation_token },
        )
        .await;
    assert_eq!(post_resp.status_code, expected_status);
    post_resp.body
}

struct GetConfigureDelaysTestVector {
    account_type: AccountType,
    privileged_action_type: PrivilegedActionType,
    expected_initiate_status: StatusCode,
    expected_privileged_action: bool,
}

// Tests the get delays and configure delays call, as well as general privileged action mechanism
//   since configure delays is itself a privileged action
async fn get_configure_delays_test(vector: GetConfigureDelaysTestVector) {
    let clock = Arc::new(OffsetClock::new());
    let (mut context, bootstrap) =
        gen_services_with_overrides(GenServiceOverrides::new().clock(clock.clone())).await;
    let client = TestClient::new(bootstrap.router).await;

    let account = create_account(
        &mut context,
        &bootstrap.services,
        vector.account_type.clone(),
        false,
    )
    .await;
    create_phone_touchpoint(&bootstrap.services, account.get_id(), true).await;

    let auth = match vector.account_type {
        AccountType::Full => CognitoAuthentication::Wallet {
            is_app_signed: false,
            is_hardware_signed: false,
        },
        AccountType::Lite => CognitoAuthentication::Recovery,
        AccountType::Software => CognitoAuthentication::Wallet {
            is_app_signed: false,
            is_hardware_signed: false,
        },
    };

    // Get definitions
    let get_resp = get_privileged_action_definitions(
        &mut context,
        &client,
        account.get_id(),
        &auth,
        StatusCode::OK,
    )
    .await;

    // Configure delay
    let put_resp = initiate_configure_privileged_action_delays(
        &mut context,
        &client,
        account.get_id(),
        vector.privileged_action_type.clone(),
        &auth,
        vector.expected_initiate_status,
    )
    .await;

    if !vector.expected_initiate_status.is_success() {
        return;
    }

    if vector.expected_privileged_action {
        let PrivilegedActionResponse::Pending(pending_resp) = put_resp.unwrap() else {
            panic!("Expected Pending response");
        };

        let AuthorizationStrategyOutput::DelayAndNotify(delay_and_notify_output) = pending_resp
            .privileged_action_instance
            .authorization_strategy
        else {
            panic!("Expected DelayAndNotify authorization strategy");
        };

        // Cannot initiate again because concurrency not allowed
        initiate_configure_privileged_action_delays(
            &mut context,
            &client,
            account.get_id(),
            vector.privileged_action_type.clone(),
            &auth,
            StatusCode::CONFLICT,
        )
        .await;

        // Cannot continue before delay is over
        continue_configure_privileged_action_delays(
            &mut context,
            &client,
            account.get_id(),
            pending_resp.privileged_action_instance.id.clone(),
            delay_and_notify_output.completion_token.clone(),
            &auth,
            StatusCode::CONFLICT,
        )
        .await;

        // Advance time to after delay
        clock.add_offset(delay_and_notify_output.delay_end_time - OffsetDateTime::now_utc());

        // Cannot continue with wrong completion_token
        continue_configure_privileged_action_delays(
            &mut context,
            &client,
            account.get_id(),
            pending_resp.privileged_action_instance.id.clone(),
            "COMPLETION_TOKEN".to_string(),
            &auth,
            StatusCode::BAD_REQUEST,
        )
        .await;

        // Successfully continue
        continue_configure_privileged_action_delays(
            &mut context,
            &client,
            account.get_id(),
            pending_resp.privileged_action_instance.id.clone(),
            delay_and_notify_output.completion_token,
            &auth,
            StatusCode::OK,
        )
        .await;
    }

    let AuthorizationStrategyDefinition::DelayAndNotify(before_delay_and_notify_definition) =
        get_resp
            .unwrap()
            .definitions
            .into_iter()
            .find(|d| d.privileged_action_type == vector.privileged_action_type)
            .unwrap()
            .authorization_strategy
    else {
        panic!("Expected DelayAndNotify authorization strategy");
    };

    // Get definitions after configuring
    let get_resp = get_privileged_action_definitions(
        &mut context,
        &client,
        account.get_id(),
        &auth,
        StatusCode::OK,
    )
    .await;

    let AuthorizationStrategyDefinition::DelayAndNotify(after_delay_and_notify_definition) =
        get_resp
            .unwrap()
            .definitions
            .into_iter()
            .find(|d| d.privileged_action_type == vector.privileged_action_type)
            .unwrap()
            .authorization_strategy
    else {
        panic!("Expected DelayAndNotify authorization strategy");
    };

    assert_ne!(
        before_delay_and_notify_definition.delay_duration_secs,
        after_delay_and_notify_definition.delay_duration_secs
    );
    assert_eq!(after_delay_and_notify_definition.delay_duration_secs, 666);

    if vector.expected_privileged_action {
        // Check whether the notifications were created
        let mut scheduled_notifications = bootstrap
            .services
            .notification_service
            .fetch_scheduled_for_account(FetchForAccountInput {
                account_id: account.get_id().clone(),
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
                account_id: account.get_id().clone(),
            })
            .await
            .unwrap();
        customer_notifications.sort_by_key(|n| n.created_at);
        let customer_notifications_types = customer_notifications
            .iter()
            .map(|n| n.payload_type)
            .collect::<Vec<NotificationPayloadType>>();

        let expected_scheduled_notification_types = vec![
            // The pending schedule gets split into 1 that gets sent immediately
            // and a schedule that starts in 2 days
            NotificationPayloadType::PrivilegedActionPendingDelayPeriod,
            // Two completed schedules
            NotificationPayloadType::PrivilegedActionCompletedDelayPeriod,
            NotificationPayloadType::PrivilegedActionCompletedDelayPeriod,
        ];

        // One of each type for each channel, account only has 1 touchpoint so we expect only 1 per event
        let expected_customer_notification_types =
            vec![NotificationPayloadType::PrivilegedActionPendingDelayPeriod];

        assert_eq!(
            scheduled_notifications_types,
            expected_scheduled_notification_types
        );

        assert_eq!(
            customer_notifications_types,
            expected_customer_notification_types
        );
    }
}

tests! {
    runner = get_configure_delays_test,
    test_successfully_configure_delay: GetConfigureDelaysTestVector {
        account_type: AccountType::Software,
        privileged_action_type: PrivilegedActionType::ActivateTouchpoint,
        expected_initiate_status: StatusCode::OK,
        expected_privileged_action: true,
    },
    test_configure_unconfigurable_delay: GetConfigureDelaysTestVector {
        account_type: AccountType::Software,
        privileged_action_type: PrivilegedActionType::ConfigurePrivilegedActionDelays,
        expected_initiate_status: StatusCode::BAD_REQUEST,
        expected_privileged_action: false,
    },
    test_full_account_configure_delay: GetConfigureDelaysTestVector {
        account_type: AccountType::Full,
        privileged_action_type: PrivilegedActionType::ActivateTouchpoint,
        expected_initiate_status: StatusCode::FORBIDDEN,
        expected_privileged_action: false,
    },
    test_lite_account_configure_delay: GetConfigureDelaysTestVector {
        account_type: AccountType::Lite,
        privileged_action_type: PrivilegedActionType::ActivateTouchpoint,
        expected_initiate_status: StatusCode::UNAUTHORIZED, // Right now, the configure endpoint doesn't accept recovery auth
        expected_privileged_action: false,
    },
}

#[tokio::test]
async fn get_instances_cancel_instance_test() {
    let clock = Arc::new(OffsetClock::new());
    let (mut context, bootstrap) =
        gen_services_with_overrides(GenServiceOverrides::new().clock(clock.clone())).await;
    let client = TestClient::new(bootstrap.router).await;
    let account = Account::Software(
        create_software_account(&mut context, &bootstrap.services, None, false).await,
    );
    create_phone_touchpoint(&bootstrap.services, account.get_id(), true).await;

    let auth = CognitoAuthentication::Wallet {
        is_app_signed: false,
        is_hardware_signed: false,
    };

    // Get pending instances (should be 0)
    get_pending_privileged_action_instances(
        &mut context,
        &client,
        account.get_id(),
        &auth,
        StatusCode::OK,
        0,
    )
    .await;

    // Create a privileged action instance
    let put_resp = initiate_configure_privileged_action_delays(
        &mut context,
        &client,
        account.get_id(),
        PrivilegedActionType::ActivateTouchpoint,
        &auth,
        StatusCode::OK,
    )
    .await;

    // Get pending instances (should be 1)
    get_pending_privileged_action_instances(
        &mut context,
        &client,
        account.get_id(),
        &auth,
        StatusCode::OK,
        1,
    )
    .await;

    // Cannot cancel with incorrect cancellation token
    cancel_pending_privileged_action_instance(
        &client,
        "CANCELLATION_TOKEN".to_string(),
        StatusCode::NOT_FOUND,
    )
    .await;

    let PrivilegedActionResponse::Pending(pending_resp) = put_resp.unwrap() else {
        panic!("Expected Pending response");
    };

    let AuthorizationStrategyOutput::DelayAndNotify(delay_and_notify_output) = pending_resp
        .privileged_action_instance
        .authorization_strategy
    else {
        panic!("Expected DelayAndNotify authorization strategy");
    };

    // Can cancel with correct cancellation token
    cancel_pending_privileged_action_instance(
        &client,
        delay_and_notify_output.cancellation_token.clone(),
        StatusCode::OK,
    )
    .await;

    // Can't re-cancel with correct cancellation token
    cancel_pending_privileged_action_instance(
        &client,
        delay_and_notify_output.cancellation_token.clone(),
        StatusCode::CONFLICT,
    )
    .await;

    // Get pending instances (should be 0)
    get_pending_privileged_action_instances(
        &mut context,
        &client,
        account.get_id(),
        &auth,
        StatusCode::OK,
        0,
    )
    .await;

    // Create a privileged action instance
    let put_resp = initiate_configure_privileged_action_delays(
        &mut context,
        &client,
        account.get_id(),
        PrivilegedActionType::ActivateTouchpoint,
        &auth,
        StatusCode::OK,
    )
    .await;

    // Get pending instances (should be 1)
    get_pending_privileged_action_instances(
        &mut context,
        &client,
        account.get_id(),
        &auth,
        StatusCode::OK,
        1,
    )
    .await;

    let PrivilegedActionResponse::Pending(pending_resp) = put_resp.unwrap() else {
        panic!("Expected Pending response");
    };

    let AuthorizationStrategyOutput::DelayAndNotify(delay_and_notify_output) = pending_resp
        .privileged_action_instance
        .authorization_strategy
    else {
        panic!("Expected DelayAndNotify authorization strategy");
    };

    // Advance time to after delay
    clock.add_offset(delay_and_notify_output.delay_end_time - OffsetDateTime::now_utc());

    // Successfully continue
    continue_configure_privileged_action_delays(
        &mut context,
        &client,
        account.get_id(),
        pending_resp.privileged_action_instance.id,
        delay_and_notify_output.completion_token,
        &auth,
        StatusCode::OK,
    )
    .await;

    // Get pending instances (should be 0)
    get_pending_privileged_action_instances(
        &mut context,
        &client,
        account.get_id(),
        &auth,
        StatusCode::OK,
        0,
    )
    .await;

    // Can't cancel completed instance with correct cancellation token
    cancel_pending_privileged_action_instance(
        &client,
        delay_and_notify_output.cancellation_token.clone(),
        StatusCode::CONFLICT,
    )
    .await;

    // Check whether the notifications were created
    let mut scheduled_notifications = bootstrap
        .services
        .notification_service
        .fetch_scheduled_for_account(FetchForAccountInput {
            account_id: account.get_id().clone(),
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
            account_id: account.get_id().clone(),
        })
        .await
        .unwrap();
    customer_notifications.sort_by_key(|n| n.created_at);
    let customer_notifications_types = customer_notifications
        .iter()
        .map(|n| n.payload_type)
        .collect::<Vec<NotificationPayloadType>>();

    let expected_scheduled_notification_types = vec![
        // The pending schedule gets split into 1 that gets sent immediately
        // and a schedule that starts in 2 days, and we started 2 delays
        NotificationPayloadType::PrivilegedActionPendingDelayPeriod,
        NotificationPayloadType::PrivilegedActionPendingDelayPeriod,
        // Two completed schedules per delay
        NotificationPayloadType::PrivilegedActionCompletedDelayPeriod,
        NotificationPayloadType::PrivilegedActionCompletedDelayPeriod,
        NotificationPayloadType::PrivilegedActionCompletedDelayPeriod,
        NotificationPayloadType::PrivilegedActionCompletedDelayPeriod,
    ];

    // One of each type for each channel, account only has 1 touchpoint so we expect only 1 per event
    let expected_customer_notification_types = vec![
        NotificationPayloadType::PrivilegedActionPendingDelayPeriod,
        NotificationPayloadType::PrivilegedActionCanceledDelayPeriod,
        NotificationPayloadType::PrivilegedActionPendingDelayPeriod,
    ];

    assert_eq!(
        scheduled_notifications_types,
        expected_scheduled_notification_types
    );

    assert_eq!(
        customer_notifications_types,
        expected_customer_notification_types
    );
}

#[tokio::test]
pub async fn respond_to_privileged_action_request_test() {
    let clock = Arc::new(OffsetClock::new());
    let (mut context, bootstrap) =
        gen_services_with_overrides(GenServiceOverrides::new().clock(clock.clone())).await;
    let client = TestClient::new(bootstrap.router).await;

    let account = create_account(&mut context, &bootstrap.services, AccountType::Full, false).await;
    let keys = context
        .get_authentication_keys_for_account_id(account.get_id())
        .unwrap();

    bootstrap
        .services
        .account_service
        .put_transaction_verification_policy(
            account.get_id(),
            TransactionVerificationPolicy::Always,
        )
        .await
        .expect("Failed to set transaction verification policy");

    let resp = client
        .update_transaction_verification_policy(
            account.get_id(),
            true,
            true,
            &keys,
            &PutTransactionVerificationPolicyRequest {
                policy: TransactionVerificationPolicy::Never,
            },
        )
        .await;

    let Some(PrivilegedActionResponse::Pending(pending_resp)) = resp.body else {
        panic!("Expected Pending response");
    };

    let web_auth_token = {
        let instance_record: PrivilegedActionInstanceRecord<
            PutTransactionVerificationPolicyRequest,
        > = bootstrap
            .services
            .privileged_action_service
            .privileged_action_repository
            .fetch_by_id(&pending_resp.privileged_action_instance.id)
            .await
            .expect("Failed to fetch privileged action instance");

        let AuthorizationStrategyRecord::OutOfBand(oob_record) =
            &instance_record.authorization_strategy
        else {
            panic!("Expected OutOfBand authorization strategy record");
        };

        oob_record.web_auth_token.clone()
    };

    let resp = client
        .respond_to_out_of_band_privileged_action(
            &ProcessPrivilegedActionVerificationRequest::Confirm {
                privileged_action_type: PrivilegedActionType::LoosenTransactionVerificationPolicy,
                web_auth_token,
            },
        )
        .await;

    assert_eq!(resp.status_code, StatusCode::OK);
}
