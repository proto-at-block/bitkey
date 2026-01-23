use std::{
    collections::{HashMap, HashSet},
    str::FromStr,
    sync::Arc,
};

use crypto::chaincode_delegation::{
    HwAccountLevelDescriptorPublicKeys, Keyset, UntweakedPsbt, XpubWithOrigin,
};
use http::StatusCode;

use mockall::mock;
use serde_json::json;
use ulid::Ulid;

use account::service::{tests::default_electrum_rpc_uris, FetchAccountInput};
use bdk_utils::{
    bdk::{
        self,
        bitcoin::{
            bip32::{DerivationPath, ExtendedPrivKey, ExtendedPubKey},
            ecdsa,
            key::Secp256k1,
            psbt::PartiallySignedTransaction,
            secp256k1::Message,
            Address, Network as BdkNetwork, OutPoint, Txid,
        },
        database::MemoryDatabase,
        electrum_client::Error as ElectrumClientError,
        keys::DescriptorPublicKey,
        miniscript::descriptor::{DescriptorXKey, Wildcard},
        wallet::{AddressIndex, AddressInfo},
        Error::Electrum,
        KeychainKind, SignOptions, Wallet,
    },
    error::BdkUtilError,
    DescriptorKeyset, ElectrumRpcUris, TransactionBroadcasterTrait,
};
use external_identifier::ExternalIdentifier;
use mobile_pay::routes::SignTransactionData;
use onboarding::routes::RotateSpendingKeysetRequest;
use rstest::rstest;
use types::{
    account::{
        bitcoin::Network,
        entities::{DescriptorBackup, DescriptorBackupsSet},
        identifiers::AccountId,
        money::Money,
        spend_limit::SpendingLimit,
        spending::SpendingKeyset,
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

use crate::tests::lib::wallet_protocol::{
    build_app_signed_psbt_for_protocol, build_sweep_psbt_for_protocol, check_finalized_psbt,
    setup_fixture, sweep_destination_for_ccd, SweepDestination, WalletTestProtocol,
};
use crate::tests::lib::{
    create_default_account_with_private_wallet, create_inactive_spending_keyset_for_account,
    gen_external_wallet_address, predefined_descriptor_public_keys, predefined_server_root_xpub,
};
use crate::tests::mobile_pay_tests::build_mobile_pay_request;
use crate::tests::requests::axum::TestClient;
use crate::tests::{gen_services, gen_services_with_overrides, GenServiceOverrides};
use std::sync::atomic::{AtomicUsize, Ordering};

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
    wallet_protocol: WalletTestProtocol,
) {
    let broadcast_will_succeed = broadcast_failure_mode.is_none();
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

    let fixture = setup_fixture(&mut context, &client, &bootstrap.services, wallet_protocol).await;
    let keys = context
        .get_authentication_keys_for_account_id(&fixture.account.id)
        .unwrap();
    let transfer_amount = 1_000;

    let app_signed_psbt = if let Some(override_psbt) = override_psbt {
        override_psbt.to_string()
    } else {
        build_app_signed_psbt_for_protocol(
            &fixture,
            gen_external_wallet_address(),
            transfer_amount,
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
        let mobile_pay_response = client
            .put_mobile_pay(&fixture.account.id, &request, &keys)
            .await;
        assert_eq!(
            mobile_pay_response.status_code,
            StatusCode::OK,
            "{}",
            mobile_pay_response.body_string
        );
        fixture.account.id
    };

    // Only check mobile pay spent amounts when we have a valid account
    let current_spent = if !override_account_id {
        Some(
            client
                .get_mobile_pay(&account_id, &keys)
                .await
                .body
                .unwrap()
                .mobile_pay()
                .unwrap()
                .spent,
        )
    } else {
        None
    };

    let actual_response = client
        .sign_transaction_with_keyset(
            &account_id,
            &fixture.account.active_keyset_id,
            &SignTransactionData {
                psbt: app_signed_psbt,
                ..Default::default()
            },
        )
        .await;

    let final_spent = if !override_account_id {
        Some(
            client
                .get_mobile_pay(&account_id, &keys)
                .await
                .body
                .unwrap()
                .mobile_pay()
                .unwrap()
                .spent,
        )
    } else {
        None
    };

    assert_eq!(
        actual_response.status_code, expected_status,
        "{}",
        actual_response.body_string
    );

    // Only assert on spent amounts if we have valid mobile pay data
    if let (Some(current), Some(final_amount)) = (current_spent, final_spent) {
        if expect_broadcast && broadcast_will_succeed {
            // When broadcast succeeds, the spent amount should increase by the transaction amount
            assert_eq!(final_amount.amount, current.amount + transfer_amount);
        } else {
            // When broadcast fails or is not attempted, spent amount should remain unchanged
            assert_eq!(final_amount.amount, current.amount);
        }
    }
}

#[rstest]
#[case::test_sign_transaction_success(
    false,
    vec![OutPoint {
        txid: Txid::from_str("4ee151877683e05a6c0cecc54e9e5a3af3a3741850285e73a908898b66312d7b").unwrap(),
        vout: 0,
    },
    OutPoint {
        txid: Txid::from_str("8a65b99a749c974cefc465a1156bce0b7cf7d990fa256fc605124c913d19aeb3").unwrap(),
        vout: 1,
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
            txid: Txid::from_str("bf3e40a813154cfca0bf52e7323491c4b6a4c6a67bf48abb2feed82a886fcb07").unwrap(),
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
        txid: Txid::from_str("e8ea0e7bf354897e850a664a687d508983708ad3dee40cd0c95001c6e490c97c").unwrap(),
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
        txid: Txid::from_str("2f00e1e8466c18ee5044c1868bc985a566b46559384f6bde00ffbd7e46fe456c").unwrap(),
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
        WalletTestProtocol::Legacy,
    )
    .await
}

#[rstest]
#[case::test_sign_transaction_success(
    false,
    vec![OutPoint {
        txid: Txid::from_str("61bad0730512d6e729284b63bc55879ed5e7bcf3a9224c4f3038fdb03bd91036").unwrap(),
        vout: 1,
    },
    OutPoint {
        txid: Txid::from_str("61bad0730512d6e729284b63bc55879ed5e7bcf3a9224c4f3038fdb03bd91036").unwrap(),
        vout: 2,
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
            txid: Txid::from_str("61bad0730512d6e729284b63bc55879ed5e7bcf3a9224c4f3038fdb03bd91036").unwrap(),
            vout: 3,
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
        txid: Txid::from_str("61bad0730512d6e729284b63bc55879ed5e7bcf3a9224c4f3038fdb03bd91036").unwrap(),
        vout: 4,
    }],
    None,
    StatusCode::INTERNAL_SERVER_ERROR,
    true,
    Some(BroadcastFailureMode::ElectrumGeneric)
)]
#[case::test_sign_transaction_fail_broadcast_duplicate_tx(
    false,
    vec![OutPoint {
        txid: Txid::from_str("61bad0730512d6e729284b63bc55879ed5e7bcf3a9224c4f3038fdb03bd91036").unwrap(),
        vout: 5,
    }],
    None,
    StatusCode::CONFLICT,
    true,
    Some(BroadcastFailureMode::ElectrumDuplicateTx)
)]
#[tokio::test]
async fn test_sign_transaction_private_ccd(
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
        WalletTestProtocol::PrivateCcd,
    )
    .await
}

#[tokio::test]
async fn test_failed_broadcast_does_not_count_against_spending_limit() {
    let attempts = AtomicUsize::new(0);
    let mut broadcaster_mock = MockTransactionBroadcaster::new();
    broadcaster_mock
        .expect_broadcast()
        .times(2)
        .returning(move |_, _, _| {
            let attempt = attempts.fetch_add(1, Ordering::SeqCst);
            if attempt == 0 {
                Err(BdkUtilError::TransactionBroadcastError(Electrum(
                    ElectrumClientError::Message("Fail".to_string()),
                )))
            } else {
                Ok(())
            }
        });

    let overrides = GenServiceOverrides::new().broadcaster(Arc::new(broadcaster_mock));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let fixture = setup_fixture(
        &mut context,
        &client,
        &bootstrap.services,
        WalletTestProtocol::Legacy,
    )
    .await;
    let keys = context
        .get_authentication_keys_for_account_id(&fixture.account.id)
        .unwrap();
    let transfer_amount = 1_000;

    // Configure mobile pay so spending records are present.
    let limit = SpendingLimit {
        active: true,
        amount: Money {
            amount: 5_000,
            currency_code: USD,
        },
        ..Default::default()
    };
    let request = build_mobile_pay_request(limit);
    let mobile_pay_response = client
        .put_mobile_pay(&fixture.account.id, &request, &keys)
        .await;
    assert_eq!(
        mobile_pay_response.status_code,
        StatusCode::OK,
        "{}",
        mobile_pay_response.body_string
    );

    let psbt = build_app_signed_psbt_for_protocol(
        &fixture,
        gen_external_wallet_address(),
        transfer_amount,
        &[],
    )
    .to_string();

    let request_data = SignTransactionData {
        psbt: psbt.clone(),
        ..Default::default()
    };

    let current_spent = client
        .get_mobile_pay(&fixture.account.id, &keys)
        .await
        .body
        .unwrap()
        .mobile_pay()
        .unwrap()
        .spent
        .amount;

    // First attempt: broadcast fails, should not count toward spent.
    let first_response = client
        .sign_transaction_with_keyset(
            &fixture.account.id,
            &fixture.account.active_keyset_id,
            &request_data,
        )
        .await;
    assert_eq!(
        first_response.status_code,
        StatusCode::INTERNAL_SERVER_ERROR
    );

    let spent_after_failure = client
        .get_mobile_pay(&fixture.account.id, &keys)
        .await
        .body
        .unwrap()
        .mobile_pay()
        .unwrap()
        .spent
        .amount;
    assert_eq!(spent_after_failure, current_spent);

    // Retry: broadcast succeeds; spent should increase by transfer_amount.
    let second_response = client
        .sign_transaction_with_keyset(
            &fixture.account.id,
            &fixture.account.active_keyset_id,
            &request_data,
        )
        .await;
    assert_eq!(second_response.status_code, StatusCode::OK);

    let spent_after_success = client
        .get_mobile_pay(&fixture.account.id, &keys)
        .await
        .body
        .unwrap()
        .mobile_pay()
        .unwrap()
        .spent
        .amount;
    assert_eq!(spent_after_success, current_spent + transfer_amount);
}

async fn sign_transaction_with_transaction_verification_test(
    policy: PolicyUpdate,
    include_verification_id: bool,
    expect_success: bool,
    wallet_protocol: WalletTestProtocol,
) {
    let mut broadcaster_mock = MockTransactionBroadcaster::new();
    broadcaster_mock
        .expect_broadcast()
        .times(if expect_success { 1 } else { 0 })
        .returning(move |_, _, _| Ok(()));

    let overrides = GenServiceOverrides::new().broadcaster(Arc::new(broadcaster_mock));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let fixture = setup_fixture(&mut context, &client, &bootstrap.services, wallet_protocol).await;

    let keys = context
        .get_authentication_keys_for_account_id(&fixture.account.id)
        .unwrap();

    let app_signed_psbt =
        build_app_signed_psbt_for_protocol(&fixture, gen_external_wallet_address(), 2_000, &[]);

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
    let mobile_pay_response = client
        .put_mobile_pay(&fixture.account.id, &request, &keys)
        .await;
    assert_eq!(
        mobile_pay_response.status_code,
        StatusCode::OK,
        "{}",
        mobile_pay_response.body_string
    );

    let update_policy_request = client
        .update_transaction_verification_policy(
            &fixture.account.id,
            true,
            false,
            &keys,
            &PutTransactionVerificationPolicyRequest {
                policy,
                use_bip_177: false,
            },
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
            fixture.account.id.clone(),
            app_signed_psbt.clone(),
            CurrencyCode::USD,
            BitcoinDisplayUnit::Bitcoin,
            false,
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
            &fixture.account.id,
            &fixture.account.active_keyset_id,
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
        check_finalized_psbt(response.body, &fixture.wallet);
    }
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
    sign_transaction_with_transaction_verification_test(
        policy,
        include_verification_id,
        expect_success,
        WalletTestProtocol::Legacy,
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
async fn test_sign_transaction_with_transaction_verification_private_ccd(
    #[case] policy: PolicyUpdate,
    #[case] include_verification_id: bool,
    #[case] expect_success: bool,
) {
    sign_transaction_with_transaction_verification_test(
        policy,
        include_verification_id,
        expect_success,
        WalletTestProtocol::PrivateCcd,
    )
    .await
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
    wallet_protocol: WalletTestProtocol,
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

    let fixture = setup_fixture(&mut context, &client, &bootstrap.services, wallet_protocol).await;

    let keys = context
        .get_authentication_keys_for_account_id(&fixture.account.id)
        .expect("Keys not found");
    let recipient = match should_self_addressed_tx {
        true => fixture.wallet.get_address(AddressIndex::New).unwrap(),
        false => gen_external_wallet_address(),
    };
    let app_signed_psbt = build_app_signed_psbt_for_protocol(&fixture, recipient, 2_000, &[]);

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
        .put_mobile_pay(&fixture.account.id, &mobile_pay_request, &keys)
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
        .sign_transaction_with_keyset(
            &fixture.account.id,
            &fixture.account.active_keyset_id,
            &request_data,
        )
        .await;
    assert_eq!(
        expected_status, response.status_code,
        "{}",
        response.body_string
    );
    check_finalized_psbt(response.body, &fixture.wallet);

    let response = client
        .sign_transaction_with_keyset(
            &fixture.account.id,
            &fixture.account.active_keyset_id,
            &request_data,
        )
        .await;
    assert_eq!(
        expected_status, response.status_code,
        "{}",
        response.body_string
    );
    check_finalized_psbt(response.body, &fixture.wallet);
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
        WalletTestProtocol::Legacy,
    )
    .await
}

#[rstest]
#[case::self_addressed_no_movable_funds(0, true, false, StatusCode::BAD_REQUEST)]
#[case::below_spend_limit(2033, false, false, StatusCode::OK)]
#[case::above_limit_to_self(0, true, false, StatusCode::BAD_REQUEST)]
#[case::above_limit_to_external(0, false, false, StatusCode::BAD_REQUEST)]
#[tokio::test]
async fn test_spend_limit_transaction_private_ccd(
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
        WalletTestProtocol::PrivateCcd,
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
async fn test_bypass_mobile_spend_limit_for_sweep_legacy(
    #[case] spending_limit: Option<SpendingLimit>,
    #[case] target_external_address: bool,
    #[case] expected_status: StatusCode,
) {
    bypass_mobile_spend_limit_for_sweep_test(
        spending_limit,
        target_external_address,
        expected_status,
        WalletTestProtocol::Legacy,
        WalletTestProtocol::Legacy,
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
async fn test_bypass_mobile_spend_limit_for_sweep_private(
    #[case] spending_limit: Option<SpendingLimit>,
    #[case] target_external_address: bool,
    #[case] expected_status: StatusCode,
) {
    bypass_mobile_spend_limit_for_sweep_test(
        spending_limit,
        target_external_address,
        expected_status,
        WalletTestProtocol::PrivateCcd,
        WalletTestProtocol::PrivateCcd,
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
async fn test_bypass_mobile_spend_limit_for_sweep_migration(
    #[case] spending_limit: Option<SpendingLimit>,
    #[case] target_external_address: bool,
    #[case] expected_status: StatusCode,
) {
    bypass_mobile_spend_limit_for_sweep_test(
        spending_limit,
        target_external_address,
        expected_status,
        WalletTestProtocol::Legacy,
        WalletTestProtocol::PrivateCcd,
    )
    .await
}

async fn bypass_mobile_spend_limit_for_sweep_test(
    spending_limit: Option<SpendingLimit>,
    target_external_address: bool,
    expected_status: StatusCode,
    source_protocol: WalletTestProtocol,
    dest_protocol: WalletTestProtocol,
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

    let fixture = setup_fixture(&mut context, &client, &bootstrap.services, source_protocol).await;

    let keys = context
        .get_authentication_keys_for_account_id(&fixture.account.id)
        .expect("Keys not found");
    let inactive_keyset_id = fixture.account.active_keyset_id.clone();
    let (active_keyset_id, (app_xpub, hw_xpub)) = create_inactive_spending_keyset_for_account(
        &context,
        &client,
        &fixture.account.id,
        Network::BitcoinSignet,
        dest_protocol,
    )
    .await;

    let dest_backup = match dest_protocol {
        WalletTestProtocol::Legacy => DescriptorBackup::Legacy {
            keyset_id: active_keyset_id.clone(),
            sealed_descriptor: Default::default(),
        },
        WalletTestProtocol::PrivateCcd => DescriptorBackup::Private {
            keyset_id: active_keyset_id.clone(),
            sealed_descriptor: Default::default(),
            sealed_server_root_xpub: Default::default(),
        },
    };

    let response = client
        .update_descriptor_backups(
            &fixture.account.id.to_string(),
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![dest_backup],
            },
            Some(&keys),
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );

    let response = client
        .rotate_to_spending_keyset(
            &fixture.account.id.to_string(),
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
            account_id: &fixture.account.id,
        })
        .await
        .unwrap();

    let app_signed_psbt = if target_external_address {
        build_sweep_psbt_for_protocol(
            &fixture,
            SweepDestination::External(gen_external_wallet_address().address),
        )
    } else {
        match dest_protocol {
            WalletTestProtocol::Legacy => {
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

                build_sweep_psbt_for_protocol(
                    &fixture,
                    SweepDestination::External(
                        active_wallet
                            .get_address(AddressIndex::Peek(0))
                            .unwrap()
                            .address,
                    ),
                )
            }
            WalletTestProtocol::PrivateCcd => {
                // Here, we use the new XPUBs that were generated when
                // `create_inactive_spending_keyset_for_account` was called, and the server xpub
                // that we know would always be the same
                let active_keyset = account.spending_keysets.get(&active_keyset_id).unwrap();
                let server_pub = match active_keyset {
                    SpendingKeyset::PrivateMultiSig(k) => k.server_pub,
                    _ => panic!("Expected PrivateMultiSig keyset for PrivateCcd protocol"),
                };
                // The server never has a chain code, so we simulate an app generating one for us
                let server_root_xpub =
                    predefined_server_root_xpub(active_keyset.network(), server_pub);
                let server_dpub = {
                    let unhardened_path = DerivationPath::from_str("m/84/1/0").unwrap();
                    let derived_xpub = server_root_xpub
                        .derive_pub(&Secp256k1::new(), &unhardened_path)
                        .expect("derived server xpub");
                    let origin = (server_root_xpub.fingerprint(), unhardened_path);
                    DescriptorPublicKey::XPub(DescriptorXKey {
                        origin: Some(origin),
                        xkey: derived_xpub,
                        derivation_path: DerivationPath::default(),
                        wildcard: Wildcard::Unhardened,
                    })
                };

                // We create a BDK wallet without syncing, to generate a sweep address
                // This MUST be the first index.
                let descriptor_keyset = DescriptorKeyset::new(
                    active_keyset.network().into(),
                    app_xpub.clone(),
                    hw_xpub.clone(),
                    server_dpub.clone(),
                );

                let wallet = Wallet::new(
                    descriptor_keyset
                        .receiving()
                        .into_multisig_descriptor()
                        .unwrap(),
                    None,
                    active_keyset.network().into(),
                    MemoryDatabase::new(),
                )
                .unwrap();

                let sweep_address = wallet.get_address(AddressIndex::Peek(0)).unwrap().address;
                let destination =
                    sweep_destination_for_ccd(app_xpub, hw_xpub, server_root_xpub, sweep_address);
                build_sweep_psbt_for_protocol(&fixture, destination)
            }
        }
    };

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
    check_finalized_psbt(response.body, &fixture.wallet);
}

async fn disabled_mobile_pay_spend_limit_test(wallet_protocol: WalletTestProtocol) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let fixture = setup_fixture(&mut context, &client, &bootstrap.services, wallet_protocol).await;
    let keys = context
        .get_authentication_keys_for_account_id(&fixture.account.id)
        .expect("Keys not found");
    let recipient = gen_external_wallet_address();
    let app_signed_psbt = build_app_signed_psbt_for_protocol(&fixture, recipient, 2_000, &[]);

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
        .put_mobile_pay(&fixture.account.id, &mobile_pay_request, &keys)
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
            &fixture.account.id,
            &fixture.account.active_keyset_id,
            &SignTransactionData {
                psbt: app_signed_psbt.to_string(),
                ..Default::default()
            },
        )
        .await;
    assert_eq!(actual_response.status_code, StatusCode::FORBIDDEN,);
}

#[tokio::test]
async fn test_disabled_mobile_pay_spend_limit() {
    disabled_mobile_pay_spend_limit_test(WalletTestProtocol::Legacy).await
}

#[tokio::test]
async fn test_disabled_mobile_pay_spend_limit_private_ccd() {
    disabled_mobile_pay_spend_limit_test(WalletTestProtocol::PrivateCcd).await
}

async fn mismatched_account_id_keyproof_test(wallet_protocol: WalletTestProtocol) {
    let broadcaster_mock = MockTransactionBroadcaster::new();

    let overrides = GenServiceOverrides::new().broadcaster(Arc::new(broadcaster_mock));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let fixture = setup_fixture(&mut context, &client, &bootstrap.services, wallet_protocol).await;
    let keys = context
        .get_authentication_keys_for_account_id(&fixture.account.id)
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
            &fixture.account.id,
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
    let mobile_pay_response = client
        .put_mobile_pay(&fixture.account.id, &request, &keys)
        .await;
    // Ensure Mobile Pay was set up correctly
    assert_eq!(
        mobile_pay_response.status_code,
        StatusCode::OK,
        "{}",
        mobile_pay_response.body_string
    );

    // Attempt to sign a transaction with a different account id
    let app_signed_psbt =
        build_app_signed_psbt_for_protocol(&fixture, gen_external_wallet_address(), 2_000, &[])
            .to_string();
    let response = client
        .sign_transaction_with_keyset_and_keyproof_account_id(
            &fixture.account.id,
            &AccountId::new(Ulid(400)).unwrap(),
            &fixture.account.active_keyset_id,
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
async fn test_mismatched_account_id_keyproof() {
    mismatched_account_id_keyproof_test(WalletTestProtocol::Legacy).await
}

#[tokio::test]
async fn test_mismatched_account_id_keyproof_private_ccd() {
    mismatched_account_id_keyproof_test(WalletTestProtocol::PrivateCcd).await
}

async fn fail_sends_to_sanctioned_address_test(wallet_protocol: WalletTestProtocol) {
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

    let fixture = setup_fixture(&mut context, &client, &bootstrap.services, wallet_protocol).await;
    let keys = context
        .get_authentication_keys_for_account_id(&fixture.account.id)
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
    let mobile_pay_response = client
        .put_mobile_pay(&fixture.account.id, &request, &keys)
        .await;
    assert_eq!(
        mobile_pay_response.status_code,
        StatusCode::OK,
        "{}",
        mobile_pay_response.body_string
    );

    let app_signed_psbt =
        build_app_signed_psbt_for_protocol(&fixture, blocked_address_info, 2_000, &[]).to_string();

    let request_data = SignTransactionData {
        psbt: app_signed_psbt.to_string(),
        ..Default::default()
    };

    let response = client
        .sign_transaction_with_keyset(
            &fixture.account.id,
            &fixture.account.active_keyset_id,
            &request_data,
        )
        .await;

    assert_eq!(
        response.status_code,
        StatusCode::UNAVAILABLE_FOR_LEGAL_REASONS,
        "{}",
        response.body_string
    );

    let screener_rows = bootstrap
        .services
        .screener_repository
        .test_fetch_for_account_id(&fixture.account.id)
        .await
        .unwrap();
    assert_eq!(1, screener_rows.len())
}

#[tokio::test(flavor = "multi_thread")]
async fn test_fail_sends_to_sanctioned_address() {
    fail_sends_to_sanctioned_address_test(WalletTestProtocol::Legacy).await
}

#[tokio::test(flavor = "multi_thread")]
async fn test_fail_sends_to_sanctioned_address_private_ccd() {
    fail_sends_to_sanctioned_address_test(WalletTestProtocol::PrivateCcd).await
}

async fn fail_to_send_if_kill_switch_is_on_test(wallet_protocol: WalletTestProtocol) {
    let feature_flag_override =
        HashMap::from([("f8e-mobile-pay-enabled".to_string(), "false".to_string())]);
    let overrides = GenServiceOverrides::new().feature_flags(feature_flag_override);

    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;
    let fixture = setup_fixture(&mut context, &client, &bootstrap.services, wallet_protocol).await;
    let keys = context
        .get_authentication_keys_for_account_id(&fixture.account.id)
        .unwrap();
    assert_eq!(fixture.account.spending_limit, None);

    let spend_limit = SpendingLimit {
        active: true,
        amount: Money {
            amount: 109_798,
            currency_code: USD,
        },
        ..Default::default()
    };
    let request = build_mobile_pay_request(spend_limit.clone());
    let response = client
        .put_mobile_pay(&fixture.account.id, &request, &keys)
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );

    let app_signed_psbt =
        build_app_signed_psbt_for_protocol(&fixture, gen_external_wallet_address(), 2_000, &[])
            .to_string();
    let actual_response = client
        .sign_transaction_with_keyset(
            &fixture.account.id,
            &fixture.account.active_keyset_id,
            &SignTransactionData {
                psbt: app_signed_psbt,
                ..Default::default()
            },
        )
        .await;
    assert_eq!(actual_response.status_code, StatusCode::FORBIDDEN,);
}

#[tokio::test]
async fn test_fail_to_send_if_kill_switch_is_on() {
    fail_to_send_if_kill_switch_is_on_test(WalletTestProtocol::Legacy).await
}

#[tokio::test]
async fn test_fail_to_send_if_kill_switch_is_on_private_ccd() {
    fail_to_send_if_kill_switch_is_on_test(WalletTestProtocol::PrivateCcd).await
}

async fn fail_if_signed_with_different_wallet_test(wallet_protocol: WalletTestProtocol) {
    let (mut context, bootstrap) = gen_services().await;

    let client = TestClient::new(bootstrap.router).await;
    let fixture = setup_fixture(&mut context, &client, &bootstrap.services, wallet_protocol).await;
    let keys = context
        .get_authentication_keys_for_account_id(&fixture.account.id)
        .unwrap();

    // Set some spending limit for this account
    let spend_limit = SpendingLimit {
        active: true,
        amount: Money {
            amount: 109_798,
            currency_code: USD,
        },
        ..Default::default()
    };
    let request = build_mobile_pay_request(spend_limit.clone());
    let response = client
        .put_mobile_pay(&fixture.account.id, &request, &keys)
        .await;
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
            build_app_signed_psbt_for_protocol(&fixture, gen_external_wallet_address(), 2_000, &[]);
        for input in &mut psbt_without_signatures.inputs {
            input.partial_sigs.clear();
        }

        let response = client
            .sign_transaction_with_keyset(
                &fixture.account.id,
                &fixture.account.active_keyset_id,
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
            build_app_signed_psbt_for_protocol(&fixture, gen_external_wallet_address(), 1_000, &[]);
        for input in &mut psbt_with_two_signatures.inputs {
            let sig = ecdsa::Signature::sighash_all(secp.sign_ecdsa(
                &Message::from_slice(&[0; 32]).unwrap(),
                &master_xprv.private_key,
            ));

            input.partial_sigs.insert(xpub.to_pub(), sig);
        }

        let response = client
            .sign_transaction_with_keyset(
                &fixture.account.id,
                &fixture.account.active_keyset_id,
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
            build_app_signed_psbt_for_protocol(&fixture, gen_external_wallet_address(), 1_000, &[]);
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
                &fixture.account.id,
                &fixture.account.active_keyset_id,
                &SignTransactionData {
                    psbt: psbt_with_attacker_signature.to_string(),
                    ..Default::default()
                },
            )
            .await;
        assert_eq!(response.status_code, StatusCode::INTERNAL_SERVER_ERROR,);
        match wallet_protocol {
            WalletTestProtocol::Legacy => {
                assert!(response.body_string.contains("Invalid PSBT"));
            }
            WalletTestProtocol::PrivateCcd => {
                assert!(response.body_string.contains("Failed to finalize PSBT."));
            }
        }
    }
}

#[tokio::test]
async fn test_fail_if_signed_with_different_wallet() {
    fail_if_signed_with_different_wallet_test(WalletTestProtocol::Legacy).await
}

#[tokio::test]
async fn test_fail_if_signed_with_different_wallet_private_ccd() {
    fail_if_signed_with_different_wallet_test(WalletTestProtocol::PrivateCcd).await
}

#[tokio::test]
async fn test_sign_psbt_v2_happy_path() {
    let mut broadcaster_mock = MockTransactionBroadcaster::new();
    broadcaster_mock
        .expect_broadcast()
        .times(1)
        .returning(|_, _, _| Ok(()));

    let overrides = GenServiceOverrides::new().broadcaster(Arc::new(broadcaster_mock));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let (account, wallet) =
        create_default_account_with_private_wallet(&mut context, &client, &bootstrap.services)
            .await;

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
    let mobile_pay_response = client
        .put_mobile_pay(
            &account.id,
            &request,
            &context
                .get_authentication_keys_for_account_id(&account.id)
                .unwrap(),
        )
        .await;
    assert_eq!(
        mobile_pay_response.status_code,
        StatusCode::OK,
        "{}",
        mobile_pay_response.body_string
    );

    // Simulate app signing a PSBT
    let app_signed_psbt = {
        let (psbt, _details) = {
            let mut builder = wallet.build_tx();
            builder
                .add_recipient(gen_external_wallet_address().script_pubkey(), 30_000)
                .enable_rbf()
                .fee_rate(bdk::FeeRate::from_sat_per_vb(1.0));
            builder.finish().expect("Failed to build transaction")
        };

        let predefined_keys = predefined_descriptor_public_keys();
        let hw_descriptor_public_keys = match predefined_keys.hardware_account_dpub {
            DescriptorPublicKey::XPub(DescriptorXKey {
                origin: Some(ref origin),
                xkey,
                ..
            }) => HwAccountLevelDescriptorPublicKeys::new(origin.0, xkey),
            _ => panic!("Unsupported"),
        };

        let keyset = match account
            .spending_keysets
            .get(&account.active_keyset_id)
            .unwrap()
        {
            SpendingKeyset::PrivateMultiSig(k) => k,
            SpendingKeyset::LegacyMultiSig(_) => panic!("Legacy multi sig not supported"),
        };

        let app_account_xpub = match predefined_keys.app_account_descriptor_keypair().1 {
            DescriptorPublicKey::XPub(DescriptorXKey { xkey, .. }) => xkey,
            _ => panic!("Expected XPub descriptor"),
        };

        let mut psbt = UntweakedPsbt::new(psbt)
            .with_source_wallet_tweaks(&Keyset {
                hw_descriptor_public_keys: HwAccountLevelDescriptorPublicKeys::new(
                    hw_descriptor_public_keys.root_fingerprint(),
                    hw_descriptor_public_keys.account_xpub(),
                ),
                server_root_xpub: predefined_server_root_xpub(keyset.network, keyset.server_pub),
                app_account_xpub_with_origin: XpubWithOrigin {
                    fingerprint: predefined_keys.app_root_xprv.fingerprint(&Secp256k1::new()),
                    xpub: app_account_xpub,
                },
            })
            .unwrap()
            .into_psbt();

        wallet.sign(&mut psbt, SignOptions::default()).unwrap();

        psbt
    };

    let response = client
        .sign_transaction_with_keyset(
            &account.id,
            &account.active_keyset_id,
            &SignTransactionData {
                psbt: app_signed_psbt.to_string(),
                ..Default::default()
            },
        )
        .await;

    assert_eq!(response.status_code, StatusCode::OK);
    check_finalized_psbt(response.body, &wallet);
}
