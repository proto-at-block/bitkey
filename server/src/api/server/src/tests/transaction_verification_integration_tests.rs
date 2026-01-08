// Third-party imports
use http::StatusCode;
use rand::distributions::{Alphanumeric, DistString};
use rstest::rstest;
use std::time::{SystemTime, UNIX_EPOCH};
use time::OffsetDateTime;

// Internal crate imports
use crate::tests::{
    gen_services,
    lib::{create_default_account_with_predefined_wallet, wallet_protocol::setup_fixture},
    requests::axum::TestClient,
};

// Workspace crate imports
use crate::tests::lib::wallet_protocol::{build_app_signed_psbt_for_protocol, WalletTestProtocol};
use account::service::FetchAccountInput;
use bdk_utils::bdk::wallet::{get_funded_wallet, AddressIndex};
use transaction_verification::routes::InitiateTransactionVerificationRequest;
use transaction_verification::routes::ProcessTransactionVerificationTokenRequest;
use types::{
    account::{
        entities::{Account, FullAccount, TransactionVerificationPolicy},
        money::Money,
    },
    currencies::CurrencyCode::USD,
    privileged_action::{
        repository::PrivilegedActionInstanceRecord, router::generic::PrivilegedActionResponse,
    },
    transaction_verification::{
        entities::{
            BitcoinDisplayUnit, PolicyUpdate, PolicyUpdateMoney, TransactionVerification,
            TransactionVerificationDiscriminants,
        },
        router::{
            InitiateTransactionVerificationView, InitiateTransactionVerificationViewRequested,
            InitiateTransactionVerificationViewSigned, PutTransactionVerificationPolicyRequest,
            TransactionVerificationView,
        },
    },
};

#[rstest]
// Cases that should succeed without out-of-band verification
#[case::never_to_always(
    TransactionVerificationPolicy::Never,
    PolicyUpdate::Always,
    false, // has_hw_signed
    StatusCode::OK,
    false // expect_out_of_band
)]
#[case::never_to_threshold(
    TransactionVerificationPolicy::Never,
    PolicyUpdate::Threshold(PolicyUpdateMoney {
        amount_sats: 1000,
        amount_fiat: 118,
        currency_code: USD,
    }),
    false, // has_hw_signed
    StatusCode::OK,
    false // expect_out_of_band
)]
#[case::threshold_lower(
    TransactionVerificationPolicy::Threshold(Money {
        amount: 236,
        currency_code: USD,
    }),
    PolicyUpdate::Threshold(PolicyUpdateMoney {
        amount_sats: 1000,
        amount_fiat: 118,
        currency_code: USD,
    }),
    false, // has_hw_signed
    StatusCode::OK,
    false // expect_out_of_band
)]
// Cases that require out-of-band verification (loosening policies with hw signature)
#[case::always_to_never(
    TransactionVerificationPolicy::Always,
    PolicyUpdate::Never,
    true, // has_hw_signed
    StatusCode::OK,
    true // expect_out_of_band
)]
#[case::threshold_to_never(
    TransactionVerificationPolicy::Threshold(Money {
        amount: 1000,
        currency_code: USD,
    }),
    PolicyUpdate::Never,
    true, // has_hw_signed
    StatusCode::OK,
    true // expect_out_of_band
)]
#[case::threshold_higher(
    TransactionVerificationPolicy::Threshold(Money {
        amount: 118,
        currency_code: USD,
    }),
    PolicyUpdate::Threshold(PolicyUpdateMoney {
        amount_sats: 2000,
        amount_fiat: 236,
        currency_code: USD,
    }),
    true, // has_hw_signed
    StatusCode::OK,
    true // expect_out_of_band
)]
// Cases that should fail (loosening policies without hw signature)
#[case::always_to_never_no_hw(
    TransactionVerificationPolicy::Always,
    PolicyUpdate::Never,
    false, // has_hw_signed
    StatusCode::FORBIDDEN,
    true // expect_out_of_band (irrelevant since it fails)
)]
#[case::threshold_higher_no_hw(
    TransactionVerificationPolicy::Threshold(Money {
        amount: 118,
        currency_code: USD,
    }),
    PolicyUpdate::Threshold(PolicyUpdateMoney {
        amount_sats: 2000,
        amount_fiat: 236,
        currency_code: USD,
    }),
    false, // has_hw_signed
    StatusCode::FORBIDDEN,
    true // expect_out_of_band (irrelevant since it fails)
)]
#[tokio::test]
async fn update_transaction_verification_policy_test(
    #[case] from_policy: TransactionVerificationPolicy,
    #[case] to_policy: PolicyUpdate,
    #[case] has_hw_signed: bool,
    #[case] expected_status: StatusCode,
    #[case] expect_out_of_band: bool,
) {
    // Setup test environment
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let (account, _) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;

    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();

    let resp = client
        .get_transaction_verification_policy(&account.id, &keys)
        .await;
    assert_eq!(
        resp.body.unwrap().policy,
        TransactionVerificationPolicy::Never
    );

    let account_id = account.id.clone();
    bootstrap
        .services
        .account_repository
        .persist(&Account::Full(FullAccount {
            transaction_verification_policy: Some(from_policy.clone()),
            ..account
        }))
        .await
        .expect("Failed to persist account");

    let resp = client
        .update_transaction_verification_policy(
            &account_id,
            true, // app_signed
            has_hw_signed,
            &keys,
            &PutTransactionVerificationPolicyRequest {
                policy: to_policy.clone(),
            },
        )
        .await;

    assert_eq!(resp.status_code, expected_status);
    let response = resp.body.unwrap();

    if resp.status_code != StatusCode::OK {
        let account = bootstrap
            .services
            .account_service
            .fetch_full_account(FetchAccountInput {
                account_id: &account_id,
            })
            .await
            .expect("Failed to fetch account");
        assert_eq!(account.transaction_verification_policy, Some(from_policy));
    } else if expect_out_of_band {
        let PrivilegedActionResponse::Pending(p) = response else {
            panic!("Expected privileged action response to be pending");
        };

        let privileged_action_instance: PrivilegedActionInstanceRecord<
            PutTransactionVerificationPolicyRequest,
        > = bootstrap
            .services
            .privileged_action_repository
            .fetch_by_id(&p.privileged_action_instance.id)
            .await
            .expect("Failed to get privileged action instance");
        assert_eq!(privileged_action_instance.request.policy, to_policy);
    } else {
        if !matches!(response, PrivilegedActionResponse::Completed(_)) {
            panic!("Expected privileged action response to be completed");
        }

        let resp = client
            .get_transaction_verification_policy(&account_id, &keys)
            .await;
        assert_eq!(resp.body.unwrap().policy, to_policy.into());
    }
}

#[rstest]
#[case(WalletTestProtocol::Legacy)]
#[case(WalletTestProtocol::PrivateCcd)]
#[tokio::test]
async fn transaction_verification_with_threshold_under_limit_approved_by_wsm(
    #[case] protocol: WalletTestProtocol,
) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let fixture = setup_fixture(&mut context, &client, &bootstrap.services, protocol).await;

    let keys = context
        .get_authentication_keys_for_account_id(&fixture.account.id)
        .unwrap();

    // Set transaction verification policy to $1000 threshold
    let threshold_policy = PolicyUpdate::Threshold(PolicyUpdateMoney {
        amount_sats: 1000,
        amount_fiat: 118,
        currency_code: USD,
    });

    client
        .update_transaction_verification_policy(
            &fixture.account.id,
            true, // app signed only
            false,
            &keys,
            &PutTransactionVerificationPolicyRequest {
                policy: threshold_policy.clone(),
            },
        )
        .await;

    // Create a PSBT for a transaction under the threshold (e.g., 1000 sats ≈ $0.50 at current rates)
    // This is a test PSBT that sends 1000 sats
    let recipient_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
    let recipient_address = recipient_wallet.get_address(AddressIndex::New).unwrap();
    let psbt = build_app_signed_psbt_for_protocol(
        &fixture,
        recipient_address,
        get_unique_test_amount(1000),
        &[],
    );

    // Use the active keyset from the account
    let signing_keyset_id = fixture.signing_keyset_id;

    // Initiate transaction verification
    let request = InitiateTransactionVerificationRequest {
        psbt: psbt.to_string(),
        fiat_currency: USD,
        bitcoin_display_unit: BitcoinDisplayUnit::Satoshi,
        signing_keyset_id,
        should_prompt_user: true,
    };

    let resp = client
        .initiate_transaction_verification(&fixture.account.id, &keys, &request)
        .await;

    // Verify the response
    match resp.body.unwrap() {
        InitiateTransactionVerificationView::Signed(
            InitiateTransactionVerificationViewSigned { hw_grant, .. },
        ) => {
            // Transaction was approved by WSM without verification because it's under the threshold
            assert_eq!(hw_grant.version, 0);
            assert!(!hw_grant.commitment.is_empty());
            assert!(!hw_grant.reverse_hash_chain.is_empty());
            assert!(!hw_grant.signature.to_string().is_empty());
            assert_eq!(
                hw_grant.hw_auth_public_key,
                fixture.account.hardware_auth_pubkey
            );
        }
        _ => panic!("Expected transaction to be signed without verification"),
    }
}

#[rstest]
#[case(WalletTestProtocol::Legacy)]
#[case(WalletTestProtocol::PrivateCcd)]
#[tokio::test]
async fn transaction_verification_with_threshold_over_limit_requires_verification(
    #[case] protocol: WalletTestProtocol,
) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let fixture = setup_fixture(&mut context, &client, &bootstrap.services, protocol).await;

    let keys = context
        .get_authentication_keys_for_account_id(&fixture.account.id)
        .unwrap();

    // Set transaction verification policy to ALWAYS
    let threshold_policy = PolicyUpdate::Always;

    client
        .update_transaction_verification_policy(
            &fixture.account.id,
            true, // app signed only
            false,
            &keys,
            &PutTransactionVerificationPolicyRequest {
                policy: threshold_policy.clone(),
            },
        )
        .await;

    let recipient_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
    let recipient_address = recipient_wallet.get_address(AddressIndex::New).unwrap();
    let psbt = build_app_signed_psbt_for_protocol(
        &fixture,
        recipient_address,
        get_unique_test_amount(1000),
        &[],
    );

    // Initiate transaction verification
    let request = InitiateTransactionVerificationRequest {
        psbt: psbt.to_string(),
        fiat_currency: USD,
        bitcoin_display_unit: BitcoinDisplayUnit::Satoshi,
        signing_keyset_id: fixture.signing_keyset_id,
        should_prompt_user: true,
    };

    let resp = client
        .initiate_transaction_verification(&fixture.account.id, &keys, &request)
        .await;

    // Verify the response
    let verification_id = match resp.body.unwrap() {
        InitiateTransactionVerificationView::VerificationRequested(
            InitiateTransactionVerificationViewRequested {
                verification_id,
                expiration,
            },
        ) => {
            // Transaction requires verification because it's over the threshold
            assert!(!verification_id.to_string().is_empty());
            assert!(expiration > OffsetDateTime::now_utc());
            verification_id
        }
        _ => panic!("Expected transaction to require verification"),
    };

    let check_resp = client
        .check_transaction_verification(&fixture.account.id, &verification_id, &keys)
        .await;
    match check_resp.body.unwrap() {
        TransactionVerificationView::Pending => (),
        _ => panic!("Expected transaction to be pending"),
    }
}

enum TokenType {
    ConfirmationToken,
    CancellationToken,
    RandomToken,
}

#[rstest]
#[case::legacy(
    TokenType::RandomToken,
    StatusCode::BAD_REQUEST,
    WalletTestProtocol::Legacy
)]
#[case::legacy_confirm(
    TokenType::ConfirmationToken,
    StatusCode::OK,
    WalletTestProtocol::Legacy
)]
#[case::legacy_cancel(
    TokenType::CancellationToken,
    StatusCode::OK,
    WalletTestProtocol::Legacy
)]
#[case::private(
    TokenType::RandomToken,
    StatusCode::BAD_REQUEST,
    WalletTestProtocol::PrivateCcd
)]
#[case::private_confirm(
    TokenType::ConfirmationToken,
    StatusCode::OK,
    WalletTestProtocol::PrivateCcd
)]
#[case::private_cancel(
    TokenType::CancellationToken,
    StatusCode::OK,
    WalletTestProtocol::PrivateCcd
)]
#[tokio::test]
async fn transaction_verification_verification_flow_test(
    #[case] use_token_type: TokenType,
    #[case] expected_status: StatusCode,
    #[case] protocol: WalletTestProtocol,
) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let fixture = setup_fixture(&mut context, &client, &bootstrap.services, protocol).await;

    let keys = context
        .get_authentication_keys_for_account_id(&fixture.account.id)
        .unwrap();

    // Set transaction verification policy to ALWAYS
    let threshold_policy = PolicyUpdate::Always;

    client
        .update_transaction_verification_policy(
            &fixture.account.id,
            true, // app signed only
            false,
            &keys,
            &PutTransactionVerificationPolicyRequest {
                policy: threshold_policy.clone(),
            },
        )
        .await;

    let recipient_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
    let recipient_address = recipient_wallet.get_address(AddressIndex::New).unwrap();
    let psbt = build_app_signed_psbt_for_protocol(
        &fixture,
        recipient_address,
        get_unique_test_amount(1000),
        &[],
    );

    // Initiate transaction verification
    let request = InitiateTransactionVerificationRequest {
        psbt: psbt.to_string(),
        fiat_currency: USD,
        bitcoin_display_unit: BitcoinDisplayUnit::Satoshi,
        signing_keyset_id: fixture.signing_keyset_id,
        should_prompt_user: true,
    };

    let resp = client
        .initiate_transaction_verification(&fixture.account.id, &keys, &request)
        .await;

    // Verify the response
    let verification_id = match resp.body.unwrap() {
        InitiateTransactionVerificationView::VerificationRequested(
            InitiateTransactionVerificationViewRequested {
                verification_id,
                expiration,
            },
        ) => {
            // Transaction requires verification because it's over the threshold
            assert!(!verification_id.to_string().is_empty());
            assert!(expiration > OffsetDateTime::now_utc());
            verification_id
        }
        _ => panic!("Expected transaction to require verification"),
    };

    let Ok(TransactionVerification::Pending(pending)) = bootstrap
        .services
        .transaction_verification_service
        .fetch(&fixture.account.id, &verification_id)
        .await
    else {
        panic!("Expected verification to be pending");
    };

    let (request, expect_verification_status) = match use_token_type {
        TokenType::ConfirmationToken => (
            ProcessTransactionVerificationTokenRequest::Confirm {
                confirm_token: pending.confirmation_token.clone(),
            },
            TransactionVerificationDiscriminants::Success,
        ),
        TokenType::CancellationToken => (
            ProcessTransactionVerificationTokenRequest::Cancel {
                cancel_token: pending.cancellation_token.clone(),
            },
            TransactionVerificationDiscriminants::Failed,
        ),
        TokenType::RandomToken => (
            ProcessTransactionVerificationTokenRequest::Confirm {
                confirm_token: Alphanumeric.sample_string(&mut rand::thread_rng(), 16),
            },
            TransactionVerificationDiscriminants::Pending,
        ),
    };
    let resp = client
        .process_transaction_verification_token(&verification_id, &request)
        .await;
    assert_eq!(resp.status_code, expected_status);

    let response = bootstrap
        .services
        .transaction_verification_service
        .fetch(&fixture.account.id, &verification_id)
        .await
        .expect("Failed to fetch verification");
    assert_eq!(
        TransactionVerificationDiscriminants::from(response),
        expect_verification_status
    );
}

#[rstest]
#[case(WalletTestProtocol::Legacy)]
#[case(WalletTestProtocol::PrivateCcd)]
#[tokio::test]
async fn transaction_verification_requires_verification_idempotent(
    #[case] protocol: WalletTestProtocol,
) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let fixture = setup_fixture(&mut context, &client, &bootstrap.services, protocol).await;

    let keys = context
        .get_authentication_keys_for_account_id(&fixture.account.id)
        .unwrap();

    // Set transaction verification policy to ALWAYS
    let threshold_policy = PolicyUpdate::Always;

    client
        .update_transaction_verification_policy(
            &fixture.account.id,
            true, // app signed only
            false,
            &keys,
            &PutTransactionVerificationPolicyRequest {
                policy: threshold_policy.clone(),
            },
        )
        .await;

    let recipient_wallet = get_funded_wallet("wpkh([c258d2e4/84h/1h/0h]tpubDDYkZojQFQjht8Tm4jsS3iuEmKjTiEGjG6KnuFNKKJb5A6ZUCUZKdvLdSDWofKi4ToRCwb9poe1XdqfUnP4jaJjCB2Zwv11ZLgSbnZSNecE/0/*)").0;
    let recipient_address = recipient_wallet.get_address(AddressIndex::New).unwrap();
    let psbt = build_app_signed_psbt_for_protocol(
        &fixture,
        recipient_address,
        get_unique_test_amount(1000),
        &[],
    );

    // Initiate transaction verification
    let request = InitiateTransactionVerificationRequest {
        psbt: psbt.to_string(),
        fiat_currency: USD,
        bitcoin_display_unit: BitcoinDisplayUnit::Satoshi,
        signing_keyset_id: fixture.signing_keyset_id,
        should_prompt_user: true,
    };

    let resp = client
        .initiate_transaction_verification(&fixture.account.id, &keys, &request)
        .await;

    // Verify the response
    let original_verification_id = match resp.body.unwrap() {
        InitiateTransactionVerificationView::VerificationRequested(
            InitiateTransactionVerificationViewRequested {
                verification_id,
                expiration,
            },
        ) => {
            // Transaction requires verification because it's over the threshold
            assert!(!verification_id.to_string().is_empty());
            assert!(expiration > OffsetDateTime::now_utc());
            verification_id
        }
        _ => panic!("Expected transaction to require verification"),
    };

    let idempotent_resp = client
        .initiate_transaction_verification(&fixture.account.id, &keys, &request)
        .await;

    // Verify the response
    let idempotent_verification_id = match idempotent_resp.body.unwrap() {
        InitiateTransactionVerificationView::VerificationRequested(
            InitiateTransactionVerificationViewRequested {
                verification_id,
                expiration,
            },
        ) => {
            // Transaction requires verification because it's over the threshold
            assert!(!verification_id.to_string().is_empty());
            assert!(expiration > OffsetDateTime::now_utc());
            verification_id
        }
        _ => panic!("Expected transaction to require verification"),
    };
    assert_eq!(original_verification_id, idempotent_verification_id);
}

/// Helper function to generate random amounts so we don't end up with the same txids
fn get_unique_test_amount(base_amount: u64) -> u64 {
    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis() as u64;
    // Use last 3 digits of timestamp to add variance while keeping amount reasonable
    base_amount + (timestamp % 1000)
}
