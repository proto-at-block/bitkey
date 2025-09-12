use std::{
    collections::{HashMap, HashSet},
    env,
    str::FromStr,
    sync::Arc,
};

use crypto::chaincode_delegation::{psbt_with_tweaks, HwAccountLevelDescriptorPublicKeys, Keyset};
use http::StatusCode;

use http_server::middlewares::wsm;
use mockall::mock;
use serde_json::json;
use ulid::Ulid;

use account::service::{tests::default_electrum_rpc_uris, FetchAccountInput};
use bdk_utils::{
    bdk::{
        self,
        bitcoin::{
            bip32::{ChildNumber, DerivationPath, ExtendedPrivKey, ExtendedPubKey, KeySource},
            ecdsa,
            hashes::{sha512, Hash, HashEngine, Hmac, HmacEngine},
            key::Secp256k1,
            psbt::{PartiallySignedTransaction, PartiallySignedTransaction as Psbt},
            secp256k1::{All, Message},
            Address, Amount, Network as BdkNetwork, OutPoint, Txid,
        },
        blockchain::{rpc::Auth, Blockchain, ConfigurableBlockchain, RpcBlockchain, RpcConfig},
        database::{AnyDatabase, MemoryDatabase},
        electrum_client::Error as ElectrumClientError,
        keys::{DerivableKey, DescriptorKey, DescriptorPublicKey, DescriptorSecretKey},
        miniscript::{psbt::PsbtExt, Segwitv0},
        wallet::{wallet_name_from_descriptor, AddressIndex, AddressInfo},
        Error::Electrum,
        KeychainKind, SignOptions, Wallet,
    },
    error::BdkUtilError,
    generate_block, treasury_fund_address, DescriptorKeyset, ElectrumRpcUris,
    TransactionBroadcasterTrait,
};
use external_identifier::ExternalIdentifier;
use mobile_pay::routes::{SignTransactionData, SignTransactionResponse};
use onboarding::{routes::RotateSpendingKeysetRequest, routes_v2::CreateAccountRequestV2};
use rstest::rstest;
use types::{
    account::{
        bitcoin::Network,
        entities::v2::{FullAccountAuthKeysInputV2, SpendingKeysetInputV2},
        identifiers::AccountId,
        money::Money,
        spend_limit::SpendingLimit,
    },
    currencies::CurrencyCode::{self, USD},
    transaction_verification::{
        entities::{
            BitcoinDisplayUnit, PolicyUpdate, PolicyUpdateMoney, TransactionVerification,
            TransactionVerificationPending,
        },
        router::PutTransactionVerificationPolicyRequest,
        TransactionVerificationId,
    },
};
use wsm_rust_client::SigningService;

use crate::tests::lib::{
    build_sweep_transaction, build_transaction_with_amount,
    create_default_account_with_predefined_wallet, create_inactive_spending_keyset_for_account,
    create_new_authkeys, gen_external_wallet_address,
};
use crate::tests::mobile_pay_tests::build_mobile_pay_request;
use crate::tests::requests::axum::TestClient;
use crate::tests::{gen_services, gen_services_with_overrides, GenServiceOverrides};

fn check_psbt(response_body: Option<SignTransactionResponse>, bdk_wallet: &Wallet<AnyDatabase>) {
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
}

mock! {
    TransactionBroadcaster { }
    impl TransactionBroadcasterTrait for TransactionBroadcaster {
        fn broadcast(
            &self,
            network: bdk_utils::bdk::bitcoin::Network,
            transaction: &mut PartiallySignedTransaction,
            rpc_uris: &ElectrumRpcUris
        ) -> Result<(), BdkUtilError>;
    }
}

async fn sign_transaction_test(
    override_account_id: bool,
    with_inputs: Vec<OutPoint>,
    override_psbt: Option<&'static str>,
    expected_status: StatusCode,
    expect_broadcast: bool, // Should the test expect a transaction to be broadcast?
    broadcast_failure_mode: Option<BroadcastFailureMode>, // Should the test double return an ApiError?
) {
    let mut broadcaster_mock = MockTransactionBroadcaster::new();
    broadcaster_mock
        .expect_broadcast()
        .times(if expect_broadcast { 1 } else { 0 })
        .returning(move |_, _, _| match broadcast_failure_mode {
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

    let app_signed_psbt = if let Some(override_psbt) = override_psbt {
        override_psbt.to_string()
    } else {
        build_transaction_with_amount(
            &bdk_wallet,
            gen_external_wallet_address(),
            1_000,
            &with_inputs,
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

    let account_id = if override_account_id {
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
                ..Default::default()
            },
        )
        .await;
    assert_eq!(
        actual_response.status_code, expected_status,
        "{}",
        actual_response.body_string
    );
}

#[rstest]
#[case::test_sign_transaction_success(
    false,
    vec![OutPoint {
        txid: Txid::from_str("42364e8ad22f93be1321efe2e53eca5337d1935b8c6aea8b648991321ff9d132").unwrap(),
        vout: 0,
    },
    OutPoint {
        txid: Txid::from_str("96b4d4506dc368fe704fa9875002c6a157fca460adc0d1fd0b4e0e6ed734fd64").unwrap(),
        vout: 0,
    }],
    None,
    StatusCode::OK,
    true,
    None
)]
#[case::test_sign_transaction_with_no_existing_account(
    true,
    vec![],
    Some("cHNidP8BAIkBAAAAARba0uJxgoOu4Qb4Hl2O4iC/zZhhEJZ7+5HuZDe8gkB5AQAAAAD/////AugDAAAAAAAAIgAgF5/lDEQhJZCBD9n6jaI46jvtUEg38/2j1s1PTw0lkcbugQEAAAAAACIAIBef5QxEISWQgQ/Z+o2iOOo77VBIN/P9o9bNT08NJZHGAAAAAAABAOoCAAAAAAEB97UeXCkIkrURS0D1VEse6bslADCfk6muDzWMawqsSkoAAAAAAP7///8CTW7uAwAAAAAWABT3EVvw7PVw4dEmLqWe/v9ETcBTtKCGAQAAAAAAIgAgF5/lDEQhJZCBD9n6jaI46jvtUEg38/2j1s1PTw0lkcYCRzBEAiBswJbFVv3ixdepzHonCMI1BujKEjxMHQ2qKmhVjVkiMAIgdcn1gzW+S4utbYQlfMHdVlpmK4T6onLbN+QCda1UVsYBIQJQtXaqHMYW0tBOmIEwjeBpTORXNrsO4FMWhqCf8feXXClTIgABASughgEAAAAAACIAIBef5QxEISWQgQ/Z+o2iOOo77VBIN/P9o9bNT08NJZHGAQVpUiECF0P0bwdqX4NvwdYkr9Vxkao2/0yB1rcqgHW1tXkVvlYhA4j/DyKUDUrb8kg9K4UAclJV/1Vgs/De/yOcz9L6e1AYIQPSBYIG9nN3JQbL65BnavWnmjgjoYn/Z6rmvHogngpbI1OuIgYCF0P0bwdqX4NvwdYkr9Vxkao2/0yB1rcqgHW1tXkVvlYEIgnl9CIGA4j/DyKUDUrb8kg9K4UAclJV/1Vgs/De/yOcz9L6e1AYBJhPJu0iBgPSBYIG9nN3JQbL65BnavWnmjgjoYn/Z6rmvHogngpbIwR2AHgNACICAhdD9G8Hal+Db8HWJK/VcZGqNv9Mgda3KoB1tbV5Fb5WBCIJ5fQiAgOI/w8ilA1K2/JIPSuFAHJSVf9VYLPw3v8jnM/S+ntQGASYTybtIgID0gWCBvZzdyUGy+uQZ2r1p5o4I6GJ/2eq5rx6IJ4KWyMEdgB4DQAiAgIXQ/RvB2pfg2/B1iSv1XGRqjb/TIHWtyqAdbW1eRW+VgQiCeX0IgIDiP8PIpQNStvySD0rhQByUlX/VWCz8N7/I5zP0vp7UBgEmE8m7SICA9IFggb2c3clBsvrkGdq9aeaOCOhif9nqua8eiCeClsjBHYAeA0A"),
    StatusCode::NOT_FOUND,
    false,
    None
)]
#[case::test_sign_transaction_with_invalid_psbt(
    false,
    vec![],
    Some("cHNidP8BAIkBAAAAARba0uJxgoOu4Qb4Hl2O4iC/zZhhEJZ7+5HuZDe8gkB5AQAAAAD/////AugDAAAAAAAAIgAgF5/lDEQhJZCBD9n6jaI46jvtUEg38/2j1s1PTw0lkcbugQEAAAAAACIAIBef5QxEISWQgQ/Z+o2iOOo77VBIN/P9o9bNT08NJZHGAAAAAAABAOoCAAAAAAEB97UeXCkIkrURS0D1VEse6bslADCfk6muDzWMawqsSkoAAAAAAP7///8CTW7uAwAAAAAWABT3EVvw7PVw4dEmLqWe/v9ETcBTtKCGAQAAAAAAIgAgF5/lDEQhJZCBD9n6jaI46jvtUEg38/2j1s1PTw0lkcYCRzBEAiBswJbFVv3ixdepzHonCMI1BujKEjxMHQ2qKmhVjVkiMAIgdcn1gzW+S4utbYQlfMHdVlpmK4T6onLbN+QCda1UVsYBIQJQtXaqHMYW0tBOmIEwjeBpTORXNrsO4FMWhqCf8feXXClTIgABASughgEAAAAAACIAIBef5QxEISWQgQ/Z+o2iOOo77VBIN/P9o9bNT08NJZHGAQVpUiECF0P0bwdqX4NvwdYkr9Vxkao2/0yB1rcqgHW1tXkVvlYhA4j/DyKUDUrb8kg9K4UAclJV/1Vgs/De/yOcz9L6e1AYIQPSBYIG9nN3JQbL65BnavWnmjgjoYn/Z6rmvHogngpbI1OuIgYCF0P0bwdqX4NvwdYkr9Vxkao2/0yB1rcqgHW1tXkVvlYEIgnl9CIGA4j/DyKUDUrb8kg9K4UAclJV/1Vgs/De/yOcz9L6e1AYBJhPJu0iBgPSBYIG9nN3JQbL65BnavWnmjgjoYn/Z6rmvHogngpbIwR2AHgNACICAhdD9G8Hal+Db8HWJK/VcZGqNv9Mgda3KoB1tbV5Fb5WBCIJ5fQiAgOI/w8ilA1K2/JIPSuFAHJSVf9VYLPw3v8jnM/S+ntQGASYTybtIgID0gWCBvZzdyUGy+uQZ2r1p5o4I6GJ/2eq5rx6IJ4KWyMEdgB4DQAiAgIXQ/RvB2pfg2/B1iSv1XGRqjb/TIHWtyqAdbW1eRW+VgQiCeX0IgIDiP8PIpQNStvySD0rhQByUlX/VWCz8N7/I5zP0vp7UBgEmE8m7SICA9IFggb2c3clBsvrkGdq9aeaOCOhif9nqua8eiCeClsjBHYAeA0A"),
    StatusCode::BAD_REQUEST,
    false,
    None
)]
#[case::test_sign_transaction_fail_broadcast(
    false,
    vec![
        OutPoint {
            txid: Txid::from_str("44a61ffbd14496e1b2f419665af8275521fc44679432cbba0b3bac80d3c4f3ab").unwrap(),
            vout: 0,
        }
    ],
    None,
    StatusCode::INTERNAL_SERVER_ERROR,
    true,
    Some(BroadcastFailureMode::Generic)
)]
#[case::test_sign_transaction_fail_broadcast_electrum(
    false,
    vec![OutPoint {
        txid: Txid::from_str("ca801cd4300832038cf47fabdd2c058469ede63f6de558374a4ecb351ac86e32").unwrap(),
        vout: 0,
    }],
    None,
    StatusCode::INTERNAL_SERVER_ERROR,
    true,
    Some(BroadcastFailureMode::ElectrumGeneric)
)]
#[case::test_sign_transaction_fail_broadcast_duplicate_tx(
    false,
    vec![OutPoint {
        txid: Txid::from_str("2675ee4faa0fa6201cf9a39854d8bf0379d275744a8c38271f9e3c4d44d5b87d").unwrap(),
        vout: 0,
    }],
    None,
    StatusCode::CONFLICT,
    true,
    Some(BroadcastFailureMode::ElectrumDuplicateTx)
)]
#[tokio::test]
async fn test_sign_transaction(
    #[case] override_account_id: bool,
    #[case] with_inputs: Vec<OutPoint>,
    #[case] override_psbt: Option<&'static str>,
    #[case] expected_status: StatusCode,
    #[case] expect_broadcast: bool,
    #[case] broadcast_failure_mode: Option<BroadcastFailureMode>,
) {
    sign_transaction_test(
        override_account_id,
        with_inputs,
        override_psbt,
        expected_status,
        expect_broadcast,
        broadcast_failure_mode,
    )
    .await
}

#[rstest::rstest]
#[case::never(PolicyUpdate::Never, false, true)]
#[case::always_without_verification(PolicyUpdate::Always, false, false)]
#[case::always_with_verification(PolicyUpdate::Always, true, true)]
#[case::threshold_over_balance(PolicyUpdate::Threshold(PolicyUpdateMoney{
    amount_sats: 10_000,
    amount_fiat: 1_179,
    currency_code: USD,
}), false, true)]
#[case::threshold_under_balance_without_verification(PolicyUpdate::Threshold(PolicyUpdateMoney{
    amount_sats: 5,
    amount_fiat: 0,
    currency_code: USD,
}), false, false)]
#[case::threshold_under_balance_with_verification(PolicyUpdate::Threshold(PolicyUpdateMoney{
    amount_sats: 5,
    amount_fiat: 0,
    currency_code: USD,
}), true, true)]
#[tokio::test]
async fn test_sign_transaction_with_transaction_verification(
    #[case] policy: PolicyUpdate,
    #[case] include_verification_id: bool,
    #[case] expect_success: bool,
) {
    let mut broadcaster_mock = MockTransactionBroadcaster::new();
    broadcaster_mock
        .expect_broadcast()
        .times(if expect_success { 1 } else { 0 })
        .returning(move |_, _, _| Ok(()));

    let overrides = GenServiceOverrides::new().broadcaster(Arc::new(broadcaster_mock));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let (account, bdk_wallet) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();

    let app_signed_psbt =
        build_transaction_with_amount(&bdk_wallet, gen_external_wallet_address(), 2_000, &[]);

    let limit = SpendingLimit {
        active: true,
        amount: Money {
            amount: 5_000,
            currency_code: USD,
        },
        ..Default::default()
    };

    // In this case, we can setup mobile pay so our code can check some spend conditions
    let request = build_mobile_pay_request(limit);
    let mobile_pay_response = client.put_mobile_pay(&account.id, &request, &keys).await;
    assert_eq!(
        mobile_pay_response.status_code,
        StatusCode::OK,
        "{}",
        mobile_pay_response.body_string
    );

    let update_policy_request = client
        .update_transaction_verification_policy(
            &account.id,
            true,
            false,
            &keys,
            &PutTransactionVerificationPolicyRequest { policy },
        )
        .await;
    assert_eq!(
        update_policy_request.status_code,
        StatusCode::OK,
        "{}",
        update_policy_request.body_string
    );

    let grant = if include_verification_id {
        let verification_id =
            TransactionVerificationId::gen().expect("Failed to generate verification id");
        let pending_verification = TransactionVerificationPending::new(
            verification_id.clone(),
            account.id.clone(),
            app_signed_psbt.clone(),
            CurrencyCode::USD,
            BitcoinDisplayUnit::Bitcoin,
        );
        let confirmation_token = pending_verification.confirmation_token.clone();
        bootstrap
            .services
            .transaction_verification_repository
            .persist(&TransactionVerification::Pending(pending_verification))
            .await
            .expect("Failed to persist verification");
        let TransactionVerification::Success(success) = bootstrap
            .services
            .transaction_verification_service
            .verify_with_confirmation_token(&verification_id, &confirmation_token)
            .await
            .expect("Failed to verify with confirmation token")
        else {
            panic!("Expected success");
        };
        Some(success.signed_hw_grant)
    } else {
        None
    };

    let response = client
        .sign_transaction_with_keyset(
            &account.id,
            &account.active_keyset_id,
            &SignTransactionData {
                psbt: app_signed_psbt.to_string(),
                grant,
            },
        )
        .await;
    assert_eq!(
        response.status_code,
        if expect_success {
            StatusCode::OK
        } else {
            StatusCode::BAD_REQUEST
        },
        "{}",
        response.body_string
    );

    if expect_success {
        check_psbt(response.body, &bdk_wallet);
    }
}

#[derive(Debug)]
enum BroadcastFailureMode {
    Generic,
    ElectrumDuplicateTx,
    ElectrumGeneric,
}

async fn spend_limit_transaction_test(
    max_spend_per_day: u64,
    should_self_addressed_tx: bool,
    invalidate_server_signature: bool,
    expected_status: StatusCode,
) {
    let mut broadcaster_mock = MockTransactionBroadcaster::new();
    broadcaster_mock
        .expect_broadcast()
        .times(if expected_status == StatusCode::OK {
            2
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
    let recipient = match should_self_addressed_tx {
        true => bdk_wallet.get_address(AddressIndex::New).unwrap(),
        false => gen_external_wallet_address(),
    };
    let app_signed_psbt = build_transaction_with_amount(&bdk_wallet, recipient, 2_000, &[]);

    let limit = SpendingLimit {
        active: true,
        amount: Money {
            amount: max_spend_per_day,
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

    let _srv_sig = if invalidate_server_signature {
        "abeda3".to_string()
    } else {
        "".to_string()
    };

    let request_data = SignTransactionData {
        psbt: app_signed_psbt.to_string(),
        ..Default::default()
    };

    let response = client
        .sign_transaction_with_keyset(&account.id, &account.active_keyset_id, &request_data)
        .await;
    assert_eq!(
        expected_status, response.status_code,
        "{}",
        response.body_string
    );
    check_psbt(response.body, &bdk_wallet);

    let response = client
        .sign_transaction_with_keyset(&account.id, &account.active_keyset_id, &request_data)
        .await;
    assert_eq!(
        expected_status, response.status_code,
        "{}",
        response.body_string
    );
    check_psbt(response.body, &bdk_wallet);
}

#[rstest]
#[case::self_addressed_no_movable_funds(0, true, false, StatusCode::BAD_REQUEST)]
#[case::below_spend_limit(2033, false, false, StatusCode::OK)]
#[case::above_limit_to_self(0, true, false, StatusCode::BAD_REQUEST)]
#[case::above_limit_to_external(0, false, false, StatusCode::BAD_REQUEST)]
#[tokio::test]
async fn test_spend_limit_transaction(
    #[case] max_spend_per_day: u64,
    #[case] should_self_addressed_tx: bool,
    #[case] invalidate_server_signature: bool,
    #[case] expected_status: StatusCode,
) {
    spend_limit_transaction_test(
        max_spend_per_day,
        should_self_addressed_tx,
        invalidate_server_signature,
        expected_status,
    )
    .await
}

#[rstest]
#[case::none_spending_limit(None, false, StatusCode::OK)]
#[case::inactive_spending_limit(
    Some(SpendingLimit { active: false, amount: Money { amount: 42, currency_code: USD }, ..Default::default() }),
    false,
    StatusCode::OK
)]
#[case::limit_below_balance(
    Some(SpendingLimit { active: true, amount: Money { amount: 0, currency_code: USD }, ..Default::default() }),
    false,
    StatusCode::OK
)]
#[case::to_external_address(None, true, StatusCode::BAD_REQUEST)]
#[tokio::test]
async fn test_bypass_mobile_spend_limit_for_sweep(
    #[case] spending_limit: Option<SpendingLimit>,
    #[case] target_external_address: bool,
    #[case] expected_status: StatusCode,
) {
    bypass_mobile_spend_limit_for_sweep_test(
        spending_limit,
        target_external_address,
        expected_status,
    )
    .await
}

async fn bypass_mobile_spend_limit_for_sweep_test(
    spending_limit: Option<SpendingLimit>,
    target_external_address: bool,
    expected_status: StatusCode,
) {
    let mut broadcaster_mock = MockTransactionBroadcaster::new();
    broadcaster_mock
        .expect_broadcast()
        .times(if expected_status == StatusCode::OK {
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
        .optional_legacy_multi_sig()
        .unwrap()
        .to_owned()
        .into();
    let electrum_rpc_uris = default_electrum_rpc_uris();
    let active_wallet = active_descriptor_keyset
        .generate_wallet(true, &electrum_rpc_uris)
        .unwrap();
    let recipient = if target_external_address {
        gen_external_wallet_address()
    } else {
        active_wallet.get_address(AddressIndex::Peek(0)).unwrap()
    };

    let app_signed_psbt = build_sweep_transaction(&bdk_wallet, recipient);

    if let Some(limit) = spending_limit.clone() {
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

    let request_data = SignTransactionData {
        psbt: app_signed_psbt.to_string(),
        ..Default::default()
    };

    // Send the sweep transaction
    let response = client
        .sign_transaction_with_keyset(&account.id, &inactive_keyset_id, &request_data)
        .await;
    assert_eq!(
        expected_status, response.status_code,
        "{}",
        response.body_string
    );
    check_psbt(response.body, &bdk_wallet);
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
                ..Default::default()
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
                ..Default::default()
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
        ..Default::default()
    };

    let response = client
        .sign_transaction_with_keyset(&account.id, &account.active_keyset_id, &request_data)
        .await;

    assert_eq!(
        response.status_code,
        StatusCode::UNAVAILABLE_FOR_LEGAL_REASONS,
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
            currency_code: USD,
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
                ..Default::default()
            },
        )
        .await;
    assert_eq!(actual_response.status_code, StatusCode::FORBIDDEN,);
}

#[tokio::test]
async fn test_fail_if_signed_with_different_wallet() {
    let (mut context, bootstrap) = gen_services().await;

    let client = TestClient::new(bootstrap.router).await;
    let (account, bdk_wallet) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;
    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();

    // Set some spendig limit for this account
    let spend_limit = SpendingLimit {
        active: true,
        amount: Money {
            amount: 109_798,
            currency_code: USD,
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

    let secp = Secp256k1::new();
    let master_xprv = ExtendedPrivKey::new_master(BdkNetwork::Bitcoin, &[0; 32]).unwrap();
    let xpub = ExtendedPubKey::from_priv(&secp, &master_xprv);

    // Test PSBT without signatures
    {
        let mut psbt_without_signatures =
            build_transaction_with_amount(&bdk_wallet, gen_external_wallet_address(), 2_000, &[]);
        for input in &mut psbt_without_signatures.inputs {
            input.partial_sigs.clear();
        }

        let response = client
            .sign_transaction_with_keyset(
                &account.id,
                &account.active_keyset_id,
                &SignTransactionData {
                    psbt: psbt_without_signatures.to_string(),
                    ..Default::default()
                },
            )
            .await;
        assert_eq!(response.status_code, StatusCode::INTERNAL_SERVER_ERROR,);
        assert!(response
            .body_string
            .contains("Input does not only have one signature"));
    }

    // Test PSBT with >1 signatures
    {
        let mut psbt_with_two_signatures =
            build_transaction_with_amount(&bdk_wallet, gen_external_wallet_address(), 1_000, &[]);
        for input in &mut psbt_with_two_signatures.inputs {
            let sig = ecdsa::Signature::sighash_all(secp.sign_ecdsa(
                &Message::from_slice(&[0; 32]).unwrap(),
                &master_xprv.private_key,
            ));

            input.partial_sigs.insert(xpub.to_pub(), sig);
        }

        let response = client
            .sign_transaction_with_keyset(
                &account.id,
                &account.active_keyset_id,
                &SignTransactionData {
                    psbt: psbt_with_two_signatures.to_string(),
                    ..Default::default()
                },
            )
            .await;
        assert_eq!(response.status_code, StatusCode::INTERNAL_SERVER_ERROR,);
        assert!(response
            .body_string
            .contains("Input does not only have one signature"));
    }

    // Test PSBT with signature that does not belong to wallet
    {
        let mut psbt_with_attacker_signature =
            build_transaction_with_amount(&bdk_wallet, gen_external_wallet_address(), 1_000, &[]);
        for input in &mut psbt_with_attacker_signature.inputs {
            // Clear out all partial signatures, and insert a signature that does not belong to the wallet
            input.partial_sigs.clear();

            let sig = ecdsa::Signature::sighash_all(secp.sign_ecdsa(
                &Message::from_slice(&[0; 32]).unwrap(),
                &master_xprv.private_key,
            ));

            input.partial_sigs.insert(xpub.to_pub(), sig);
        }

        let response = client
            .sign_transaction_with_keyset(
                &account.id,
                &account.active_keyset_id,
                &SignTransactionData {
                    psbt: psbt_with_attacker_signature.to_string(),
                    ..Default::default()
                },
            )
            .await;
        assert_eq!(response.status_code, StatusCode::INTERNAL_SERVER_ERROR,);
        assert!(response.body_string.contains("Invalid PSBT"));
    }
}

#[tokio::test]
async fn test_sign_psbt_v2_happy_path() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let secp = Secp256k1::new();

    let app_root_xprv = ExtendedPrivKey::new_master(BdkNetwork::Regtest, &[1; 32]).unwrap();
    let app_account_xprv = app_root_xprv
        .derive_priv(&secp, &DerivationPath::from_str("m/84'/0'/0'").unwrap())
        .unwrap();

    // This is given to use from the HW
    let (hw_ext_desc_pub, hw_int_desc_pub) = {
        let hw_root_xprv = ExtendedPrivKey::new_master(BdkNetwork::Regtest, &[2; 32]).unwrap();
        let (_, hw_ext_desc_pub) = derive_descriptor_keypair(
            &secp,
            &hw_root_xprv,
            DerivationPath::from_str("m/84'/0'/0'").unwrap(),
            ChildNumber::from_normal_idx(0).expect("external index"),
        )
        .unwrap();
        let (_, hw_int_desc_pub) = derive_descriptor_keypair(
            &secp,
            &hw_root_xprv,
            DerivationPath::from_str("m/84'/0'/0'").unwrap(),
            ChildNumber::from_normal_idx(1).expect("internal index"),
        )
        .unwrap();

        (hw_ext_desc_pub, hw_int_desc_pub)
    };

    let spending_app_xpub = ExtendedPubKey::from_priv(&secp, &app_account_xprv);
    let spending_hw_xpub = match &hw_ext_desc_pub {
        DescriptorPublicKey::XPub(xpub) => xpub.xkey,
        _ => panic!("Expected XPub descriptor"),
    };

    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
        },
        spend: SpendingKeysetInputV2 {
            network: BdkNetwork::Regtest,
            app_pub: spending_app_xpub.public_key,
            hardware_pub: spending_hw_xpub.public_key,
        },
        is_test_account: true,
    };
    let actual_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
    let create_response = actual_response.body.unwrap();
    let _ = bootstrap
        .services
        .account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &create_response.account_id,
        })
        .await
        .unwrap();

    // A hardcoded chaincode with ZERO-ed out seed. DO NOT use this in production.
    let server_chaincode = {
        let mut hmac_engine: HmacEngine<sha512::Hash> = HmacEngine::new(b"Bitcoin seed");
        hmac_engine.input(&[0u8; 32]);
        let hmac_result: Hmac<sha512::Hash> = Hmac::from_engine(hmac_engine);
        hmac_result[32..]
            .try_into()
            .expect("half of hmac is guaranteed to be 32 bytes")
    };
    // We construct the server's xpub by combining the chain code we generate above with the
    // raw public key provided to us.
    let server_root_xpub = ExtendedPubKey {
        network: BdkNetwork::Regtest,
        depth: 0,
        parent_fingerprint: Default::default(),
        child_number: ChildNumber::from_normal_idx(0).expect("root child number"),
        public_key: create_response.server_pub,
        chain_code: server_chaincode,
    };

    let (external_descriptor_str, internal_descriptor_str) = generate_wallet_descriptors(
        &secp,
        app_root_xprv,
        server_root_xpub,
        &(hw_ext_desc_pub.clone(), hw_int_desc_pub),
    );

    let wallet: Wallet<MemoryDatabase> = Wallet::new(
        &external_descriptor_str,
        Some(&internal_descriptor_str),
        BdkNetwork::Regtest,
        MemoryDatabase::new(),
    )
    .expect("Unable to create wallet");

    let rpc_config = RpcConfig {
        url: env::var("REGTEST_BITCOIND_SERVER_URI")
            .unwrap_or("127.0.0.1:18443".to_string())
            .to_string(),
        auth: Auth::UserPass {
            username: env::var("BITCOIND_RPC_USER").unwrap_or("test".to_string()),
            password: env::var("BITCOIND_RPC_PASSWORD").unwrap_or("test".to_string()),
        },
        network: BdkNetwork::Regtest,
        wallet_name: wallet_name_from_descriptor(
            &external_descriptor_str,
            Some(&internal_descriptor_str),
            BdkNetwork::Regtest,
            &secp,
        )
        .unwrap(),
        sync_params: None,
    };
    let blockchain = RpcBlockchain::from_config(&rpc_config).unwrap();
    let funding_address = wallet.get_address(AddressIndex::Peek(0)).unwrap();
    treasury_fund_address(&funding_address, Amount::from_sat(50_000));
    generate_block(1, &funding_address).unwrap();
    wallet.sync(&blockchain, Default::default()).unwrap();

    let (psbt, _details) = {
        let mut builder = wallet.build_tx();
        builder
            .add_recipient(
                wallet
                    .get_address(AddressIndex::Peek(0))
                    .unwrap()
                    .script_pubkey(),
                30_000,
            )
            .enable_rbf()
            .fee_rate(bdk::FeeRate::from_sat_per_vb(1.0));
        builder.finish().expect("Failed to build transaction")
    };

    let mut psbt = psbt_with_tweaks(
        psbt,
        &Keyset {
            hw_descriptor_public_keys: HwAccountLevelDescriptorPublicKeys::new(
                hw_ext_desc_pub.master_fingerprint(),
                spending_hw_xpub,
            ),
            server_root_xpub,
            app_root_xprv,
        },
    )
    .unwrap();

    wallet.sign(&mut psbt, SignOptions::default()).unwrap();

    let wsm_service = http_server::config::extract::<wsm::Config>("test".into())
        .unwrap()
        .to_client()
        .unwrap();

    let result = wsm_service
        .client
        .sign_psbt_v2(
            &create_response.keyset_id.to_string(),
            spending_app_xpub.public_key,
            spending_hw_xpub.public_key,
            &psbt.to_string(),
        )
        .await;

    match result {
        Ok(signed_psbt) => {
            let psbt = Psbt::from_str(&signed_psbt.psbt).unwrap();
            let finalized = psbt.finalize(&secp).unwrap();

            let final_tx = finalized.extract_tx();
            blockchain
                .broadcast(&final_tx)
                .expect("Failed to broadcast transaction");
        }
        Err(e) => {
            println!("Error: {:?}", e);
        }
    }

    static HARDENED_PATH: [ChildNumber; 3] = [
        ChildNumber::Hardened { index: 84 },
        ChildNumber::Hardened { index: 0 },
        ChildNumber::Hardened { index: 0 },
    ];

    fn generate_wallet_descriptors(
        secp: &Secp256k1<All>,
        app_root_xprv: ExtendedPrivKey,
        server_root_xpub: ExtendedPubKey,
        hw_descriptor_public_keys: &(DescriptorPublicKey, DescriptorPublicKey),
    ) -> (String, String) {
        let (app_ext_desc_xprv, _) = derive_descriptor_keypair(
            secp,
            &app_root_xprv,
            DerivationPath::from(HARDENED_PATH.as_ref()),
            ChildNumber::from_normal_idx(0).expect("external index"),
        )
        .expect("app external descriptor key");
        let (app_int_desc_xprv, _app_int_desc_xpub_) = derive_descriptor_keypair(
            secp,
            &app_root_xprv,
            DerivationPath::from(HARDENED_PATH.as_ref()),
            ChildNumber::from_normal_idx(1).expect("internal index"),
        )
        .expect("app internal descriptor key");

        let (server_ext_desc_xpub, _) = derive_descriptor_public_key(
            secp,
            &server_root_xpub,
            DerivationPath::from_str("m/84/0/0").expect("external path"),
            ChildNumber::from_normal_idx(0).expect("external index"),
        );
        let (server_int_desc_xpub, _) = derive_descriptor_public_key(
            secp,
            &server_root_xpub,
            DerivationPath::from_str("m/84/0/0").expect("external path"),
            ChildNumber::from_normal_idx(1).expect("internal index"),
        );

        // Construct descriptor strings manually
        let external_descriptor_str = format!(
            "wsh(sortedmulti(2,{},{},{}))",
            app_ext_desc_xprv, hw_descriptor_public_keys.0, server_ext_desc_xpub
        );

        let internal_descriptor_str = format!(
            "wsh(sortedmulti(2,{},{},{}))",
            app_int_desc_xprv, hw_descriptor_public_keys.1, server_int_desc_xpub
        );

        (external_descriptor_str, internal_descriptor_str)
    }

    fn derive_descriptor_keypair(
        secp: &Secp256k1<All>,
        root_xprv: &ExtendedPrivKey,
        account_level_path: DerivationPath,
        change_index: ChildNumber,
    ) -> Result<(DescriptorSecretKey, DescriptorPublicKey), bdk::Error> {
        let derived_xprv = root_xprv.derive_priv(secp, &account_level_path)?;
        let origin: KeySource = (root_xprv.fingerprint(secp), account_level_path);

        let derived_xprv_desc_key: DescriptorKey<Segwitv0> = derived_xprv.into_descriptor_key(
            Some(origin.clone()),
            DerivationPath::from([change_index].as_ref()),
        )?;

        match derived_xprv_desc_key {
            DescriptorKey::Secret(desc_seckey, _, _) => {
                let desc_pubkey = desc_seckey
                    .to_public(secp)
                    .map_err(|e| bdk::Error::Generic(e.to_string()))?;
                Ok((desc_seckey, desc_pubkey))
            }
            _ => unreachable!(),
        }
    }

    fn derive_descriptor_public_key(
        secp: &Secp256k1<All>,
        root_xpub: &ExtendedPubKey,
        unhardened_path: DerivationPath,
        change_index: ChildNumber,
    ) -> (DescriptorPublicKey, KeySource) {
        let derived_xpub = root_xpub
            .derive_pub(secp, &unhardened_path)
            .expect("derived server xpub");
        let origin = (root_xpub.fingerprint(), unhardened_path);

        let derived_xpub_desc_key: DescriptorKey<Segwitv0> = derived_xpub
            .into_descriptor_key(
                Some(origin.clone()),
                DerivationPath::from([change_index].as_ref()),
            )
            .expect("descriptor key");

        match derived_xpub_desc_key {
            DescriptorKey::Public(desc_pubkey, _, _) => (desc_pubkey, origin),
            _ => unreachable!(),
        }
    }
}
