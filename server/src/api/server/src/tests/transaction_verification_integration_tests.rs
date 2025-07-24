// Third-party imports
use http::StatusCode;
use rstest::rstest;
use time::OffsetDateTime;

// Internal crate imports
use crate::tests::{
    gen_services, lib::create_default_account_with_predefined_wallet, requests::axum::TestClient,
};

// Workspace crate imports
use account::service::FetchAccountInput;
use bdk_utils::bdk::{
    wallet::{get_funded_wallet, AddressIndex},
    FeeRate,
};
use transaction_verification::routes::InitiateTransactionVerificationRequest;
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
        entities::BitcoinDisplayUnit,
        router::{
            InitiateTransactionVerificationView, InitiateTransactionVerificationViewRequested,
            InitiateTransactionVerificationViewSigned, PutTransactionVerificationPolicyRequest,
            TransactionVerificationView,
        },
    },
};

#[rstest]
// Cases that should succeed without out-of-band verification
#[case::from_never_to_always(
    TransactionVerificationPolicy::Never,
    TransactionVerificationPolicy::Always,
    false, // has_hw_signed
    StatusCode::OK,
    false // expect_out_of_band
)]
#[case::from_never_to_threshold(
    TransactionVerificationPolicy::Never,
    TransactionVerificationPolicy::Threshold(Money {
        amount: 1000,
        currency_code: USD,
    }),
    false, // has_hw_signed
    StatusCode::OK,
    false // expect_out_of_band
)]
#[case::from_threshold_to_lower_threshold(
    TransactionVerificationPolicy::Threshold(Money {
        amount: 2000,
        currency_code: USD,
    }),
    TransactionVerificationPolicy::Threshold(Money {
        amount: 1000,
        currency_code: USD,
    }),
    false, // has_hw_signed
    StatusCode::OK,
    false // expect_out_of_band
)]
// Cases that require out-of-band verification (loosening policies with hw signature)
#[case::from_always_to_never(
    TransactionVerificationPolicy::Always,
    TransactionVerificationPolicy::Never,
    true, // has_hw_signed
    StatusCode::OK,
    true // expect_out_of_band
)]
#[case::from_threshold_to_never(
    TransactionVerificationPolicy::Threshold(Money {
        amount: 1000,
        currency_code: USD,
    }),
    TransactionVerificationPolicy::Never,
    true, // has_hw_signed
    StatusCode::OK,
    true // expect_out_of_band
)]
#[case::from_threshold_to_higher_threshold(
    TransactionVerificationPolicy::Threshold(Money {
        amount: 1000,
        currency_code: USD,
    }),
    TransactionVerificationPolicy::Threshold(Money {
        amount: 2000,
        currency_code: USD,
    }),
    true, // has_hw_signed
    StatusCode::OK,
    true // expect_out_of_band
)]
// Cases that should fail (loosening policies without hw signature)
#[case::from_always_to_never_without_hw_signature(
    TransactionVerificationPolicy::Always,
    TransactionVerificationPolicy::Never,
    false, // has_hw_signed
    StatusCode::FORBIDDEN,
    true // expect_out_of_band (irrelevant since it fails)
)]
#[case::from_threshold_to_higher_threshold_without_hw_signature(
    TransactionVerificationPolicy::Threshold(Money {
        amount: 1000,
        currency_code: USD,
    }),
    TransactionVerificationPolicy::Threshold(Money {
        amount: 2000,
        currency_code: USD,
    }),
    false, // has_hw_signed
    StatusCode::FORBIDDEN,
    true // expect_out_of_band (irrelevant since it fails)
)]
#[tokio::test]
async fn update_transaction_verification_policy_test(
    #[case] from_policy: TransactionVerificationPolicy,
    #[case] to_policy: TransactionVerificationPolicy,
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
        assert_eq!(resp.body.unwrap().policy, to_policy);
    }
}

#[tokio::test]
async fn transaction_verification_with_threshold_under_limit_approved_by_wsm() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let (account, wallet) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;

    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();

    // Set transaction verification policy to $1000 threshold
    let threshold_policy = TransactionVerificationPolicy::Threshold(Money {
        amount: 1000,
        currency_code: USD,
    });

    client
        .update_transaction_verification_policy(
            &account.id,
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
    let mut builder = wallet.build_tx();
    builder
        .add_recipient(recipient_address.script_pubkey(), 1000)
        .fee_rate(FeeRate::default_min_relay_fee());
    let (mut psbt, _) = builder.finish().unwrap();
    wallet.sign(&mut psbt, Default::default()).unwrap();

    // Use the active keyset from the account
    let signing_keyset_id = account.active_keyset_id;

    // Initiate transaction verification
    let request = InitiateTransactionVerificationRequest {
        psbt: psbt.to_string(),
        fiat_currency: USD,
        bitcoin_display_unit: BitcoinDisplayUnit::Satoshi,
        signing_keyset_id,
        should_prompt_user: true,
    };

    let resp = client
        .initiate_transaction_verification(&account.id, &keys, &request)
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
            assert_eq!(hw_grant.hw_auth_public_key, account.hardware_auth_pubkey);
        }
        _ => panic!("Expected transaction to be signed without verification"),
    }
}

#[tokio::test]
async fn transaction_verification_with_threshold_over_limit_requires_verification() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let (account, wallet) =
        create_default_account_with_predefined_wallet(&mut context, &client, &bootstrap.services)
            .await;

    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .unwrap();

    // Set transaction verification policy to ALWAYS
    let threshold_policy = TransactionVerificationPolicy::Always;

    client
        .update_transaction_verification_policy(
            &account.id,
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
    let mut builder = wallet.build_tx();
    builder
        .add_recipient(recipient_address.script_pubkey(), 1000)
        .fee_rate(FeeRate::default_min_relay_fee());
    let (mut psbt, _) = builder.finish().unwrap();
    wallet.sign(&mut psbt, Default::default()).unwrap();

    // Initiate transaction verification
    let request = InitiateTransactionVerificationRequest {
        psbt: psbt.to_string(),
        fiat_currency: USD,
        bitcoin_display_unit: BitcoinDisplayUnit::Satoshi,
        signing_keyset_id: account.active_keyset_id,
        should_prompt_user: true,
    };

    let resp = client
        .initiate_transaction_verification(&account.id, &keys, &request)
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
        .check_transaction_verification(&account.id, &verification_id, &keys)
        .await;
    match check_resp.body.unwrap() {
        TransactionVerificationView::Pending => (),
        _ => panic!("Expected transaction to be pending"),
    }
}
