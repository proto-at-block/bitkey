use crate::tests::gen_services;
use crate::tests::lib::create_default_account_with_predefined_wallet;
use crate::tests::requests::axum::TestClient;
use bdk_utils::bdk::wallet::{get_funded_wallet, AddressIndex};
use bdk_utils::bdk::FeeRate;
use time::OffsetDateTime;
use transaction_verification::routes::{
    InitiateTransactionVerificationRequest, PutTransactionVerificationPolicyRequest,
};
use types::account::entities::TransactionVerificationPolicy;
use types::account::money::Money;
use types::currencies::CurrencyCode::USD;
use types::transaction_verification::entities::BitcoinDisplayUnit;
use types::transaction_verification::router::{
    InitiateTransactionVerificationView, InitiateTransactionVerificationViewRequested,
    InitiateTransactionVerificationViewSigned,
};

#[tokio::test]
async fn update_transaction_verification_policy_test() {
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

    let expected_policy = TransactionVerificationPolicy::Never;
    assert_eq!(resp.body.unwrap().policy, expected_policy);

    // Create a new transaction verification policy
    let new_policy = TransactionVerificationPolicy::Threshold(Money {
        amount: 1000,
        currency_code: USD,
    });

    // Update the transaction verification policy
    client
        .update_transaction_verification_policy(
            &account.id,
            true, // app signed only
            false,
            &keys,
            &PutTransactionVerificationPolicyRequest {
                policy: new_policy.clone(),
            },
        )
        .await;

    let resp = client
        .get_transaction_verification_policy(&account.id, &keys)
        .await;

    assert_eq!(resp.body.unwrap().policy, new_policy);
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
            assert!(!hw_grant.allowed_hash.is_empty());
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
    match resp.body.unwrap() {
        InitiateTransactionVerificationView::VerificationRequested(
            InitiateTransactionVerificationViewRequested {
                verification_id,
                expiration,
            },
        ) => {
            // Transaction requires verification because it's over the threshold
            assert!(!verification_id.to_string().is_empty());
            assert!(expiration > OffsetDateTime::now_utc());
        }
        _ => panic!("Expected transaction to require verification"),
    }
}
