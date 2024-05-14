use std::collections::{HashMap, HashSet};
use std::str::FromStr;
use std::sync::Arc;

use account::entities::Network;
use account::service::FetchAccountInput;
use account::spend_limit::{Money, SpendingLimit};
use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction as Psbt;
use bdk_utils::bdk::bitcoin::psbt::PartiallySignedTransaction;
use bdk_utils::bdk::bitcoin::Address;
use bdk_utils::bdk::bitcoin::{OutPoint, Txid};
use bdk_utils::bdk::database::AnyDatabase;
use bdk_utils::bdk::electrum_client::Error as ElectrumClientError;
use bdk_utils::bdk::wallet::{AddressIndex, AddressInfo};
use bdk_utils::bdk::Error::Electrum;
use bdk_utils::bdk::{KeychainKind, SignOptions, Wallet};
use bdk_utils::error::BdkUtilError;
use bdk_utils::{DescriptorKeyset, ElectrumRpcUris, TransactionBroadcasterTrait};
use external_identifier::ExternalIdentifier;
use http::StatusCode;
use mobile_pay::routes::SignTransactionData;
use mobile_pay::routes::SignTransactionResponse;
use mockall::mock;
use onboarding::routes::RotateSpendingKeysetRequest;
use serde_json::json;
use types::account::identifiers::{AccountId, KeysetId};
use types::currencies::CurrencyCode::{BTC, USD};
use ulid::Ulid;

use crate::tests;
use crate::tests::lib::{
    build_sweep_transaction, build_transaction_with_amount,
    create_default_account_with_predefined_wallet, create_inactive_spending_keyset_for_account,
    default_electrum_rpc_uris, gen_external_wallet_address,
};
use crate::tests::mobile_pay_tests::build_mobile_pay_request;
use crate::tests::requests::axum::TestClient;
use crate::tests::{gen_services, gen_services_with_overrides, GenServiceOverrides};

#[derive(Debug)]
struct SignTransactionTestVector {
    override_account_id: bool,
    with_inputs: Vec<OutPoint>,
    override_psbt: Option<&'static str>,
    expected_status: StatusCode,
    expect_broadcast: bool, // Should the test expect a transaction to be broadcast?
    broadcast_failure_mode: Option<BroadcastFailureMode>, // Should the test double return an ApiError?
}

mock! {
    TransactionBroadcaster { }
    impl TransactionBroadcasterTrait for TransactionBroadcaster {
        fn broadcast(
            &self,
            wallet: Wallet<AnyDatabase>,
            transaction: &mut PartiallySignedTransaction,
            rpc_uris: &ElectrumRpcUris
        ) -> Result<(), BdkUtilError>;
    }
}

async fn sign_transaction_test(vector: SignTransactionTestVector) {
    let mut broadcaster_mock = MockTransactionBroadcaster::new();
    broadcaster_mock
        .expect_broadcast()
        .times(if vector.expect_broadcast { 1 } else { 0 })
        .returning(move |_, _, _| match vector.broadcast_failure_mode {
            Some(BroadcastFailureMode::Generic) => Err(BdkUtilError::TransactionBroadcastError(
                Electrum(ElectrumClientError::Message("Fail".to_string())),
            )),
            // Specific protocol failure to test true positive case
            Some(BroadcastFailureMode::ElectrumDuplicateTx) => {
                Err(BdkUtilError::TransactionAlreadyInMempoolError)
            }
            // Generic protocol failure to guard against type-1 (false-positive) errors
            Some(BroadcastFailureMode::ElectrumGeneric) => {
                Err(BdkUtilError::TransactionBroadcastError(Electrum(
                    ElectrumClientError::Protocol(json!({"code": -77,"message": "Fail"})),
                )))
            }
            None => Ok(()),
        });

    let overrides = GenServiceOverrides::new().broadcaster(Arc::new(broadcaster_mock));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let (account, bdk_wallet) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();

    let app_signed_psbt = if let Some(override_psbt) = vector.override_psbt {
        override_psbt.to_string()
    } else {
        build_transaction_with_amount(
            &bdk_wallet,
            gen_external_wallet_address(),
            1_000,
            &vector.with_inputs,
        )
        .to_string()
    };

    let limit = SpendingLimit {
        active: true,
        amount: Money {
            amount: 5_000,
            currency_code: USD,
        },
        ..Default::default()
    };

    let account_id = if vector.override_account_id {
        AccountId::new(Ulid(400)).unwrap()
    } else {
        // In this case, we can setup mobile pay so our code can check some spend conditions
        let request = build_mobile_pay_request(limit);
        let mobile_pay_response = client.put_mobile_pay(&account.id, &request, &keys).await;
        assert_eq!(
            mobile_pay_response.status_code,
            StatusCode::OK,
            "{}",
            mobile_pay_response.body_string
        );
        account.id
    };

    let actual_response = client
        .sign_transaction_with_keyset(
            &account_id,
            &account.active_keyset_id,
            &SignTransactionData {
                psbt: app_signed_psbt,
            },
        )
        .await;
    assert_eq!(
        actual_response.status_code, vector.expected_status,
        "{}",
        actual_response.body_string
    );
}

tests! {
    runner = sign_transaction_test,
    test_sign_transaction_success: SignTransactionTestVector {
        override_account_id: false,
        with_inputs: vec![OutPoint {
            txid: Txid::from_str("42364e8ad22f93be1321efe2e53eca5337d1935b8c6aea8b648991321ff9d132").unwrap(),
            vout: 0,
        },
        OutPoint {
            txid: Txid::from_str("96b4d4506dc368fe704fa9875002c6a157fca460adc0d1fd0b4e0e6ed734fd64").unwrap(),
            vout: 0,
        }],
        override_psbt: None,
        expected_status: StatusCode::OK,
        expect_broadcast: true,
        broadcast_failure_mode: None
    },
    test_sign_transaction_with_no_existing_account: SignTransactionTestVector {
        override_account_id: true,
        with_inputs: vec![],
        override_psbt: Some("cHNidP8BAIkBAAAAARba0uJxgoOu4Qb4Hl2O4iC/zZhhEJZ7+5HuZDe8gkB5AQAAAAD/////AugDAAAAAAAAIgAgF5/lDEQhJZCBD9n6jaI46jvtUEg38/2j1s1PTw0lkcbugQEAAAAAACIAIBef5QxEISWQgQ/Z+o2iOOo77VBIN/P9o9bNT08NJZHGAAAAAAABAOoCAAAAAAEB97UeXCkIkrURS0D1VEse6bslADCfk6muDzWMawqsSkoAAAAAAP7///8CTW7uAwAAAAAWABT3EVvw7PVw4dEmLqWe/v9ETcBTtKCGAQAAAAAAIgAgF5/lDEQhJZCBD9n6jaI46jvtUEg38/2j1s1PTw0lkcYCRzBEAiBswJbFVv3ixdepzHonCMI1BujKEjxMHQ2qKmhVjVkiMAIgdcn1gzW+S4utbYQlfMHdVlpmK4T6onLbN+QCda1UVsYBIQJQtXaqHMYW0tBOmIEwjeBpTORXNrsO4FMWhqCf8feXXClTIgABASughgEAAAAAACIAIBef5QxEISWQgQ/Z+o2iOOo77VBIN/P9o9bNT08NJZHGAQVpUiECF0P0bwdqX4NvwdYkr9Vxkao2/0yB1rcqgHW1tXkVvlYhA4j/DyKUDUrb8kg9K4UAclJV/1Vgs/De/yOcz9L6e1AYIQPSBYIG9nN3JQbL65BnavWnmjgjoYn/Z6rmvHogngpbI1OuIgYCF0P0bwdqX4NvwdYkr9Vxkao2/0yB1rcqgHW1tXkVvlYEIgnl9CIGA4j/DyKUDUrb8kg9K4UAclJV/1Vgs/De/yOcz9L6e1AYBJhPJu0iBgPSBYIG9nN3JQbL65BnavWnmjgjoYn/Z6rmvHogngpbIwR2AHgNACICAhdD9G8Hal+Db8HWJK/VcZGqNv9Mgda3KoB1tbV5Fb5WBCIJ5fQiAgOI/w8ilA1K2/JIPSuFAHJSVf9VYLPw3v8jnM/S+ntQGASYTybtIgID0gWCBvZzdyUGy+uQZ2r1p5o4I6GJ/2eq5rx6IJ4KWyMEdgB4DQAiAgIXQ/RvB2pfg2/B1iSv1XGRqjb/TIHWtyqAdbW1eRW+VgQiCeX0IgIDiP8PIpQNStvySD0rhQByUlX/VWCz8N7/I5zP0vp7UBgEmE8m7SICA9IFggb2c3clBsvrkGdq9aeaOCOhif9nqua8eiCeClsjBHYAeA0A"),
        expected_status: StatusCode::NOT_FOUND,
        expect_broadcast: false,
        broadcast_failure_mode: None
    },
    test_sign_transaction_with_invalid_psbt: SignTransactionTestVector {
        override_account_id: false,
        with_inputs: vec![],
        override_psbt: Some("cHNidP8BAIkBAAAAARba0uJxgoOu4Qb4Hl2O4iC/zZhhEJZ7+5HuZDe8gkB5AQAAAAD/////AugDAAAAAAAAIgAgF5/lDEQhJZCBD9n6jaI46jvtUEg38/2j1s1PTw0lkcbugQEAAAAAACIAIBef5QxEISWQgQ/Z+o2iOOo77VBIN/P9o9bNT08NJZHGAAAAAAABAOoCAAAAAAEB97UeXCkIkrURS0D1VEse6bslADCfk6muDzWMawqsSkoAAAAAAP7///8CTW7uAwAAAAAWABT3EVvw7PVw4dEmLqWe/v9ETcBTtKCGAQAAAAAAIgAgF5/lDEQhJZCBD9n6jaI46jvtUEg38/2j1s1PTw0lkcYCRzBEAiBswJbFVv3ixdepzHonCMI1BujKEjxMHQ2qKmhVjVkiMAIgdcn1gzW+S4utbYQlfMHdVlpmK4T6onLbN+QCda1UVsYBIQJQtXaqHMYW0tBOmIEwjeBpTORXNrsO4FMWhqCf8feXXClTIgABASughgEAAAAAACIAIBef5QxEISWQgQ/Z+o2iOOo77VBIN/P9o9bNT08NJZHGAQVpUiECF0P0bwdqX4NvwdYkr9Vxkao2/0yB1rcqgHW1tXkVvlYhA4j/DyKUDUrb8kg9K4UAclJV/1Vgs/De/yOcz9L6e1AYIQPSBYIG9nN3JQbL65BnavWnmjgjoYn/Z6rmvHogngpbI1OuIgYCF0P0bwdqX4NvwdYkr9Vxkao2/0yB1rcqgHW1tXkVvlYEIgnl9CIGA4j/DyKUDUrb8kg9K4UAclJV/1Vgs/De/yOcz9L6e1AYBJhPJu0iBgPSBYIG9nN3JQbL65BnavWnmjgjoYn/Z6rmvHogngpbIwR2AHgNACICAhdD9G8Hal+Db8HWJK/VcZGqNv9Mgda3KoB1tbV5Fb5WBCIJ5fQiAgOI/w8ilA1K2/JIPSuFAHJSVf9VYLPw3v8jnM/S+ntQGASYTybtIgID0gWCBvZzdyUGy+uQZ2r1p5o4I6GJ/2eq5rx6IJ4KWyMEdgB4DQAiAgIXQ/RvB2pfg2/B1iSv1XGRqjb/TIHWtyqAdbW1eRW+VgQiCeX0IgIDiP8PIpQNStvySD0rhQByUlX/VWCz8N7/I5zP0vp7UBgEmE8m7SICA9IFggb2c3clBsvrkGdq9aeaOCOhif9nqua8eiCeClsjBHYAeA0A"),
        expected_status: StatusCode::BAD_REQUEST,
        expect_broadcast: false,
        broadcast_failure_mode: None
    },
    test_sign_transaction_fail_broadcast: SignTransactionTestVector {
        override_account_id: false,
        with_inputs: vec![
            OutPoint {
                txid: Txid::from_str("44a61ffbd14496e1b2f419665af8275521fc44679432cbba0b3bac80d3c4f3ab").unwrap(),
                vout: 0,
            }
        ],
        override_psbt: None,
        expected_status: StatusCode::INTERNAL_SERVER_ERROR,
        expect_broadcast: true,
        broadcast_failure_mode: Some(BroadcastFailureMode::Generic)
    },
    test_sign_transaction_fail_broadcast_electrum: SignTransactionTestVector {
        override_account_id: false,
        with_inputs: vec![OutPoint {
            txid: Txid::from_str("ca801cd4300832038cf47fabdd2c058469ede63f6de558374a4ecb351ac86e32").unwrap(),
            vout: 0,
        }],
        override_psbt: None,
        expected_status: StatusCode::INTERNAL_SERVER_ERROR,
        expect_broadcast: true,
        broadcast_failure_mode: Some(BroadcastFailureMode::ElectrumGeneric)
    },
    test_sign_transaction_fail_broadcast_duplicate_tx: SignTransactionTestVector {
        override_account_id: false,
        with_inputs: vec![OutPoint {
            txid: Txid::from_str("2675ee4faa0fa6201cf9a39854d8bf0379d275744a8c38271f9e3c4d44d5b87d").unwrap(),
            vout: 0,
        }],
        override_psbt: None,
        expected_status: StatusCode::CONFLICT,
        expect_broadcast: true,
        broadcast_failure_mode: Some(BroadcastFailureMode::ElectrumDuplicateTx)
    },
}

#[derive(Debug)]
enum BroadcastFailureMode {
    Generic,
    ElectrumDuplicateTx,
    ElectrumGeneric,
}

#[derive(Debug)]
struct SpendingLimitTransactionTestVector {
    max_spend_per_day: u64,
    should_self_addressed_tx: bool,
    invalidate_server_signature: bool,
    expected_status: StatusCode,
}

async fn spend_limit_transaction_test(vector: SpendingLimitTransactionTestVector) {
    let mut broadcaster_mock = MockTransactionBroadcaster::new();
    broadcaster_mock
        .expect_broadcast()
        .times(if vector.expected_status == StatusCode::OK {
            1 // even though we call sign twice, there is caching so the same psbt will only be sent by the server once
        } else {
            0
        })
        .returning(|_, _, _| Ok(()));

    let overrides = GenServiceOverrides::new().broadcaster(Arc::new(broadcaster_mock));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let (account, bdk_wallet) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .expect("Keys not found");
    let recipient = match vector.should_self_addressed_tx {
        true => bdk_wallet.get_address(AddressIndex::New).unwrap(),
        false => gen_external_wallet_address(),
    };
    let app_signed_psbt = build_transaction_with_amount(&bdk_wallet, recipient, 2_000, &[]);

    let limit = SpendingLimit {
        active: true,
        amount: Money {
            amount: vector.max_spend_per_day,
            currency_code: USD,
        },
        ..Default::default()
    };

    // Set up Mobile Pay to populate PrivilegedAction
    let mobile_pay_request = build_mobile_pay_request(limit.clone());
    let mobile_pay_response = client
        .put_mobile_pay(&account.id, &mobile_pay_request, &keys)
        .await;
    assert_eq!(
        mobile_pay_response.status_code,
        StatusCode::OK,
        "{}",
        mobile_pay_response.body_string
    );

    let check_psbt = |response_body: Option<SignTransactionResponse>| async {
        let mut server_and_app_signed_psbt = match response_body {
            Some(r) => Psbt::from_str(&r.tx).unwrap(),
            None => return,
        };

        let sign_options = SignOptions {
            remove_partial_sigs: false,
            ..SignOptions::default()
        };

        let is_finalized = bdk_wallet
            .finalize_psbt(&mut server_and_app_signed_psbt, sign_options)
            .unwrap();
        assert!(is_finalized);
    };

    let _srv_sig = if vector.invalidate_server_signature {
        "abeda3".to_string()
    } else {
        "".to_string()
    };

    let request_data = SignTransactionData {
        psbt: app_signed_psbt.to_string(),
    };

    let response = client
        .sign_transaction_with_active_keyset(&account.id, &request_data)
        .await;
    assert_eq!(
        vector.expected_status, response.status_code,
        "{}",
        response.body_string
    );
    check_psbt(response.body).await;

    let response = client
        .sign_transaction_with_keyset(&account.id, &account.active_keyset_id, &request_data)
        .await;
    assert_eq!(
        vector.expected_status, response.status_code,
        "{}",
        response.body_string
    );
    check_psbt(response.body).await;
}

tests! {
    runner = spend_limit_transaction_test,
    test_sign_transaction_with_self_addressed_transaction_and_no_movable_funds: SpendingLimitTransactionTestVector {
        max_spend_per_day: 0,
        should_self_addressed_tx: true,
        invalidate_server_signature: false,
        expected_status: StatusCode::BAD_REQUEST,
    },
    test_sign_transaction_below_spend_limit: SpendingLimitTransactionTestVector {
        max_spend_per_day: 2033,
        should_self_addressed_tx: false,
        invalidate_server_signature: false,
        expected_status: StatusCode::OK,
    },
    test_sign_transaction_above_spend_limit_to_self: SpendingLimitTransactionTestVector {
        max_spend_per_day: 0,
        should_self_addressed_tx: true,
        invalidate_server_signature: false,
        expected_status: StatusCode::BAD_REQUEST,
    },
    test_sign_transaction_above_spend_limit_to_external: SpendingLimitTransactionTestVector {
        max_spend_per_day: 0,
        should_self_addressed_tx: false,
        invalidate_server_signature: false,
        expected_status: StatusCode::BAD_REQUEST,
    },
}

struct SweepBypassTransactionTestVector {
    spending_limit: Option<SpendingLimit>,
    target_external_address: bool,
    expected_status: StatusCode,
}

tests! {
    runner = test_bypass_mobile_spend_limit_for_sweep,
    test_sweep_with_none_spending_limit: SweepBypassTransactionTestVector {
        spending_limit: None,
        target_external_address: false,
        expected_status: StatusCode::OK
    },
    test_sweep_with_inactive_spending_limit: SweepBypassTransactionTestVector {
        spending_limit: Some(SpendingLimit { active: false, amount: Money { amount: 42, currency_code: USD }, ..Default::default() }),
        target_external_address: false,
        expected_status: StatusCode::OK

    },
    test_sweep_with_spending_limit_below_balance: SweepBypassTransactionTestVector {
        spending_limit: Some(SpendingLimit { active: true, amount: Money { amount: 0, currency_code: USD }, ..Default::default() }),
        target_external_address: false,
        expected_status: StatusCode::OK

    },
    test_sweep_to_external_address: SweepBypassTransactionTestVector {
        spending_limit: None,
        target_external_address: true,
        expected_status: StatusCode::BAD_REQUEST
    },
}

async fn test_bypass_mobile_spend_limit_for_sweep(vector: SweepBypassTransactionTestVector) {
    let mut broadcaster_mock = MockTransactionBroadcaster::new();
    broadcaster_mock
        .expect_broadcast()
        .times(if vector.expected_status == StatusCode::OK {
            1
        } else {
            0
        })
        .returning(|_, _, _| Ok(()));

    let overrides = GenServiceOverrides::new().broadcaster(Arc::new(broadcaster_mock));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let (account, bdk_wallet) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .expect("Keys not found");
    let inactive_keyset_id = account.active_keyset_id;
    let active_keyset_id = create_inactive_spending_keyset_for_account(
        &context,
        &client,
        &account.id,
        Network::BitcoinSignet,
    )
    .await;
    let response = client
        .rotate_to_spending_keyset(
            &account.id.to_string(),
            &active_keyset_id.to_string(),
            &RotateSpendingKeysetRequest {},
            &keys,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );

    let account = bootstrap
        .services
        .account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account.id,
        })
        .await
        .unwrap();
    let active_descriptor_keyset: DescriptorKeyset = account
        .spending_keysets
        .get(&active_keyset_id)
        .unwrap()
        .to_owned()
        .into();
    let electrum_rpc_uris = default_electrum_rpc_uris();
    let active_wallet = active_descriptor_keyset
        .generate_wallet(true, &electrum_rpc_uris)
        .unwrap();
    let recipient = if vector.target_external_address {
        gen_external_wallet_address()
    } else {
        active_wallet.get_address(AddressIndex::Peek(0)).unwrap()
    };

    let app_signed_psbt = build_sweep_transaction(&bdk_wallet, recipient);

    if let Some(limit) = vector.spending_limit.clone() {
        // Set up Mobile Pay
        let mobile_pay_request = build_mobile_pay_request(limit.clone());
        let mobile_pay_response = client
            .put_mobile_pay(&account.id, &mobile_pay_request, &keys)
            .await;
        assert_eq!(
            mobile_pay_response.status_code,
            StatusCode::OK,
            "{}",
            mobile_pay_response.body_string
        );
    }

    let check_psbt = |response_body: Option<SignTransactionResponse>| async {
        let mut server_and_app_signed_psbt = match response_body {
            Some(r) => Psbt::from_str(&r.tx).unwrap(),
            None => return,
        };

        let sign_options = SignOptions {
            remove_partial_sigs: false,
            ..SignOptions::default()
        };

        let is_finalized = bdk_wallet
            .finalize_psbt(&mut server_and_app_signed_psbt, sign_options)
            .unwrap();
        assert!(is_finalized);
    };

    let request_data = SignTransactionData {
        psbt: app_signed_psbt.to_string(),
    };

    // Send the sweep transaction
    let response = client
        .sign_transaction_with_keyset(&account.id, &inactive_keyset_id, &request_data)
        .await;
    assert_eq!(
        vector.expected_status, response.status_code,
        "{}",
        response.body_string
    );
    check_psbt(response.body).await;

    // Ensure PSBT caching works, by sending an invalid keyset with the same psbt
    if vector.expected_status == StatusCode::OK {
        let response = client
            .sign_transaction_with_keyset(&account.id, &KeysetId::gen().unwrap(), &request_data)
            .await;
        assert_eq!(
            vector.expected_status, response.status_code,
            "{}",
            response.body_string
        );
        check_psbt(response.body).await;
    }
}

#[tokio::test]
async fn test_disabled_mobile_pay_spend_limit() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account, bdk_wallet) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .expect("Keys not found");
    let recipient = gen_external_wallet_address();
    let app_signed_psbt = build_transaction_with_amount(&bdk_wallet, recipient, 2_000, &[]);

    let limit = SpendingLimit {
        active: false,
        amount: Money {
            amount: 42,
            currency_code: USD,
        },
        ..Default::default()
    };

    // Set Mobile Pay to disabled
    let mobile_pay_request = build_mobile_pay_request(limit.clone());
    let mobile_pay_response = client
        .put_mobile_pay(&account.id, &mobile_pay_request, &keys)
        .await;
    assert_eq!(
        mobile_pay_response.status_code,
        StatusCode::OK,
        "{}",
        mobile_pay_response.body_string
    );

    // Attempt Mobile Pay
    let actual_response = client
        .sign_transaction_with_keyset(
            &account.id,
            &account.active_keyset_id,
            &SignTransactionData {
                psbt: app_signed_psbt.to_string(),
            },
        )
        .await;
    assert_eq!(actual_response.status_code, StatusCode::FORBIDDEN,);
}

#[tokio::test]
async fn test_mismatched_account_id_keyproof() {
    let broadcaster_mock = MockTransactionBroadcaster::new();

    let overrides = GenServiceOverrides::new().broadcaster(Arc::new(broadcaster_mock));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let (account, bdk_wallet) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();

    // Attempt to set up Mobile Pay with a different account id
    let limit = SpendingLimit {
        active: true,
        amount: Money {
            amount: 5_000,
            currency_code: USD,
        },
        ..Default::default()
    };

    // Should fail keyproof check
    let request = build_mobile_pay_request(limit);
    let mobile_pay_response = client
        .put_mobile_pay_with_keyproof_account_id(
            &account.id,
            &AccountId::new(Ulid(400)).unwrap(),
            &request,
            &keys,
        )
        .await;
    assert_eq!(
        mobile_pay_response.status_code,
        StatusCode::UNAUTHORIZED,
        "{}",
        mobile_pay_response.body_string
    );

    // Set up Mobile Pay with correct account id
    let mobile_pay_response = client.put_mobile_pay(&account.id, &request, &keys).await;
    // Ensure Mobile Pay was set up correctly
    assert_eq!(
        mobile_pay_response.status_code,
        StatusCode::OK,
        "{}",
        mobile_pay_response.body_string
    );

    // Attempt to sign a transaction with a different account id
    let app_signed_psbt =
        build_transaction_with_amount(&bdk_wallet, gen_external_wallet_address(), 2_000, &[])
            .to_string();
    let response = client
        .sign_transaction_with_keyset_and_keyproof_account_id(
            &account.id,
            &AccountId::new(Ulid(400)).unwrap(),
            &account.active_keyset_id,
            &SignTransactionData {
                psbt: app_signed_psbt,
            },
        )
        .await;

    // Should fail keyproof check
    assert_eq!(
        response.status_code,
        StatusCode::UNAUTHORIZED,
        "{}",
        response.body_string
    );
}

#[tokio::test]
async fn test_fail_sends_to_sanctioned_address() {
    let blocked_address_info = AddressInfo {
        index: 0,
        address: Address::from_str("tb1q763lyaz775cnd86mf7v59t4589fpejfrxwg0c2")
            .unwrap()
            .assume_checked(),
        keychain: KeychainKind::External,
    };
    let blocked_hash_set = HashSet::from([blocked_address_info.clone().to_string()]);
    let overrides = GenServiceOverrides::new().blocked_addresses(blocked_hash_set);

    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;
    let (account, bdk_wallet) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();

    let limit = SpendingLimit {
        active: true,
        amount: Money {
            amount: 5_000,
            currency_code: USD,
        },
        ..Default::default()
    };

    // Setup Mobile Pay
    let request = build_mobile_pay_request(limit);
    let mobile_pay_response = client.put_mobile_pay(&account.id, &request, &keys).await;
    assert_eq!(
        mobile_pay_response.status_code,
        StatusCode::OK,
        "{}",
        mobile_pay_response.body_string
    );

    let app_signed_psbt =
        build_transaction_with_amount(&bdk_wallet, blocked_address_info, 2_000, &[]).to_string();

    let request_data = SignTransactionData {
        psbt: app_signed_psbt.to_string(),
    };

    let response = client
        .sign_transaction_with_keyset(&account.id, &account.active_keyset_id, &request_data)
        .await;

    assert_eq!(
        response.status_code,
        StatusCode::BAD_REQUEST,
        "{}",
        response.body_string
    );
}

#[tokio::test]
async fn test_fail_to_send_if_kill_switch_is_on() {
    let feature_flag_override =
        HashMap::from([("f8e-mobile-pay-enabled".to_string(), "false".to_string())]);
    let overrides = GenServiceOverrides::new().feature_flags(feature_flag_override);

    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;
    let (account, bdk_wallet) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();
    assert_eq!(account.spending_limit, None);

    let spend_limit = SpendingLimit {
        active: true,
        amount: Money {
            amount: 109_798,
            currency_code: BTC,
        },
        ..Default::default()
    };
    let request = build_mobile_pay_request(spend_limit.clone());
    let response = client.put_mobile_pay(&account.id, &request, &keys).await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );

    let app_signed_psbt =
        build_transaction_with_amount(&bdk_wallet, gen_external_wallet_address(), 2_000, &[])
            .to_string();
    let actual_response = client
        .sign_transaction_with_keyset(
            &account.id,
            &account.active_keyset_id,
            &SignTransactionData {
                psbt: app_signed_psbt,
            },
        )
        .await;
    assert_eq!(actual_response.status_code, StatusCode::FORBIDDEN,);
}
